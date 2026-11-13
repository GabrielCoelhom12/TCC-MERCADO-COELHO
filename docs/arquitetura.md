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
        api["API REST<br/>[Java 21 · Spring Boot 3.5]<br/>Regras, motor de cálculo,<br/>segurança JWT"]
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
        seg["seguranca<br/>JwtFiltro · AutenticadorJwt · JwtService · SecurityConfig"]
        ctrl["api<br/>Controllers REST + TratadorErros<br/>(somente HTTP)"]
        serv["servico<br/>Escalas: EscalaService · MotorService · MovimentoService · ContextoService<br/>Cadastros: Funcionario · Setor · TurnoModelo · Feriado · Projecao · Parametros · Usuario<br/>Chat: ChatService · PresencaChat · NotificadorChat"]
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
    serv --> motor
    serv --> repo
    motor --> dom
    repo --> dom
    repo --> db
```

As setas mostram o único sentido permitido das dependências: **api → servico → motor / repositorio → dominio**.
Os controllers só tratam HTTP (rota, validação da entrada, código de status); regras de negócio e transações ficam nos
serviços; o motor não conhece banco nem web. Essas regras são verificadas a cada build pelo `ArquiteturaTest`
(ArchUnit), de modo que uma dependência fora do desenho quebra o pipeline.

## Implantação

Como o sistema roda em produção (detalhes e passo a passo em [deploy.md](deploy.md)). A VM é criada pelo Terraform
([`infra/terraform`](../infra/terraform/main.tf)) e os contêineres seguem o
[`docker-compose.prod.yml`](../docker-compose.prod.yml).

```mermaid
flowchart TB
    gestor["👤 Gestor<br/>[Navegador]"]
    dev["👤 Desenvolvedor"]

    subgraph github["GitHub"]
        repo["Repositório<br/>[código-fonte]"]
        actions["GitHub Actions<br/>[CI/CD: testes, cobertura,<br/>imagem e deploy]"]
        ghcr[("GHCR<br/>[registro de imagens]<br/>tags latest e sha")]
    end

    sonar["SonarCloud<br/>[análise estática e segurança]"]

    subgraph azure["Microsoft Azure"]
        arm["Azure Resource Manager<br/>[run-command da VM]"]
    subgraph servidor["VM Ubuntu 24.04 com Docker · firewall: só 80/443"]
        proxy["caddy<br/>[Caddy 2 · HTTPS Let's Encrypt]<br/>portas 80/443"]

        subgraph compose["Docker Compose · rede interna"]
            app["app<br/>[imagem escalas-coelho]<br/>SPA + API · 8080<br/>métricas/health · 8081"]
            db[("db<br/>[PostgreSQL 17]<br/>5432 · volume dados-postgres")]
            prom["prometheus<br/>[Prometheus 3.5] · 9090"]
            graf["grafana<br/>[Grafana 12.1] · 3000<br/>volume dados-grafana"]
        end
    end
    end

    dev -- "git push / Pull Request" --> repo
    repo -- "dispara o workflow" --> actions
    actions -- "relatórios de cobertura" --> sonar
    actions -- "push da imagem (main)" --> ghcr
    actions -- "deploy: login OIDC +<br/>infra/atualizar.sh" --> arm
    arm -- "executa o script na VM<br/>(sem SSH)" --> servidor
    ghcr -- "docker pull da imagem<br/>do commit publicado" --> app

    gestor -- "HTTPS 443<br/>páginas, API e WebSocket /ws" --> proxy
    proxy -- "HTTP 8080<br/>repassa o upgrade do WebSocket" --> app
    proxy -- "/grafana" --> graf
    app -- "JDBC 5432" --> db
    prom -- "scrape a cada 15 s<br/>GET /actuator/prometheus · 8081" --> app
    graf -- "PromQL" --> prom
```

| Elemento | Exposição | Observação |
|----------|-----------|------------|
| Caddy (proxy reverso) | Pública (80/443) | Termina o TLS; senhas, tokens e mensagens do chat nunca trafegam em texto aberto. |
| `app` | Interna (8080 via proxy) | Uma única instância, porque o broker do chat é em memória. Segredos vêm de `/etc/escalas/env`, gravado pelo Terraform. |
| Porta 8081 | Somente rede interna | Health check do provedor e coleta do Prometheus; nunca exposta à internet. |
| `db` | Somente rede interna | Dados persistidos no volume `dados-postgres`; pode ser trocado por um PostgreSQL gerenciado. |
| Prometheus | Somente rede interna | Coleta as métricas da aplicação a cada 15 s. |
| Grafana | Pública, com login | Em `https://<APP_HOST>/grafana`, atrás do Caddy, com senha (`GRAFANA_SENHA`). |
| SSH (22) | Fechada | Não há acesso manual: atualizações só pelo pipeline, via run-command do Azure. |

## Stack e justificativas

Todas as escolhas estão entre as preferidas (✅) do
[Portfólio Directions](https://github.com/CatolicaSC-Portfolio/The-Portfolio-Playbook/blob/main/directions/portfolio-directions-GERAL.md).
O modelo de dados está em [modelo-de-dados.md](modelo-de-dados.md).

| Tecnologia | Por que foi escolhida |
|------------|-----------------------|
| **Java 21** | Tipagem forte e `BigDecimal` para valores monetários sem erro de arredondamento; `record` deixa os DTOs e os resultados do motor imutáveis e curtos. Versão LTS. |
| **Spring Boot 3.5** | Reúne em um só projeto API REST, segurança, JPA, validação, WebSocket, agendamento (geração automática da escala) e métricas, com convenções que reduzem configuração. |
| **Spring Security + JWT (JJWT)** | Autenticação stateless: o mesmo token vale para a API REST e para a conexão WebSocket do chat, sem sessão guardada no servidor. O token só identifica o usuário; situação e perfil são conferidos no banco a cada requisição. |
| **Spring Data JPA (Hibernate)** | Repositórios com consultas parametrizadas (protege contra SQL injection) e mapeamento das entidades sem SQL manual para o CRUD. |
| **PostgreSQL 17** | Banco relacional robusto e gratuito, com integridade referencial e restrições únicas que protegem as regras (ex.: um feriado por data). Usado também nos testes, para que eles rodem no mesmo banco da produção. |
| **React 18 + TypeScript** | A matriz colaborador × dia é uma tela muito interativa (validação a cada clique); componentes React e tipos compartilhados com a API evitam erros de contrato em tempo de compilação. |
| **Vite** | Build rápido e servidor de desenvolvimento com proxy para a API. |
| **STOMP sobre WebSocket** | Entrega em tempo real das mensagens do chat e da presença online, com o broker já embutido no Spring. |
| **JUnit 5, AssertJ, MockMvc e JaCoCo** | Testes unitários do motor e de integração da API inteira contra o PostgreSQL; o JaCoCo bloqueia o build abaixo de 75%. |
| **ArchUnit** | Transforma o desenho em camadas em teste automatizado: o build falha se um controller acessar um repositório ou se o motor depender do banco. |
| **Vitest + Testing Library** | Mesma configuração do Vite; testa as telas como o usuário as usa (por papel, rótulo e texto). |
| **ESLint (typescript-eslint)** | Análise estática do frontend no pipeline (regras de TypeScript e de React Hooks), sem nenhum aviso tolerado. |
| **Docker e Docker Compose** | Uma imagem com SPA + API e o mesmo conjunto de contêineres em desenvolvimento e produção. |
| **GitHub Actions** | Pipeline junto do código: testes, cobertura, SonarCloud, imagem e deploy, sem servidor de CI próprio. |
| **SonarCloud** | Análise estática e de segurança gratuita para repositório público, com os relatórios de cobertura dos dois lados. |
| **Prometheus + Grafana** | Monitoramento open source; o Spring Actuator expõe as métricas no formato do Prometheus sem código extra. |
| **Azure + Terraform** | VM com controle total do ambiente (não é PaaS que esconda a infraestrutura) e infraestrutura reproduzível como código; crédito estudantil sem cartão. |
| **Caddy** | Proxy reverso com HTTPS automático e suporte a WebSocket sem configuração extra. |

## Decisões

| Decisão | Motivo |
|---------|--------|
| Arquitetura em camadas, com o **motor isolado** do Spring e do banco | O núcleo de custo e de regras trabalhistas é testado com cenários em memória (`motor/*Test`), sem infraestrutura. O sentido das dependências é verificado pelo `ArquiteturaTest`. |
| **SPA + API na mesma imagem** | Um único artefato para implantar e versionar; o Spring serve o build do Vite e encaminha as rotas da SPA (`SpaController`). |
| **JWT stateless**, com o usuário conferido a cada requisição | Escala horizontal sem sessão no servidor; segredo fornecido pelo ambiente (`APP_JWT_SECRET`). Como situação e perfil vêm do banco (`AutenticadorJwt`), desativar uma conta ou mudar o perfil vale na hora, sem esperar o token expirar. |
| **PostgreSQL** em todos os ambientes, inclusive nos testes de integração | Mesmo dialeto em testes e produção; atende à exigência de persistência real. |
| **Porta de gerência separada (8081)** | Métricas e health check ficam fora da porta pública. |
| **Previsão explicável (estatística), não um modelo opaco** | Média ponderada recente do mesmo dia da semana + efeitos medidos no próprio histórico (início do mês, véspera de feriado), com a justificativa de cada número. Funciona com poucas semanas de dados, é testável com cenários exatos e o gestor entende e confia na previsão; sem histórico, vale a configuração manual. |
| **Demanda por faixa com escolha gulosa de turnos** | Pessoas por faixa = ⌈clientes ÷ (capacidade × horas)⌉; a cada passo, o modelo de turno que cobre mais faixas descobertas. Simples de explicar e próximo do ótimo para os poucos modelos de turno de uma loja. Abaixo do previsto é alerta (o gestor decide); abaixo do piso manual é erro. |
| **Chat: envio por REST, entrega por WebSocket** | As mensagens passam pela mesma validação, autorização e testes da API; o WebSocket (STOMP) só entrega eventos, e apenas após o commit. O broker é em memória: para mais de uma instância da aplicação, trocar por um broker externo (ex.: RabbitMQ). |
| **VM no Azure com Docker Compose**, provisionada com **Terraform** | Mantém o controle da arquitetura exigido pela linha Web Apps (sem PaaS que esconda a infraestrutura) e roda a mesma composição do desenvolvimento, com monitoramento junto. A infraestrutura é reproduzível e revisável em PR. |
| **Deploy por run-command com login OIDC**, sem SSH | O pipeline não guarda senha nem chave: o Azure confia só em jobs do ambiente `producao` deste repositório, e a permissão vale apenas para a VM. Cada deploy é um commit; o rollback é reimplantar um commit anterior. |
| **Caddy como proxy reverso** | HTTPS com certificado renovado automaticamente e WebSocket sem configuração extra; o Grafana fica no mesmo domínio, em `/grafana`. |
