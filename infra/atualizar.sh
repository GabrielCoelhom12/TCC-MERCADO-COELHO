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

$COMPOSE pull --quiet
$COMPOSE up -d --remove-orphans
docker image prune -f > /dev/null

echo "Versão $IMAGEM_TAG publicada."
