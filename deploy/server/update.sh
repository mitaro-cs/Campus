#!/usr/bin/env bash
# Обновить Campus на сервере до последнего выпуска: копия данных → новый groupbase.jar →
# перезапуск. Не поднялся за минуту – возвращаем прежний файл.
#   sudo bash /opt/campus/update.sh
set -euo pipefail
REPO="${CAMPUS_REPO:-mitaro-cs/Campus}"
APP=/opt/campus
[ "$(id -u)" -eq 0 ] || { echo "Запустите от root (sudo)" >&2; exit 1; }
set -a; . /etc/campus.env; set +a

echo "==> Копия данных перед обновлением"
runuser -u campus -- java -jar "$APP/groupbase.jar" backup -d "$GROUPBASE_DATA_DIR"

echo "==> Новый выпуск"
curl -fL --retry 4 -o "$APP/groupbase.jar.new" \
	"https://github.com/$REPO/releases/latest/download/groupbase.jar"
unzip -tq "$APP/groupbase.jar.new" >/dev/null || { echo "Файл повреждён" >&2; exit 1; }
cp -f "$APP/groupbase.jar" "$APP/groupbase.jar.prev"
mv -f "$APP/groupbase.jar.new" "$APP/groupbase.jar"
systemctl restart campus

for _ in $(seq 1 30); do
	if curl -fs http://127.0.0.1:8080/api/health >/dev/null; then
		echo "Готово."
		exit 0
	fi
	sleep 2
done
echo "Новая версия не запустилась – возвращаем прежнюю" >&2
mv -f "$APP/groupbase.jar.prev" "$APP/groupbase.jar"
systemctl restart campus
exit 1
