# Experimento: assertividade da previsão com o histórico real de vendas

A previsão de movimento é a entrada dos dois algoritmos de escala: dela saem a demanda de pessoas por faixa de horário
(L_min) e o teto da folha do dia (L_max). Tanto o **gerador guloso** quanto o **Timefold** só montam uma boa escala se a
previsão estiver certa: se ela subestima o dia, os dois deixam a loja com gente a menos; se superestima, gastam folha
à toa. Por isso, melhorar a previsão melhora os dois algoritmos ao mesmo tempo.

## Dados

Vendas diárias reais da loja parceira (exportação do PDV: venda líquida e quantidade de cupons por dia), de jan/2024
a nov/2026, cerca de 1.000 dias de funcionamento. **Os dados são confidenciais e não fazem parte do repositório**:
foram usados só para medir e calibrar a previsão. O `.gitignore` bloqueia planilhas (`*.csv`, `*.xlsx`) para que
nenhum arquivo de vendas seja versionado por engano. Este documento não traz valores de venda, só erros percentuais.

## Método

Para cada dia de 2025 e 2026 (679 dias), a previsão é refeita **só com o histórico anterior a ele** (janela de 400
dias, a mesma do sistema), como se o gestor a tivesse pedido na véspera, e comparada com a venda real:

- **MAPE**: erro médio absoluto, em % da venda real;
- **viés**: erro médio com sinal (negativo = a previsão ficou abaixo do real).

O arquivo só traz o total do dia; a distribuição por faixa usa um perfil horário hipotético e não entra no erro.

Para reproduzir (com o arquivo fora do repositório):

```bash
cd backend
mvn test -Dtest=ExperimentoPrevisaoTest -Dvendas=C:/caminho/vendas.csv
```

## O que os dados mostraram

1. **Datas muito diferentes estavam juntas.** A "semana do Natal" (20 a 24/12) usava um efeito só, mas a véspera de
   Natal vende bem mais que o dobro de um dia comum, e os dias 20 a 22 só um pouco acima. O mesmo vale para 30 e 31/12.
2. **Algumas hipóteses não se confirmaram**: o sábado de Aleluia, a sexta e o sábado antes do Carnaval e a Black
   Friday não mudaram o movimento de forma consistente; já a segunda-feira de Carnaval e a quinta-feira Santa sobem.
3. **Feriado não é domingo.** A previsão de feriado tirava a média dos últimos feriados, misturando um feriado de
   sábado com um de segunda. Na loja, o feriado em dia útil vende acima do mesmo dia da semana (as pessoas estão de
   folga), e no fim de semana quase nada muda.
4. **O crescimento da loja se confundia com o efeito das datas.** O efeito de uma data passada era medido contra os
   dias comuns mais recentes; com a loja crescendo de um ano para o outro, o Natal passado parecia "fraco".
5. **Bug encontrado:** com vésperas suficientes no histórico e efeito medido desprezível, a previsão de uma véspera de
   feriado lançava `NullPointerException` (e a geração da escala falharia). Só aparece com histórico longo e real.

## Mudanças

- `CalendarioSazonal`: Natal e Ano-Novo separados por dia (20–22, 23 e 24/12; 30 e 31/12); quinta-feira Santa e
  segunda-feira de Carnaval no lugar do sábado de Aleluia e do pré-Carnaval com efeito; hipóteses iniciais
  recalibradas com o histórico (arredondadas para baixo).
- `PrevisaoMovimento`:
  - feriado = média do mesmo dia da semana × efeito do feriado medido no histórico, separado em dia útil e fim de semana
    (com pelo menos 2 feriados do mesmo tipo; senão, o cálculo antigo);
  - efeitos medidos contra os dias comuns **mais próximos** da data, e não os mais recentes;
  - véspera sem efeito relevante não recebe ajuste (correção do bug).

## Resultados (679 dias, 2025–2026)

| Grupo | Antes | Depois |
|-------|------:|-------:|
| Todos os dias | 7,8% | **7,3%** |
| Feriados (17 dias) | 15,3% (viés +6,2%) | **9,8%** (viés +0,6%) |
| Datas comemorativas (≈ 28 dias) | 13,6% | **8,1%** |
| Natal 20 a 24/12 | 25,2% | 11,4% (20–22) · **4,2%** (23) · **4,4%** (24) |
| Ano-Novo | 22,7% | **9,6%** (30) · **7,5%** (31) |

O "antes" já inclui a correção do bug (sem ela o experimento nem termina).

**Leitura.** No dia a dia o ganho é pequeno (a média do mesmo dia da semana já funcionava bem). O ganho está nos dias
de pico, que são justamente os que mais pesam na escala: na véspera de Natal, a previsão antiga esperava um dia +30%
maior que o normal; a real foi bem mais que o dobro. Com a previsão nova, guloso e Timefold recebem uma demanda
próxima da real e escalam a equipe para o pico, em vez de deixar a frente de caixa descoberta.

**Limitações.** As hipóteses do calendário foram calibradas com o mesmo histórico usado no teste, então a melhora nas
datas comemorativas é otimista para uma loja nova; as mudanças de feriado e da base de comparação são estruturais (sem
ajuste fino) e valem para qualquer loja. Há só uma ou duas ocorrências de cada data no período testado. E o arquivo não
traz vendas por hora: pedir ao PDV o relatório por faixa de horário melhoraria a distribuição da demanda ao longo do dia.
