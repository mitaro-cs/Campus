#!/usr/bin/env bash
# Campus на своём сервере (VPS) одной командой: Java 21, Caddy (HTTPS сам), служба systemd,
# которая поднимается после сбоя и перезагрузки. Ubuntu 22.04/24.04 или Debian 12.
#
#   curl -fsSL https://raw.githubusercontent.com/mitaro-cs/Campus/miaro/deploy/server/install.sh \
#     | sudo bash -s -- campus.example.ru
#
# Без своего домена подойдёт адрес вида 203-0-113-7.sslip.io (IP сервера через дефисы).
# Перенести группу с компьютера хоста: CAMPUS_RESTORE=/root/копия.zip (копия из «Управление →
# Сервер → Копии»; только на пустой сервер). Свой файл сервера: CAMPUS_JAR=/путь/groupbase.jar.
set -euo pipefail

DOMAIN="${1:-}"
REPO="${CAMPUS_REPO:-mitaro-cs/Campus}"
APP=/opt/campus
DATA=/var/lib/campus
ENV_FILE=/etc/campus.env

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\033[31mОшибка: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "запустите от root (sudo)"
[ -n "$DOMAIN" ] || die "укажите адрес сайта: install.sh campus.example.ru"
case "$DOMAIN" in http://* | https://* | */*) die "адрес без https:// и пути, например campus.example.ru" ;; esac
command -v apt-get >/dev/null || die "нужна Ubuntu или Debian (apt)"

say "Пакеты"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q curl ca-certificates gnupg debian-keyring debian-archive-keyring apt-transport-https unzip

# Бесплатные серверы (Google e2-micro) – 1 ГБ памяти: Java и Caddy не влезут без подкачки.
if [ "$(awk '/MemTotal/ {print $2}' /proc/meminfo)" -lt 1600000 ] && [ -z "$(swapon --noheadings)" ]; then
	say "Подкачка 2 ГБ (памяти мало)"
	fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
	chmod 600 /swapfile
	mkswap /swapfile >/dev/null
	swapon /swapfile
	grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi

say "Java 21"
if ! java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])'; then
	if ! apt-get install -y -q openjdk-21-jre-headless; then
		# Debian 12: Java 21 нет в репозитории – берём Temurin (Eclipse Adoptium).
		curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public |
			gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
		echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
			>/etc/apt/sources.list.d/adoptium.list
		apt-get update -q
		apt-get install -y -q temurin-21-jre
	fi
fi
JAVA="$(command -v java)"

say "Caddy (HTTPS-сертификат получит сам)"
if ! command -v caddy >/dev/null; then
	curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key |
		gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
	curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
		>/etc/apt/sources.list.d/caddy-stable.list
	apt-get update -q
	apt-get install -y -q caddy
fi

say "Пользователь и папки"
id campus >/dev/null 2>&1 || useradd --system --home-dir "$DATA" --shell /usr/sbin/nologin campus
install -d -o campus -g campus -m 750 "$DATA" "$DATA/data"
install -d -m 755 "$APP"

say "Сервер Campus"
if [ -n "${CAMPUS_JAR:-}" ]; then
	install -m 644 "$CAMPUS_JAR" "$APP/groupbase.jar.new"
else
	curl -fL --retry 4 -o "$APP/groupbase.jar.new" \
		"https://github.com/$REPO/releases/latest/download/groupbase.jar"
fi
unzip -tq "$APP/groupbase.jar.new" >/dev/null || die "скачанный groupbase.jar повреждён"
mv -f "$APP/groupbase.jar.new" "$APP/groupbase.jar"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]:-.}")" && pwd)"
if [ -f "$HERE/update.sh" ]; then
	install -m 755 "$HERE/update.sh" "$APP/update.sh"
else
	curl -fsSL -o "$APP/update.sh" "https://raw.githubusercontent.com/$REPO/miaro/deploy/server/update.sh"
	chmod 755 "$APP/update.sh"
fi

say "Настройки ($ENV_FILE)"
if [ ! -f "$ENV_FILE" ]; then
	cat >"$ENV_FILE" <<CONF
# Campus – настройки сервера (полный список – README, «Свой сервер»). После правки:
#   sudo systemctl restart campus
GROUPBASE_BASE_URL=https://$DOMAIN
GROUPBASE_DATA_DIR=$DATA/data
GROUPBASE_HTTP_ADDRESS=127.0.0.1
GROUPBASE_HTTP_PORT=8080
GROUPBASE_TIMEZONE=Europe/Moscow
GROUPBASE_UPLOADS_MAX_FILE_MB=100
GROUPBASE_BACKUP_AT=03:30
GROUPBASE_BACKUP_KEEP=14
CONF
	chmod 640 "$ENV_FILE"
	chgrp campus "$ENV_FILE"
fi

if [ -n "${CAMPUS_RESTORE:-}" ]; then
	say "Данные из копии"
	[ -f "$CAMPUS_RESTORE" ] || die "нет файла $CAMPUS_RESTORE"
	[ ! -e "$DATA/data/groupbase.db" ] || die "на сервере уже есть данные – восстановите из «Управление → Копии» или командой restore (docs/server.md)"
	install -o campus -g campus -m 640 "$CAMPUS_RESTORE" "$DATA/import.zip"
	runuser -u campus -- "$JAVA" -jar "$APP/groupbase.jar" restore -y -d "$DATA/data" "$DATA/import.zip"
	rm -f "$DATA/import.zip"
fi

say "Служба systemd"
cat >/etc/systemd/system/campus.service <<UNIT
[Unit]
Description=Campus – сайт группы
After=network-online.target
Wants=network-online.target

[Service]
User=campus
Group=campus
EnvironmentFile=$ENV_FILE
WorkingDirectory=$DATA
ExecStart=$JAVA -Xmx512m -XX:+UseSerialGC -jar $APP/groupbase.jar serve
# Код 3 – сервер сам просит перезапуск (восстановление из копии).
Restart=always
RestartSec=5
SuccessExitStatus=143
TimeoutStopSec=30
LimitNOFILE=65536
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$DATA

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable campus
systemctl restart campus

say "HTTPS через Caddy"
cat >/etc/caddy/Caddyfile <<CADDY
$DOMAIN {
	encode zstd gzip
	reverse_proxy 127.0.0.1:8080 {
		# Живые обновления (SSE) – без буфера.
		flush_interval -1
	}
}
CADDY
systemctl reload caddy || systemctl restart caddy

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
	ufw allow 80/tcp >/dev/null
	ufw allow 443/tcp >/dev/null
fi
# Oracle Cloud: в образе Ubuntu iptables отклоняет всё, кроме SSH.
if command -v iptables >/dev/null && iptables -S INPUT 2>/dev/null | grep -q -- '-j REJECT'; then
	for port in 443 80; do
		iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null ||
			iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
	done
	command -v netfilter-persistent >/dev/null && netfilter-persistent save >/dev/null 2>&1 || true
fi

say "Ждём запуска"
for _ in $(seq 1 60); do
	curl -fs http://127.0.0.1:8080/api/health >/dev/null && break
	sleep 2
done
curl -fs http://127.0.0.1:8080/api/health >/dev/null || die "сервер не ответил – смотрите: journalctl -u campus -n 80"

CODE="$(journalctl -u campus --no-pager -o cat | grep -oE 'setup\?code=[A-Za-z0-9_-]+' | tail -1 || true)"
echo
echo "Готово. Campus работает и сам поднимется после перезагрузки сервера."
if [ -n "$CODE" ]; then
	echo "Откройте: https://$DOMAIN/$CODE"
	echo "Там – создать группу или «Восстановить из резервной копии» (перенос с компьютера);"
	echo "после восстановления: sudo systemctl restart campus"
else
	echo "Сайт: https://$DOMAIN"
fi
echo "Обновление: sudo bash $APP/update.sh   ·   журнал: journalctl -u campus -f"
