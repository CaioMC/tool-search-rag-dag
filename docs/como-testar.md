# Como testar

Roteiro para validar a POC passo a passo, do mais barato (sem rede) ao mais completo (com o
modelo). Cada cenário tem o **comando**, a **saída esperada** e **o que observar**, e pode ser
rodado de dois jeitos: pela IDE, sem digitar argumentos (uma classe com `main` por cenário), ou
pelo terminal com `./toolsearch`.

| Nível | O que prova | Precisa de chave? | Custo / tempo |
|---|---|---|---|
| [A. Testes automatizados](#a-testes-automatizados) | Regras do DAG, RAG, laço do agente e formato da API, com dublês | não | ~10 s, sem custo |
| [B. Cenários locais](#b-cenários-locais-sem-chave) | Catálogo, DAG e RAG por BM25 na prática, e mensagens de erro | não | segundos, sem custo |
| [C. Cenários com o modelo](#c-cenários-com-o-modelo) | Embeddings, tool search hospedado e no cliente, comparação dos modos | sim | 5–30 s por execução, consome tokens |
| [D. Catálogo próprio](#d-testando-com-as-suas-ferramentas) | Se a abordagem funciona com as ferramentas de verdade | sim | depende do volume |

## Preparação

```bash
java -version          # 17 ou mais
mvn -v                 # 3.8 ou mais
git clone https://github.com/CaioMC/tool-search-rag-dag.git
cd tool-search-rag-dag
cp .env.example .env   # e preencha OPENAI_API_KEY (o .env não vai para o git)
mvn package            # compila, roda os testes e gera target/tool-search-rag-dag.jar
./toolsearch                  # mostra a ajuda
```

Saída esperada do `./toolsearch`:

```
POC de tool search com RAG + DAG (modelo padrão: gpt-5.4-mini)

Sem chave (local):
  catalogo                                  números do catálogo e do DAG
  plano <ferramenta>                        ordem de execução pelo DAG
  buscar "<pedido>" [--top 5] [--sem-dag]   RAG BM25 (+ DAG)
...
```

### A chave fica no `.env`

A aplicação lê o arquivo `.env` da pasta do projeto, tanto pelo `./toolsearch` quanto pela IDE. Não
é preciso exportar a chave a cada terminal. Prioridade, da maior para a menor:

1. valores fixados pelo cenário (ex.: C6 troca o modelo, C7 usa uma chave errada);
2. variáveis exportadas no terminal (`export OPENAI_API_KEY=...`);
3. o `.env`.

Para usar outro arquivo: `TOOLSEARCH_ENV_FILE=/caminho/arquivo.env`.

## Rodando pela IDE (sem argumentos)

Cada cenário deste guia é uma classe com `main` no pacote
`io.github.caiomc.toolsearch.adapters.cli.scenario`, separado em `local/` (seção B, sem chave),
`model/` (seção C, chama o modelo) e `support/` (o apoio `Scenario`, sem `main`). Abra a classe
e clique em *Run*: ela imprime o título, o checklist do que observar, o comando equivalente para
o terminal e a saída.

| Classe | Cenário | Chave? |
|---|---|---|
| `local/ScenarioAllLocal` | B1 a B7 em sequência | não |
| `local/ScenarioB1CatalogOverview` | B1. Tamanho do problema | não |
| `local/ScenarioB2ExecutionPlan` | B2. O DAG decide a ordem | não |
| `local/ScenarioB3RagVersusRagPlusDag` | B3. RAG sozinho × RAG + DAG | não |
| `local/ScenarioB4Bm25Limit` | B4. Limite do BM25 | não |
| `local/ScenarioB5CustomCatalog` | B5. Catálogo próprio | não |
| `local/ScenarioB6InvalidCatalogs` | B6. Catálogos inválidos | não |
| `local/ScenarioB7ArgumentErrors` | B7. Erros de argumento | não |
| `model/ScenarioC1EmbeddingSynonyms` | C1. Embeddings entendem sinônimos | sim |
| `model/ScenarioC2ClientSearchSmallCatalog` | C2. Tool search no cliente, catálogo pequeno | sim |
| `model/ScenarioC3ClientVersusHosted` | C3. Cliente × hospedado, 250 ferramentas | sim |
| `model/ScenarioC4CompareModes` | C4. Comparação dos 5 modos (leva alguns minutos) | sim |
| `model/ScenarioC5TopK` | C5. O top-k importa | sim |
| `model/ScenarioC6ModelWithoutToolSearch` | C6. Modelos sem tool search | sim |
| `model/ScenarioC7InvalidKey` | C7. Chave inválida | não (usa uma chave errada de propósito) |
| `model/ScenarioC8HostedThousandTools` | C8. Tool search hospedado com 1000 ferramentas | sim |
| `model/ScenarioC9ClientThousandTools` | C9. Tool search no cliente (RAG + DAG) com 1000 ferramentas | sim |

O diretório de trabalho da execução deve ser a raiz do projeto (padrão do IntelliJ), para o
`.env` ser encontrado. Pelo terminal, o mesmo cenário roda com:

```bash
java -cp target/tool-search-rag-dag.jar io.github.caiomc.toolsearch.adapters.cli.scenario.local.ScenarioAllLocal
```

Os cenários não chamam `System.exit`: um erro esperado (B6, B7, C7) não interrompe os seguintes.

## A. Testes automatizados

```bash
mvn verify
```

Esperado: `BUILD SUCCESS` e **57 testes, 0 falhas**. Nenhum teste usa a rede: o core é testado
com dublês escritos à mão (`ToolSearchTestDoubles`) e o adapter da OpenAI conversa com um
`HttpServer` local que devolve respostas reais capturadas da API (`src/test/resources/openai/`).

Rodar uma classe ou um único teste:

```bash
mvn test -Dtest=RunAgentServiceTest
mvn test -Dtest='RunAgentServiceTest#dagBlocksToolWhosePrerequisitesDidNotRun'
```

### Qual teste prova cada conceito

| Conceito | Teste |
|---|---|
| Fecho transitivo (o DAG traz pré-requisitos indiretos) | `ToolDependencyDagTest#withAllPrerequisitesIncludesTransitivePrerequisites` |
| Ordem de execução (ordenação topológica) | `ToolDependencyDagTest#executionOrderPutsPrerequisitesFirst` |
| Catálogo com ciclo é recusado | `ToolDependencyDagTest#rejectsCycles` |
| BM25 não entende sinônimos | `Bm25RetrieverRagTest#doesNotUnderstandSynonyms` |
| Embeddings entendem sinônimos | `EmbeddingRetrieverRagTest#findsBySynonymWhereBm25Fails` |
| RAG + DAG: o DAG completa o que o RAG achou | `SelectToolsServiceTest#withDagAddsPrerequisitesInExecutionOrder` |
| Tool search no cliente respondido com RAG + DAG | `RunAgentServiceTest#clientToolSearchIsAnsweredWithRagPlusDag` |
| **Trava do DAG em tempo de execução** | `RunAgentServiceTest#dagBlocksToolWhosePrerequisitesDidNotRun` |
| Pré-requisitos chamados em ordem no mesmo turno passam | `RunAgentServiceTest#prerequisitesCalledInOrderInTheSameTurnAreAccepted` |
| Formato `tool_search` / `namespace` / `defer_loading` | `ResponsesToolMapperTest` |
| Leitura de `tool_search_call` hospedado e no cliente | `ResponsesOutputParserTest` |
| Histórico reenviado sem os campos nulos acrescentados por proxies, `call_id` devolvido | `OpenAiResponsesSessionTest#clientToolSearchRoundTripReplaysHistoryWithoutNullFieldsAndEchoesCallId` |

> A trava do DAG quase nunca aparece numa execução manual curta: os pré-requisitos estão na
> descrição de cada função e o modelo costuma segui-los. O teste acima força a situação com um
> modelo roteirizado, então é a prova determinística de que ela funciona. Nas execuções com o
> catálogo de 250 ferramentas ela aparece com frequência (ver C3 e C4).

## B. Cenários locais (sem chave)

Estes cenários não usam a chave, mesmo que ela esteja no `.env`: nenhum deles chama o modelo.

### B1. Tamanho do problema

Pela IDE: `ScenarioB1CatalogOverview`. No terminal:

```bash
./toolsearch catalogo
```

```
Catálogo
  ferramentas ..................... 250
  namespaces ...................... 50
  arestas no DAG (dependências) ... 114
  ferramentas com pré-requisito ... 101
  maior cadeia .................... 2 níveis, terminando em aprovar_pedido_compra
```

Observe: das 250 ferramentas, 101 têm pré-requisito. Por isso não basta achar a ferramenta
"parecida": é preciso trazer as dependências dela.

### B2. O DAG decide a ordem

Pela IDE: `ScenarioB2ExecutionPlan`. No terminal:

```bash
./toolsearch plano emitir_nota_fiscal
```

```
Para executar emitir_nota_fiscal, o DAG exige esta ordem:
   1. consultar_pedido_venda
   2. consultar_produto
   3. determinar_cfop_operacao
   4. calcular_impostos_operacao         precisa de: consultar_produto, determinar_cfop_operacao
   5. validar_certificado_digital
   6. emitir_nota_fiscal                 precisa de: calcular_impostos_operacao, consultar_pedido_venda, validar_certificado_digital
```

Observe:
- [ ] todo pré-requisito aparece **antes** de quem depende dele;
- [ ] `consultar_produto` e `determinar_cfop_operacao` entram mesmo sem serem pré-requisito direto
  de `emitir_nota_fiscal` (fecho transitivo).

### B3. RAG sozinho × RAG + DAG

Pela IDE: `ScenarioB3RagVersusRagPlusDag`. No terminal:

```bash
./toolsearch buscar "emita a nota fiscal do pedido 42" --sem-dag
./toolsearch buscar "emita a nota fiscal do pedido 42" --top 1
```

Sem DAG (só RAG, ordem por nota):

```
  emitir_nota_fiscal                 [RAG] nota 13.752  ...
  incluir_nota_fiscal                [RAG] nota 9.776   ...
  alterar_nota_fiscal                [RAG] nota 9.600   ...
  consultar_nota_fiscal              [RAG] nota 7.671   ...
  pesquisar_nota_fiscal              [RAG] nota 7.100   ...
```

Com DAG e `--top 1`:

```
  consultar_pedido_venda             [DAG] pré-requisito
  consultar_produto                  [DAG] pré-requisito
  determinar_cfop_operacao           [DAG] pré-requisito
  calcular_impostos_operacao         [DAG] pré-requisito
  validar_certificado_digital        [DAG] pré-requisito
  emitir_nota_fiscal                 [RAG] nota 13.752

6 ferramentas (1 do RAG + 5 do DAG) em vez de todas do catálogo.
```

Observe:
- [ ] sem DAG, nenhuma das ferramentas de imposto aparece: o modelo não conseguiria emitir a nota;
- [ ] com DAG, uma única ferramenta encontrada pelo RAG vira o conjunto completo e ordenado;
- [ ] 6 ferramentas em vez de 250.

### B4. Limite do BM25

Pela IDE: `ScenarioB4Bm25Limit`. No terminal:

```bash
./toolsearch buscar "gerar NF-e para o pedido 42" --sem-dag
```

Observe:
- [ ] `gerar_balancete` aparece entre as primeiras (casou a palavra "gerar");
- [ ] `emitir_nota_fiscal` não aparece: BM25 casa palavras, não significado. Compare com C1.

### B5. Catálogo próprio

Pela IDE: `ScenarioB5CustomCatalog`. No terminal:

`catalog/exemplos/catalogo-mini.json` tem 3 ferramentas encadeadas
(`consultar_pedido` → `analisar_credito` → `aprovar_pedido`). Os catálogos de exemplo ficam em
`src/main/resources/catalog/exemplos/` e são lidos do classpath, então o caminho funciona de
qualquer diretório.

```bash
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-mini.json ./toolsearch catalogo
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-mini.json ./toolsearch plano aprovar_pedido
```

```
Para executar aprovar_pedido, o DAG exige esta ordem:
   1. consultar_pedido
   2. analisar_credito                   precisa de: consultar_pedido
   3. aprovar_pedido                     precisa de: analisar_credito
```

Use esse arquivo como modelo para montar catálogos de teste (ver [D](#d-testando-com-as-suas-ferramentas)).

### B6. Catálogos inválidos são recusados ao carregar

Pela IDE: `ScenarioB6InvalidCatalogs`. No terminal:

```bash
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-com-ciclo.json ./toolsearch catalogo
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-dependencia-inexistente.json ./toolsearch catalogo
TOOLSEARCH_CATALOG=/nao/existe.json ./toolsearch catalogo
```

```
Erro: Ciclo de dependências entre ferramentas: analisar_credito -> consultar_pedido -> aprovar_pedido -> analisar_credito. Remova uma das arestas do ciclo.
Erro: A ferramenta 'aprovar_pedido' depende de 'consultar_pedidos', que não existe no catálogo. Corrija o nome ou cadastre a dependência.
Erro: Não foi possível ler o catálogo de ferramentas em '/nao/existe.json': arquivo não encontrado no disco nem no classpath
```

Observe:
- [ ] o erro sai **antes** de qualquer chamada ao modelo;
- [ ] a mensagem do ciclo mostra o caminho completo.

### B7. Erros de argumento

Pela IDE: `ScenarioB7ArgumentErrors`. No terminal:

```bash
./toolsearch plano nao_existe
./toolsearch agente --modo client
./toolsearch agente "x" --modo foo
./toolsearch buscar "x" --estrategia foo
```

```
Erro: Ferramenta 'nao_existe' não existe no catálogo.
Erro: Faltou o texto do comando 'agente'. Exemplo: ./toolsearch agente "emitir a nota fiscal do pedido 42"
Erro: Modo desconhecido: 'foo'. Use prompt, todas, preselecao, hosted ou client.
Erro: Estratégia desconhecida: 'foo'. Use bm25 ou embedding.
```

Observe:
- [ ] argumentos inválidos são apontados antes de pedir a chave (mesmo sem `OPENAI_API_KEY`).

## C. Cenários com o modelo

A chave vem do `.env` (ver [Preparação](#preparação)). Padrões: API oficial `https://api.openai.com/v1` (troque com `OPENAI_BASE_URL`), modelo `gpt-5.4-mini`, embeddings
`text-embedding-3-small` (troque com `OPENAI_MODEL` e `OPENAI_EMBEDDING_MODEL`).

> **Resultados variam.** O modelo não é determinístico: número de chamadas, tokens e às vezes o
> caminho mudam a cada execução. Os números abaixo são exemplos reais; compare a **ordem de
> grandeza** e rode cada cenário mais de uma vez.

### C1. Embeddings entendem sinônimos

Pela IDE: `ScenarioC1EmbeddingSynonyms`. No terminal:

```bash
./toolsearch buscar "quero mandar a NF-e pra SEFAZ" --estrategia embedding --top 3
```

```
  consultar_nota_fiscal              [DAG] pré-requisito
  alterar_nota_fiscal                [RAG] nota 0.587
  consultar_pedido_venda             [DAG] pré-requisito
  consultar_produto                  [DAG] pré-requisito
  determinar_cfop_operacao           [DAG] pré-requisito
  calcular_impostos_operacao         [DAG] pré-requisito
  incluir_nota_fiscal                [RAG] nota 0.587
  validar_certificado_digital        [DAG] pré-requisito
  emitir_nota_fiscal                 [RAG] nota 0.679

9 ferramentas (3 do RAG + 6 do DAG) em vez de todas do catálogo.
```

Observe:
- [ ] `emitir_nota_fiscal` com a maior nota, mesmo o pedido dizendo "NF-e" e "mandar";
- [ ] a primeira busca por embeddings demora um pouco mais: ela indexa as 250 ferramentas
  (uma chamada); as próximas buscas da mesma execução reaproveitam o índice.

### C2. Agente com tool search no cliente, catálogo pequeno

Pela IDE: `ScenarioC2ClientSearchSmallCatalog`. No terminal:

```bash
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-mini.json ./toolsearch agente "Aprove o pedido 10" --modo client
```

```
   1. busca RAG+DAG "Aprovar o pedido 10 no ERP" → consultar_pedido, analisar_credito, aprovar_pedido
   2. executou consultar_pedido {"codigo":"10"} → ...
   3. executou analisar_credito {"codigo":"10"} → ...
   4. executou aprovar_pedido {"codigo":"10"} → ...

Resposta: Pedido 10 consultado, crédito analisado e pedido aprovado com sucesso.

Ferramentas enviadas na 1ª chamada: 0
Chamadas ao modelo: 5 | tokens de entrada: 2669 (cache: 0) | saída: 151 | tempo: 7.1s
```

Observe:
- [ ] **0 ferramentas na 1ª chamada**: o modelo começa só com a ferramenta de busca;
- [ ] o passo 1 é o `tool_search_call`: o objetivo (`goal`) foi escrito pelo modelo, não é o
  texto original do pedido;
- [ ] as ferramentas executam na ordem do DAG.

### C3. Agente com tool search no cliente, 250 ferramentas

Pela IDE: `ScenarioC3ClientVersusHosted`. No terminal:

```bash
./toolsearch agente "Emita a nota fiscal do pedido de venda 42" --modo client
```

Observe:
- [ ] uma busca (`busca RAG+DAG ...`) traz a ferramenta e os pré-requisitos (marcados com `*`);
- [ ] tokens de entrada na casa de 10–15 mil;
- [ ] se aparecer `BARRADA pelo DAG: ... (falta [...])`, o passo seguinte deve ser o modelo
  executando o que faltava e tentando de novo.

Repita com `--modo hosted` e compare:

```bash
./toolsearch agente "Emita a nota fiscal do pedido de venda 42" --modo hosted
```

- [ ] várias linhas `tool_search (OpenAI) paths=[...]`, um namespace por vez: a busca
  hospedada não conhece as dependências entre namespaces.

### C4. Comparação dos 5 modos

Pela IDE: `ScenarioC4CompareModes`. No terminal:

```bash
./toolsearch comparar "Emita a nota fiscal do pedido de venda 42"
./toolsearch comparar "Emita a nota fiscal do pedido de venda 42" --detalhes    # com o passo a passo de cada modo
./toolsearch comparar "Pague o título 77 do fornecedor" --modos client,preselecao
```

Exemplo real:

```
modo        tools-1ª chamadas  tok.entr. tok.saí.   execs   barr.    tempo
prompt           250        7      69496      392       7       1    13.6s
todas            250       11     189359      300       9       1    28.5s
preselecao        12       10      13489      333       8       2    13.2s
hosted           250        8      38869      586       9       2    24.1s
client             0        9      14453      365       8       1    15.6s
```

Como ler as colunas:

| Coluna | Significado |
|---|---|
| `tools-1ª` | ferramentas enviadas na primeira chamada |
| `chamadas` | idas ao modelo até a resposta final |
| `tok.entr.` | tokens de entrada somados em todas as chamadas (inclui cache) |
| `execs` | ferramentas executadas com sucesso |
| `barr.` | chamadas recusadas pela trava do DAG |

Observe:
- [ ] `todas` e `prompt` com muito mais tokens de entrada que `client` e `preselecao`;
- [ ] todos os modos terminam a tarefa (a resposta cita a nota emitida). Se algum disser que
  não conseguiu, veja o `--detalhes`: geralmente é uma ferramenta que não foi carregada.

### C5. O top-k importa

Pela IDE: `ScenarioC5TopK`. No terminal:

```bash
./toolsearch comparar "Pague o título 77 do fornecedor" --modos client,preselecao --estrategia embedding --top 3
./toolsearch agente "Pague o título 77 do fornecedor" --modo client --estrategia embedding --top 8
```

Observe:
- [ ] com `--top 3` o RAG não traz `efetuar_pagamento_titulo_pagar` e o modelo responde que não
  tem ferramenta para pagar;
- [ ] com `--top 8` ela entra (6º lugar), o DAG acrescenta `projetar_fluxo_caixa` e
  `consultar_conta_bancaria`, e o pagamento é feito.

### C6. Modelos sem tool search

Pela IDE: `ScenarioC6ModelWithoutToolSearch`. No terminal:

```bash
OPENAI_MODEL=gpt-4o ./toolsearch agente "Emita a nota fiscal do pedido 42" --modo hosted
OPENAI_MODEL=gpt-4o ./toolsearch agente "Emita a nota fiscal do pedido 42" --modo preselecao
```

Observe:
- [ ] `hosted` (e `client`) falham: `Tool 'tool_search' is not supported with gpt-4o.`;
- [ ] `preselecao` funciona: é a alternativa de RAG + DAG para modelos sem tool search.

### C7. Chave inválida

Pela IDE: `ScenarioC7InvalidKey`. No terminal:

```bash
OPENAI_API_KEY=errada ./toolsearch agente "x"
```

```
Erro: OpenAI respondeu 401 em /responses: {"error":{"message":"Incorrect API key provided: errada. ...
```

### C8. Tool search hospedado com 1000 ferramentas

Isola o tool search **da OpenAI** num catálogo 4× maior: as 1000 funções vão com
`defer_loading: true` em 200 namespaces e a OpenAI decide o que carregar. Não há RAG + DAG no
cliente; a trava do DAG em tempo de execução continua ligada, como em todos os modos.

O catálogo (`catalog/exemplos/catalogo-1000.json`) tem as **mesmas 250 ferramentas** do catálogo
padrão, com as mesmas dependências, mais 30 domínios extras de ERP (produção, qualidade,
manutenção, comércio exterior, WMS...). Assim o pedido da nota fiscal continua comparável. Para
gerar de novo: `python3 scripts/gerar_catalogo.py --mil`.

Pela IDE: `ScenarioC8HostedThousandTools`. No terminal:

```bash
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-1000.json ./toolsearch catalogo
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-1000.json ./toolsearch comparar "Emita a nota fiscal do pedido de venda 42" --modos hosted --detalhes
```

Observe:
- [ ] `catalogo` mostra 1000 ferramentas, 200 namespaces e 414 dependências;
- [ ] a API aceita as 1000 funções deferidas (ou responde com erro de limite: anote a mensagem);
- [ ] quantas linhas `tool_search (OpenAI) paths=[...]` aparecem até as 6 ferramentas da nota fiscal;
- [ ] tokens de entrada, chamadas e tempo, comparados com o `hosted` de 250 (C3 e
  [resultados](resultados.md#2-comparação-dos-5-modos)).

### C9. Tool search no cliente (RAG + DAG) com 1000 ferramentas

A contraparte do C8: o mesmo catálogo de 1000 ferramentas e o mesmo pedido, mas quem responde ao
`tool_search` é a nossa aplicação, com RAG (BM25, top 5) + DAG.

Pela IDE: `ScenarioC9ClientThousandTools`. No terminal:

```bash
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-1000.json ./toolsearch buscar "Emita a nota fiscal do pedido de venda 42"
TOOLSEARCH_CATALOG=catalog/exemplos/catalogo-1000.json ./toolsearch comparar "Emita a nota fiscal do pedido de venda 42" --modos client --detalhes
```

Observe:
- [ ] `buscar` devolve 9 ferramentas (5 do RAG + 4 do DAG) entre as 1000, sem chamar o modelo;
- [ ] `Ferramentas enviadas na 1ª chamada: 0`, mesmo com 1000 no catálogo;
- [ ] uma única linha `busca RAG+DAG ...` com os pré-requisitos marcados com `*`;
- [ ] tokens de entrada e tempo bem abaixo do C8 (comparação em
  [resultados](resultados.md#4-escala-1000-ferramentas-c8-e-c9)).

## D. Testando com as suas ferramentas

1. **Montar o catálogo.** Exporte as ferramentas do seu sistema para um JSON no formato de
   `src/main/resources/catalog/exemplos/catalogo-mini.json`:
   - `name` em snake_case (letras minúsculas, números e `_`, até 64 caracteres);
   - `namespace` agrupando no máximo ~10 ferramentas (recomendação da OpenAI para o modo hospedado);
   - `description` clara, com os termos que o usuário usaria (é o que o RAG indexa);
   - `dependsOn` com as ferramentas que **precisam** ter rodado antes.
2. **Validar a estrutura** (sem chave):
   ```bash
   TOOLSEARCH_CATALOG=/caminho/meu-catalogo.json ./toolsearch catalogo
   TOOLSEARCH_CATALOG=/caminho/meu-catalogo.json ./toolsearch plano <ferramenta-mais-complexa>
   ```
   Ciclos e dependências inexistentes aparecem aqui (ver B6).
3. **Validar o RAG** com pedidos reais de usuários, antes de gastar com o modelo:
   ```bash
   TOOLSEARCH_CATALOG=/caminho/meu-catalogo.json ./toolsearch buscar "<pedido real>" --sem-dag
   TOOLSEARCH_CATALOG=/caminho/meu-catalogo.json ./toolsearch buscar "<pedido real>" --estrategia embedding --sem-dag
   ```
   Anote se a ferramenta certa aparece e em que posição. Isso define o `--top`.
4. **Comparar os modos** com os mesmos pedidos:
   ```bash
   TOOLSEARCH_CATALOG=/caminho/meu-catalogo.json ./toolsearch comparar "<pedido real>" --estrategia embedding --top <k> --detalhes
   ```

Planilha sugerida (uma linha por pedido × modo, rodando cada um ao menos 3 vezes):

| Pedido | Ferramentas esperadas | Posição no RAG (BM25 / emb.) | Modo | Tarefa concluída? | Chamadas | Tokens de entrada | Barradas | Tempo |
|---|---|---|---|---|---:|---:|---:|---:|
| Emita a NF do pedido 42 | emitir_nota_fiscal + 5 | 1 / 1 | client | sim | 9 | 14.453 | 1 | 15,6 s |
| | | | | | | | | |

O critério principal é **tarefa concluída**; tokens e tempo desempatam.

## Solução de problemas

| Mensagem | Causa | O que fazer |
|---|---|---|
| `OPENAI_API_KEY não definida` | comando precisa do modelo e o `.env` não foi encontrado ou está sem a chave | preencher `OPENAI_API_KEY` no `.env` da raiz do projeto; pela IDE, conferir se o diretório de trabalho é a raiz |
| `Jar não encontrado. Rode primeiro: mvn package` | projeto não compilado | `mvn package` |
| `401 ... Incorrect API key` (ou `LiteLLM Virtual Key expected`, num proxy) | chave inválida | conferir `OPENAI_API_KEY` e, se usar proxy, `OPENAI_BASE_URL` |
| `Tool 'tool_search' is not supported with <modelo>` | modelo anterior ao gpt-5.4 | use `OPENAI_MODEL=gpt-5.4-mini` ou o modo `preselecao` |
| `Invalid 'tools': array too long ... maximum length 128` | mais de 128 funções na Chat Completions | esta POC usa a Responses API; não deve ocorrer aqui |
| `Unknown parameter: 'input[1].created_by'` | campo nulo acrescentado por um proxy (ex.: LiteLLM), reenviado no histórico | já tratado em `OpenAiResponsesSession`; se voltar a aparecer, o proxy passou a mandar outro campo |
| `O agente não terminou em 15 chamadas` | modelo em laço (ex.: insistindo numa ferramenta barrada) | rode com `--detalhes` no `comparar` para ver o passo a passo |
| `Ciclo de dependências...` / `depende de '...', que não existe` | catálogo inválido | corrigir o `dependsOn` no JSON |
