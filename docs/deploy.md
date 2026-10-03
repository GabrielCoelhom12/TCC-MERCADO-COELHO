# Deploy e operação

## Variáveis de ambiente

| Variável | Obrigatória em produção | Descrição |
|----------|:-----------------------:|-----------|
| `DB_URL` | ✔ | JDBC do PostgreSQL, ex.: `jdbc:postgresql://host:5432/escalas_coelho` |
| `DB_USER` / `DB_PASSWORD` | ✔ | Credenciais do banco |
| `APP_JWT_SECRET` | ✔ | Chave Base64 com ≥ 32 bytes (`openssl rand -base64 48`). Sem ela, uma chave aleatória é gerada a cada start. |
| `APP_ADMIN_SENHA` | recomendada | Senha do usuário `gestor` criado no primeiro start. Sem ela, é gerada e exibida uma vez no log. |
| `APP_SEED` | — | `true` carrega o cenário de demonstração quando o banco está vazio. Use `false` em produção real. |
| `PORT` / `MANAGEMENT_PORT` | — | Porta HTTP (8080) e porta de métricas/health (8081). |
| `APP_TURN_URLS` / `APP_TURN_SEGREDO` | recomendada | Servidor TURN para chamadas de voz em redes que bloqueiam conexão direta (ex.: `turn:turn.seudominio.com:3478`) e o segredo compartilhado (`static-auth-secret` do coturn). |
| `APP_STUN_URLS` | — | Servidores STUN (padrão: STUN público do Google). |
| `APP_GERACAO_AUTOMATICA` | — | `false` desliga o agendamento do rascunho semanal (a opção também pode ser desligada em Parâmetros). Roda todo dia às 6h e ao iniciar. |
| `APP_WS_ORIGENS` | — | Origens extras aceitas no WebSocket do chat (a própria origem da aplicação é sempre aceita). |

## Ambiente completo com Docker Compose

```bash
cp .env.example .env        # edite as senhas e gere APP_JWT_SECRET
docker compose up -d --build
```

| Serviço | Endereço |
|---------|----------|
| Aplicação | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / `GRAFANA_SENHA`), com o Prometheus já configurado como fonte de dados |

Para um dashboard pronto, importe no Grafana o dashboard **JVM (Micrometer)** (ID `4701`).

## Pipeline de CI/CD (`.github/workflows/ci.yml`)

Executado a cada push e pull request:

1. **Backend**: `mvn verify` contra um PostgreSQL de serviço; o build falha se a cobertura de linhas ficar abaixo de 75% (JaCoCo).
2. **Frontend**: checagem de tipos, testes (Vitest) e cobertura mínima de 25%.
3. **SonarCloud**: análise estática e de segurança com os relatórios de cobertura dos dois lados.
4. **Imagem Docker**: construída em todo PR; em push na `main`, publicada em `ghcr.io/<usuário>/<repositório>` com as tags `latest` e `sha-<commit>`.

### Configuração do SonarCloud (uma vez)

1. Entre em https://sonarcloud.io com a conta do GitHub e importe o repositório.
2. Em *Administration → Analysis Method*, desative a *Automatic Analysis*.
3. No GitHub, em *Settings → Secrets and variables → Actions*:
   - Secret `SONAR_TOKEN`: token gerado no SonarCloud.
   - Variables `SONAR_ORGANIZATION` e `SONAR_PROJECT_KEY`: valores exibidos no SonarCloud.

## Implantação em servidor

O destino de produção recebe a imagem publicada pelo pipeline, sem cópia manual de arquivos. Em qualquer provedor
com Docker (AWS, Azure, GCP, VPS institucional):

1. Provisione um PostgreSQL gerenciado ou o serviço `db` do compose.
2. Configure as variáveis acima no provedor (nunca no repositório).
3. Execute a imagem `ghcr.io/<usuário>/<repositório>:latest` expondo a porta 8080 atrás de HTTPS.
4. Use `GET :8081/actuator/health` como health check do provedor.
5. O chat usa WebSocket em `/ws`: o proxy reverso/balanceador precisa repassar o *upgrade* de conexão
   (no Nginx: `proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade";`). Rode uma única
   instância da aplicação (o broker de mensagens do chat é em memória).

### Chamadas de voz em produção

- O navegador só libera o microfone em **HTTPS** (ou `localhost`).
- Sem TURN, a chamada funciona quando os dois lados conseguem se conectar diretamente (a maioria das redes domésticas
  e móveis). Em redes corporativas, suba um [coturn](https://github.com/coturn/coturn) com
  `use-auth-secret` e `static-auth-secret=<segredo>` (portas 3478/UDP-TCP e a faixa de retransmissão) e informe
  `APP_TURN_URLS` e o mesmo segredo em `APP_TURN_SEGREDO`. O backend gera credenciais válidas por 12 horas.

## Desenvolvimento local (sem Docker)

Pré-requisitos: JDK 21+, Maven 3.9+, Node 20+ e PostgreSQL 15+ com os bancos `escalas_coelho` e `escalas_coelho_test`.

```powershell
# Backend (http://localhost:8080). Por padrão conecta em localhost:5432 com postgres/postgres.
cd backend
mvn spring-boot:run

# Frontend com hot reload (http://localhost:5173, proxy para a API)
cd frontend
npm install
npm run dev
```

Testes:

```powershell
cd backend;  mvn verify               # testes + relatório em target/site/jacoco/index.html
cd frontend; npm run test:coverage    # relatório em coverage/
```
