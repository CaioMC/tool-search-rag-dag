# Resultados medidos

Execuções reais na Responses API (por um proxy compatível com OpenAI), modelo `gpt-5.4-mini`, em 06/10/2026.
LLM não é determinístico: rodando de novo, os números mudam. Use estes como ordem de grandeza e
rode `./toolsearch comparar` para medir de novo (de preferência várias vezes e com vários pedidos).

## 1. Limite de ferramentas

Teste direto na API com 250 funções simples:

| Endpoint | Modelo | Resultado |
|---|---|---|
| `/chat/completions` | gpt-4o | **Erro 400**: `Invalid 'tools': array too long. Expected an array with maximum length 128, but got an array with length 250` |
| `/responses` | gpt-4o | aceito |
| `/responses` | gpt-5.4-mini | aceito (2.773 tokens de entrada com descrições de 1 palavra) |
| `/responses` + `tool_search`, 250 funções `defer_loading` **sem namespace** | gpt-5.4-mini | aceito, mas **6.107** tokens: deferir sem agrupar em namespaces não economiza |

Conclusão: o limite de 128 é da Chat Completions. Migrar para a Responses API resolve o limite, mas
não o custo nem a lentidão; isso é o que o tool search e o RAG + DAG atacam.

## 2. Comparação dos 5 modos

Pedido: `Emita a nota fiscal do pedido de venda 42` (6 ferramentas necessárias, 3 níveis de
dependência). RAG: BM25, top 5.

```
./toolsearch comparar "Emita a nota fiscal do pedido de venda 42" --detalhes
```

| Modo | Tools na 1ª chamada | Chamadas ao modelo | Tokens de entrada (cache) | Tokens de saída | Executadas | Barradas pelo DAG | Tempo |
|---|---:|---:|---:|---:|---:|---:|---:|
| `prompt` (ingênuo) | 250 | 7 | 69.496 (65.024) | 392 | 7 | 1 | 13,6 s |
| `todas` (250 em `tools`) | 250 | 11 | **189.359** (183.808) | 300 | 9 | 1 | 28,5 s |
| `preselecao` (RAG + DAG antes) | 12 | 10 | **13.489** (5.632) | 333 | 8 | 2 | 13,2 s |
| `hosted` (OpenAI busca) | 250 deferidas | 8 | 38.869 (30.720) | 586 | 9 | 2 | 24,1 s |
| `client` (nosso RAG + DAG) | 0 | 9 | **14.453** (8.192) | 365 | 8 | 1 | 15,6 s |

Todos os modos terminaram emitindo a nota nesta rodada. Numa rodada anterior, o `hosted` desistiu
("não consegui emitir... exigiu o pré-requisito de calcular impostos").

### O que os números mostram

1. **`todas` é o pior caso**: as 250 definições completas (com JSON Schema) vão em cada uma das 11
   chamadas. Mesmo com 97% em cache, é o mais lento.
2. **`prompt` (tudo no prompt de sistema)** custa menos que `todas` porque texto é mais compacto que JSON
   Schema, mas ainda é ~5x o modo `client`.
3. **`client` e `preselecao`** mandam ~14 mil tokens: 13x menos que `todas`, 5x menos que `prompt`.
4. **`hosted`** economiza, mas buscou 6 vezes, um namespace por vez, porque não conhece as
   dependências entre namespaces. O `client` resolveu com **uma** busca, porque o DAG já devolveu
   o fecho completo.
5. **A trava do DAG funcionou em todos os modos**: chamadas fora de ordem foram barradas e o modelo
   executou o que faltava antes de tentar de novo.

### Trecho do modo `hosted` (buscas sucessivas)

```
 1. tool_search (OpenAI) paths=[faturamento_nota_fiscal] carregou [consultar_nota_fiscal, ..., emitir_nota_fiscal]
 2. tool_search (OpenAI) paths=[vendas_pedido_venda] carregou [consultar_pedido_venda, ...]
 5. tool_search (OpenAI) paths=[fiscal_imposto, fiscal_certificado_digital] carregou [..., calcular_impostos_operacao, ...]
 6. tool_search (OpenAI) paths=[fiscal_imposto, fiscal_cfop] carregou [calcular_impostos_operacao, determinar_cfop_operacao]
 7. BARRADA pelo DAG: calcular_impostos_operacao (falta [consultar_produto, determinar_cfop_operacao])
10. tool_search (OpenAI) paths=[estoque_produto] carregou [consultar_produto, ...]
14. BARRADA pelo DAG: emitir_nota_fiscal (falta [validar_certificado_digital])
15. tool_search (OpenAI) paths=[fiscal_certificado_digital] carregou [validar_certificado_digital]
```

### Trecho do modo `client` (uma busca, DAG completa)

```
1. busca RAG+DAG "Emitir a nota fiscal do pedido de venda 42" → consultar_nota_fiscal*, alterar_nota_fiscal,
   consultar_pedido_venda, consultar_produto*, determinar_cfop_operacao*, calcular_impostos_operacao*,
   incluir_nota_fiscal, pesquisar_titulo_receber*, analisar_credito_cliente*, aprovar_pedido_venda,
   validar_certificado_digital*, emitir_nota_fiscal   (* = trazida pelo DAG)
```

Repare em `pesquisar_titulo_receber*` e `analisar_credito_cliente*`: vieram porque o BM25 trouxe
`aprovar_pedido_venda` (palavras "pedido de venda"), e o DAG expandiu os pré-requisitos dela. É o
custo de expandir resultados ruidosos (ver [conceitos, seção 5](conceitos.md#5-rag-e-dag-não-ou)).

## 3. BM25 × embeddings

```
./toolsearch buscar "gerar NF-e para o pedido 42" --sem-dag
./toolsearch buscar "gerar NF-e para o pedido 42" --sem-dag --estrategia embedding
```

| Posição | BM25 | Embeddings (`text-embedding-3-small`) |
|---:|---|---|
| 1 | incluir_nota_fiscal | alterar_nota_fiscal |
| 2 | alterar_nota_fiscal | incluir_nota_fiscal |
| 3 | **gerar_balancete** (casou "gerar") | consultar_nota_fiscal |
| 4 | consultar_pedido_compra | **emitir_nota_fiscal** |
| 5 | consultar_pedido_venda | pesquisar_nota_fiscal |

O BM25 não sabe que "NF-e" é nota fiscal e se perde com o verbo "gerar". Os embeddings ficam todos
no assunto certo. Com "Emita a nota fiscal..." (palavras que estão nas descrições), o BM25 coloca
`emitir_nota_fiscal` em 1º.

### O top-k importa

Pedido `Pague o título 77 do fornecedor`, RAG por embeddings:

| top-k | O que aconteceu |
|---:|---|
| 3 | `efetuar_pagamento_titulo_pagar` ficou de fora (6º lugar). O modelo consultou o título e respondeu, corretamente, que não tinha ferramenta para pagar. |
| 8 | A ferramenta entrou; o DAG acrescentou `projetar_fluxo_caixa` e `consultar_conta_bancaria`; pagamento feito em 5 chamadas e 6.298 tokens de entrada. |

O BM25 nem chega a achar: "pague" não casa com "pagar"/"pagamento" pelo radical simples. Isso
reforça o RAG híbrido e um top-k calibrado com pedidos reais. No modo `client` há ainda uma
segunda chance: o modelo pode chamar `tool_search` de novo com outro objetivo.

## 4. Recomendações

1. **Tool search no cliente com RAG + DAG** como caminho principal (modelos gpt-5.4+): menor custo,
   uma busca resolve dependências, e o catálogo pode vir de qualquer lugar (inclusive estado do
   usuário/tenant, que o modo hospedado não permite).
2. **Pré-seleção RAG + DAG** como alternativa para modelos sem tool search (gpt-4o, Gemini,
   Claude por um proxy compatível): mesmo índice, mesmo grafo.
3. **RAG híbrido** (BM25 + embeddings) com nota mínima, para cortar acertos ruidosos antes do DAG.
4. **DAG obrigatório** em tempo de execução, independentemente do modo: é o que garante que uma
   ferramenta não rode sem a outra.
5. Medir com um conjunto de pedidos reais do seu sistema (não só um), olhando também a taxa de acerto da
   tarefa, não só tokens.
