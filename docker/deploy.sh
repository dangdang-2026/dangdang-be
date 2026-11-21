#!/usr/bin/env bash
# 블루그린 배포 — 쉬고 있는 색을 새 이미지로 띄우고 → 건강 확인 → Nginx를 그쪽으로 → 이전 색 끔
# cd.yml(운영)·cd-dev.yml(개발)이 이 파일을 서버 ~/dangdang/docker/ 로 복사해서 실행한다
set -euo pipefail
cd "$(dirname "$0")"

UPSTREAM=${UPSTREAM:-/etc/nginx/conf.d/dangdang-upstream.conf}   # Nginx가 Spring을 찾는 주소 (서버 사용자 소유)
BACKUP=./upstream.bak                                             # 전환 실패 때 되돌릴 사본 (conf.d 밖에 둔다)
HEALTH_TRIES=${HEALTH_TRIES:-40}                                  # 3초 × 40 = 최대 2분

# 0. 서버 준비(가이드 65번 B)가 됐는지 — 안 됐으면 아무것도 건드리지 않고 멈춤
if [ ! -w "$UPSTREAM" ]; then
  echo "✖ $UPSTREAM 이 없거나 쓸 수 없음 → 서버 준비(upstream 파일·sudoers) 먼저 — README 배포 절"
  exit 1
fi

# 1. 지금 켜진 색 알아내기 — upstream 파일이 가리키는 포트로 판단
if grep -q '127.0.0.1:8081' "$UPSTREAM"; then
  OLD=green; NEW=blue;  NEW_PORT=8080
else
  OLD=blue;  NEW=green; NEW_PORT=8081
fi
echo "▶ 지금 $OLD → 새로 $NEW (포트 $NEW_PORT)"

# 2. 새 이미지 받기 + 나머지(db·redis·ai·dozzle)는 평소처럼 (바뀐 게 없으면 그대로 둠)
docker compose pull "app-$NEW" ai
docker compose up -d db redis ai dozzle

# 3. 쉬던 색을 새 이미지로 띄우기 (옛 색은 그대로 손님을 받는 중)
docker compose up -d --no-deps "app-$NEW"

# 4. 새 색 건강 확인 — 실패하면 새 색만 끄고 끝 (손님은 계속 옛 색)
ok=0
for _ in $(seq 1 "$HEALTH_TRIES"); do
  if curl -fs "http://127.0.0.1:$NEW_PORT/actuator/health" > /dev/null; then ok=1; break; fi
  sleep 3
done
if [ "$ok" != 1 ]; then
  echo "✖ $NEW 건강 확인 실패 → $NEW 끄고 $OLD 그대로 둠"
  docker compose logs "app-$NEW" --tail 80 || true
  docker compose stop "app-$NEW"
  exit 1
fi
echo "✔ $NEW 건강함"

# 5. Nginx를 새 색으로 — 문법 검사 실패하면 원래대로
cp "$UPSTREAM" "$BACKUP"
printf 'upstream dangdang_app {\n    server 127.0.0.1:%s;\n}\n' "$NEW_PORT" > "$UPSTREAM"
if ! sudo -n /usr/sbin/nginx -t; then
  echo "✖ Nginx 설정 검사 실패 → 원래대로 되돌림"
  cp "$BACKUP" "$UPSTREAM"
  docker compose stop "app-$NEW"
  exit 1
fi
sudo -n /usr/bin/systemctl reload nginx
echo "✔ Nginx → $NEW"

# 6. 이전 색 끄기 — Nginx 옛 연결이 끝날 틈을 주고, Spring이 하던 요청을 마치게(graceful) 기다림
sleep 5
docker compose stop "app-$OLD"

# 7. (한 번만 해당) 블루그린 전에 쓰던 옛 'app' 컨테이너가 남아 있으면 지움 — 8080을 잡고 있으면 다음 blue가 못 뜬다
docker ps -aq --filter "label=com.docker.compose.project=$(basename "$PWD")" \
              --filter "label=com.docker.compose.service=app" | xargs -r docker rm -f

docker image prune -f
echo "🎉 배포 끝: $NEW (포트 $NEW_PORT) — 되돌리려면 가이드 「되돌리기」"
