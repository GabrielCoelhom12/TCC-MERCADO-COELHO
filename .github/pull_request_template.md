## O que muda

<!-- Uma ou duas frases: o que este PR faz e por quê. -->

Closes #

## Como testar

<!-- Passos para o revisor conferir a mudança no sistema. -->

1.

## Checklist

- [ ] Branch criada a partir da `main` atualizada
- [ ] Teste escrito antes da implementação (TDD) e cobrindo a regra de negócio, não só o percentual
- [ ] `mvn verify` passou no backend (cobertura ≥ 75%)
- [ ] `npm run typecheck` e `npm run test:coverage` passaram no frontend (cobertura ≥ 25%)
- [ ] Documentação em `docs/` atualizada, se a mudança afeta requisitos, arquitetura ou deploy
- [ ] Nenhum segredo (senha, token, `.env`, `terraform.tfvars`) no commit
