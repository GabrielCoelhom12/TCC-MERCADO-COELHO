# Escalas · Supermercado Coelho — Documentação

Trabalho de Conclusão de Curso — Gabriel Coelho — Engenharia de Software (Católica SC) — 2026.
Linha de projeto: **Web Apps**. Parceiro: **Supermercado Coelho** (Joinville/SC).

Sistema web que gera e valida as escalas de trabalho da loja: cobre o movimento previsto de cada dia, respeita a CLT
e mantém o custo da equipe dentro do limite definido pela gestão.

> Esta Wiki é publicada automaticamente a partir da pasta `docs/` do repositório a cada push na `main`
> (workflow `wiki.yml`). Para corrigir uma página, edite o arquivo correspondente em `docs/`.

| Página | Conteúdo |
|--------|----------|
| [Requisitos](requisitos.md) | Contexto, três fluxos de negócio, requisitos funcionais e não funcionais, user stories |
| [Arquitetura](arquitetura.md) | Diagramas C4 (contexto, contêineres e componentes), implantação e registro de decisões |
| [Deploy](deploy.md) | Ambientes, variáveis, pipeline CI/CD, provisionamento no Azure (Terraform), rollback e monitoramento |
| [Conformidade](conformidade.md) | LGPD, segurança (OWASP), acessibilidade (WCAG) e licenças |

## Visão rápida

| Item | Onde |
|------|------|
| Código-fonte | [repositório](../README.md) — `backend/` (Java 21 · Spring Boot) e `frontend/` (React · TypeScript) |
| Testes e cobertura | JUnit + JaCoCo (backend, mínimo 75%) · Vitest (frontend, mínimo 25%), conferidos no pipeline |
| Pipeline | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml): testes → SonarCloud → imagem Docker → deploy |
| Infraestrutura | [`infra/terraform`](../infra/terraform/main.tf) (VM no Azure) · [`docker-compose.prod.yml`](../docker-compose.prod.yml) |
| Monitoramento | Spring Actuator + Micrometer → Prometheus → Grafana (`/grafana` em produção) |
