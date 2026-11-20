#!/bin/sh
# Backup do PostgreSQL de produção. Roda todo dia pelo cron da VM (instalado pelo infra/atualizar.sh):
#   1. pg_dump em formato custom (compactado), em /var/backups/escalas (só root lê);
#   2. confere se o arquivo é um backup legível (pg_restore --list);
#   3. envia uma cópia ao Azure Blob Storage com a identidade gerenciada da VM (sem chave), se BACKUP_CONTA existir;
#   4. apaga as cópias locais com mais de 7 dias (no Storage, a retenção é do Terraform: 30 dias).
# Restauração: infra/restaurar.sh. Teste de restauração: infra/testar-restauracao.sh (docs/deploy.md).
set -eu

ESCALAS_DIR="${ESCALAS_DIR:-/opt/escalas}"
ESCALAS_ENV="${ESCALAS_ENV:-/etc/escalas/env}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/escalas}"
[ -f /etc/escalas/backup.conf ] && . /etc/escalas/backup.conf
DB_USER="$(sed -n 's/^DB_USER=//p' "$ESCALAS_ENV")"
DB_USER="${DB_USER:-escalas}"

cd "$ESCALAS_DIR"
COMPOSE="docker compose --env-file $ESCALAS_ENV -f docker-compose.yml"
umask 077
mkdir -p "$BACKUP_DIR"
ARQUIVO="$BACKUP_DIR/escalas-$(date -u +%Y%m%dT%H%M%SZ).dump"

$COMPOSE exec -T db pg_dump -U "$DB_USER" -d escalas_coelho --format=custom --no-owner > "$ARQUIVO"
$COMPOSE exec -T db pg_restore --list < "$ARQUIVO" > /dev/null
echo "Backup local: $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"

if [ -n "${BACKUP_CONTA:-}" ]; then
  # Token da identidade gerenciada da VM (IMDS); o Storage não aceita chaves de acesso.
  TOKEN="$(curl -fsS -H Metadata:true \
    "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https%3A%2F%2Fstorage.azure.com%2F" \
    | sed -E 's/.*"access_token":"([^"]+)".*/\1/')"
  curl -fsS -X PUT -T "$ARQUIVO" \
    -H "Authorization: Bearer $TOKEN" -H "x-ms-version: 2021-08-06" -H "x-ms-blob-type: BlockBlob" \
    "https://$BACKUP_CONTA.blob.core.windows.net/backups/$(basename "$ARQUIVO")"
  echo "Cópia enviada ao Storage: $BACKUP_CONTA/backups/$(basename "$ARQUIVO")"
else
  echo "AVISO: BACKUP_CONTA não configurada; o backup ficou só na VM."
fi

find "$BACKUP_DIR" -name 'escalas-*.dump' -mtime +7 -delete
echo "Backup concluído."
