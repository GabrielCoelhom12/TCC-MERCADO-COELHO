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
| `APP_GERACAO_AUTOMATICA` | — | `false` desliga o agendamento do rascunho semanal (a opção também pode ser desligada em Parâmetros). Roda todo dia às 6h e ao iniciar. |
| `APP_WS_ORIGENS` | — | Origens extras aceitas no WebSocket do chat (a própria origem da aplicação é sempre aceita). |

## Ambientes

| Ambiente | Onde | Como é atualizado | Dados |
|----------|------|-------------------|-------|
| **Desenvolvimento** | Máquina do desenvolvedor (`docker compose` ou sem Docker) | Manualmente, a partir da branch de trabalho | Cenário fictício (`APP_SEED=true`) |
| **Integração** | GitHub Actions, a cada push e pull request | Pipeline: testes contra um PostgreSQL descartável | Criados e apagados pelos testes |
| **Produção** | VM no Azure, `https://<APP_HOST>` | Pipeline, a cada push na `main` que passa em todos os testes | Do parceiro (ou o cenário de demonstração) |

O parceiro testa sempre a versão de produção, que só recebe código aprovado no pipeline.

## Ambiente completo com Docker Compose (desenvolvimento)

```bash
cp .env.example .env        # edite as senhas e gere APP_JWT_SECRET
docker compose up -d --build
```

O `docker compose` combina a base comum (`docker-compose.yml`) com o `docker-compose.override.yml`, que constrói a
imagem localmente e publica as portas:

| Serviço | Endereço |
|---------|----------|
| Aplicação | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / `GRAFANA_SENHA`), com o Prometheus já configurado como fonte de dados |

Para um dashboard pronto, importe no Grafana o dashboard **JVM (Micrometer)** (ID `4701`).

## Pipeline de CI/CD (`.github/workflows/ci.yml`)

Executado a cada push e pull request. Um job só começa se os anteriores passarem:

1. **Backend**: `mvn verify` contra um PostgreSQL de serviço; o build falha se a cobertura de linhas ficar abaixo de 75% (JaCoCo).
2. **Frontend**: checagem de tipos, testes (Vitest) e cobertura mínima de 25%.
3. **Infraestrutura**: `terraform fmt -check` e `terraform validate` em `infra/terraform`.
4. **SonarCloud**: análise estática e de segurança com os relatórios de cobertura dos dois lados.
5. **Imagem Docker**: construída em todo PR; em push na `main`, publicada em `ghcr.io/<usuário>/<repositório>` com as tags `latest` e `sha-<commit>`.
6. **Deploy** (só push na `main`): entra no Azure por OIDC, executa o `infra/atualizar.sh` na VM e confere se a
   aplicação responde em `APP_URL`. Não há SSH nem cópia manual de arquivos.

A Wiki tem um workflow próprio (`wiki.yml`), que publica a pasta `docs/` a cada mudança nela.

### Configuração do SonarCloud (uma vez)

1. Entre em https://sonarcloud.io com a conta do GitHub e importe o repositório.
2. Em *Administration → Analysis Method*, desative a *Automatic Analysis*.
3. No GitHub, em *Settings → Secrets and variables → Actions*:
   - Secret `SONAR_TOKEN`: token gerado no SonarCloud.
   - Variables `SONAR_ORGANIZATION` e `SONAR_PROJECT_KEY`: valores exibidos no SonarCloud.

### Configuração da Wiki (uma vez)

Em *Settings → General → Features*, deixe **Wikis** marcado; na aba *Wiki*, crie qualquer primeira página (ela será
substituída). A partir daí o workflow `wiki.yml` mantém a Wiki igual à pasta `docs/`.

## Produção no Azure

A infraestrutura é código (`infra/terraform`): uma VM Ubuntu com Docker, IP fixo com endereço
`<prefixo>.<região>.cloudapp.azure.com`, firewall com apenas as portas 80 e 443 abertas e a identidade que o pipeline
usa para publicar. Na VM rodam os contêineres do `docker-compose.prod.yml`:

- **Caddy** na frente, com HTTPS automático (Let's Encrypt) e redirecionamento de HTTP;
- **app**, com a imagem `sha-<commit>` do GHCR;
- **db** (PostgreSQL), **Prometheus** e **Grafana** (em `https://<APP_HOST>/grafana`), só na rede interna.

Os segredos ficam em `/etc/escalas/env` na VM (permissão 600), gravados pelo Terraform na criação. Eles nunca passam
pelo repositório nem pelo pipeline.

### Provisionamento (uma vez)

Pré-requisitos: conta no Azure (o [GitHub Student Developer Pack](https://education.github.com/pack) dá US$ 100 de
crédito, sem cartão), [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) e
[Terraform](https://developer.hashicorp.com/terraform/install).

1. No GitHub, deixe o repositório **público**. Depois do primeiro push na `main`, torne público também o pacote da
   imagem (*Packages → tcc-mercado-coelho → Package settings → Change visibility*), para a VM baixá-la sem senha.
2. Crie a infraestrutura:
   ```bash
   az login
   cd infra/terraform
   cp terraform.tfvars.example terraform.tfvars   # preencha a assinatura e as senhas
   terraform init
   terraform apply
   ```
   Na primeira inicialização a VM instala o Docker, clona o repositório e publica a versão atual da `main`.
3. No GitHub, crie o ambiente **producao** (*Settings → Environments*) e cadastre nele, como *Variables*, os valores
   que o `terraform output` mostra: `APP_URL`, `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`,
   `AZURE_RESOURCE_GROUP` e `AZURE_VM`. Enquanto `AZURE_VM` não existir, o job de deploy é ignorado.
4. Opcional, domínio próprio: crie um registro A apontando para `IP_PUBLICO`, defina `dominio` no `terraform.tfvars`
   e rode `terraform apply` de novo (a VM é recriada com o novo endereço).

O `terraform.tfvars` e o estado do Terraform (`*.tfstate`) contêm os segredos e ficam só na sua máquina (estão no
`.gitignore`).

### Atualização e rollback

- **Atualizar:** basta o merge na `main`. O pipeline publica o commit depois que todos os testes passam.
- **Voltar para uma versão anterior:** em *Actions → CI/CD → Run workflow*, informe em **commit** o hash de uma versão
  já publicada. O pipeline roda de novo e a VM volta para a imagem `sha-<commit>` e a configuração daquele commit.
- **Ver o que está no ar:** a aba *Environments → producao* do GitHub lista cada deploy, com o commit e o link.

### Operação

| O quê | Onde |
|-------|------|
| Aplicação | `APP_URL` (usuário `gestor`, senha `admin_senha` do Terraform) |
| Métricas e painéis | `APP_URL/grafana` (usuário `admin`, senha `grafana_senha`) |
| Health check | `GET :8081/actuator/health`, na rede interna (usado pelo Docker e pelo Prometheus) |
| Logs dos contêineres | Portal do Azure → VM → *Run command* → `docker logs --tail 200 escalas-app-1` |

A aplicação roda em **uma única instância**, porque o broker de mensagens do chat é em memória.

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
