# Como contribuir

Toda mudança segue o mesmo caminho: **branch → testes → commit → pull request**. Ninguém faz commit direto na
`main`: ela só recebe código por pull request, com o pipeline verde.

## 1. Crie uma branch a partir da `main` atualizada

```bash
git switch main
git pull
git switch -c feat/nome-curto-da-mudanca
```

| Prefixo | Quando usar |
|---------|-------------|
| `feat/` | Funcionalidade nova |
| `fix/` | Correção de bug (de preferência citando a issue, ex.: `fix/42-interjornada-meia-noite`) |
| `test/` | Só testes |
| `refactor/` | Mudança de código sem mudar comportamento |
| `docs/` | Documentação (pasta `docs/`, publicada na Wiki) |
| `ci/` | Pipeline, Docker ou infraestrutura |

## 2. Escreva o teste primeiro (TDD)

1. Escreva um teste que descreve o comportamento esperado e veja-o **falhar**.
2. Implemente o mínimo para ele passar.
3. Refatore com os testes verdes.

| Onde | Tipo de teste | Exemplo |
|------|---------------|---------|
| `backend/src/test/.../motor` | Unitário das regras (sem Spring nem banco) | `ValidadorEscalaTest` |
| `backend/src/test/.../api` | Integração da API inteira contra o PostgreSQL | `EscalaIntegracaoTest` |
| `frontend/src/**/*.test.tsx` | Tela, do jeito que o usuário usa (Vitest + Testing Library) | `Escalas.test.tsx` |

## 3. Rode todos os testes antes de commitar

```bash
cd backend  && mvn verify                                  # testes + cobertura ≥ 75% (JaCoCo)
cd frontend && npm run typecheck && npm run test:coverage   # tipos + testes + cobertura ≥ 25%
```

O build falha se a cobertura ficar abaixo da meta. O pipeline roda os mesmos comandos no pull request, mas rodar
antes evita idas e voltas.

## 4. Commit

Mensagens no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/), em português, dizendo **o que**
mudou e **por quê**:

```
fix: turno que atravessa a meia-noite conta para a interjornada

O descanso era calculado só com as horas, sem a data, e um fechamento às
23h seguido de abertura às 7h passava como 16 h de descanso.
```

Prefira commits pequenos, um por passo lógico.

## 5. Pull request

```bash
git push -u origin feat/nome-curto-da-mudanca
```

Abra o pull request para a `main` no GitHub. O modelo de descrição já traz o checklist. Antes do merge:

- o pipeline **CI/CD** precisa estar verde (backend, frontend e infraestrutura);
- pelo menos uma **revisão** — no PAC VIII, de um colega do squad;
- os comentários da revisão respondidos ou resolvidos.

Depois do merge na `main`, o pipeline publica a nova versão em produção sozinho.

## Proteção da `main` (configuração do repositório)

Em *Settings → Branches → Add branch ruleset* (ou *Branch protection rules*), para a `main`:

- **Require a pull request before merging**, com 1 aprovação;
- **Require status checks to pass**: `Backend (testes + cobertura ≥ 75%)`,
  `Frontend (tipos + testes + cobertura ≥ 25%)` e `Infraestrutura (Terraform fmt + validate)`;
- **Block force pushes**.

## Bugs e sugestões

Abra uma [issue](../../issues) descrevendo o que aconteceu, os passos para reproduzir e o que era esperado. O
pull request que corrige deve citá-la (`Closes #42`).
