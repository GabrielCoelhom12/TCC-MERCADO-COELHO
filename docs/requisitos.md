# Requisitos — Escalas · Supermercado Coelho

## Contexto

O Supermercado Coelho (Joinville/SC) monta as escalas de trabalho manualmente. Isso gera quadros superdimensionados
em dias de baixo movimento, falta de pessoal em picos e risco de infração trabalhista (interjornada, repouso semanal,
folga dominical). O sistema gera, valida e otimiza financeiramente as escalas, respeitando a CLT e o teto de folha
definido sobre a receita projetada de cada dia.

## Fluxos de negócio

O sistema tem três fluxos de negócio completos, cada um coberto por testes de integração em
`backend/src/test/java/br/com/coelho/escalas/api`:

| # | Fluxo | Do início ao fim | Teste |
|---|-------|------------------|-------|
| 1 | **Cadastros e regras** | Gestor cadastra setores com demanda mínima, colaboradores (salário, encargos, disponibilidade, ausências), modelos de turno, feriados, projeções de receita e parâmetros CLT. | `CadastrosIntegracaoTest` |
| 2 | **Ciclo da escala** | O sistema cria o rascunho da semana (ou o gestor cria a escala de um período) → o motor gera a sugestão de menor custo que cobre o movimento previsto → o gestor ajusta células na matriz com validação instantânea → aprova (somente sem erros) → pode reabrir. | `EscalaIntegracaoTest` |
| 3 | **Análise financeira** | Gestor consulta capacidade diária (L_min, L_max, teto), simula um quadro proposto contra o recomendado pelo motor e acompanha o painel consolidado (custo × teto). | `MotorIntegracaoTest` |

## Requisitos funcionais

| ID | Requisito |
|----|-----------|
| RF01 | Autenticar o usuário com login e senha e emitir um token JWT com validade limitada. |
| RF02 | Manter setores com cor, situação e demanda mínima de operadores por perfil de dia (seg–dom e feriado). |
| RF03 | Manter colaboradores com setor, cargo, salário mensal, carga horária, % de encargos, dias disponíveis e situação. Um colaborador com histórico de escala é desativado em vez de excluído. |
| RF04 | Registrar e remover ausências (férias, atestado, folga acordada) de um colaborador. |
| RF05 | Manter modelos de turno (horário, intervalo, aplicação em dias úteis ou domingos/feriados). |
| RF06 | Manter feriados e importar automaticamente os nacionais e os municipais de Joinville de um ano. |
| RF07 | Manter projeções de receita padrão por perfil de dia e projeções específicas por data. |
| RF08 | Manter os parâmetros operacionais: fatores de domingo/feriado, % de teto da folha, limites CLT e modo financeiro (BLOQUEAR/ALERTAR). |
| RF09 | Criar, renomear e excluir escalas de até 62 dias sem sobreposição de período. |
| RF10 | Gerar automaticamente a escala alocando exatamente L_min por setor, ao menor custo, respeitando as regras CLT e informando déficits. |
| RF11 | Editar a matriz colaborador × dia (modelo de turno, horário personalizado ou folga) com revalidação imediata. |
| RF12 | Validar a escala contra as regras: interjornada de 11h, repouso semanal, intervalo intrajornada, jornada diária/semanal, folga dominical, sobreposição, indisponibilidade, ausência, sub e superdimensionamento e custo acima do teto. |
| RF13 | No modo BLOQUEAR, recusar a alocação que leve o dia acima de L_max ou do teto da folha. |
| RF14 | Aprovar a escala somente quando não houver violações de severidade ERRO e bloquear a edição de escalas aprovadas, que podem ser reabertas. |
| RF15 | Calcular a capacidade diária: fator F_d, receita projetada, teto da folha, L_min e L_max. |
| RF16 | Simular o custo de um quadro proposto e compará-lo ao recomendado pelo motor, mostrando a economia. |
| RF17 | Exibir o painel financeiro do período com custo × teto e a situação de cada dia. |
| RF18 | Permitir que administradores criem e editem usuários (nome, login, perfil, senha, foto e situação), mantendo sempre ao menos um administrador ativo. Usuários desativados não conseguem entrar. |
| RF19 | Exigir senha forte (8 a 64 caracteres, com maiúscula, minúscula, número e caractere especial), exibindo os requisitos durante a digitação e avisando quando o Caps Lock estiver ativo. |
| RF20 | Aceitar foto de usuários e colaboradores em PNG ou JPEG de até 2 MB, verificada pelo conteúdo do arquivo e regravada no servidor como JPEG 256×256 (sem metadados). |
| RF21 | Permitir conversas privadas entre usuários e grupos livres (criar, renomear, adicionar pessoas e sair), com entrega das mensagens em tempo real. |
| RF22 | Anexar imagens (PNG/JPEG até 5 MB, regravadas no servidor) e mencionar escalas nas mensagens, com link para a matriz. |
| RF23 | Exibir quem está online, o contador de mensagens não lidas (no menu e por conversa) e a indicação de leitura nas conversas privadas. |
| RF24 | Permitir chamadas de voz entre os participantes de uma conversa privada (ligar, atender, recusar, silenciar e desligar), com áudio direto entre os navegadores, aviso visível em qualquer tela e registro no histórico (duração, perdida ou recusada). |
| RF25 | Registrar o fechamento de cada dia (clientes e vendas por faixa de horário configurável) e corrigi-lo depois, destacando os dias pendentes. |
| RF26 | Prever o movimento dos próximos dias a partir dos fechamentos (média recente do mesmo dia da semana, efeitos aprendidos de início do mês e véspera de feriado, feriados e pico por faixa), com a explicação de cada previsão. |
| RF27 | Calcular as pessoas necessárias por faixa nos setores que acompanham o movimento (capacidade de atendimento), escolher os turnos que cobrem os picos e alertar quando uma edição manual deixar um pico descoberto. |
| RF28 | Criar automaticamente, como rascunho, a escala das próximas semanas (antecedência configurável), sem alterar escalas existentes. |

## Requisitos não funcionais

| ID | Requisito |
|----|-----------|
| RNF01 | Arquitetura em camadas (API REST → serviço → motor de cálculo → repositório) com o motor sem dependência de banco. |
| RNF02 | Persistência em PostgreSQL (servidor), configurada por variáveis de ambiente. |
| RNF03 | Segurança: senhas com BCrypt, API stateless com JWT, nenhum segredo versionado, validação de entrada em todas as rotas. |
| RNF04 | Cobertura de testes de no mínimo 75% no backend e 25% no frontend, verificada no pipeline. |
| RNF05 | Pipeline de CI/CD (GitHub Actions) com testes, cobertura, análise estática (SonarCloud) e publicação da imagem Docker. |
| RNF06 | Observabilidade: health check e métricas Prometheus (Spring Actuator + Micrometer), visualizadas no Grafana. |
| RNF07 | Interface web responsiva em português, com retorno ao usuário (carregamento, erros e confirmações). |
| RNF08 | Implantação em contêiner (Docker), com a SPA e a API servidas por uma única imagem. |
| RNF09 | Comunicação em tempo real por WebSocket/STOMP autenticado com o mesmo JWT da API; cada usuário só recebe eventos das conversas de que participa. |

## User stories

**US01 — Gerar escala da semana** (RF09, RF10)
Como gestor, quero gerar automaticamente a escala da próxima semana para não precisar montá-la à mão.
- *Dado* que setores, colaboradores e modelos de turno estão cadastrados, *quando* crio uma escala com "gerar sugestão"
  marcado, *então* o sistema aloca a demanda mínima de cada setor ao menor custo e informa os déficits.

**US02 — Ajustar a escala com feedback imediato** (RF11, RF12)
Como gestor, quero alterar o turno de um colaborador na matriz e ver na hora se alguma regra foi violada.
- *Quando* altero uma célula, *então* a validação é refeita e as violações aparecem com a regra, o dia e o colaborador.

**US03 — Impedir superdimensionamento** (RF13)
Como gestor financeiro, quero que o sistema impeça alocações que estourem o teto de folha do dia.
- *Dado* o modo BLOQUEAR, *quando* adiciono um operador além de L_max, *então* a alocação é recusada com o motivo.

**US04 — Aprovar somente escalas válidas** (RF14)
Como gerente, quero aprovar apenas escalas sem erros para garantir conformidade com a CLT.
- *Dada* uma escala com erros, *quando* tento aprová-la, *então* a aprovação é recusada e os erros são listados.
- *Dada* uma escala aprovada, *quando* tento editá-la, *então* preciso reabri-la antes.

**US05 — Comparar quadro proposto × recomendado** (RF16)
Como gerente, quero simular o quadro que eu montaria "no olho" e ver quanto o motor economiza.

**US06 — Cadastrar ausências** (RF04)
Como gestor, quero registrar as férias de um colaborador para que ele não seja escalado no período.

**US07 — Configurar regras e teto** (RF08)
Como gerente, quero ajustar o % de teto da folha e os limites CLT para refletir a convenção coletiva vigente.

**US08 — Acompanhar o custo do período** (RF17)
Como gerente, quero ver o custo escalado de cada dia contra o teto para agir antes do fechamento da folha.

**US09 — Gerenciar quem acessa o sistema** (RF18)
Como administrador, quero criar e editar os usuários da equipe de gestão para controlar quem acessa o sistema.
- *Quando* crio um usuário com login único e senha forte, *então* ele consegue entrar.
- *Quando* digito uma senha fraca, *então* vejo quais requisitos faltam e não consigo salvar.
- *Quando* escolho uma foto PNG ou JPEG, *então* ela aparece na lista de usuários e no menu lateral do próprio usuário.
- *Quando* desativo um usuário, *então* ele não consegue mais entrar, mas o histórico de aprovações dele é mantido.
- *Dado* que sou o único administrador ativo, *quando* tento rebaixar ou desativar a minha conta, *então* a alteração é recusada.

**US10 — Conversar com a equipe** (RF21–RF23)
Como gestor, quero conversar com outros gestores dentro do sistema para combinar trocas de turno sem depender de outros aplicativos.
- *Quando* envio uma mensagem, *então* a outra pessoa a recebe na hora, com o contador de não lidas no menu.
- *Quando* menciono uma escala, *então* quem recebe abre a matriz dela com um clique.
- *Quando* a outra pessoa lê a minha mensagem, *então* vejo a indicação "Lida".

**US11 — Ligar para resolver na hora** (RF24)
Como gestor, quero ligar para outro gestor pelo sistema para resolver uma troca de turno urgente sem sair da tela.
- *Dado* que a pessoa está online, *quando* ligo, *então* ela vê o aviso de chamada em qualquer tela e pode atender ou recusar.
- *Quando* ninguém atende em 30 segundos, *então* a chamada é encerrada e aparece como "Chamada perdida" na conversa.
- *Quando* a chamada termina, *então* o histórico mostra a duração.

**US12 — Escala que acompanha o movimento** (RF25–RF28)
Como gerente, quero que o sistema entenda os dias e horários de maior movimento e monte a escala sozinho, para eu só revisar.
- *Quando* lanço o fechamento do dia (clientes e vendas por faixa), *então* a previsão dos próximos dias passa a considerá-lo.
- *Dado* um histórico em que o início do mês vende mais, *então* a previsão mostra o efeito aprendido ("+12%, aprendido de 22 dias").
- *Dado* um pico previsto às 16h–19h, *então* a escala gerada reforça os turnos que cobrem esse horário.
- *Quando* a semana seguinte ainda não tem escala, *então* o sistema cria o rascunho sozinho e eu ajusto o que quiser na matriz.
