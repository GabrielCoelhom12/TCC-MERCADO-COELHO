# Experimento — gerador guloso × otimização (Timefold)

Comparação dos dois geradores de escala do sistema, com os mesmos dados de entrada. Código:
[`ExperimentoTimefoldTest`](../backend/src/test/java/br/com/coelho/escalas/motor/ExperimentoTimefoldTest.java).

## Método

- **30 cenários sorteados** com semente fixa (`2026`), sempre os mesmos a cada execução, cada um com uma semana de escala:
  - Frente de caixa com piso de 2 ou 3 pessoas por dia e 4 ou 5 operadores; açougue com piso de 1 e 2 açougueiros;
  - custo-hora sorteado por pessoa; cada pessoa perde de 0 a 2 dias de disponibilidade;
  - em metade dos cenários, uma pessoa sai de férias por parte da semana.
- Os dois algoritmos geram **do zero, para as mesmas vagas** (vindas do mesmo planejador de demanda). O Timefold roda com
  limite de 3 s por cenário.
- Métricas: **vagas sem ninguém**, **custo da semana** (com encargos e acréscimo de domingo) e **erros das regras da CLT**
  apontados pelo validador do sistema (o mesmo usado para aprovar uma escala).

Para reproduzir: `cd backend && mvn test -Dtest=ExperimentoTimefoldTest -Dexperimento=true`.

## Resultado (14/11/2026)

| | Gerador guloso | Timefold |
|---|---|---|
| Vagas sem ninguém (total nos 30 cenários) | 29 | **22** (−24%) |
| Cenários em que cobriu mais vagas | 0 | **7** |
| Cenários empatados em cobertura | 23 | 23 |
| Erros de CLT | 0 | 0 |
| Custo médio da semana | R$ 2.258,51 | R$ 2.283,14 (+1,1%) |

- **A otimização nunca cobriu menos** que o guloso e cobriu mais em 7 de 30 cenários. O guloso decide dia a dia e às
  vezes "gasta" uma pessoa num dia em que outra serviria, faltando gente mais adiante na semana (descanso de 11 h, dias
  seguidos, folgas); o Timefold enxerga a semana inteira.
- O custo médio maior vem justamente das vagas a mais preenchidas (cada uma é um turno pago). Nos cenários empatados em
  cobertura, os custos ficaram praticamente iguais, ora um pouco menores, ora um pouco maiores — a pontuação também
  pesa o equilíbrio de horas entre as pessoas.
- **No cenário de demonstração** (22 colaboradores, sobra de gente), os dois cobrem toda a demanda sem erros; o Timefold
  ficou entre R$ 3 e R$ 42 mais barato por semana em duas das três escalas e, na terceira, distribuiu melhor as horas
  (desvio-padrão de 3,8 h para 3,1 h), com custo R$ 20 maior.

## Leitura

O ganho da otimização aparece quando **falta gente** — semanas com férias, atestados e folgas restritas —, que é
quando a escala manual mais falha. Com equipe folgada, o guloso já chega perto do ótimo e é instantâneo; por isso ele
continua sendo o gerador da criação automática diária, e o Timefold fica disponível na matriz ("Otimizar com Timefold"),
com a comparação ("Comparar algoritmos") para o gestor ver a diferença na semana dele.

Limites: cenários sintéticos e uma semana por vez; o Timefold escolhe **quem** ocupa cada vaga, mas não muda o horário
das vagas (os turnos vêm do planejador de demanda).
