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

## 4. Escala: 1000 ferramentas (C8 e C9)

### 4.1 Tool search hospedado (C8)

Pergunta: o tool search **hospedado** da OpenAI aguenta um catálogo 4× maior? O guia oficial não
documenta um número máximo de ferramentas, então medimos. Catálogo
`catalog/exemplos/catalogo-1000.json`: as mesmas 250 ferramentas (e dependências) do catálogo
padrão + 30 domínios extras = **1000 funções com `defer_loading` em 200 namespaces**. Só o modo
`hosted`, sem RAG + DAG no cliente; a trava do DAG continua ligada.

```
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-1000.json ./toolsearch comparar "Emita a nota fiscal do pedido de venda 42" --modos hosted --detalhes
```

3 execuções com 1000 e, para comparar no mesmo dia, 3 com 250 (`gpt-5.4-mini`, 09/10/2026):

| Catálogo | Execução | Emitiu a nota? | Chamadas | Buscas | Barradas | Tokens de entrada (cache) | Tempo |
|---|---:|---|---:|---:|---:|---:|---:|
| 1000 | 1 | **sim** | 6 | 3 | 2 | 64.317 (40.448) | 36,7 s |
| 1000 | 2 | não (desistiu ao ser barrado) | 5 | 3 | 3 | 50.204 (45.568) | 26,7 s |
| 1000 | 3 | não (criou a nota com `incluir_nota_fiscal`) | 6 | 3 | 3 | 60.329 (53.760) | 32,5 s |
| 250 | 1 | não (desistiu ao ser barrado) | 6 | 3 | 2 | 24.839 (14.848) | 16,9 s |
| 250 | 2 | não (criou a nota com `incluir_nota_fiscal`) | 3 | 1 | 1 | 9.813 (7.168) | 8,1 s |
| 250 | 3 | não (criou a nota com `incluir_nota_fiscal`) | 4 | 3 | 2 | 15.529 (12.288) | 11,5 s |

Como as execuções que falham terminam mais cedo (e gastam menos), a comparação justa é **por
chamada ao modelo**:

| Por chamada ao modelo (média das 3) | 250 ferramentas | 1000 ferramentas | Variação |
|---|---:|---:|---:|
| Tokens de entrada | ~3.800 | ~10.300 | **~2,7×** |
| Latência | ~2,8 s | ~5,6 s | **~2×** |

### O que o C8 mostra

1. **A API aceita 1000 funções deferidas**: nenhuma recusa por limite. O tool search hospedado
   "aguenta" o volume.
2. **O custo fixo cresce com o catálogo**: o índice de 200 namespaces (nome + descrição) entra em
   **toda** chamada. 4× mais ferramentas → ~2,7× mais tokens e ~2× mais latência por chamada,
   antes de o modelo fazer qualquer coisa útil. O cache ajuda no preço, não no tamanho do contexto.
3. **Aguentar não é resolver**: só 1 das 6 execuções emitiu a nota. A busca hospedada não conhece
   as dependências entre namespaces, então o modelo é barrado pela trava do DAG e, muitas vezes,
   desiste ou troca a tarefa por uma parecida (`incluir_nota_fiscal` em vez de
   `emitir_nota_fiscal`). Esse problema já existe com 250 e não melhora com 1000.
4. **Para catálogos grandes, o modo `client` (RAG + DAG) é o caminho**: a 1ª chamada leva 0
   ferramentas qualquer que seja o tamanho do catálogo, e uma busca devolve a ferramenta com
   todos os pré-requisitos, já ordenados (ver 4.2).

### 4.2 Tool search no cliente com RAG + DAG (C9)

Mesmo catálogo de 1000 ferramentas, mesmo pedido, modo `client` (RAG BM25 top 5 + DAG), 3
execuções no mesmo dia do C8:

```
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-1000.json ./toolsearch comparar "Emita a nota fiscal do pedido de venda 42" --modos client --detalhes
```

| Execução | Emitiu a nota? | Chamadas | Buscas | Barradas | Tokens de entrada (cache) | Tempo |
|---:|---|---:|---:|---:|---:|---:|
| 1 | **sim** | 10 | 1 | 1 | 14.362 (8.704) | 15,7 s |
| 2 | não (parou em `incluir_nota_fiscal`) | 5 | 1 | 0 | 6.200 (2.048) | 9,3 s |
| 3 | **sim** | 7 | 1 | 0 | 10.330 (5.120) | 10,7 s |

Em todas as execuções, a única busca devolveu as 6 ferramentas necessárias, com os pré-requisitos
completados pelo DAG. O resto variou com o objetivo que o modelo escreveu: na execução 1 vieram
9 ferramentas (+ `consultar_nota_fiscal`, `alterar_nota_fiscal`, `incluir_nota_fiscal`); nas
execuções 2 e 3, com um objetivo mais longo, o BM25 trouxe também `aprovar_pedido_venda` e o DAG
acrescentou os pré-requisitos dela (`analisar_credito_cliente`, `pesquisar_titulo_receber`), 12 no
total.

### 4.3 C8 × C9: as mesmas 1000 ferramentas

| Média das 3 execuções | `hosted` (C8) | `client` RAG + DAG (C9) | Diferença |
|---|---:|---:|---:|
| Ferramentas na 1ª chamada | 1000 | **0** | — |
| Buscas por execução | 3 | **1** | 3× menos |
| Tokens de entrada por execução | ~58.300 | **~10.300** | **~5,7× menos** |
| Tokens de entrada por chamada | ~10.300 | **~1.400** | **~7× menos** |
| Latência por chamada | ~5,6 s | **~1,7 s** | **~3,4× menor** |
| Tempo por execução | ~32,0 s | **~11,9 s** | **~2,7× menor** |
| Execuções que emitiram a nota | 1 de 3 | **2 de 3** | — |

### O que a comparação mostra

1. **O custo do `client` não depende do tamanho do catálogo.** ~1.400 tokens por chamada com 1000
   ferramentas, na mesma faixa do `client` com 250 (~1.600 na medição de 06/10). No `hosted`, o
   índice de namespaces cresce com o catálogo e vai em toda chamada.
2. **Uma busca basta**: o DAG entrega os pré-requisitos que estão em outros namespaces. O `hosted`
   precisou de 3 buscas e foi barrado 2 a 3 vezes por execução.
3. **Não é perfeito**: o RAG também traz ferramentas parecidas e ruidosas (`incluir_nota_fiscal`),
   e numa execução o modelo confundiu "incluir" com "emitir". Um RAG mais preciso (híbrido, nota
   mínima) ou descrições mais distintas atacam esse ponto.

> Amostra pequena (3 + 3) e modelo não determinístico: a taxa de conclusão e os tokens variam
> entre rodadas (na medição de 06/10, o `hosted` de 250 concluiu). O que se mantém é a ordem de
> grandeza do custo por chamada e o motivo das falhas: dependências entre namespaces.

## 5. Recomendações

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
