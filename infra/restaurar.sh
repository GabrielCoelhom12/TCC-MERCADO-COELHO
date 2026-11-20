#!/bin/sh
# Restaura um backup NO BANCO DE PRODUÇÃO, substituindo os dados atuais. Antes, faz um backup do estado atual
# (para poder desfazer). A aplicação fica parada durante a restauração.
# Uso: restaurar.sh <arquivo.dump> --confirmar
# Para trazer um backup do Storage para a VM, veja docs/deploy.md ("Restaurar um backup").
set -eu

ARQUIVO="${1:?informe o arquivo .dump}"
[ "${2:-}" = "--confirmar" ] || { echo "Isto SUBSTITUI os dados de produção. Repita com --confirmar." >&2; exit 1; }
[ -f "$ARQUIVO" ] || { echo "Arquivo não encontrado: $ARQUIVO" >&2; exit 1; }

ESCALAS_DIR="${ESCALAS_DIR:-/opt/escalas}"
ESCALAS_ENV="${ESCALAS_ENV:-/etc/escalas/env}"
DB_USER="$(sed -n 's/^DB_USER=//p' "$ESCALAS_ENV")"
DB_USER="${DB_USER:-escalas}"
cd "$ESCALAS_DIR"
COMPOSE="docker compose --env-file $ESCALAS_ENV -f docker-compose.yml -f docker-compose.prod.yml"

echo "1/4 Backup do estado atual (para desfazer, se preciso)..."
sh infra/backup.sh

echo "2/4 Parando a aplicação..."
$COMPOSE stop app

echo "3/4 Restaurando $(basename "$ARQUIVO")..."
$COMPOSE exec -T db pg_restore -U "$DB_USER" -d escalas_coelho --clean --if-exists --no-owner --exit-on-error < "$ARQUIVO"

echo "4/4 Iniciando a aplicação..."
$COMPOSE start app
echo "Restauração concluída."
