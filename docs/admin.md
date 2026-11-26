# 운영자 화면 연결

운영자 화면(`dangdang-2026/dangdang-admin`, private)은 배포하지 않고 운영진 PC에서만 띄웁니다. 데이터는 배포된 이 백엔드의 `/api/admin/**`에서 받습니다.

## 운영자 계정 만들기

회원가입 화면으로는 운영자 계정을 만들 수 없습니다. 이메일로 가입한 뒤 DB에서 역할을 바꿉니다.

1. 앱에서 운영자로 쓸 이메일로 가입하고 이메일 인증까지 끝냅니다.
2. DB에 접속해 역할을 바꿉니다.

```sql
UPDATE users SET role = 'ADMIN', updated_at = now()
WHERE email = '운영자이메일@example.com' AND auth_provider = 'EMAIL';
```

3. 앱에서 로그아웃했다가 다시 로그인합니다. 역할은 로그인할 때 토큰에 들어가서, 이미 받은 토큰에는 반영되지 않습니다.

운영자를 그만두면 `role = 'USER'`로 되돌립니다. 바꾼 사람과 날짜는 팀 노션에 따로 적어 둡니다.

## 세 겹으로 막는 방법

- `/api/admin/**`는 `SecurityConfig`에서 ADMIN 역할만 통과합니다. 이게 실제 잠금입니다.
- CORS도 나눴습니다. `/api/admin/**`는 `cors.admin-allowed-origins`(기본 `http://localhost:5180`)에서 온 요청만 받습니다. 나머지 `/api/**`는 앱 주소와 운영자 화면 주소를 둘 다 받습니다. 운영자도 `/api/auth/login`으로 로그인하기 때문입니다.
- CORS는 브라우저만 막습니다. 터미널이나 Postman 요청은 막지 못하니 ADMIN 확인을 빼면 안 됩니다.
- 운영자 API는 허용한 IP에서 온 요청만 받습니다(`AdminIpFilter`). 목록에 없는 IP는 운영자 계정이어도 403 `AUTH_40302`입니다. 로그인과 앱 API는 IP를 보지 않습니다.

운영 서버에서 주소를 바꾸려면 `CORS_ADMIN_ALLOWED_ORIGINS` 환경 변수에 쉼표로 적습니다.

### 허용 IP 정하기

운영진이 쓰는 네트워크의 공인 IP를 `ADMIN_ALLOWED_IPS`에 쉼표로 적습니다. 비워 두면 IP는 검사하지 않습니다(로컬, 개발 서버).

```sh
# 지금 쓰는 와이파이의 공인 IP
curl ifconfig.me

# 운영 서버 .env
ADMIN_ALLOWED_IPS=203.0.113.10,198.51.100.7
```

- 운영자 화면은 PC의 vite 프록시로 서버를 부르기 때문에, 서버에는 그 PC가 붙은 와이파이의 공인 IP가 찍힙니다.
- 와이파이가 바뀌면 IP도 바뀝니다. 졸전 당일 부스 IP는 리허설 때 확인해서 넣고 서버를 다시 띄웁니다. 핫스팟은 IP가 자주 바뀌어서 쓰지 않습니다.
- 같은 와이파이에 있는 사람은 모두 같은 IP로 보입니다. IP 검사를 통과해도 ADMIN 계정이 아니면 막힙니다.
- 운영 서버는 Nginx 뒤에 있어서 Spring에는 Nginx 주소(127.0.0.1)가 찍힙니다. 바로 앞이 내부 주소일 때만 Nginx가 넘긴 `X-Real-IP`, 없으면 `X-Forwarded-For`의 마지막 값을 씁니다. Nginx 설정에 아래 줄이 있어야 합니다.

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

막혔을 때 서버 로그에 `허용하지 않은 IP의 운영자 API 요청: ip=...`이 남습니다. 그 IP가 지금 쓰는 와이파이 IP인지 보고 목록에 넣습니다.

## 운영자 화면에서 백엔드 부르기

로그인 쿠키(`ACCESS_TOKEN`, `REFRESH_TOKEN`)가 `SameSite=Lax`라서, `localhost:5180` 화면에서 배포된 서버 주소를 바로 부르면 쿠키가 실리지 않습니다. 운영자 화면은 개발 서버의 프록시로 `/api`를 배포된 서버에 넘깁니다. 브라우저에는 같은 주소(`localhost:5180`)로 보여서 쿠키가 그대로 오갑니다.

프록시 설정과 실행 방법은 `dangdang-admin` README에 있습니다.

## 비밀값

DB 비밀번호, S3 키, JWT 비밀값은 `.env`에만 두고 깃에 올리지 않습니다. 팀끼리는 깃 말고 따로 주고받습니다.
