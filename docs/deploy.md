# Deploy e operação

## Variáveis de ambiente

| Variável | Obrigatória em produção | Descrição |
|----------|:-----------------------:|-----------|
| `DB_URL` | ✔ | JDBC do PostgreSQL, ex.: `jdbc:postgresql://host:5432/escalas_coelho` |
| `DB_USER` / `DB_PASSWORD` | ✔ | Credenciais do banco |
| `APP_JWT_SECRET` | ✔ | Chave Base64 com ≥ 32 bytes (`openssl rand -base64 48`). Sem ela, uma chave aleatória é gerada a cada start. |
| `APP_ADMIN_SENHA` | recomendada | Senha do usuário `gestor` criado no primeiro start. Sem ela, é gerada e exibida uma vez no log. |
| `APP_SEED` | — | `true` carrega o cenário de demonstração (colaboradores fictícios) quando o banco está vazio. Em produção o padrão é `false`. |
| `GRAFANA_IPS` | — | Produção: IPs (ou faixas) que podem abrir o `/grafana`, separados por espaço. Sem a variável, ninguém de fora acessa. |
| `PORT` / `MANAGEMENT_PORT` | — | Porta HTTP (8080) e porta de métricas/health (8081). |
| `APP_OTIMIZACAO_SEGUNDOS` | — | Tempo máximo de cada otimização com o Timefold (padrão `5`). |
| `APP_OTIMIZACAO_SIMULTANEAS` | — | Quantas otimizações podem rodar ao mesmo tempo (padrão `2`); as demais recebem HTTP 429. |
| `APP_GERACAO_AUTOMATICA` | — | `false` desliga o agendamento do rascunho semanal (a opção também pode ser desligada em Parâmetros). Roda todo dia às 6h e ao iniciar. |

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

1. **Backend**: `mvn verify` contra um PostgreSQL de serviço; o build falha se a cobertura de linhas ficar abaixo de 75% (JaCoCo)
   ou se alguma regra de arquitetura for violada (`ArquiteturaTest`).
2. **Frontend**: checagem de tipos, lint (ESLint, sem avisos), testes (Vitest) e cobertura mínima de 25%.
3. **Infraestrutura**: `terraform fmt -check` e `terraform validate` em `infra/terraform`, e o `Caddyfile` validado pelo próprio Caddy.
   **Backup e teste de restauração**: sobe o PostgreSQL do compose com dados de exemplo, roda o `infra/backup.sh` e
   restaura o arquivo num banco descartável com o `infra/testar-restauracao.sh`; o deploy só acontece se ele passar.
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
   `AZURE_RESOURCE_GROUP`, `AZURE_VM` e `BACKUP_CONTA`. Enquanto `AZURE_VM` não existir, o job de deploy é ignorado.
   No mesmo ambiente, **proteja o deploy** (veja "Proteção do ambiente de produção" abaixo).
4. Opcional, domínio próprio: crie um registro A apontando para `IP_PUBLICO`, defina `dominio` no `terraform.tfvars`
   e rode `terraform apply` de novo. Como o `custom_data` da VM é ignorado depois da criação (para o
   Terraform não recriar a VM e apagar o banco), atualize também o `APP_HOST` no `/etc/escalas/env` pela *Run command*.

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
| Métricas e painéis | `APP_URL/grafana` (usuário `admin`, senha `grafana_senha`), só a partir dos IPs de `GRAFANA_IPS` |
| Backups | Todo dia às 03h, em `/var/backups/escalas` (7 dias) e no Storage `BACKUP_CONTA` (30 dias); log em `/var/log/escalas-backup.log` |
| Health check | `GET :8081/actuator/health`, na rede interna (usado pelo Docker e pelo Prometheus) |
| Logs dos contêineres | Portal do Azure → VM → *Run command* → `docker logs --tail 200 escalas-app-1` |

A aplicação roda em **uma única instância**, porque o limite de tentativas de login fica em memória.

Os comandos abaixo rodam na VM pelo Portal do Azure (*VM → Run command → RunShellScript*) ou pela linha de comando
(`az vm run-command invoke -g <grupo> -n <vm> --command-id RunShellScript --scripts "<comando>"`).

### Backup e restauração

O banco é copiado **todo dia às 03h** (`infra/backup.sh`, agendado pelo `infra/atualizar.sh` a cada deploy): um
`pg_dump` compactado fica 7 dias na VM e uma cópia vai para o Azure Blob Storage (`BACKUP_CONTA`), onde fica 30 dias,
em outra região (GRS) e com 7 dias de lixeira. A VM grava com a própria identidade gerenciada: não há chave nem senha
do Storage em lugar nenhum.

- **Testar a restauração (uma vez por mês):** `sh /opt/escalas/infra/testar-restauracao.sh` restaura o backup mais
  recente num PostgreSQL descartável, sem tocar na produção, e lista as tabelas com o número de linhas. O pipeline
  roda o mesmo teste a cada push.
- **Fazer um backup agora:** `sh /opt/escalas/infra/backup.sh`.
- **Restaurar na produção** (substitui os dados atuais; antes, o script faz um backup do estado atual):
  `sh /opt/escalas/infra/restaurar.sh /var/backups/escalas/<arquivo>.dump --confirmar`.
- **Restaurar um backup que só está no Storage** (a VM foi perdida): baixe o arquivo no Portal (*Storage →
  Containers → backups*), crie a VM de novo (`terraform apply`), envie o arquivo para a VM e rode o `restaurar.sh`.

### Acesso ao Grafana

O `/grafana` só abre para os IPs de `GRAFANA_IPS` no `/etc/escalas/env` (ex.: `GRAFANA_IPS=200.150.10.20`, o IP da
sua internet em https://ifconfig.me). Sem a variável, ninguém de fora acessa. Depois de mudar o arquivo:
`cd /opt/escalas && docker compose --env-file /etc/escalas/env -f docker-compose.yml -f docker-compose.prod.yml up -d caddy`.

### Migrações do banco (Flyway)

O esquema é versionado em `backend/src/main/resources/db/migration`. O Flyway aplica as migrações pendentes ao iniciar
e o Hibernate só **confere** se as entidades batem com o banco (`ddl-auto: validate`): se não baterem, a aplicação
não sobe, em vez de alterar tabelas por conta própria.

- **Mudou uma entidade?** Crie `V<próximo número>__descricao.sql` com o `ALTER TABLE`/`CREATE TABLE` correspondente.
  Nunca edite uma migração já publicada.
- **Novo valor num enum gravado como texto** (ex.: um tipo de afastamento): a migração recria a restrição `CHECK` da
  coluna (`*_check`). O teste `oBancoAceitaTodosOsTiposDeAfastamento` avisa se faltar.
- Os testes de integração apagam o banco de teste e aplicam todas as migrações a cada execução: a produção recebe
  exatamente o esquema testado.
- No banco de produção que já existia, a `V1__esquema_inicial` é registrada como aplicada sem rodar
  (`baseline-on-migrate`).

### Proteção do ambiente de produção (configuração do GitHub, uma vez)

A identidade do Azure confia em qualquer job que declare `environment: producao`, e ela executa comandos como root na
VM. Por isso, em *Settings → Environments → producao*:

- **Deployment branches and tags → Selected branches and tags → `main`**: só a `main` publica;
- **Required reviewers** (opcional, recomendado): um deploy espera a aprovação de uma pessoa;
- em *Settings → Code security*: ative **Dependabot alerts**, **Secret scanning** e **Push protection**.

### Evoluções (fora do escopo do TCC)

- **Segredos no Azure Key Vault**: hoje eles vão no `custom_data` da VM (legível para quem tem leitura da VM no
  Azure) e no `/etc/escalas/env`. Com o Key Vault e a identidade gerenciada, a VM leria os segredos na inicialização.
- **Estado do Terraform remoto** (Azure Storage com criptografia e bloqueio), em vez do `terraform.tfstate` local.
- **Banco gerenciado** (Azure Database for PostgreSQL), com backup automático e réplica, quando a VM única não bastar.
- **Limite de requisições por usuário** nos envios de arquivo (relatório do PDV, documentos).

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
