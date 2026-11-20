## O que muda

<!-- Uma ou duas frases: o que este PR faz e por quê. O título do PR vira o commit na main (squash):
     use Conventional Commits, ex.: "feat: troca de colaborador no painel". -->

<!-- Opcional: se o PR resolve uma issue, descomente a linha abaixo com o número dela.
Closes #
-->

## Como testar

<!-- Passos para o revisor conferir a mudança no sistema. Em PR só de documentação, escreva "N/A". -->

1.

## Checklist

<!-- Marque o que se aplica. Se um item não se aplica a este PR (ex.: PR só de backend, só de frontend ou só de
     documentação), marque-o e acrescente "N/A" no fim da linha. As metas de cobertura estão no CONTRIBUTING.md. -->

- [ ] Branch criada a partir da `main` atualizada
- [ ] Teste escrito antes da implementação (TDD) e cobrindo a regra de negócio, não só o percentual
- [ ] Backend: `mvn verify` passou
- [ ] Frontend: `npm run typecheck`, `npm run lint` e `npm run test:coverage` passaram
- [ ] Documentação em `docs/` atualizada, se a mudança afeta requisitos, arquitetura ou deploy
- [ ] Nenhum segredo (senha, token, `.env`, `terraform.tfvars`) no commit
