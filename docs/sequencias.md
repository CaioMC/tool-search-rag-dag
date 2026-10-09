# Diagramas de sequência

Nomes reais das classes e métodos do projeto.

<!-- externos: Usuário, OpenAI -->

## 1. Busca de ferramentas (RAG + DAG), sem modelo de linguagem

`./toolsearch buscar "emita a nota fiscal do pedido 42"`

```mermaid
sequenceDiagram
    actor Usuário
    participant CliController
    participant CliCommandMapper
    participant SelectToolsService
    participant ToolSearchRag
    participant Bm25RetrieverRag
    participant PrerequisiteExpanderDag
    participant ToolDependencyDag
    participant ReportPrinter

    Usuário->>CliController: buscar "emita a nota fiscal..."
    CliController->>CliCommandMapper: toSelectCommand(args)
    CliCommandMapper-->>CliController: SelectToolsCommand
    CliController->>SelectToolsService: select(SelectToolsCommand)
    Note over SelectToolsService: passo 1 - RAG
    SelectToolsService->>ToolSearchRag: findSimilar(query, strategyRag, topKRag)
    ToolSearchRag->>Bm25RetrieverRag: retrieve(query, topKRag)
    Bm25RetrieverRag-->>ToolSearchRag: top-k SelectedTool (origem RAG)
    ToolSearchRag-->>SelectToolsService: foundByRag
    Note over SelectToolsService: passo 2 - DAG (se useDag)
    SelectToolsService->>PrerequisiteExpanderDag: addPrerequisitesInOrder(foundByRag)
    PrerequisiteExpanderDag->>ToolDependencyDag: executionOrder(nomes encontrados)
    Note over ToolDependencyDag: withAllPrerequisites() acrescenta pré-requisitos<br/>Kahn ordena: pré-requisitos primeiro
    ToolDependencyDag-->>PrerequisiteExpanderDag: nomes em ordem de execução
    PrerequisiteExpanderDag-->>SelectToolsService: completedByDag (origem RAG + DAG)
    SelectToolsService-->>CliController: ToolSelection
    CliController->>ReportPrinter: selection(ToolSelection)
    ReportPrinter-->>Usuário: tabela no terminal
```

## 2. Agente com tool search no cliente (modo `client`)

`./toolsearch agente "Emita a nota fiscal do pedido de venda 42" --modo client`

```mermaid
sequenceDiagram
    participant RunAgentService
    participant OpenAiResponsesSession
    participant OpenAI
    participant ResponsesOutputParser
    participant SelectToolsService
    participant ToolDependencyDag
    participant SimulatedErpToolExecutorAdapter

    RunAgentService->>OpenAiResponsesSession: startConversation() com tools = [tool_search execution client]
    RunAgentService->>OpenAiResponsesSession: sendToModel()
    OpenAiResponsesSession->>OpenAI: POST /responses (input = pedido)
    OpenAI-->>OpenAiResponsesSession: tool_search_call (call_id, goal)
    OpenAiResponsesSession->>ResponsesOutputParser: toModelResponse(response)
    ResponsesOutputParser-->>RunAgentService: ModelResponse [ToolSearchCall]

    RunAgentService->>SelectToolsService: select(goal, topKRag, useDag=true)
    SelectToolsService-->>RunAgentService: ToolSelection (RAG + DAG, em ordem)
    RunAgentService->>OpenAiResponsesSession: addToolSearchOutput(call_id, ferramentas)

    loop até o modelo não pedir mais nada
        RunAgentService->>OpenAiResponsesSession: sendToModel()
        OpenAiResponsesSession->>OpenAI: POST /responses (histórico + tool_search_output)
        OpenAI-->>OpenAiResponsesSession: function_call(s)
        OpenAiResponsesSession-->>RunAgentService: ModelResponse [ToolCall]
        RunAgentService->>ToolDependencyDag: missingPrerequisites(tool, executadas)
        alt faltam pré-requisitos
            RunAgentService->>OpenAiResponsesSession: addToolCallOutput(pre_requisito_pendente)
        else tudo pronto
            RunAgentService->>SimulatedErpToolExecutorAdapter: execute(tool, argumentos)
            SimulatedErpToolExecutorAdapter-->>RunAgentService: resultado JSON
            RunAgentService->>OpenAiResponsesSession: addToolCallOutput(resultado)
        end
    end

    OpenAI-->>OpenAiResponsesSession: message (resposta final)
    OpenAiResponsesSession-->>RunAgentService: ModelResponse final
    Note over RunAgentService: AgentRunReport com passos, tokens e tempo
```

## 3. Agente com tool search hospedado (modo `hosted`)

A diferença: a busca acontece dentro da OpenAI. A aplicação só observa.

```mermaid
sequenceDiagram
    participant RunAgentService
    participant OpenAiResponsesSession
    participant ResponsesToolMapper
    participant OpenAI
    participant ResponsesOutputParser

    RunAgentService->>OpenAiResponsesSession: startConversation() com as 250 ferramentas
    OpenAiResponsesSession->>ResponsesToolMapper: toolsField(request)
    ResponsesToolMapper-->>OpenAiResponsesSession: tool_search + 50 namespaces com defer_loading
    RunAgentService->>OpenAiResponsesSession: sendToModel()
    OpenAiResponsesSession->>OpenAI: POST /responses
    Note over OpenAI: escolhe namespaces pela descrição<br/>e carrega as funções deles
    OpenAI-->>OpenAiResponsesSession: tool_search_call + tool_search_output (server) + function_call
    OpenAiResponsesSession->>ResponsesOutputParser: toModelResponse(response)
    ResponsesOutputParser-->>RunAgentService: ModelResponse [HostedToolSearch, ToolCall]
    Note over RunAgentService: HostedToolSearch só vira AgentStep.HostedSearch<br/>ToolCall segue o mesmo fluxo do modo client
```

## 4. Composição (composition root)

```mermaid
sequenceDiagram
    participant Main
    participant ApplicationFactory
    participant JsonToolCatalogAdapter
    participant ToolDependencyDag
    participant CliArgumentsParser
    participant CliController

    Main->>ApplicationFactory: new ApplicationFactory(System.getenv())
    ApplicationFactory->>JsonToolCatalogAdapter: load()
    JsonToolCatalogAdapter-->>ApplicationFactory: ToolCatalog (valida dependências)
    ApplicationFactory->>ToolDependencyDag: of(catalog)
    Note over ToolDependencyDag: recusa ciclos
    Main->>ApplicationFactory: cliController(System.out)
    ApplicationFactory-->>Main: CliController com casos de uso, CliCommandMapper e ReportPrinter
    Main->>CliArgumentsParser: parse(args)
    CliArgumentsParser-->>Main: CliArguments
    Main->>CliController: handle(CliArguments)
    Note over CliController: RunAgentUseCase vem de um Supplier<br/>a OpenAI só é criada se o comando precisar
```
