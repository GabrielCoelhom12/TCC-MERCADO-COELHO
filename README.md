# Escalas · Supermercado Coelho

Trabalho de Conclusão de Curso — Gabriel Coelho — Engenharia de Software — 2026

Sistema web de **geração e validação de escalas de trabalho** para o Supermercado Coelho: monta a escala que cobre o
movimento previsto da loja, respeita a CLT e mantém o custo da equipe dentro do limite de cada dia
(PAC VII / PAC VIII — Engenharia de Software, Católica SC). Linha de projeto: **Web Apps**.

[![CI/CD](../../actions/workflows/ci.yml/badge.svg)](../../actions/workflows/ci.yml)

A documentação completa está na **[Wiki do projeto](../../wiki)**, publicada automaticamente a partir de `docs/`:

| Documento | Conteúdo |
|-----------|----------|
| [docs/requisitos.md](docs/requisitos.md) | Fluxos de negócio, requisitos, regras de negócio, fluxos alternativos, fora do escopo e user stories |
| [docs/arquitetura.md](docs/arquitetura.md) | Diagramas C4 (contexto, contêineres e componentes), diagrama de implantação, stack e decisões |
| [docs/modelo-de-dados.md](docs/modelo-de-dados.md) | Diagramas entidade-relacionamento |
| [docs/deploy.md](docs/deploy.md) | Ambientes, pipeline CI/CD, provisionamento no Azure (Terraform), rollback e monitoramento |
| [docs/conformidade.md](docs/conformidade.md) | LGPD, segurança (OWASP), acessibilidade (WCAG) e licenças |

Licença: [MIT](LICENSE) (os logotipos do Supermercado Coelho não estão incluídos).

## Stack (definição explícita)

| Camada   | Tecnologia |
|----------|------------|
| Backend  | Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security + JWT · API REST |
| Banco    | PostgreSQL 17 |
| Frontend | **React 18 + TypeScript (SPA) com Vite** · React Router |
| Testes   | JUnit 5 + AssertJ + MockMvc (backend, cobertura ≥ 75%) · Vitest + Testing Library (frontend, ≥ 25%) |
| Qualidade | GitHub Actions (CI/CD) · JaCoCo · ArchUnit (regras de arquitetura) · ESLint · SonarCloud |
| Operação | Docker Compose · Caddy (HTTPS) · Azure (VM provisionada com Terraform) · Spring Actuator + Micrometer → Prometheus → Grafana |

## Os três pilares do artigo

1. **Cadastros e regras sindicais** — setores com demanda mínima por perfil de dia, colaboradores (salário, encargos,
   disponibilidade, ausências), modelos de turno, feriados, projeções de receita e parâmetros CLT.
2. **Motor de cálculo do custo da escala** (`backend/.../motor`), Java puro e testado:
   - `C_escala(d) = Σ [ S_i × H_i,d × F_d ]`, com `S_i = salário / carga mensal × (1 + encargos)`;
   - `F_d` = 1,0 em dias úteis, fator de domingo/feriado configurável;
   - teto do dia = `R_proj(d) × % da folha`; `L_max = ⌊teto / custo médio do operador⌋`; `L_min = Σ demandas mínimas`;
   - aprovação somente se `L_min ≤ ΣO ≤ L_max`, sem estourar o teto e sem violar regras;
   - modo **BLOQUEAR** (recusa a alocação que gera superdimensionamento) ou **ALERTAR**.
3. **Matriz dinâmica de geração e validação** — grade colaborador × dia com feedback instantâneo do motor,
   gerador automático (aloca exatamente `L_min`, ao menor custo, respeitando a CLT) e aprovação.

Regras validadas: interjornada de 11h (CLT art. 66), repouso semanal após 6 dias (art. 67), intervalo intrajornada
(art. 71), jornada diária máxima e horas extras (arts. 58/59), jornada semanal, folga dominical no comércio a cada
3 semanas (Lei 10.101/2000, art. 6º), sobreposição, indisponibilidade, ausências, subdimensionamento por setor,
superdimensionamento e custo acima do teto.

O **simulador de custos** compara um quadro montado "no olho" com o recomendado pelo motor e mostra a economia.

## Como executar

Com Docker: `cp .env.example .env`, ajuste as senhas e rode `docker compose up -d --build` (detalhes em
[docs/deploy.md](docs/deploy.md)).

Sem Docker, os pré-requisitos são JDK 21, Maven 3.9+, Node 20+ e PostgreSQL com os bancos `escalas_coelho` e
`escalas_coelho_test` (usuário e senha padrão `postgres`/`postgres`, ou defina `DB_URL`, `DB_USER` e `DB_PASSWORD`).

```powershell
# Backend (http://localhost:8080; métricas e health em http://localhost:8081/actuator)
cd backend
mvn spring-boot:run

# Frontend em desenvolvimento (http://localhost:5173, com proxy para a API)
cd frontend
npm install
npm run dev
```

Acesso inicial: usuário **gestor**, com a senha definida em `APP_ADMIN_SENHA`. Sem essa variável, uma senha aleatória
é gerada no primeiro start e exibida uma única vez no log.

Na primeira execução o banco é populado com um **cenário hipotético** (22 colaboradores, 4 setores, projeções e
feriados nacionais + Joinville) e uma escala da próxima semana gerada pelo motor. Desative com `APP_SEED=false`.

Build único (piloto em *shadow mode*): `npm run build` no frontend grava a SPA em
`backend/src/main/resources/static`; depois `mvn package` gera um único `.jar` que serve app + API.

Testes:

```powershell
cd backend;  mvn verify               # unitários + integração (PostgreSQL) + arquitetura + cobertura ≥ 75%
cd frontend; npm run typecheck        # checagem de tipos (TypeScript)
cd frontend; npm run lint             # análise estática (ESLint), sem avisos
cd frontend; npm run test:coverage    # Vitest + verificação de cobertura ≥ 25%
```

Os testes do backend seguem os fluxos de negócio: `CadastrosIntegracaoTest`, `EscalaIntegracaoTest` e
`MotorIntegracaoTest` (API completa contra o PostgreSQL), além dos testes unitários do motor. O `ArquiteturaTest`
garante que as camadas só dependem umas das outras no sentido previsto (veja [docs/arquitetura.md](docs/arquitetura.md)).

### Rede corporativa (proxy com inspeção SSL)

`backend/.mvn/jvm.config` faz o Maven usar o repositório de certificados do Windows (o pipeline de CI o remove, pois
roda em Linux). Para o npm, defina
`NODE_OPTIONS=--use-system-ca` antes de `npm install`.

## Estrutura

As dependências do backend seguem um único sentido: `api → servico → motor / repositorio → dominio`.

```
backend/src/main/java/br/com/coelho/escalas/
  api/          controllers REST (somente HTTP) e tratamento uniforme de erros
  servico/      regras de negócio e transações: escalas, movimento, cadastros, usuários e chat
  motor/        MotorFinanceiro, ValidadorEscala, GeradorEscala, SimuladorCusto, PrevisaoMovimento e
                PlanejadorDemanda (Java puro, sem dependência de banco)
  repositorio/  acesso a dados (Spring Data JPA)
  dominio/      entidades JPA
  seguranca/    autenticação JWT (API e WebSocket) e política de senha
  config/       dados iniciais, WebSocket e encaminhamento da SPA
backend/src/test/   testes unitários do motor, de integração da API e de arquitetura
frontend/src/
  pages/        uma tela por arquivo: Painel, Escalas, MatrizEscala, Movimento, Simulador, Mensagens, Funcionarios,
                Setores, Turnos, Calendario, Parametros, Usuarios e Login
  chat/         estado, conexão em tempo real (STOMP) e componentes do chat
  components/   Layout e componentes de interface reutilizáveis
  api.ts        cliente HTTP e sessão        auth.tsx   contexto de autenticação
  types.ts      tipos do contrato da API     format.ts  formatação de datas, moeda e rótulos
docs/           requisitos, arquitetura (C4), modelo de dados, deploy e conformidade — publicados na Wiki
infra/          Terraform (Azure), Caddyfile e script de atualização da VM
monitoramento/  Prometheus e Grafana
.github/        pipelines de CI/CD e da Wiki
```
