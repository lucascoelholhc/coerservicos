#!/bin/sh
# Gera as identidades do S3 a partir de S3_ACCESS_KEY/S3_SECRET_KEY (vindas do .env) e sobe o SeaweedFS.
# Nenhuma chave fica no repositório: o arquivo é escrito só dentro do contêiner, legível só pelo
# usuário do SeaweedFS. Usado pelo docker-compose e pelos testes (mesma imagem, CORE-09).
set -eu

: "${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no .env}"
: "${S3_SECRET_KEY:?defina S3_SECRET_KEY no .env}"

# As chaves vão para um JSON montado aqui: só caracteres que não precisam de escape.
for chave in "$S3_ACCESS_KEY" "$S3_SECRET_KEY"; do
  case "$chave" in
    *[!A-Za-z0-9/+=_-]*) echo "S3_ACCESS_KEY e S3_SECRET_KEY: use só letras, números e / + = _ -" >&2; exit 1 ;;
  esac
done

CONFIG=/tmp/s3-identidades.json
umask 077
cat > "$CONFIG" <<JSON
{"identities":[{"name":"coe","credentials":[{"accessKey":"$S3_ACCESS_KEY","secretKey":"$S3_SECRET_KEY"}],
"actions":["Admin","Read","Write","List","Tagging"]}]}
JSON
# o entrypoint da imagem troca para o usuário seaweed antes de rodar o weed
if [ "$(id -u)" = "0" ]; then chown seaweed:seaweed "$CONFIG"; fi

exec /entrypoint.sh server -ip.bind=0.0.0.0 -volume.max=0 -s3 -s3.port=8333 -s3.config="$CONFIG" -master.telemetry=false
