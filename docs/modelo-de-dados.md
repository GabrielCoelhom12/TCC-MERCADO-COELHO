# Modelo de dados

Banco relacional **PostgreSQL**. As tabelas são geradas pelo Hibernate a partir das entidades JPA em
[`backend/.../dominio`](../backend/src/main/java/br/com/coelho/escalas/dominio/Funcionario.java)
(`spring.jpa.hibernate.ddl-auto=update`). Os nomes abaixo são os das tabelas e colunas no banco.

## Escalas e cadastros

```mermaid
erDiagram
    SETOR ||--o{ FUNCIONARIO : "lota"
    SETOR ||--o{ SETOR_DEMANDA_MINIMA : "exige por tipo de dia"
    FUNCIONARIO ||--o{ FUNCIONARIO_DISPONIBILIDADE : "pode trabalhar em"
    FUNCIONARIO ||--o{ AUSENCIA : "tem"
    FUNCIONARIO ||--o{ TURNO : "trabalha"
    ESCALA ||--o{ TURNO : "contém"

    SETOR {
        bigint id PK
        varchar nome UK
        varchar cor
        boolean ativo
        int clientes_por_colaborador_hora "nulo = setor não acompanha o movimento"
    }
    SETOR_DEMANDA_MINIMA {
        bigint setor_id FK
        varchar tipo_dia "SEGUNDA ... DOMINGO, FERIADO"
        int minimo_operadores
    }
    FUNCIONARIO {
        bigint id PK
        varchar nome
        varchar matricula UK
        varchar cargo
        bigint setor_id FK
        numeric salario_mensal
        int carga_horaria_mensal
        numeric percentual_encargos
        boolean ativo
        bytea foto "opcional"
    }
    FUNCIONARIO_DISPONIBILIDADE {
        bigint funcionario_id FK
        varchar dia_semana
    }
    AUSENCIA {
        bigint id PK
        bigint funcionario_id FK
        date inicio
        date fim
        varchar motivo "FERIAS, ATESTADO, FOLGA_ACORDADA, OUTRO"
        varchar observacao
    }
    ESCALA {
        bigint id PK
        varchar nome
        date data_inicio
        date data_fim
        varchar status "RASCUNHO, APROVADA"
        boolean gerada_automaticamente
        timestamp aprovada_em
        varchar aprovada_por
    }
    TURNO {
        bigint id PK
        bigint escala_id FK
        bigint funcionario_id FK
        date data
        time hora_inicio
        time hora_fim
        int intervalo_minutos
        varchar rotulo "sigla do modelo de turno"
    }
    TURNO_MODELO {
        bigint id PK
        varchar nome
        varchar sigla
        time hora_inicio
        time hora_fim
        int intervalo_minutos
        varchar aplicacao "DIAS_UTEIS, DOMINGOS_FERIADOS, TODOS"
    }
```

`TURNO_MODELO` não tem chave estrangeira para `TURNO`: ao escolher um modelo na matriz, os horários dele são
**copiados** para o turno. Assim, alterar um modelo depois não muda escalas já montadas.

## Previsão, custo e parâmetros

```mermaid
erDiagram
    MOVIMENTO_DIARIO ||--o{ MOVIMENTO_FAIXA : "por faixa de horário"

    MOVIMENTO_DIARIO {
        bigint id PK
        date data UK
        varchar observacao
        varchar registrado_por
        timestamp registrado_em
    }
    MOVIMENTO_FAIXA {
        bigint movimento_id FK
        time inicio
        time fim
        int clientes
        numeric vendas
    }
    FAIXA_HORARIA {
        bigint id PK
        time inicio
        time fim
    }
    FERIADO {
        bigint id PK
        date data UK
        varchar descricao
        varchar abrangencia "NACIONAL, ESTADUAL, MUNICIPAL"
        numeric fator_custo "nulo = usa o padrão"
    }
    PROJECAO_RECEITA {
        bigint id PK
        varchar tipo_dia UK
        numeric valor
    }
    PROJECAO_RECEITA_DATA {
        bigint id PK
        date data UK
        numeric valor
        varchar observacao
    }
    PARAMETROS_OPERACIONAIS {
        bigint id PK "sempre 1"
        numeric fator_domingo
        numeric fator_feriado
        numeric percentual_teto_folha
        numeric jornada_referencia_horas
        varchar modo_financeiro "BLOQUEAR, ALERTAR"
        boolean geracao_automatica
        int semanas_antecedencia
        numeric jornada_normal_diaria_horas
        numeric jornada_maxima_diaria_horas
        numeric jornada_semanal_horas
        numeric interjornada_minima_horas
        int max_dias_consecutivos
        int max_domingos_consecutivos
    }
```

- **Receita projetada de um dia:** `PROJECAO_RECEITA_DATA` da data, se existir; senão, `PROJECAO_RECEITA` do tipo de dia.
- **Faixas horárias:** `FAIXA_HORARIA` define as faixas em que o fechamento do dia é lançado (`MOVIMENTO_FAIXA`).
- **Parâmetros:** registro único (`id = 1`) com as regras da CLT e os limites de custo usados pelo motor.

## Usuários e chat

```mermaid
erDiagram
    USUARIO ||--o{ PARTICIPANTE_CONVERSA : "participa"
    CONVERSA ||--o{ PARTICIPANTE_CONVERSA : "tem"
    CONVERSA ||--o{ MENSAGEM : "contém"
    USUARIO ||--o{ MENSAGEM : "escreve"
    USUARIO ||--o{ CONVERSA : "cria"

    USUARIO {
        bigint id PK
        varchar login UK
        varchar senha_hash "BCrypt"
        varchar nome
        varchar perfil "ADMIN, GESTOR, SUPERVISOR"
        boolean ativo
        bytea foto "opcional"
    }
    CONVERSA {
        bigint id PK
        varchar tipo "DIRETA, GRUPO"
        varchar nome "só grupos"
        varchar chave_direta UK "impede duas conversas privadas entre o mesmo par"
        bigint criada_por_id FK
        timestamp atualizada_em
    }
    PARTICIPANTE_CONVERSA {
        bigint id PK
        bigint conversa_id FK
        bigint usuario_id FK
        bigint ultima_mensagem_lida_id "base das não lidas e do indicador Lida"
    }
    MENSAGEM {
        bigint id PK
        bigint conversa_id FK
        bigint autor_id FK
        varchar texto
        bigint imagem_id "aponta para ANEXO_IMAGEM"
        bigint escala_id "escala mencionada"
        varchar escala_nome "mantido se a escala for excluída"
        timestamp enviada_em
    }
    ANEXO_IMAGEM {
        bigint id PK
        bigint conversa_id
        bytea dados "JPEG regravado no servidor"
    }
```

- `PARTICIPANTE_CONVERSA` tem restrição única em `(conversa_id, usuario_id)`.
- `MENSAGEM` tem índice em `(conversa_id, id)`, usado na paginação do histórico.
- Os bytes das imagens ficam em `ANEXO_IMAGEM`, fora de `MENSAGEM`, para o histórico carregar sem eles.
- `escala_id` não é chave estrangeira de propósito: a mensagem continua legível (pelo `escala_nome`) mesmo se a
  escala for excluída.
