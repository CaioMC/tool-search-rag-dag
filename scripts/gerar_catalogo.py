#!/usr/bin/env python3
"""Gera src/main/resources/catalog/erp-tools.json: 250 ferramentas de um ERP fictício.

10 domínios x 5 entidades x 5 ações = 250 ferramentas.

Cada entidade vira um namespace (5 ferramentas), seguindo a recomendação da OpenAI de
manter namespaces pequenos (menos de 10 funções).

Dependências (o DAG):
  - genéricas: alterar_X e a ação especial de X dependem de consultar_X;
  - de negócio: CROSS_DEPENDENCIES abaixo (ex.: emitir_nota_fiscal precisa dos impostos).
    Numa ação especial, a lista de negócio substitui a dependência genérica.

Uso: python3 scripts/gerar_catalogo.py
"""
import json
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "src/main/resources/catalog/erp-tools.json"

# dominio -> descrição, entidades
# entidade -> (rótulo, campos do cadastro, ação especial, descrição da ação especial)
DOMAINS = {
    "vendas": ("Vendas: clientes, pedidos de venda, orçamentos, preços e comissões.", {
        "cliente": ("cliente", ["nome", "cnpj", "email"], "analisar_credito_cliente", "Analisa o limite de crédito e o risco de inadimplência de um cliente."),
        "pedido_venda": ("pedido de venda", ["codigo_cliente", "itens", "condicao_pagamento"], "aprovar_pedido_venda", "Aprova um pedido de venda bloqueado por regra comercial ou de crédito."),
        "orcamento": ("orçamento de venda", ["codigo_cliente", "itens", "validade"], "converter_orcamento_em_pedido", "Converte um orçamento aprovado pelo cliente em pedido de venda."),
        "tabela_preco": ("tabela de preço", ["descricao", "vigencia", "moeda"], "aplicar_reajuste_tabela_preco", "Aplica reajuste percentual em todos os itens de uma tabela de preço."),
        "comissao": ("comissão de vendedor", ["codigo_vendedor", "percentual", "periodo"], "calcular_comissao", "Calcula a comissão dos vendedores sobre as vendas faturadas no período."),
    }),
    "faturamento": ("Faturamento: notas fiscais de saída, faturas, boletos e devoluções.", {
        "nota_fiscal": ("nota fiscal (NF-e)", ["codigo_pedido", "serie", "tipo_operacao"], "emitir_nota_fiscal", "Emite e transmite a nota fiscal eletrônica (NF-e) para a SEFAZ e retorna a chave de acesso."),
        "fatura": ("fatura", ["codigo_cliente", "valor", "vencimento"], "enviar_fatura_por_email", "Envia a fatura em PDF por e-mail para o cliente."),
        "boleto": ("boleto bancário", ["codigo_titulo", "banco", "vencimento"], "registrar_boleto_banco", "Registra o boleto no banco (registro online) e retorna a linha digitável."),
        "devolucao": ("devolução de venda", ["codigo_nota_fiscal", "itens", "motivo"], "processar_devolucao", "Processa a devolução: gera nota de entrada, estorna estoque e financeiro."),
        "remessa_cobranca": ("remessa de cobrança", ["banco", "data_geracao", "titulos"], "gerar_arquivo_remessa_cobranca", "Gera o arquivo CNAB de remessa de cobrança para o banco."),
    }),
    "estoque": ("Estoque: produtos, saldos, lotes, inventário e transferências.", {
        "produto": ("produto", ["descricao", "unidade", "ncm"], "calcular_custo_produto", "Calcula o custo médio do produto a partir das entradas e saídas."),
        "saldo_estoque": ("saldo de estoque", ["codigo_produto", "armazem", "quantidade"], "reservar_saldo_estoque", "Reserva quantidade de estoque de um produto para um pedido."),
        "lote": ("lote de produto", ["codigo_produto", "validade", "quantidade"], "bloquear_lote", "Bloqueia um lote para movimentação (ex.: problema de qualidade)."),
        "inventario": ("inventário", ["armazem", "data_contagem", "contador"], "encerrar_inventario", "Encerra o inventário e ajusta o saldo de estoque pelas contagens."),
        "transferencia": ("transferência entre armazéns", ["codigo_produto", "armazem_origem", "armazem_destino"], "efetivar_transferencia", "Efetiva a transferência de estoque entre armazéns ou filiais."),
    }),
    "compras": ("Compras: fornecedores, solicitações, cotações, pedidos e contratos de compra.", {
        "fornecedor": ("fornecedor", ["razao_social", "cnpj", "categoria"], "homologar_fornecedor", "Homologa um fornecedor após análise documental e de qualidade."),
        "pedido_compra": ("pedido de compra", ["codigo_fornecedor", "itens", "entrega_prevista"], "aprovar_pedido_compra", "Aprova o pedido de compra conforme a alçada do aprovador."),
        "cotacao": ("cotação de compra", ["codigo_solicitacao", "fornecedores", "prazo_resposta"], "analisar_cotacao", "Analisa as propostas da cotação e indica o fornecedor vencedor."),
        "solicitacao_compra": ("solicitação de compra", ["codigo_produto", "quantidade", "centro_custo"], "aprovar_solicitacao_compra", "Aprova a solicitação de compra de um departamento."),
        "contrato_fornecimento": ("contrato de fornecimento", ["codigo_fornecedor", "vigencia", "valor"], "renovar_contrato_fornecimento", "Renova um contrato de fornecimento por mais um período."),
    }),
    "financeiro": ("Financeiro: contas a receber, contas a pagar, bancos, fluxo de caixa e conciliação.", {
        "titulo_receber": ("título a receber", ["codigo_cliente", "valor", "vencimento"], "baixar_titulo_receber", "Baixa (liquida) um título a receber registrando o recebimento."),
        "titulo_pagar": ("título a pagar", ["codigo_fornecedor", "valor", "vencimento"], "efetuar_pagamento_titulo_pagar", "Efetua o pagamento de um título a pagar a partir de uma conta bancária."),
        "conta_bancaria": ("conta bancária", ["banco", "agencia", "numero_conta"], "importar_extrato_bancario", "Importa o extrato bancário (OFX/CNAB) de uma conta."),
        "fluxo_caixa": ("fluxo de caixa", ["data_inicial", "data_final", "moeda"], "projetar_fluxo_caixa", "Projeta o fluxo de caixa futuro com base em títulos a pagar e a receber."),
        "conciliacao": ("conciliação bancária", ["codigo_conta", "periodo", "responsavel"], "conciliar_extrato_bancario", "Concilia os lançamentos do extrato com a movimentação financeira."),
    }),
    "fiscal": ("Fiscal: impostos, CFOP, apuração de ICMS, SPED e certificado digital.", {
        "imposto": ("regra de imposto", ["tipo_imposto", "aliquota", "uf"], "calcular_impostos_operacao", "Calcula ICMS, IPI, PIS e COFINS de uma operação de venda ou compra."),
        "cfop": ("CFOP", ["codigo_cfop", "descricao", "tipo_operacao"], "determinar_cfop_operacao", "Determina o CFOP correto conforme UF de origem, destino e tipo de operação."),
        "apuracao_icms": ("apuração de ICMS", ["periodo", "filial", "regime"], "apurar_icms_periodo", "Apura o ICMS a recolher no período a partir das notas fiscais."),
        "sped": ("arquivo SPED", ["tipo_sped", "periodo", "filial"], "gerar_arquivo_sped", "Gera o arquivo SPED Fiscal (EFD ICMS/IPI) do período."),
        "certificado_digital": ("certificado digital", ["cnpj", "validade", "tipo"], "validar_certificado_digital", "Valida se o certificado digital A1/A3 está vigente para transmitir documentos."),
    }),
    "contabilidade": ("Contabilidade: lançamentos, plano de contas, centros de custo, balancete e fechamento.", {
        "lancamento_contabil": ("lançamento contábil", ["conta_debito", "conta_credito", "valor"], "estornar_lancamento_contabil", "Estorna um lançamento contábil gerando o lançamento inverso."),
        "plano_contas": ("conta do plano de contas", ["codigo_conta", "descricao", "natureza"], "bloquear_conta_contabil", "Bloqueia uma conta contábil para novos lançamentos."),
        "centro_custo": ("centro de custo", ["codigo", "descricao", "responsavel"], "ratear_centro_custo", "Rateia despesas de um centro de custo entre outros centros."),
        "balancete": ("balancete", ["periodo", "nivel", "filial"], "gerar_balancete", "Gera o balancete de verificação do período."),
        "fechamento_contabil": ("fechamento contábil", ["periodo", "filial", "responsavel"], "executar_fechamento_contabil", "Executa o fechamento contábil do mês e bloqueia o período."),
    }),
    "rh": ("Recursos humanos: funcionários, folha de pagamento, férias, ponto e benefícios.", {
        "funcionario": ("funcionário", ["nome", "cpf", "cargo"], "desligar_funcionario", "Desliga um funcionário e calcula as verbas rescisórias."),
        "folha_pagamento": ("folha de pagamento", ["competencia", "filial", "tipo_folha"], "calcular_folha_pagamento", "Calcula a folha de pagamento da competência (salários, descontos e encargos)."),
        "ferias": ("férias", ["codigo_funcionario", "data_inicio", "dias"], "programar_ferias", "Programa as férias de um funcionário respeitando o período aquisitivo."),
        "ponto": ("marcação de ponto", ["codigo_funcionario", "data", "horario"], "apurar_ponto", "Apura horas trabalhadas, extras e faltas do período."),
        "beneficio": ("benefício", ["codigo_funcionario", "tipo_beneficio", "valor"], "conceder_beneficio", "Concede um benefício (vale-transporte, refeição, plano de saúde) ao funcionário."),
    }),
    "logistica": ("Logística: transportadoras, fretes, entregas, rotas e romaneios.", {
        "transportadora": ("transportadora", ["razao_social", "cnpj", "modal"], "avaliar_transportadora", "Avalia o desempenho da transportadora (prazo, avarias, custo)."),
        "frete": ("frete", ["codigo_transportadora", "peso", "cep_destino"], "cotar_frete", "Cota o valor e o prazo do frete com as transportadoras cadastradas."),
        "entrega": ("entrega", ["codigo_nota_fiscal", "endereco", "data_prevista"], "rastrear_entrega", "Rastreia a entrega e retorna a posição atual da carga."),
        "rota": ("rota de entrega", ["regiao", "veiculo", "data"], "otimizar_rota", "Otimiza a sequência de entregas de uma rota."),
        "romaneio": ("romaneio de carga", ["veiculo", "motorista", "notas_fiscais"], "fechar_romaneio", "Fecha o romaneio de carga liberando o veículo para saída."),
    }),
    "crm": ("CRM: oportunidades, contatos, campanhas, atendimentos e contratos de clientes.", {
        "oportunidade": ("oportunidade de venda", ["codigo_cliente", "valor_estimado", "estagio"], "ganhar_oportunidade", "Marca a oportunidade como ganha e gera o orçamento correspondente."),
        "contato": ("contato", ["nome", "email", "telefone"], "registrar_interacao_contato", "Registra uma interação (ligação, reunião, e-mail) com o contato."),
        "campanha": ("campanha de marketing", ["nome", "publico_alvo", "canal"], "disparar_campanha", "Dispara a campanha para o público-alvo pelo canal configurado."),
        "atendimento": ("atendimento ao cliente", ["codigo_cliente", "assunto", "prioridade"], "encerrar_atendimento", "Encerra o chamado de atendimento registrando a solução."),
        "contrato_cliente": ("contrato de cliente", ["codigo_cliente", "vigencia", "valor_mensal"], "renovar_contrato_cliente", "Renova o contrato de serviço do cliente."),
    }),
}

# ferramenta -> ferramentas que precisam ser executadas antes.
# Para ações especiais, esta lista SUBSTITUI a dependência genérica de consultar_X.
CROSS_DEPENDENCIES = {
    # ações que não dependem de nada (cálculos e validações autocontidos)
    "determinar_cfop_operacao": [],
    "validar_certificado_digital": [],
    "projetar_fluxo_caixa": [],
    "otimizar_rota": [],
    "apurar_ponto": [],
    "importar_extrato_bancario": [],
    "incluir_pedido_venda": ["consultar_cliente", "consultar_produto", "consultar_tabela_preco"],
    "aprovar_pedido_venda": ["analisar_credito_cliente"],
    "analisar_credito_cliente": ["pesquisar_titulo_receber"],
    "converter_orcamento_em_pedido": ["incluir_pedido_venda"],
    "calcular_comissao": ["pesquisar_pedido_venda"],
    "incluir_nota_fiscal": ["consultar_pedido_venda"],
    "emitir_nota_fiscal": ["consultar_pedido_venda", "calcular_impostos_operacao", "validar_certificado_digital"],
    "calcular_impostos_operacao": ["determinar_cfop_operacao", "consultar_produto"],
    "incluir_boleto": ["consultar_titulo_receber"],
    "registrar_boleto_banco": ["consultar_conta_bancaria"],
    "processar_devolucao": ["consultar_nota_fiscal"],
    "gerar_arquivo_remessa_cobranca": ["pesquisar_boleto"],
    "reservar_saldo_estoque": ["consultar_produto"],
    "efetivar_transferencia": ["reservar_saldo_estoque"],
    "encerrar_inventario": ["pesquisar_saldo_estoque"],
    "incluir_pedido_compra": ["consultar_fornecedor", "consultar_produto"],
    "aprovar_pedido_compra": ["analisar_cotacao"],
    "analisar_cotacao": ["consultar_solicitacao_compra"],
    "incluir_titulo_receber": ["consultar_nota_fiscal"],
    "baixar_titulo_receber": ["consultar_conta_bancaria"],
    "efetuar_pagamento_titulo_pagar": ["consultar_conta_bancaria", "projetar_fluxo_caixa"],
    "conciliar_extrato_bancario": ["importar_extrato_bancario"],
    "apurar_icms_periodo": ["pesquisar_nota_fiscal"],
    "gerar_arquivo_sped": ["apurar_icms_periodo", "validar_certificado_digital"],
    "incluir_lancamento_contabil": ["consultar_plano_contas", "consultar_centro_custo"],
    "gerar_balancete": ["pesquisar_lancamento_contabil"],
    "executar_fechamento_contabil": ["gerar_balancete", "conciliar_extrato_bancario"],
    "calcular_folha_pagamento": ["apurar_ponto", "pesquisar_beneficio"],
    "desligar_funcionario": ["consultar_folha_pagamento"],
    "programar_ferias": ["consultar_funcionario"],
    "cotar_frete": ["consultar_transportadora", "consultar_pedido_venda"],
    "incluir_romaneio": ["consultar_nota_fiscal", "cotar_frete"],
    "fechar_romaneio": ["otimizar_rota"],
    "rastrear_entrega": ["consultar_romaneio"],
    "ganhar_oportunidade": ["consultar_cliente"],
    "disparar_campanha": ["pesquisar_contato"],
}


def param(name, type_, description, required=True):
    return {"name": name, "type": type_, "description": description, "required": required}


def entity_tools(domain, entity, label, fields, special, special_description):
    namespace = f"{domain}_{entity}"
    key = param("codigo", "string", f"Código do(a) {label}.")
    fields_params = [param(f, "string", f"Campo '{f}' do(a) {label}.") for f in fields]

    return namespace, [
        {
            "name": f"consultar_{entity}",
            "description": f"Consulta os dados de um(a) {label} pelo código.",
            "parameters": [key],
            "dependsOn": [],
        },
        {
            "name": f"pesquisar_{entity}",
            "description": f"Pesquisa e lista {label} por filtro livre (nome, período, status).",
            "parameters": [
                param("filtro", "string", "Texto ou expressão de filtro."),
                param("limite", "integer", "Quantidade máxima de registros.", required=False),
            ],
            "dependsOn": [],
        },
        {
            "name": f"incluir_{entity}",
            "description": f"Inclui (cadastra) um(a) novo(a) {label}.",
            "parameters": fields_params,
            "dependsOn": [],
        },
        {
            "name": f"alterar_{entity}",
            "description": f"Altera os dados de um(a) {label} existente.",
            "parameters": [key] + [dict(p, required=False) for p in fields_params],
            "dependsOn": [f"consultar_{entity}"],
        },
        {
            "name": special,
            "description": special_description,
            "parameters": [key],
            "dependsOn": [f"consultar_{entity}"],
        },
    ]


def build():
    namespaces = []
    tools = []

    for domain, (domain_description, entities) in DOMAINS.items():
        for entity, (label, fields, special, special_description) in entities.items():
            namespace, entity_list = entity_tools(domain, entity, label, fields, special, special_description)
            namespaces.append({"name": namespace, "description": f"{domain_description.split(':')[0]} - {label}."})

            for tool in entity_list:
                tool["namespace"] = namespace
                tool["domain"] = domain
                tools.append(tool)

    by_name = {t["name"]: t for t in tools}

    specials = {entity[2] for _, entities in DOMAINS.values() for entity in entities.values()}

    for name, deps in CROSS_DEPENDENCIES.items():
        if name not in by_name:
            raise SystemExit(f"ferramenta desconhecida em CROSS_DEPENDENCIES: {name}")

        if name in specials:
            by_name[name]["dependsOn"] = []

        for dep in deps:
            if dep not in by_name:
                raise SystemExit(f"dependência desconhecida: {name} -> {dep}")

            if dep not in by_name[name]["dependsOn"]:
                by_name[name]["dependsOn"].append(dep)

    assert len(tools) == 250, len(tools)
    assert_acyclic(by_name)

    return {"namespaces": namespaces, "tools": tools}


def assert_acyclic(by_name):
    state = {}

    def visit(name, path):
        if state.get(name) == "done":
            return

        if state.get(name) == "visiting":
            raise SystemExit("ciclo: " + " -> ".join(path + [name]))

        state[name] = "visiting"

        for dep in by_name[name]["dependsOn"]:
            visit(dep, path + [name])

        state[name] = "done"

    for name in by_name:
        visit(name, [])


if __name__ == "__main__":
    catalog = build()
    OUTPUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    edges = sum(len(t["dependsOn"]) for t in catalog["tools"])
    print(f"{len(catalog['tools'])} ferramentas, {len(catalog['namespaces'])} namespaces, {edges} dependências -> {OUTPUT}")
