# Arquitetura — modelo C4

Diagramas em Mermaid (renderizados pelo GitHub).

## Nível 1 — Contexto

```mermaid
flowchart LR
    gestor["👤 Gestor / Gerente<br/>Supermercado Coelho"]
    sistema["Escalas Coelho<br/>[Sistema web]<br/>Gera, valida e otimiza<br/>escalas de trabalho"]
    dev["👤 Equipe de desenvolvimento"]
    gh["GitHub Actions + GHCR<br/>[Sistema externo]"]
    sonar["SonarCloud<br/>[Sistema externo]"]

    gestor -- "Cadastra regras, gera e aprova<br/>escalas (HTTPS)" --> sistema
    dev -- "Push / Pull Request" --> gh
    gh -- "Testes, cobertura e imagem Docker" --> sistema
    gh -- "Análise estática e segurança" --> sonar
```

## Nível 2 — Contêineres

```mermaid
flowchart TB
    gestor["👤 Gestor"]

    subgraph app["Escalas Coelho (imagem Docker única)"]
        spa["SPA<br/>[React 18 + TypeScript + Vite]<br/>Telas de cadastro, matriz,<br/>simulador e painel"]
        api["API REST<br/>[Java 21 · Spring Boot 3.5]<br/>Regras, motor financeiro,<br/>segurança JWT"]
    end

    db[("PostgreSQL 17<br/>[Banco relacional]")]
    prom["Prometheus<br/>[Coleta de métricas]"]
    graf["Grafana<br/>[Dashboards]"]

    gestor -- "HTTPS" --> spa
    spa -- "JSON/HTTPS + Bearer JWT<br/>porta 8080" --> api
    api -- "Eventos do chat<br/>WebSocket/STOMP (/ws)" --> spa
    api -- "JDBC" --> db
    prom -- "GET /actuator/prometheus<br/>porta 8081 (interna)" --> api
    graf -- "PromQL" --> prom
```

## Nível 3 — Componentes da API

```mermaid
flowchart TB
    spa["SPA"]

    subgraph api["API REST (Spring Boot)"]
        seg["seguranca<br/>JwtFiltro · JwtService · SecurityConfig"]
        ctrl["api<br/>Controllers REST + TratadorErros"]
        serv["servico<br/>EscalaService · ContextoService · ChatService<br/>PresencaChat · CalendarioFeriados"]
        ws["config/seguranca<br/>WebSocketConfig · AutenticacaoStomp"]
        motor["motor (Java puro)<br/>MotorFinanceiro · ValidadorEscala · GeradorEscala<br/>SimuladorCusto · PrevisaoMovimento · PlanejadorDemanda"]
        repo["repositorio<br/>Spring Data JPA"]
        dom["dominio<br/>Entidades JPA"]
    end

    db[("PostgreSQL")]

    spa --> seg --> ctrl
    spa -. "STOMP /ws" .-> ws
    serv -- "eventos após o commit" --> ws
    ctrl --> serv
    ctrl --> repo
    serv --> motor
    serv --> repo
    motor --> dom
    repo --> dom
    repo --> db
```

## Decisões

| Decisão | Motivo |
|---------|--------|
| Arquitetura em camadas, com o **motor isolado** do Spring e do banco | O núcleo financeiro e trabalhista é testado com cenários em memória (`motor/*Test`), sem infraestrutura. |
| **SPA + API na mesma imagem** | Um único artefato para implantar e versionar; o Spring serve o build do Vite e encaminha as rotas da SPA (`SpaController`). |
| **JWT stateless** | Escala horizontal sem sessão no servidor; segredo fornecido pelo ambiente (`APP_JWT_SECRET`). |
| **PostgreSQL** em todos os ambientes, inclusive nos testes de integração | Mesmo dialeto em testes e produção; atende à exigência de persistência real. |
| **Porta de gerência separada (8081)** | Métricas e health check ficam fora da porta pública. |
| **Previsão explicável (estatística), não um modelo opaco** | Média ponderada recente do mesmo dia da semana + efeitos medidos no próprio histórico (início do mês, véspera de feriado), com a justificativa de cada número. Funciona com poucas semanas de dados, é testável com cenários exatos e o gestor entende e confia na previsão; sem histórico, vale a configuração manual. |
| **Demanda por faixa com escolha gulosa de turnos** | Pessoas por faixa = ⌈clientes ÷ (capacidade × horas)⌉; a cada passo, o modelo de turno que cobre mais faixas descobertas. Simples de explicar e próximo do ótimo para os poucos modelos de turno de uma loja. Abaixo do previsto é alerta (o gestor decide); abaixo do piso manual é erro. |
| **Chamadas de voz com WebRTC** | O áudio vai direto entre os navegadores; o servidor só controla o estado da chamada e repassa a sinalização (offer/answer/ICE) pelo mesmo WebSocket do chat. Credenciais TURN temporárias (HMAC), sem segredo no navegador. |
| **Chat: envio por REST, entrega por WebSocket** | As mensagens passam pela mesma validação, autorização e testes da API; o WebSocket (STOMP) só entrega eventos, e apenas após o commit. O broker é em memória: para mais de uma instância da aplicação, trocar por um broker externo (ex.: RabbitMQ). |
