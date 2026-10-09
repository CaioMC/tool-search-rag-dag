#!/usr/bin/env python3
"""Gera os catálogos de um ERP fictício.

Padrão: src/main/resources/catalog/erp-tools.json
  10 domínios x 5 entidades x 5 ações = 250 ferramentas.

Com --mil: src/main/resources/catalog/exemplos/catalogo-1000.json (cenário C8)
  as mesmas 250 + 30 domínios extras (EXTRA_DOMAINS) = 40 x 5 x 5 = 1000 ferramentas.
  As 250 originais e as dependências delas não mudam, então o pedido
  "Emita a nota fiscal do pedido de venda 42" continua comparável.

Cada entidade vira um namespace (5 ferramentas), seguindo a recomendação da OpenAI de
manter namespaces pequenos (menos de 10 funções).

Dependências (o DAG):
  - genéricas: alterar_X e a ação especial de X dependem de consultar_X;
  - de negócio: CROSS_DEPENDENCIES abaixo (ex.: emitir_nota_fiscal precisa dos impostos).
    Numa ação especial, a lista de negócio substitui a dependência genérica.

Uso: python3 scripts/gerar_catalogo.py [--mil]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "src/main/resources/catalog/erp-tools.json"
OUTPUT_1000 = ROOT / "src/main/resources/catalog/exemplos/catalogo-1000.json"

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

# Só para o catálogo de 1000 (--mil). Mesma estrutura de DOMAINS; dependências só as genéricas.
EXTRA_DOMAINS = {
    "producao": ("Produção: ordens de produção, roteiros, estruturas de produto, apontamentos e recursos fabris.", {
        "ordem_producao": ("ordem de produção", ["codigo_produto", "quantidade", "data_inicio"], "firmar_ordem_producao", "Firma a ordem de produção prevista, liberando-a para a fábrica."),
        "roteiro_producao": ("roteiro de produção", ["codigo_produto", "operacoes", "centro_trabalho"], "validar_roteiro_producao", "Valida a sequência de operações do roteiro de produção."),
        "estrutura_produto": ("estrutura de produto", ["codigo_produto", "componentes", "revisao"], "explodir_estrutura_produto", "Explode a estrutura do produto listando todos os componentes e quantidades."),
        "apontamento_producao": ("apontamento de produção", ["codigo_ordem", "quantidade_produzida", "operador"], "estornar_apontamento_producao", "Estorna um apontamento de produção lançado indevidamente."),
        "recurso_fabril": ("recurso fabril", ["descricao", "capacidade_hora", "centro_trabalho"], "calcular_capacidade_recurso", "Calcula a capacidade disponível do recurso fabril no período."),
    }),
    "planejamento_producao": ("Planejamento da produção: MRP, previsões de demanda, plano mestre e sequenciamento.", {
        "previsao_demanda": ("previsão de demanda", ["codigo_produto", "periodo", "quantidade"], "recalcular_previsao_demanda", "Recalcula a previsão de demanda a partir do histórico de vendas."),
        "plano_mestre": ("plano mestre de produção", ["periodo", "produtos", "versao"], "aprovar_plano_mestre", "Aprova a versão do plano mestre de produção."),
        "necessidade_material": ("necessidade de material", ["codigo_produto", "data_necessidade", "quantidade"], "executar_mrp", "Executa o cálculo de necessidades de materiais (MRP)."),
        "sequenciamento": ("sequenciamento de produção", ["centro_trabalho", "data", "ordens"], "sequenciar_ordens_producao", "Sequencia as ordens de produção minimizando trocas de ferramenta."),
        "calendario_fabril": ("calendário fabril", ["ano", "turnos", "feriados"], "gerar_calendario_fabril", "Gera o calendário fabril com turnos e feriados do ano."),
    }),
    "qualidade": ("Qualidade: inspeções, não conformidades, planos de amostragem, laudos e auditorias.", {
        "inspecao": ("inspeção de qualidade", ["codigo_lote", "tipo_inspecao", "inspetor"], "liberar_inspecao", "Libera o material inspecionado para uso ou venda."),
        "nao_conformidade": ("não conformidade", ["origem", "descricao_problema", "gravidade"], "tratar_nao_conformidade", "Registra a ação corretiva e encerra a não conformidade."),
        "plano_amostragem": ("plano de amostragem", ["codigo_produto", "nivel_inspecao", "tamanho_lote"], "calcular_tamanho_amostra", "Calcula o tamanho da amostra conforme a norma de amostragem."),
        "laudo_qualidade": ("laudo de qualidade", ["codigo_lote", "resultado", "responsavel"], "emitir_laudo_qualidade", "Emite o laudo de qualidade do lote em PDF."),
        "auditoria_qualidade": ("auditoria de qualidade", ["area", "data", "auditor"], "concluir_auditoria_qualidade", "Conclui a auditoria registrando constatações e plano de ação."),
    }),
    "manutencao": ("Manutenção: equipamentos, ordens de serviço, planos preventivos, peças de reposição e paradas.", {
        "equipamento": ("equipamento", ["descricao", "localizacao", "fabricante"], "inativar_equipamento", "Inativa um equipamento retirado de operação."),
        "ordem_servico_manutencao": ("ordem de serviço de manutenção", ["codigo_equipamento", "tipo", "prioridade"], "encerrar_ordem_servico_manutencao", "Encerra a ordem de serviço registrando horas e peças usadas."),
        "plano_preventivo": ("plano de manutenção preventiva", ["codigo_equipamento", "periodicidade", "tarefas"], "gerar_ordens_preventivas", "Gera as ordens de manutenção preventiva vencidas no período."),
        "peca_reposicao": ("peça de reposição", ["descricao", "codigo_equipamento", "estoque_minimo"], "solicitar_reposicao_peca", "Solicita a reposição de uma peça abaixo do estoque mínimo."),
        "parada_maquina": ("parada de máquina", ["codigo_equipamento", "motivo", "duracao"], "analisar_causa_parada", "Analisa a causa raiz das paradas de máquina do período."),
    }),
    "projetos": ("Projetos: projetos, tarefas, apontamentos de horas, marcos e orçamentos de projeto.", {
        "projeto": ("projeto", ["nome", "cliente", "gerente"], "encerrar_projeto", "Encerra o projeto e bloqueia novos apontamentos."),
        "tarefa_projeto": ("tarefa de projeto", ["codigo_projeto", "descricao", "responsavel"], "replanejar_tarefa_projeto", "Replaneja datas da tarefa e das tarefas dependentes."),
        "apontamento_horas": ("apontamento de horas", ["codigo_tarefa", "colaborador", "horas"], "aprovar_apontamento_horas", "Aprova as horas apontadas pelo colaborador."),
        "marco_projeto": ("marco de projeto", ["codigo_projeto", "descricao", "data_prevista"], "concluir_marco_projeto", "Conclui o marco e libera o faturamento correspondente."),
        "orcamento_projeto": ("orçamento de projeto", ["codigo_projeto", "valor", "versao"], "revisar_orcamento_projeto", "Revisa o orçamento do projeto gerando nova versão."),
    }),
    "contratos": ("Contratos: contratos de venda e serviço, aditivos, medições, reajustes e garantias contratuais.", {
        "contrato_venda": ("contrato de venda", ["codigo_cliente", "objeto", "vigencia"], "assinar_contrato_venda", "Registra a assinatura do contrato de venda pelas partes."),
        "aditivo_contrato": ("aditivo de contrato", ["codigo_contrato", "alteracao", "data"], "aprovar_aditivo_contrato", "Aprova o aditivo e atualiza as condições do contrato."),
        "medicao_contrato": ("medição de contrato", ["codigo_contrato", "periodo", "valor_medido"], "validar_medicao_contrato", "Valida a medição do período antes do faturamento."),
        "reajuste_contrato": ("reajuste de contrato", ["codigo_contrato", "indice", "data_base"], "aplicar_reajuste_contrato", "Aplica o reajuste pelo índice contratual."),
        "garantia_contratual": ("garantia contratual", ["codigo_contrato", "tipo_garantia", "valor"], "liberar_garantia_contratual", "Libera a garantia contratual ao fim das obrigações."),
    }),
    "ativo_fixo": ("Ativo fixo: bens patrimoniais, depreciação, baixas, transferências e inventário de ativos.", {
        "bem_patrimonial": ("bem patrimonial", ["descricao", "valor_aquisicao", "data_aquisicao"], "classificar_bem_patrimonial", "Classifica o bem na conta e grupo de ativo corretos."),
        "depreciacao": ("depreciação", ["codigo_bem", "metodo", "taxa_anual"], "calcular_depreciacao", "Calcula a depreciação mensal dos bens ativos."),
        "baixa_ativo": ("baixa de ativo", ["codigo_bem", "motivo", "valor_venda"], "efetivar_baixa_ativo", "Efetiva a baixa do bem e contabiliza o ganho ou a perda."),
        "transferencia_ativo": ("transferência de ativo", ["codigo_bem", "filial_destino", "centro_custo_destino"], "confirmar_transferencia_ativo", "Confirma o recebimento do bem transferido na filial de destino."),
        "inventario_ativo": ("inventário de ativos", ["filial", "data", "responsavel"], "conciliar_inventario_ativo", "Concilia os bens contados com o cadastro patrimonial."),
    }),
    "tesouraria": ("Tesouraria: aplicações, empréstimos, câmbio, adiantamentos e limites bancários.", {
        "aplicacao_financeira": ("aplicação financeira", ["banco", "valor", "vencimento"], "resgatar_aplicacao_financeira", "Resgata a aplicação financeira e credita na conta."),
        "emprestimo": ("empréstimo", ["banco", "valor", "taxa"], "calcular_parcelas_emprestimo", "Calcula o cronograma de parcelas e juros do empréstimo."),
        "contrato_cambio": ("contrato de câmbio", ["moeda", "valor", "taxa_cambio"], "liquidar_contrato_cambio", "Liquida o contrato de câmbio na data acordada."),
        "adiantamento_fornecedor": ("adiantamento a fornecedor", ["codigo_fornecedor", "valor", "data"], "compensar_adiantamento_fornecedor", "Compensa o adiantamento com títulos a pagar do fornecedor."),
        "limite_bancario": ("limite bancário", ["banco", "tipo_limite", "valor"], "renovar_limite_bancario", "Solicita a renovação do limite bancário."),
    }),
    "orcamento_empresarial": ("Orçamento empresarial: orçamentos por área, revisões, metas e acompanhamento de realizado.", {
        "orcamento_area": ("orçamento de área", ["centro_custo", "ano", "valor"], "consolidar_orcamento_area", "Consolida os orçamentos das áreas no orçamento da empresa."),
        "revisao_orcamentaria": ("revisão orçamentária", ["ano", "versao", "justificativa"], "aprovar_revisao_orcamentaria", "Aprova a revisão orçamentária proposta."),
        "meta_financeira": ("meta financeira", ["indicador", "periodo", "valor_meta"], "medir_meta_financeira", "Mede o atingimento da meta financeira no período."),
        "previsto_realizado": ("previsto x realizado", ["centro_custo", "periodo", "conta"], "analisar_desvio_orcamento", "Analisa os desvios entre orçado e realizado."),
        "rubrica_orcamentaria": ("rubrica orçamentária", ["codigo", "descricao", "tipo"], "remanejar_rubrica_orcamentaria", "Remaneja saldo entre rubricas orçamentárias."),
    }),
    "custos": ("Custos: custo padrão, custo real, rateios, formação de preço e margem.", {
        "custo_padrao": ("custo padrão", ["codigo_produto", "valor", "vigencia"], "atualizar_custo_padrao", "Atualiza o custo padrão a partir da estrutura e do roteiro."),
        "custo_real": ("custo real", ["codigo_ordem", "periodo", "valor"], "apurar_custo_real", "Apura o custo real das ordens encerradas no período."),
        "rateio_custo": ("rateio de custo", ["centro_origem", "criterio", "percentual"], "processar_rateio_custo", "Processa os rateios de custo indiretos do período."),
        "formacao_preco": ("formação de preço", ["codigo_produto", "markup", "impostos"], "simular_formacao_preco", "Simula o preço de venda a partir do custo e do markup."),
        "margem_produto": ("margem de produto", ["codigo_produto", "periodo", "canal"], "calcular_margem_contribuicao", "Calcula a margem de contribuição por produto e canal."),
    }),
    "comercio_exterior": ("Comércio exterior: importações, exportações, despachos, invoices e drawback.", {
        "processo_importacao": ("processo de importação", ["fornecedor_exterior", "incoterm", "moeda"], "registrar_declaracao_importacao", "Registra a declaração de importação no sistema aduaneiro."),
        "processo_exportacao": ("processo de exportação", ["cliente_exterior", "incoterm", "porto"], "registrar_declaracao_exportacao", "Registra a declaração única de exportação."),
        "invoice": ("invoice comercial", ["processo", "valor", "moeda"], "emitir_invoice", "Emite a invoice comercial em inglês para o cliente exterior."),
        "despacho_aduaneiro": ("despacho aduaneiro", ["processo", "despachante", "recinto"], "acompanhar_despacho_aduaneiro", "Acompanha o canal de parametrização e o desembaraço."),
        "drawback": ("ato concessório de drawback", ["numero_ato", "validade", "produtos"], "baixar_ato_drawback", "Baixa o ato concessório comprovando as exportações."),
    }),
    "frota": ("Frota: veículos, abastecimentos, multas, licenciamentos e manutenção de veículos.", {
        "veiculo": ("veículo", ["placa", "modelo", "ano"], "vender_veiculo", "Registra a venda do veículo e encerra o controle na frota."),
        "abastecimento": ("abastecimento", ["placa", "litros", "valor"], "analisar_consumo_combustivel", "Analisa o consumo médio de combustível do veículo."),
        "multa_transito": ("multa de trânsito", ["placa", "infracao", "valor"], "indicar_condutor_multa", "Indica o condutor responsável pela multa ao órgão de trânsito."),
        "licenciamento": ("licenciamento de veículo", ["placa", "exercicio", "valor"], "pagar_licenciamento", "Paga o licenciamento anual do veículo."),
        "revisao_veiculo": ("revisão de veículo", ["placa", "quilometragem", "oficina"], "agendar_revisao_veiculo", "Agenda a revisão do veículo pela quilometragem."),
    }),
    "juridico": ("Jurídico: processos judiciais, prazos, audiências, procurações e despesas jurídicas.", {
        "processo_judicial": ("processo judicial", ["numero_processo", "vara", "parte_contraria"], "atualizar_andamento_processo", "Atualiza o andamento do processo a partir do tribunal."),
        "prazo_juridico": ("prazo jurídico", ["codigo_processo", "data_limite", "tipo"], "cumprir_prazo_juridico", "Registra o cumprimento do prazo jurídico."),
        "audiencia": ("audiência", ["codigo_processo", "data", "local"], "registrar_resultado_audiencia", "Registra o resultado da audiência."),
        "procuracao": ("procuração", ["outorgado", "poderes", "validade"], "revogar_procuracao", "Revoga a procuração outorgada."),
        "despesa_juridica": ("despesa jurídica", ["codigo_processo", "tipo_despesa", "valor"], "reembolsar_despesa_juridica", "Reembolsa a despesa jurídica ao escritório."),
    }),
    "seguranca_trabalho": ("Segurança do trabalho: EPIs, acidentes, exames ocupacionais, treinamentos de segurança e riscos.", {
        "epi": ("EPI", ["descricao", "certificado_aprovacao", "validade"], "entregar_epi", "Registra a entrega do EPI ao funcionário."),
        "acidente_trabalho": ("acidente de trabalho", ["codigo_funcionario", "data", "descricao"], "comunicar_acidente_trabalho", "Emite a comunicação de acidente de trabalho (CAT)."),
        "exame_ocupacional": ("exame ocupacional", ["codigo_funcionario", "tipo_exame", "data"], "agendar_exame_ocupacional", "Agenda o exame ocupacional periódico."),
        "treinamento_seguranca": ("treinamento de segurança", ["norma", "carga_horaria", "turma"], "certificar_treinamento_seguranca", "Emite o certificado do treinamento de segurança."),
        "risco_ambiental": ("risco ambiental", ["setor", "agente", "intensidade"], "avaliar_risco_ambiental", "Avalia o risco ambiental do setor e define medidas de controle."),
    }),
    "treinamento": ("Treinamento: cursos, turmas, inscrições, avaliações e trilhas de desenvolvimento.", {
        "curso": ("curso", ["titulo", "carga_horaria", "modalidade"], "publicar_curso", "Publica o curso no catálogo interno."),
        "turma": ("turma de curso", ["codigo_curso", "data_inicio", "instrutor"], "encerrar_turma", "Encerra a turma e consolida as presenças."),
        "inscricao_curso": ("inscrição em curso", ["codigo_turma", "codigo_funcionario", "data"], "confirmar_inscricao_curso", "Confirma a inscrição do funcionário na turma."),
        "avaliacao_treinamento": ("avaliação de treinamento", ["codigo_turma", "nota", "comentario"], "consolidar_avaliacao_treinamento", "Consolida as avaliações de reação da turma."),
        "trilha_desenvolvimento": ("trilha de desenvolvimento", ["cargo", "cursos", "prazo"], "atribuir_trilha_desenvolvimento", "Atribui a trilha de desenvolvimento ao funcionário."),
    }),
    "recrutamento": ("Recrutamento: vagas, candidatos, entrevistas, propostas e admissões.", {
        "vaga": ("vaga", ["cargo", "area", "salario"], "publicar_vaga", "Publica a vaga nos canais de recrutamento."),
        "candidato": ("candidato", ["nome", "email", "curriculo"], "triar_candidato", "Faz a triagem do candidato pelos requisitos da vaga."),
        "entrevista": ("entrevista", ["codigo_candidato", "data", "entrevistador"], "registrar_parecer_entrevista", "Registra o parecer do entrevistador."),
        "proposta_emprego": ("proposta de emprego", ["codigo_candidato", "salario", "data_inicio"], "enviar_proposta_emprego", "Envia a proposta de emprego ao candidato."),
        "admissao": ("admissão", ["codigo_candidato", "cargo", "data_admissao"], "efetivar_admissao", "Efetiva a admissão e cria o cadastro do funcionário."),
    }),
    "folha_complementar": ("Folha complementar: adiantamentos salariais, PLR, rescisões, décimo terceiro e encargos.", {
        "adiantamento_salarial": ("adiantamento salarial", ["codigo_funcionario", "valor", "competencia"], "calcular_adiantamento_salarial", "Calcula o adiantamento salarial da quinzena."),
        "plr": ("participação nos lucros (PLR)", ["ano", "indicadores", "valor_total"], "distribuir_plr", "Distribui a PLR conforme as metas atingidas."),
        "rescisao": ("rescisão", ["codigo_funcionario", "tipo_rescisao", "data"], "homologar_rescisao", "Homologa a rescisão do contrato de trabalho."),
        "decimo_terceiro": ("décimo terceiro salário", ["ano", "parcela", "filial"], "calcular_decimo_terceiro", "Calcula a parcela do décimo terceiro salário."),
        "encargo_social": ("encargo social", ["competencia", "tipo_encargo", "valor"], "gerar_guia_encargo_social", "Gera a guia de recolhimento do encargo social."),
    }),
    "ecommerce": ("E-commerce: loja virtual, carrinhos, pedidos online, cupons e avaliações.", {
        "loja_virtual": ("loja virtual", ["nome", "dominio", "tema"], "publicar_loja_virtual", "Publica as alterações da loja virtual."),
        "carrinho": ("carrinho de compras", ["codigo_cliente", "itens", "data"], "recuperar_carrinho_abandonado", "Envia lembrete para recuperar o carrinho abandonado."),
        "pedido_online": ("pedido online", ["codigo_cliente", "itens", "forma_pagamento"], "integrar_pedido_online", "Integra o pedido online como pedido de venda no ERP."),
        "cupom_desconto": ("cupom de desconto", ["codigo", "percentual", "validade"], "ativar_cupom_desconto", "Ativa o cupom de desconto na loja."),
        "avaliacao_produto": ("avaliação de produto", ["codigo_produto", "nota", "comentario"], "moderar_avaliacao_produto", "Modera a avaliação antes de publicar."),
    }),
    "marketplace": ("Marketplace: anúncios, integrações com marketplaces, perguntas, repasses e reputação.", {
        "anuncio": ("anúncio", ["codigo_produto", "marketplace", "preco"], "sincronizar_anuncio", "Sincroniza preço e estoque do anúncio com o marketplace."),
        "integracao_marketplace": ("integração com marketplace", ["marketplace", "credenciais", "status"], "testar_integracao_marketplace", "Testa a conexão com a API do marketplace."),
        "pergunta_comprador": ("pergunta de comprador", ["codigo_anuncio", "pergunta", "data"], "responder_pergunta_comprador", "Responde a pergunta do comprador no marketplace."),
        "repasse_marketplace": ("repasse de marketplace", ["marketplace", "periodo", "valor"], "conciliar_repasse_marketplace", "Concilia o repasse recebido com as vendas do período."),
        "reputacao_vendedor": ("reputação de vendedor", ["marketplace", "periodo", "indice"], "analisar_reputacao_vendedor", "Analisa a reputação e os pontos de atenção do vendedor."),
    }),
    "assistencia_tecnica": ("Assistência técnica: chamados técnicos, técnicos de campo, peças, garantias de produto e visitas.", {
        "chamado_tecnico": ("chamado técnico", ["codigo_cliente", "equipamento", "defeito"], "diagnosticar_chamado_tecnico", "Registra o diagnóstico técnico do chamado."),
        "tecnico_campo": ("técnico de campo", ["nome", "regiao", "especialidade"], "escalar_tecnico_campo", "Escala o técnico de campo para o atendimento."),
        "peca_assistencia": ("peça de assistência", ["descricao", "modelo_compativel", "preco"], "requisitar_peca_assistencia", "Requisita a peça ao estoque da assistência."),
        "garantia_produto": ("garantia de produto", ["numero_serie", "data_venda", "prazo"], "verificar_cobertura_garantia", "Verifica se o defeito está coberto pela garantia."),
        "visita_tecnica": ("visita técnica", ["codigo_chamado", "data", "endereco"], "confirmar_visita_tecnica", "Confirma a visita técnica com o cliente."),
    }),
    "licitacoes": ("Licitações: editais, propostas de licitação, documentos de habilitação, pregões e atas.", {
        "edital": ("edital de licitação", ["orgao", "modalidade", "data_abertura"], "analisar_edital", "Analisa o edital e lista exigências e riscos."),
        "proposta_licitacao": ("proposta de licitação", ["codigo_edital", "valor", "validade"], "submeter_proposta_licitacao", "Submete a proposta no portal de compras públicas."),
        "documento_habilitacao": ("documento de habilitação", ["tipo_documento", "validade", "orgao_emissor"], "renovar_documento_habilitacao", "Renova a certidão ou documento de habilitação vencido."),
        "pregao": ("pregão", ["codigo_edital", "data", "lote"], "registrar_lance_pregao", "Registra um lance no pregão eletrônico."),
        "ata_registro_preco": ("ata de registro de preço", ["orgao", "itens", "vigencia"], "solicitar_adesao_ata", "Solicita a adesão de outro órgão à ata de registro de preço."),
    }),
    "agronegocio": ("Agronegócio: safras, talhões, insumos agrícolas, colheitas e contratos de grãos.", {
        "safra": ("safra", ["cultura", "ano_safra", "area_plantada"], "encerrar_safra", "Encerra a safra e apura a produtividade."),
        "talhao": ("talhão", ["fazenda", "area_hectares", "cultura"], "registrar_aplicacao_talhao", "Registra a aplicação de defensivo ou fertilizante no talhão."),
        "insumo_agricola": ("insumo agrícola", ["descricao", "tipo", "dosagem"], "calcular_dosagem_insumo", "Calcula a dosagem do insumo por hectare."),
        "colheita": ("colheita", ["codigo_talhao", "data", "quantidade_sacas"], "classificar_colheita", "Classifica os grãos colhidos por umidade e impureza."),
        "contrato_graos": ("contrato de grãos", ["comprador", "quantidade", "preco_saca"], "fixar_preco_contrato_graos", "Fixa o preço do contrato de grãos a termo."),
    }),
    "servicos": ("Serviços: ordens de serviço, catálogo de serviços, SLAs, notas de serviço e retenções.", {
        "ordem_servico": ("ordem de serviço", ["codigo_cliente", "servico", "data"], "concluir_ordem_servico", "Conclui a ordem de serviço com o aceite do cliente."),
        "catalogo_servico": ("item do catálogo de serviços", ["descricao", "preco", "codigo_municipal"], "precificar_servico", "Calcula o preço do serviço pelas horas e insumos."),
        "sla": ("acordo de nível de serviço (SLA)", ["servico", "tempo_resposta", "tempo_solucao"], "medir_cumprimento_sla", "Mede o cumprimento do SLA no período."),
        "nota_servico": ("nota fiscal de serviço (NFS-e)", ["codigo_ordem", "municipio", "aliquota_iss"], "transmitir_nota_servico", "Transmite a NFS-e para a prefeitura."),
        "retencao_tributo": ("retenção de tributo", ["codigo_nota", "tributo", "valor"], "calcular_retencoes_servico", "Calcula as retenções de ISS, IR, PIS, COFINS e CSLL do serviço."),
    }),
    "locacao": ("Locação: equipamentos locados, contratos de locação, devoluções, vistorias e cobranças de locação.", {
        "item_locacao": ("item de locação", ["descricao", "valor_diaria", "patrimonio"], "reservar_item_locacao", "Reserva o item para uma locação futura."),
        "contrato_locacao": ("contrato de locação", ["codigo_cliente", "itens", "periodo"], "prorrogar_contrato_locacao", "Prorroga o período do contrato de locação."),
        "devolucao_locacao": ("devolução de locação", ["codigo_contrato", "data", "itens"], "receber_devolucao_locacao", "Recebe os itens devolvidos e encerra a locação."),
        "vistoria": ("vistoria de item", ["codigo_item", "data", "vistoriador"], "registrar_avaria_vistoria", "Registra avarias encontradas na vistoria."),
        "cobranca_locacao": ("cobrança de locação", ["codigo_contrato", "periodo", "valor"], "faturar_cobranca_locacao", "Fatura a cobrança mensal da locação."),
    }),
    "varejo": ("Varejo: frentes de caixa (PDV), sangrias, trocas, promoções de loja e fidelidade.", {
        "caixa_pdv": ("caixa de PDV", ["loja", "numero_caixa", "operador"], "fechar_caixa_pdv", "Fecha o caixa do PDV conferindo os valores."),
        "sangria": ("sangria de caixa", ["codigo_caixa", "valor", "responsavel"], "conferir_sangria", "Confere a sangria recebida na tesouraria da loja."),
        "troca_mercadoria": ("troca de mercadoria", ["cupom_fiscal", "itens", "motivo"], "autorizar_troca_mercadoria", "Autoriza a troca de mercadoria na loja."),
        "promocao_loja": ("promoção de loja", ["descricao", "produtos", "vigencia"], "ativar_promocao_loja", "Ativa a promoção nos PDVs da loja."),
        "programa_fidelidade": ("programa de fidelidade", ["codigo_cliente", "pontos", "nivel"], "resgatar_pontos_fidelidade", "Resgata pontos de fidelidade do cliente."),
    }),
    "armazenagem": ("Armazenagem (WMS): endereços, recebimentos, separações, ondas e expedições.", {
        "endereco_armazem": ("endereço de armazém", ["rua", "predio", "nivel"], "bloquear_endereco_armazem", "Bloqueia o endereço para novas armazenagens."),
        "recebimento_mercadoria": ("recebimento de mercadoria", ["nota_entrada", "doca", "conferente"], "conferir_recebimento_mercadoria", "Confere a mercadoria recebida contra a nota de entrada."),
        "separacao": ("separação de pedido", ["codigo_pedido", "separador", "prioridade"], "concluir_separacao", "Conclui a separação e envia para conferência."),
        "onda_separacao": ("onda de separação", ["data", "pedidos", "regiao"], "liberar_onda_separacao", "Libera a onda de separação para os separadores."),
        "expedicao": ("expedição", ["codigo_pedido", "doca", "transportadora"], "despachar_expedicao", "Despacha a carga expedida."),
    }),
    "bi": ("Business intelligence: painéis, indicadores, relatórios agendados, extrações e alertas.", {
        "painel": ("painel de indicadores", ["nome", "area", "indicadores"], "atualizar_painel", "Atualiza os dados do painel de indicadores."),
        "indicador": ("indicador de desempenho", ["nome", "formula", "meta"], "calcular_indicador", "Calcula o valor do indicador no período."),
        "relatorio_agendado": ("relatório agendado", ["nome", "periodicidade", "destinatarios"], "executar_relatorio_agendado", "Executa o relatório agendado e envia aos destinatários."),
        "extracao_dados": ("extração de dados", ["origem", "filtro", "formato"], "exportar_extracao_dados", "Exporta a extração de dados para CSV ou Parquet."),
        "alerta_indicador": ("alerta de indicador", ["codigo_indicador", "condicao", "canal"], "disparar_alerta_indicador", "Dispara o alerta quando a condição do indicador é atingida."),
    }),
    "portal_cliente": ("Portal do cliente: acessos, segunda via de documentos, solicitações e notificações do portal.", {
        "acesso_portal": ("acesso ao portal", ["codigo_cliente", "email", "perfil"], "bloquear_acesso_portal", "Bloqueia o acesso do usuário ao portal."),
        "segunda_via": ("segunda via de documento", ["tipo_documento", "codigo_documento", "codigo_cliente"], "gerar_segunda_via", "Gera a segunda via do documento para download."),
        "solicitacao_portal": ("solicitação do portal", ["codigo_cliente", "tipo", "descricao"], "responder_solicitacao_portal", "Responde a solicitação aberta pelo cliente no portal."),
        "notificacao_portal": ("notificação do portal", ["codigo_cliente", "mensagem", "canal"], "enviar_notificacao_portal", "Envia a notificação ao cliente pelo portal."),
        "termo_aceite": ("termo de aceite", ["versao", "texto", "vigencia"], "registrar_aceite_termo", "Registra o aceite do termo pelo cliente."),
    }),
    "seguros": ("Seguros: apólices, sinistros, endossos, corretoras e renovações de seguro.", {
        "apolice": ("apólice de seguro", ["seguradora", "bem_segurado", "vigencia"], "cancelar_apolice", "Cancela a apólice e calcula o prêmio a restituir."),
        "sinistro": ("sinistro", ["codigo_apolice", "data_ocorrencia", "descricao"], "abrir_aviso_sinistro", "Abre o aviso de sinistro na seguradora."),
        "endosso": ("endosso de apólice", ["codigo_apolice", "alteracao", "data"], "emitir_endosso", "Solicita a emissão do endosso à seguradora."),
        "corretora": ("corretora de seguros", ["razao_social", "susep", "contato"], "avaliar_corretora", "Avalia o atendimento da corretora no período."),
        "cotacao_seguro": ("cotação de seguro", ["bem_segurado", "coberturas", "seguradoras"], "comparar_cotacoes_seguro", "Compara as cotações de seguro recebidas."),
    }),
    "sustentabilidade": ("Sustentabilidade: emissões, resíduos, consumo de energia e água, metas ESG e relatórios.", {
        "emissao_carbono": ("emissão de carbono", ["fonte", "periodo", "toneladas_co2"], "calcular_pegada_carbono", "Calcula a pegada de carbono do período por escopo."),
        "residuo": ("resíduo", ["tipo_residuo", "quantidade", "destinacao"], "emitir_manifesto_residuo", "Emite o manifesto de transporte de resíduos (MTR)."),
        "consumo_energia": ("consumo de energia", ["unidade", "periodo", "kwh"], "analisar_consumo_energia", "Analisa o consumo de energia e sugere reduções."),
        "consumo_agua": ("consumo de água", ["unidade", "periodo", "metros_cubicos"], "analisar_consumo_agua", "Analisa o consumo de água por unidade."),
        "meta_esg": ("meta ESG", ["tema", "indicador", "prazo"], "reportar_meta_esg", "Reporta o progresso da meta ESG para o relatório anual."),
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


def build(domains, expected):
    namespaces = []
    tools = []

    for domain, (domain_description, entities) in domains.items():
        for entity, (label, fields, special, special_description) in entities.items():
            namespace, entity_list = entity_tools(domain, entity, label, fields, special, special_description)
            namespaces.append({"name": namespace, "description": f"{domain_description.split(':')[0]} - {label}."})

            for tool in entity_list:
                tool["namespace"] = namespace
                tool["domain"] = domain
                tools.append(tool)

    by_name = {t["name"]: t for t in tools}

    specials = {entity[2] for _, entities in domains.values() for entity in entities.values()}

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

    if len(by_name) != len(tools):
        raise SystemExit("nomes de ferramenta repetidos entre domínios")

    assert len(tools) == expected, len(tools)
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
    thousand = "--mil" in sys.argv[1:]
    domains = {**DOMAINS, **EXTRA_DOMAINS} if thousand else DOMAINS
    output = OUTPUT_1000 if thousand else OUTPUT
    catalog = build(domains, 1000 if thousand else 250)
    output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    edges = sum(len(t["dependsOn"]) for t in catalog["tools"])
    print(f"{len(catalog['tools'])} ferramentas, {len(catalog['namespaces'])} namespaces, {edges} dependências -> {output}")
