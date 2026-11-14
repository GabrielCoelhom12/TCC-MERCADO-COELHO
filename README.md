# Escalas · Supermercado Coelho

[![CI/CD](../../actions/workflows/ci.yml/badge.svg)](../../actions/workflows/ci.yml)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)

Sistema web que **gera, valida e acompanha as escalas de trabalho** de um supermercado. Ele monta a escala que cobre o
movimento previsto da loja, respeita a CLT e mantém o custo da equipe dentro de um limite proporcional à receita de cada
dia. Parceiro: **Supermercado Coelho** (Joinville/SC).

Trabalho de Conclusão de Curso (PAC VII / PAC VIII) de **Gabriel Coelho** — Engenharia de Software, Católica de Santa
Catarina, 2026 — linha de projeto **Web Apps**. Stack: **Java 21 · Spring Boot 3.5 · PostgreSQL 17 · React 18 +
TypeScript (Vite)**.

> **Transparência sobre o desenvolvimento.** Este projeto foi desenvolvido por Gabriel Coelho com o apoio de um
> assistente de IA (Claude Code) na escrita e revisão de código, testes e documentação. O problema, as regras de
> negócio levantadas com o parceiro, as decisões de projeto e a verificação dos resultados são de responsabilidade do
> autor.

## Sumário

[Visão geral](#visão-geral) · [Funcionalidades](#funcionalidades) · [Motor de custo e regras](#motor-de-custo-e-regras) ·
[Previsão de movimento](#previsão-de-movimento) · [Perfis de acesso](#perfis-de-acesso) · [Instalação](#instalação) ·
[Uso](#uso) · [Testes e qualidade](#testes-e-qualidade) · [Segurança e LGPD](#segurança-e-lgpd) ·
[Implantação](#implantação) · [Estrutura do repositório](#estrutura-do-repositório) · [Documentação](#documentação) ·
[Limitações conhecidas](#limitações-conhecidas) · [Licença](#licença)

## Visão geral

Em supermercados, a escala da equipe costuma ser montada à mão em planilhas. Isso gera três problemas: jornadas que
ferem a CLT (descanso de 11 h entre jornadas, repouso semanal, domingos seguidos), horários de pico sem gente
suficiente e custo de pessoal acima do que a receita do dia comporta.

O sistema resolve isso em quatro etapas:

1. **Prevê o movimento** de cada dia e faixa de horário a partir dos fechamentos de caixa lançados pela gerência.
2. **Calcula a demanda**: quantas pessoas cada setor precisa em cada faixa e qual o limite de custo do dia.
3. **Gera a escala** da semana automaticamente, como rascunho, com o menor custo que cobre o mínimo de cada setor.
4. **Valida** cada alteração contra as regras trabalhistas, a cobertura dos setores e o limite de custo, e só permite
   aprovar uma escala sem erros.

No dia a dia, o gestor abre o **Painel**, vê quem trabalha em cada dia e quanto custa, troca um colaborador por outro
que o sistema já validou e registra férias, atestados e licenças com a substituição automática dos turnos.

## Funcionalidades

**Painel (tela inicial)**
- Navegação por semana, com 4 indicadores: custo escalado, uso do limite de custo, margem preservada e conformidade.
- Cartões dos 7 dias (clique ou setas do teclado), abrindo no dia de hoje, com número de pessoas, situação e uso do limite.
- Detalhe do dia: equipe separada por manhã, tarde e noite, ausentes, custos do dia e alertas só daquele dia.
- **Troca de colaborador:** clicar em quem trabalha abre a semana da pessoa (horas escaladas × limite semanal, dias de
  trabalho e indisponíveis) e lista os colegas que podem cobrir o turno, já validados pelas regras, com horas antes e
  depois da troca e alerta de hora extra.
- Grade completa da semana sob demanda e quadro "De férias nesta semana".

**Escalas**
- Matriz colaborador × dia com revalidação imediata a cada célula, geração automática, aprovação e reabertura.
- Rascunho das próximas semanas criado sozinho todo dia às 6h (antecedência de 1 a 4 semanas, configurável).
- Modo financeiro **BLOQUEAR** (recusa a alocação que estoura o limite) ou **ALERTAR**.

**Férias**
- Página própria com quem está de férias hoje, quem começa em 30 dias e quem volta na semana.
- Regras da CLT no cadastro: 5 a 30 dias por período (arts. 130 e 134, §1º), início fora dos dois dias que antecedem
  feriado ou domingo (art. 134, §3º) e sem sobrepor outra ausência.
- **Substituição automática:** antes de salvar, a prévia mostra quem assume cada turno já escalado no período; ao
  salvar, as trocas são feitas (inclusive em escala aprovada, com anotação na escala).

**Afastamentos**
- Página própria para atestado médico, licença-maternidade, licença-paternidade, afastamento pelo INSS, acidente de
  trabalho, licença por luto, licença-casamento, folga acordada e outros, com atalhos de duração por tipo (120 dias da
  licença-maternidade, 2 dias de luto, 3 de casamento…).
- Os turnos já escalados são **repassados na hora** a colegas aptos, como nas férias. O atestado pode ser lançado
  depois do fato: turnos de dias que já passaram são só retirados, porque a pessoa não trabalhou.
- Alertas do que a lei garante: atestado acima de 15 dias vai para o INSS (Lei 8.213/1991, art. 60), licença-maternidade
  de 120 dias (CLT art. 392), luto e casamento (CLT art. 473).
- O supervisor pode registrar (o atestado chega no dia a dia); editar e remover fica com a gerência. Só o tipo é
  guardado — nunca diagnóstico ou CID.

**Movimento e previsão**
- Lançamento do fechamento diário (clientes e vendas por faixa de horário) e previsão explicada dos próximos dias.
- Selo de assertividade: erro médio e viés da previsão nos últimos fechamentos.

**Cadastros e regras**
- Setores com demanda mínima por tipo de dia e capacidade de atendimento; colaboradores com salário, encargos,
  disponibilidade, ausências e foto; modelos de turno; feriados (importação dos nacionais e de Joinville);
  projeções de receita; parâmetros da CLT e da convenção coletiva.
- **Simulador de custos:** compara um quadro montado "no olho" com o recomendado pelo motor e mostra a economia.

**Comunicação e administração**
- Chat em tempo real (WebSocket/STOMP) com conversas privadas e grupos, imagens, menção a escalas, quem está online,
  não lidas e indicação de leitura.
- Usuários com três perfis (ver [Perfis de acesso](#perfis-de-acesso)), senha forte e foto.

São **30 requisitos funcionais, 9 não funcionais, 17 regras de negócio e 14 histórias de usuário**, descritos em
[docs/requisitos.md](docs/requisitos.md).

## Motor de custo e regras

O motor (`backend/.../motor`) é **Java puro**: recebe tudo por um `ContextoCalculo` e é testado sem banco nem servidor.
Uma regra de arquitetura (ArchUnit) impede que ele dependa de banco ou web.

| Conceito | Cálculo |
|---|---|
| Custo-hora do colaborador | `S_i = salário ÷ carga mensal × (1 + encargos)` |
| Custo da escala no dia | `C_escala(d) = Σ S_i × H_i,d × F_d` (`F_d` = 1,0 em dias úteis; fator de domingo/feriado configurável) |
| Limite de custo do dia | `teto(d) = receita prevista(d) × % da folha` |
| Quadro máximo e mínimo | `L_max = ⌊teto ÷ custo médio do operador⌋` · `L_min = Σ demandas mínimas dos setores` |
| Aprovação | `L_min ≤ pessoas ≤ max(L_min, L_max)`, custo dentro do teto e nenhuma violação de severidade ERRO |

Regras validadas a cada alteração:

| Regra | Base legal | Severidade |
|---|---|---|
| Turnos do mesmo colaborador não se sobrepõem | — | Erro |
| Descanso mínimo de 11 h entre jornadas | CLT art. 66 | Erro |
| No máximo 6 dias seguidos de trabalho | CLT art. 67 | Erro |
| Intervalo intrajornada conforme a duração | CLT art. 71 | Erro |
| Jornada diária máxima / hora extra diária | CLT arts. 58 e 59 | Erro / Alerta |
| Jornada semanal acima de 44 h | CF art. 7º, XIII | Alerta |
| Folga em domingo ao menos a cada 3 semanas | Lei 10.101/2000, art. 6º | Erro |
| Indisponibilidade, ausência e colaborador inativo | — | Erro |
| Setor abaixo do mínimo, pico sem cobertura, acima de `L_max` ou do teto | — | Erro / Alerta |

A **troca de colaborador** e a **cobertura de férias e afastamentos** reaproveitam o mesmo validador: cada candidato é simulado no
turno e só entra quem não gera nenhum erro novo. A escolha prioriza o mesmo setor e quem tem menos horas na semana.

## Previsão de movimento

A previsão é **explicável**: cada número mostra de onde veio.

- **Base:** média ponderada das últimas 8 ocorrências do mesmo dia da semana.
- **Efeitos aprendidos** dos próprios fechamentos: início do mês (pagamento) e véspera de feriado.
- **Datas comemorativas** (`CalendarioSazonal`): semana do Natal, vésperas de Ano-Novo, pós-festas, Semana Santa,
  pré-Carnaval, Dia das Mães, Dia dos Pais, Dia dos Namorados e Black Friday. Elas ficam fora da base e recebem um
  fator que combina a hipótese inicial (+30% ou +15%) com o que os fechamentos mostrarem:
  `fator = (n × mediana observada + 2 × hipótese) ÷ (n + 2)`.
- **Pessoas por faixa:** a previsão de clientes por faixa, dividida pela capacidade de atendimento do setor, define
  quantas pessoas cada pico precisa.
- **Assertividade:** erro médio absoluto e viés (em %) da previsão contra os fechamentos já lançados.

## Perfis de acesso

| Ação | ADMIN | GESTOR | SUPERVISOR |
|------|:-----:|:------:|:----------:|
| Consultar painel, escalas, férias, movimento e cadastros | ✅ | ✅ | ✅ |
| Trocar colaborador num turno, registrar afastamento (atestado), lançar o fechamento do dia, chat | ✅ | ✅ | ✅ |
| Editar matriz, gerar, aprovar e reabrir escala; registrar férias; editar ou remover afastamentos | ✅ | ✅ | ❌ |
| Alterar cadastros, parâmetros, feriados e projeções; simulador | ✅ | ✅ | ❌ |
| Ver salário, encargos e custo individual | ✅ | ✅ | ❌ |
| Gerenciar usuários | ✅ | ❌ | ❌ |

A regra vale no servidor (resposta 403 "Seu perfil não permite esta ação."); a interface só esconde os botões.

## Instalação

### Com Docker (recomendado)

Requisitos: Docker com Compose.

```powershell
Copy-Item .env.example .env    # no Linux/macOS: cp .env.example .env
# edite o .env: DB_PASSWORD, APP_JWT_SECRET (openssl rand -base64 48), APP_ADMIN_SENHA e GRAFANA_SENHA
docker compose up -d --build
```

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:8080 |
| Grafana | http://localhost:3000 (admin / `GRAFANA_SENHA`) |
| Prometheus | http://localhost:9090 |

As portas ficam só em `127.0.0.1`.

### Sem Docker (desenvolvimento)

Requisitos: JDK 21, Maven 3.9+, Node 22+ e PostgreSQL com os bancos `escalas_coelho` e `escalas_coelho_test`
(padrão `postgres`/`postgres`, ou defina `DB_URL`, `DB_USER` e `DB_PASSWORD`).

```powershell
# Backend: http://localhost:8080 (health e métricas em http://localhost:8081/actuator)
cd backend
mvn spring-boot:run

# Frontend com recarga automática: http://localhost:5173 (proxy para a API)
cd frontend
npm install
npm run dev
```

Para gerar um único `.jar` com o site e a API: `npm run build` no frontend (grava em
`backend/src/main/resources/static`) e depois `mvn package` no backend.

**Rede corporativa com inspeção SSL:** `backend/.mvn/jvm.config` faz o Maven usar os certificados do Windows; para o
npm, defina `NODE_OPTIONS=--use-system-ca` antes do `npm install`.

### Configuração

| Variável | Para quê |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `APP_JWT_SECRET` | Chave dos tokens (Base64, ≥ 32 bytes). Sem ela, uma chave aleatória é gerada a cada início |
| `APP_ADMIN_SENHA` | Senha do usuário `gestor` criado no primeiro início. Sem ela, uma senha aleatória aparece uma única vez no log |
| `APP_SEED` | `true` carrega o cenário de demonstração quando o banco está vazio; use `false` com dados reais |
| `APP_GERACAO_AUTOMATICA` | Liga ou desliga a criação automática dos rascunhos semanais |
| `APP_WS_ORIGENS` | Origens extras aceitas no WebSocket do chat |

Lista completa e ambientes: [docs/deploy.md](docs/deploy.md).

### Dados de demonstração

No primeiro início, o banco recebe um **cenário fictício**: 22 colaboradores em 4 setores, modelos de turno,
projeções de receita, feriados nacionais e de Joinville, 12 semanas de fechamentos hipotéticos para a previsão ter o
que aprender e uma escala da próxima semana gerada pelo motor.

## Uso

Roteiro de demonstração (cerca de 5 minutos), entrando como `gestor`:

1. **Painel:** veja a semana, clique em outro dia e confira a equipe por período, os custos e os alertas.
2. Clique num colaborador → **Trocar colaborador** → escolha um colega da lista; a equipe e os custos se atualizam.
3. **Férias → Registrar férias:** escolha um colaborador escalado, início numa segunda e "15 dias". A prévia mostra
   o retorno e quem cobre cada turno; tente começar numa sexta para ver a regra do art. 134, §3º.
4. **Afastamentos → Registrar afastamento:** um atestado de hoje, "2 dias", para alguém escalado; veja quem cobre e
   salve. Depois, mude o tipo para licença-maternidade e use o atalho "120 dias".
5. **Escalas → Abrir matriz:** mude uma célula para criar uma interjornada menor que 11 h e veja o erro aparecer e a
   aprovação ficar bloqueada.
6. **Movimento e previsão:** lance o fechamento de ontem e veja a explicação da previsão dos próximos dias.
7. **Simulador de custos:** monte um quadro com gente demais e compare com o recomendado.
8. Entre com um usuário **SUPERVISOR** e confira que salários aparecem como "—" e os botões de edição somem.

## Testes e qualidade

```powershell
cd backend;  mvn verify               # unitários + integração (PostgreSQL) + arquitetura + cobertura ≥ 75%
cd frontend; npm run typecheck        # tipos (TypeScript)
cd frontend; npm run lint             # ESLint, sem avisos
cd frontend; npm run test:coverage    # Vitest + cobertura ≥ 25%
```

Resultado com todos os pull requests de novembro integrados (13/11/2026):

| | Testes | Cobertura de linhas | Mínimo exigido |
|---|---|---|---|
| Backend (JUnit 5, MockMvc, ArchUnit) | 306, 0 falhas | 97,6% (JaCoCo) | 75% |
| Frontend (Vitest, Testing Library) | 120, 0 falhas | 79,3% | 25% |

Os testes seguem os fluxos de negócio:
- **Integração** (API completa contra o PostgreSQL): `CadastrosIntegracaoTest`, `EscalaIntegracaoTest`,
  `MotorIntegracaoTest`, `MovimentoIntegracaoTest`, `FeriasIntegracaoTest`, `AfastamentoIntegracaoTest`,
  `SegurancaIntegracaoTest`, chat e usuários.
- **Unitários do motor**: validador, gerador, previsão, calendário sazonal, sugestor de substituição e regras de
  férias e de afastamentos.
- **Arquitetura** (`ArquiteturaTest`): as camadas só dependem umas das outras no sentido
  `api → servico → motor/repositorio → dominio`.
- **Frontend**: Painel, troca de colaborador, Escalas, Férias, Afastamentos, Movimento, Mensagens, cadastros e usuários, além da
  visão do perfil SUPERVISOR em cada tela de cadastro e na matriz (`Permissoes.test.tsx`).

**Pipeline** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)), a cada pull request e a cada push na `main`:
backend (testes + cobertura), frontend (tipos + lint + testes + cobertura), SonarCloud e Terraform (`fmt` +
`validate`); na `main`, também a imagem Docker no GHCR e o deploy no Azure. A Wiki é publicada a partir de `docs/` pelo `wiki.yml`.

Desenvolvimento por **branch → testes → commit → pull request**, sem commits direto na `main`.

## Segurança e LGPD

- **Autenticação:** JWT sem sessão no servidor, senhas com BCrypt e política de senha forte; conta desativada perde o
  acesso na hora e trocar a senha encerra as sessões abertas.
- **Força bruta:** 5 senhas erradas bloqueiam o login por 15 minutos (HTTP 429); 30 falhas bloqueiam o IP. Login
  inexistente e senha errada têm a mesma resposta e o mesmo tempo.
- **Autorização** por perfil no servidor e **dados salariais ocultos** de quem não precisa deles (LGPD, necessidade).
- **Entrada:** consultas parametrizadas (Spring Data JPA), Bean Validation e limites de período, quantidade e tamanho
  em todas as rotas; imagens verificadas pelo conteúdo e regravadas em JPEG (descarta metadados).
- **Navegador:** Content-Security-Policy só com a própria origem, `X-Frame-Options`, `Referrer-Policy` e
  `Permissions-Policy`; WebSocket só da própria origem em produção.
- **Operação:** nenhum segredo no repositório, contêiner sem privilégios, métricas só na rede interna, HTTPS com HSTS.
- **Dependências:** `npm audit` sem vulnerabilidades e Spring Boot na última versão de correção.

Dados tratados, bases legais e o mapeamento OWASP Top 10: [docs/conformidade.md](docs/conformidade.md).

## Implantação

A infraestrutura é código: [`infra/terraform`](infra/terraform) cria uma VM Ubuntu no Azure com Docker, IP fixo e
firewall só nas portas 80 e 443. O [Caddy](infra/Caddyfile) emite o certificado HTTPS (Let's Encrypt) e serve a
aplicação e o Grafana (`/grafana`). A cada push na `main` que passa em todos os testes, o pipeline publica a imagem no
GHCR e atualiza a VM pelo `az vm run-command`, com login OIDC, **sem SSH**. Rollback: rodar o workflow manualmente
informando o commit anterior.

Monitoramento: Spring Actuator + Micrometer → Prometheus → Grafana, com o Prometheus já configurado como fonte de
dados do Grafana ([`monitoramento/`](monitoramento)).

Passo a passo, variáveis e rollback: [docs/deploy.md](docs/deploy.md).

## Estrutura do repositório

| Caminho | Conteúdo |
|---|---|
| `backend/src/main/java/.../api/` | Controllers REST (só HTTP) e tratamento uniforme de erros |
| `backend/src/main/java/.../servico/` | Regras de negócio e transações: escalas, férias, afastamentos e cobertura dos turnos, movimento, cadastros, usuários e chat |
| `backend/src/main/java/.../motor/` | Motor financeiro, validador, gerador, previsão, planejador de demanda, sugestor de substituição e regras de férias e de afastamentos (Java puro) |
| `backend/src/main/java/.../repositorio/` | Acesso a dados (Spring Data JPA) |
| `backend/src/main/java/.../dominio/` | Entidades JPA |
| `backend/src/main/java/.../seguranca/` | JWT (API e WebSocket), permissões, limite de tentativas e dados confidenciais |
| `backend/src/main/java/.../config/` | Dados de demonstração, ajustes de esquema, WebSocket e encaminhamento da SPA |
| `backend/src/test/` | Testes unitários do motor, de integração da API e de arquitetura |
| `frontend/src/pages/` | Uma tela por arquivo (Painel, Escalas, Matriz, Férias, Afastamentos, Movimento, Simulador, Mensagens, cadastros…) |
| `frontend/src/chat/` | Estado, conexão em tempo real (STOMP) e componentes do chat |
| `frontend/src/components/` | Layout e componentes reutilizáveis |
| `docs/` | Requisitos, arquitetura (C4), modelo de dados, deploy e conformidade — publicados na Wiki |
| `infra/` | Terraform (Azure), Caddyfile e script de atualização da VM |
| `monitoramento/` | Prometheus e Grafana |
| `.github/` | Pipelines de CI/CD e da Wiki, modelo de pull request |

## Documentação

A documentação completa está na **[Wiki do projeto](../../wiki)**, publicada automaticamente a partir de `docs/`:

- [Requisitos](docs/requisitos.md): contexto, fluxos de negócio, requisitos, regras de negócio, fora do escopo e histórias de usuário.
- [Arquitetura](docs/arquitetura.md): diagramas C4 (contexto, contêineres e componentes), implantação, stack e registro de decisões.
- [Modelo de dados](docs/modelo-de-dados.md): diagramas entidade-relacionamento.
- [Deploy](docs/deploy.md): ambientes, variáveis, pipeline, Terraform, rollback e monitoramento.
- [Conformidade](docs/conformidade.md): LGPD, perfis de acesso, segurança (OWASP), acessibilidade (WCAG) e licenças.
- [Como contribuir](CONTRIBUTING.md): nomes de branch, TDD, comandos de teste, mensagens de commit e pull request.

## Limitações conhecidas

- **A previsão ainda não foi medida com dados reais.** O cenário de demonstração usa fechamentos hipotéticos; o selo de
  assertividade só terá valor depois de semanas de fechamentos lançados pela loja.
- **Os fatores das datas comemorativas (+30% e +15%) são hipóteses iniciais** e precisam ser confirmados com o parceiro.
  O cálculo reduz o peso delas à medida que os fechamentos dessas datas forem lançados.
- **Férias:** o sistema não controla o período aquisitivo nem a regra de que um dos períodos fracionados tenha ao menos
  14 dias; o repouso semanal de referência para a regra do início é o **domingo**.
- **Substituição automática:** quando ninguém pode cobrir o turno, ele é retirado e o dia fica com uma pessoa a menos
  (o Painel aponta). O limite financeiro não impede a troca, porque ninguém pode trabalhar de férias ou afastado.
- **Afastamento lançado depois do fato:** turnos de dias que já passaram são retirados, sem substituto, porque o
  sistema não sabe quem cobriu na prática; o custo daquele dia passa a refletir só quem ficou na escala.
- **Uma instância:** o limite de tentativas de login e o broker do chat ficam em memória; com várias instâncias, seria
  preciso um armazenamento compartilhado (ex.: Redis).
- **Bloqueio de login pode ser abusado:** quem errar de propósito 5 vezes a senha de um usuário o deixa 15 minutos sem
  entrar — o equilíbrio padrão desse tipo de proteção.
- **Esquema do banco** é atualizado pelo Hibernate (`ddl-auto: update`), sem migrações versionadas (ex.: Flyway).
- A observação da escala guarda até 255 caracteres; anotações automáticas antigas são cortadas.

## Licença

Distribuído sob a licença MIT; veja [LICENSE](LICENSE). Os logotipos do Supermercado Coelho não estão incluídos, e os
dados de demonstração são fictícios.

## Referências técnicas

[CLT — Decreto-Lei nº 5.452/1943](https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452.htm) ·
[Lei nº 10.101/2000](https://www.planalto.gov.br/ccivil_03/leis/l10101.htm) ·
[LGPD — Lei nº 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm) ·
[Spring Security](https://docs.spring.io/spring-security/reference/) ·
[OWASP Top 10](https://owasp.org/www-project-top-ten/) ·
[OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) ·
[ArchUnit](https://www.archunit.org/userguide/html/000_Index.html)
