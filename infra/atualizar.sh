#!/bin/sh
# Publica uma versão na VM de produção. Executado pelo pipeline (az vm run-command) e na criação da VM (cloud-init);
# nunca por SSH. Uso: atualizar.sh <commit>   — ex.: atualizar.sh 1a2b3c4 (a imagem usada é a tag sha-1a2b3c4).
# Para voltar a uma versão anterior, basta rodar o mesmo comando com o commit antigo (job "Deploy" do workflow).
set -eu

COMMIT="${1:?informe o commit}"
cd /opt/escalas

# Arquivos de configuração (compose, Caddyfile, Prometheus) da mesma versão da imagem.
git fetch --quiet origin
git checkout --quiet --force "$COMMIT"

COMPOSE="docker compose --env-file /etc/escalas/env -f docker-compose.yml -f docker-compose.prod.yml"
export IMAGEM_TAG="sha-$(git rev-parse --short=7 HEAD)"

# Conta do Storage dos backups, vinda do pipeline (variável BACKUP_CONTA do ambiente producao); só minúsculas e números.
if [ -n "${BACKUP_CONTA:-}" ]; then
  case "$BACKUP_CONTA" in *[!a-z0-9]*) echo "BACKUP_CONTA inválida" >&2; exit 1 ;; esac
  (umask 077 && printf 'BACKUP_CONTA=%s\n' "$BACKUP_CONTA" > /etc/escalas/backup.conf)
fi

# Backup ANTES de atualizar: se a nova versão (ou uma migração do banco) der problema, o estado anterior está salvo.
# Na criação da VM o banco ainda não existe, e este passo é pulado. Se o backup falhar, a atualização não acontece.
if $COMPOSE ps --status running --services 2> /dev/null | grep -qx db; then
  sh infra/backup.sh
fi

$COMPOSE pull --quiet
$COMPOSE up -d --remove-orphans
docker image prune -f > /dev/null

# Backup diário do banco às 03h de Brasília (06h UTC, o relógio da VM), ver infra/backup.sh.
printf '%s\n' "# Backup diário do banco (instalado pelo infra/atualizar.sh)" \
  "0 6 * * * root sh /opt/escalas/infra/backup.sh >> /var/log/escalas-backup.log 2>&1" > /etc/cron.d/escalas-backup
chmod 644 /etc/cron.d/escalas-backup

echo "Versão $IMAGEM_TAG publicada."
