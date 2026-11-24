# Como contribuir

Toda mudança segue o mesmo caminho: **branch → testes → commit → pull request → squash and merge**. Ninguém faz
commit direto na `main`: ela só recebe código por pull request, com o pipeline verde.

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
| `chore/` | Manutenção que não muda o sistema: dependências, configuração do editor, `.gitignore` |

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
cd backend  && mvn verify                                                      # testes, arquitetura e cobertura
cd frontend && npm run typecheck && npm run lint && npm run test:coverage      # tipos, lint, testes e cobertura
```

**Metas de cobertura de linhas:** backend **≥ 75%** (JaCoCo, propriedade `cobertura.minima` no `backend/pom.xml`) e
frontend **≥ 25%** (`thresholds` no `frontend/vite.config.ts`). O build falha abaixo da meta. Este é o único lugar da
documentação de contribuição com os números: se a meta mudar, altere o arquivo de configuração e este parágrafo.

Cobertura alta não basta: o teste precisa verificar a **regra de negócio** (o resultado esperado), e não só executar
as linhas. O pipeline roda os mesmos comandos no pull request, mas rodar antes evita idas e voltas.

## 4. Commit

Mensagens no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/), em português, dizendo **o que**
mudou e **por quê**:

```
fix: turno que atravessa a meia-noite conta para a interjornada

O descanso era calculado só com as horas, sem a data, e um fechamento às
23h seguido de abertura às 7h passava como 16 h de descanso.
```

Prefira commits pequenos, um por passo lógico: eles ajudam a revisão, mesmo que a `main` receba um commit só (passo 5).

## 5. Pull request e merge

```bash
git push -u origin feat/nome-curto-da-mudanca
```

Abra o pull request para a `main` no GitHub. O modelo de descrição já traz o checklist. Antes do merge:

- o pipeline **CI/CD** precisa estar verde (jobs `Backend`, `Frontend` e `Infraestrutura`);
- pelo menos uma **revisão** — no PAC VIII, de um colega do squad;
- os comentários da revisão respondidos ou resolvidos.

**Estratégia de merge: _Squash and merge_.** Cada pull request vira **um commit** na `main`, com o título do PR no
padrão Conventional Commits (ex.: `feat: troca de colaborador no painel`); por isso o título do PR já deve seguir esse
padrão. Assim, cada commit da `main` corresponde a uma mudança completa e a um deploy (imagem `sha-<commit>`), e o
rollback volta um PR inteiro.

Depois do merge, o pipeline publica a nova versão em produção sozinho. **Apague a branch**: o GitHub apaga a remota
automaticamente (veja a configuração abaixo); a local, apague você:

```bash
git switch main
git pull
git branch -D feat/nome-curto-da-mudanca   # -D: depois do squash, o Git não reconhece a branch como "mesclada"
git fetch --prune                          # limpa as referências de branches remotas já apagadas
```

## Configuração do repositório (administrador, uma vez)

> **Situação atual:** a proteção da `main` ainda **não está ativa** (conferido em 20/11/2026). Até ser ativada, as
> regras acima dependem da disciplina de cada um. Como todo merge na `main` vai para produção, ative-a antes do
> próximo merge.

**Proteção da `main`** — em *Settings → Rules → Rulesets → New branch ruleset*, alvo `main`, *Enforcement: Active*:

- **Require a pull request before merging**, com 1 aprovação e *Allowed merge methods* só **Squash**;
- **Require status checks to pass**, adicionando `Backend`, `Frontend` e `Infraestrutura`;
- **Block force pushes** e **Restrict deletions**.

> ⚠️ **Os nomes das verificações precisam ser idênticos ao campo `name:` dos jobs em `.github/workflows/ci.yml`.** Se
> alguém renomear um job, a verificação exigida com o nome antigo nunca mais é reportada e **todo merge na `main`
> fica bloqueado** ("Expected — Waiting for status to be reported"). Quem renomear um job deve, no mesmo PR, avisar o
> administrador para atualizar a lista de verificações do ruleset. Por isso os nomes dos jobs são curtos e estáveis
> (sem metas ou detalhes que mudam com o tempo).

**Merge e branches** — em *Settings → General → Pull Requests*:

- deixe marcado só **Allow squash merging** (com *Default commit message: Pull request title and description*);
- marque **Automatically delete head branches**, para a branch remota sumir depois do merge.

## Bugs e sugestões

Abra uma [issue](../../issues) descrevendo o que aconteceu, os passos para reproduzir e o que era esperado. O
pull request que corrige deve citá-la (`Closes #42`), para a issue fechar sozinha no merge.
