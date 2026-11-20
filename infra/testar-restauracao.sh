#!/bin/sh
# Teste de restauração: prova que um backup volta inteiro, SEM tocar no banco de produção. Restaura o arquivo num
# PostgreSQL descartável (contêiner temporário, sem rede nem porta publicada), confere as tabelas e apaga tudo.
# Uso: testar-restauracao.sh [arquivo.dump]   (sem argumento: o backup local mais recente)
# Rode uma vez por mês (docs/deploy.md); o pipeline de CI roda o mesmo teste a cada push.
set -eu

BACKUP_DIR="${BACKUP_DIR:-/var/backups/escalas}"
ARQUIVO="${1:-$(ls -1t "$BACKUP_DIR"/escalas-*.dump 2>/dev/null | head -n 1)}"
[ -n "$ARQUIVO" ] && [ -f "$ARQUIVO" ] || { echo "Nenhum backup encontrado em $BACKUP_DIR." >&2; exit 1; }

NOME="escalas-teste-restauracao-$$"
trap 'docker rm -f "$NOME" > /dev/null 2>&1 || true' EXIT
docker run -d --rm --name "$NOME" --network none -e POSTGRES_PASSWORD=teste -e POSTGRES_DB=restaurado \
  postgres:17-alpine > /dev/null
for _ in $(seq 1 30); do
  docker exec "$NOME" pg_isready -U postgres -d restaurado > /dev/null 2>&1 && break
  sleep 2
done

docker exec -i "$NOME" pg_restore -U postgres -d restaurado --no-owner --exit-on-error < "$ARQUIVO"

TABELAS="$(docker exec "$NOME" psql -U postgres -d restaurado -Atc \
  "select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'")"
[ "$TABELAS" -gt 0 ] || { echo "FALHOU: nenhuma tabela restaurada de $ARQUIVO." >&2; exit 1; }

echo "Restauração de $(basename "$ARQUIVO"): $TABELAS tabelas."
# Linhas de cada tabela (contagem exata, para comparar com a produção).
docker exec "$NOME" psql -U postgres -d restaurado -Atc \
  "select table_name || ': ' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from public.%I', table_name), false, true, '')))[1]::text
     from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' order by table_name" | sed 's/^/  /'
echo "Teste de restauração OK."
