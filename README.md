# Escalas · Supermercado Coelho

Sistema web de **geração, validação e otimização financeira de escalas de trabalho** para o Supermercado Coelho
(PAC VII / PAC VIII — Engenharia de Software, Católica SC). Linha de projeto: **Web Apps**.

[![CI/CD](../../actions/workflows/ci.yml/badge.svg)](../../actions/workflows/ci.yml)

| Documento | Conteúdo |
|-----------|----------|
| [docs/requisitos.md](docs/requisitos.md) | Fluxos de negócio, requisitos funcionais e não funcionais, user stories |
| [docs/arquitetura.md](docs/arquitetura.md) | Diagramas C4 (contexto, contêineres e componentes) e decisões |
| [docs/deploy.md](docs/deploy.md) | Variáveis de ambiente, Docker Compose, pipeline CI/CD, SonarCloud e monitoramento |

## Stack (definição explícita)

| Camada   | Tecnologia |
|----------|------------|
| Backend  | Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security + JWT · API REST |
| Banco    | PostgreSQL 17 |
| Frontend | **React 18 + TypeScript (SPA) com Vite** · React Router |
| Testes   | JUnit 5 + AssertJ + MockMvc (backend, cobertura ≥ 75%) · Vitest + Testing Library (frontend, ≥ 25%) |
| Qualidade | GitHub Actions (CI/CD) · JaCoCo · SonarCloud |
| Operação | Docker · Spring Actuator + Micrometer → Prometheus → Grafana |

## Os três pilares do artigo

1. **Cadastros e regras sindicais** — setores com demanda mínima por perfil de dia, colaboradores (salário, encargos,
   disponibilidade, ausências), modelos de turno, feriados, projeções de receita e parâmetros CLT.
2. **Motor de cálculo e otimização financeira** (`backend/.../motor`), Java puro e testado:
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
cd backend;  mvn verify               # unitários + integração (PostgreSQL) + verificação de cobertura ≥ 75%
cd frontend; npm run test:coverage    # Vitest + verificação de cobertura ≥ 25%
```

Os testes do backend seguem os fluxos de negócio: `CadastrosIntegracaoTest`, `EscalaIntegracaoTest` e
`MotorIntegracaoTest` (API completa contra o PostgreSQL), além dos testes unitários do motor.

### Rede corporativa (proxy com inspeção SSL)

`backend/.mvn/jvm.config` faz o Maven usar o repositório de certificados do Windows (o pipeline de CI o remove, pois
roda em Linux). Para o npm, defina
`NODE_OPTIONS=--use-system-ca` antes de `npm install`.

## Estrutura

```
backend/src/main/java/br/com/coelho/escalas/
  dominio/      entidades JPA
  motor/        MotorFinanceiro, ValidadorEscala, GeradorEscala, SimuladorCusto (sem dependência de banco)
  servico/      orquestração (EscalaService, ContextoService)
  api/          controllers REST
  seguranca/    JWT
  config/       dados iniciais e encaminhamento da SPA
frontend/src/
  pages/        Painel, Escalas, MatrizEscala, Simulador, Funcionarios, Setores, Turnos, Calendario, Parametros
  components/   Layout e componentes de interface reutilizáveis
docs/           requisitos, arquitetura (C4) e deploy
monitoramento/  Prometheus e Grafana
.github/        pipeline de CI/CD
```
