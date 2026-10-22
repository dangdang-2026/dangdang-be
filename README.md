# DANGDANG-Backend

당당(糖당) Backend 프로젝트 협업을 위한 개발 환경, 실행 방법 및 Git·코드 작성 규칙입니다.

---

# 🛠 Tech Stack

| Category | Stack |
| --- | --- |
| Framework | Spring Boot 4.1.1 |
| Language | Java 21 |
| Build Tool | Gradle 9.7.1 Wrapper |
| Web | Spring MVC |
| ORM | Spring Data JPA |
| Validation | Spring Validation |
| Database | PostgreSQL 16 |
| DB Image | pgvector/pgvector:pg16 |
| API Documentation | Swagger / OpenAPI |
| Swagger Library | springdoc-openapi 3.1.1 |
| Health Check | Spring Boot Actuator |
| Utility | Lombok |
| Development | Spring Boot DevTools |
| Cache / Infra | Redis 7 |
| Container | Docker, Docker Compose |
| CI/CD | GitHub Actions |
| Container Registry | Docker Hub |
| Deployment | Docker 기반 서버 자동 배포 |
| Log Viewer | Dozzle |

> Redis는 현재 Docker 인프라에 구성되어 있으며, Spring 애플리케이션과의 실제 연동은 기능 개발 과정에서 추가합니다.

---

# ⚙️ Project Setup

프로젝트의 기본 개발 환경은 다음과 같이 구성합니다.

- Spring Boot 4.1.1
- Java 21
- Gradle
- Spring MVC
- Spring Data JPA
- Spring Validation
- PostgreSQL
- Lombok
- Spring Boot DevTools
- Spring Boot Actuator
- Swagger / OpenAPI
- Docker
- Docker Compose
- GitHub Actions

Gradle은 별도로 설치하지 않고 프로젝트에 포함된 **Gradle Wrapper**를 사용합니다.

---

# 🗄 Database

프로젝트는 **PostgreSQL**을 사용합니다.

초기 프로젝트 생성 당시 MySQL을 사용했으나 현재 PostgreSQL로 전환되었습니다.

로컬 및 운영 Docker 환경에서는 다음 이미지를 사용합니다.

```text
pgvector/pgvector:pg16
```

PostgreSQL 16 기반 이미지이며, 추후 벡터 검색 등이 필요한 경우 pgvector를 활용할 수 있도록 구성되어 있습니다.

## 기본 로컬 DB 설정

`application.yaml`의 기본 설정은 다음과 같습니다.

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/dangdang}
    username: ${DB_USER:dangdang}
    password: ${DB_PASSWORD:dangdang}
```

환경변수를 지정하지 않았을 경우 다음 값이 사용됩니다.

| Property | Default |
| --- | --- |
| Host | `localhost` |
| Port | `5432` |
| Database | `dangdang` |
| Username | `dangdang` |
| Password | `dangdang` |

운영 환경에서는 기본값을 사용하지 않고 환경변수로 DB 정보를 주입합니다.

## JPA 설정

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
```

### `open-in-view: false`

웹 요청이 끝날 때까지 영속성 컨텍스트를 유지하지 않습니다.

데이터 조회와 변경은 Service 계층의 트랜잭션 범위 안에서 처리하는 것을 원칙으로 합니다.

### `ddl-auto: none`

Hibernate가 데이터베이스 테이블을 자동으로 생성하거나 수정하지 않습니다.

```text
create
update
create-drop
```

등의 자동 스키마 변경 기능을 사용하지 않습니다.

현재 별도의 Flyway/Liquibase 마이그레이션 도구는 적용되어 있지 않으며, DB 스키마 관리 방식은 기능 개발 과정에서 추가할 수 있습니다.

---

# 📁 Folder Structure

현재 프로젝트의 기본 구조는 다음과 같습니다.

```text
dangdang-be/
│
├── .github/
│   ├── ISSUE_TEMPLATE/
│   ├── workflows/
│   │   ├── ci.yml
│   │   └── cd.yml
│   └── PULL_REQUEST_TEMPLATE.md
│
├── docker/
│   ├── .env.example
│   ├── Dockerfile
│   ├── docker-compose.local.yml
│   └── docker-compose.yml
│
├── gradle/
│   └── wrapper/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── dangdang/
│   │   │           ├── DangdangBeApplication.java
│   │   │           └── global/
│   │   │               └── config/
│   │   │                   └── SwaggerConfig.java
│   │   │
│   │   └── resources/
│   │       └── application.yaml
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── dangdang/
│                   └── DangdangBeApplicationTests.java
│
├── .coderabbit.yaml
├── .gitattributes
├── .gitignore
├── build.gradle
├── gradlew
├── gradlew.bat
└── settings.gradle
```

## 주요 디렉터리 역할

| Directory | Description |
| --- | --- |
| `.github` | Issue / PR Template 및 GitHub Actions 관리 |
| `.github/workflows` | CI/CD Workflow 관리 |
| `docker` | 로컬·운영 Docker 환경 설정 |
| `gradle` | Gradle Wrapper 설정 |
| `src/main/java` | 애플리케이션 Java 코드 |
| `src/main/resources` | Spring 설정 파일 및 리소스 |
| `src/test` | 테스트 코드 |
| `global` | 프로젝트 전체에서 사용하는 공통 설정 |
| `global/config` | Swagger 등 전역 Configuration 관리 |

기능 개발이 진행되면 도메인 단위로 Controller, Service, Repository, Entity, DTO 등을 분리하여 관리합니다.

---

# 🧩 Package Convention

기본 패키지는 다음과 같습니다.

```text
com.dangdang
```

공통 설정이나 프로젝트 전체에서 사용하는 기능은 `global` 패키지에서 관리합니다.

```text
com.dangdang.global
```

현재 Swagger 설정은 다음 위치에 있습니다.

```text
com.dangdang.global.config.SwaggerConfig
```

도메인 기능 개발 시 하나의 클래스에 Controller, Service, Repository 로직을 모두 작성하지 않고 역할별로 분리합니다.

예를 들어 회원 도메인이 추가되는 경우 다음과 같은 형태로 확장할 수 있습니다.

```text
com.dangdang
├── domain/
│   └── user/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
└── global/
    ├── config/
    ├── exception/
    └── common/
```

위 구조는 기능 추가 시 사용할 수 있는 기본 방향이며, 실제 패키지는 도메인 구현 과정에서 팀 협의에 따라 확정합니다.

---

# 📚 API Documentation

프로젝트는 Swagger/OpenAPI를 사용하여 API 문서를 제공합니다.

사용 라이브러리:

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
```

## Local Swagger

Spring Boot 서버 실행 후 다음 주소로 접속합니다.

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## Production Swagger

Swagger에 등록된 운영 서버 주소는 다음과 같습니다.

```text
https://dang-dang.site
```

Swagger UI:

```text
https://dang-dang.site/swagger-ui.html
```

## Swagger 기본 정보

```text
Title       : 당당 API
Description : 임신성 당뇨 기록·알림 서비스 당당의 백엔드 API
Version     : v1
```

Swagger에서는 다음 서버를 사용할 수 있도록 설정되어 있습니다.

| Environment | URL |
| --- | --- |
| Production | `https://dang-dang.site` |
| Local | `http://localhost:8080` |

Swagger의 Tag와 API Operation은 알파벳 순으로 정렬됩니다.

---

# ❤️ Health Check

Spring Boot Actuator를 사용하여 서버 상태를 확인합니다.

현재 외부에 노출하는 Actuator Endpoint는 `health`입니다.

## Local

```text
http://localhost:8080/actuator/health
```

정상 실행 시 다음과 같은 형태의 응답을 확인할 수 있습니다.

```json
{
  "status": "UP"
}
```

## Production

```text
https://dang-dang.site/actuator/health
```

배포 과정에서도 해당 Health Check를 사용하여 애플리케이션의 정상 실행 여부를 확인합니다.

---

# 🌿 Branch Convention

안정적인 개발과 기능별 작업 관리를 위해 브랜치를 역할별로 분리합니다.

- 🔵 **main**
  - 실제 배포 가능한 안정적인 코드를 관리합니다.
  - 직접 작업하지 않습니다.
  - `main`에 Push되면 CD Workflow가 실행됩니다.
  - Pull Request를 통해 병합합니다.

- 🟣 **develop**
  - 기능 개발 결과를 통합하는 개발 브랜치입니다.
  - 새로운 작업 브랜치는 `develop`을 기준으로 생성합니다.

- 🟢 **작업 브랜치**
  - 기능 또는 Issue 단위로 생성합니다.
  - 하나의 브랜치는 하나의 Issue를 담당합니다.

```text
main
 └── develop
      ├── feat/10-login
      ├── feat/11-user
      ├── api/12-meal-record
      ├── fix/13-login-error
      └── chore/1-project-setup
```

---

# 📌 Branch Naming Convention

## 구조

```text
prefix/이슈번호-작업내용
```

## 예시

```text
feat/10-email-login
feat/11-kakao-login
feat/12-meal-record
api/13-food-search
fix/14-login-error
refactor/15-user-service
docs/16-update-readme
test/17-user-service
deploy/18-docker-config
chore/1-project-setup
```

| Prefix | 설명 |
| --- | --- |
| `feat` | 새로운 기능 구현 |
| `api` | API 관련 작업 |
| `fix` | 버그 수정 |
| `hotfix` | 긴급 버그 수정 |
| `refactor` | 코드 리팩토링 |
| `test` | 테스트 코드 작성 및 수정 |
| `docs` | 문서 수정 |
| `deploy` | 배포 관련 작업 |
| `chore` | 설정 및 기타 작업 |

## 작성 규칙

- 영문 소문자와 숫자를 사용합니다.
- 단어 구분은 하이픈 `-`을 사용합니다.
- 브랜치명에 `#`을 사용하지 않습니다.
- 하나의 브랜치는 하나의 Issue만 담당합니다.
- 작업 브랜치는 최신 `develop`에서 생성합니다.

```text
feat/10-email-login          ✅
feat/11-kakao-login          ✅
api/12-meal-record           ✅

feat/#10-email-login         ❌
feature/Login                ❌
```

---

# 📝 Commit Convention

커밋 메시지는 아래 형식으로 작성합니다.

```text
이모지 Type: 작업 내용
```

## 예시

```text
🎉 Start: Spring Boot 프로젝트 초기 생성

✨ Feat: 이메일 회원가입 기능 구현

🐛 Fix: 로그인 토큰 검증 오류 수정

🚑 Hotfix: 운영 서버 인증 오류 수정

♻️ Refactor: 회원 조회 로직 분리

🔧 Settings: PostgreSQL 환경 설정

🗃️ Comment: 회원 인증 로직 주석 추가

➕ Dependency/Plugin: Spring Security 의존성 추가

📝 Docs: Backend README 작성

🔀 Merge: 로그인 기능 브랜치 병합

🚀 Deploy: Docker 기반 운영 서버 배포

🚚 Rename: UserDto 클래스명 변경

🔥 Remove: 사용하지 않는 API 삭제

⏪ Revert: 이전 인증 로직으로 복구

✅ Test: 회원가입 서비스 테스트 추가
```

| 이모지 | Type | 설명 |
| --- | --- | --- |
| 🎉 | `Start` | 프로젝트 생성 및 초기 설정 |
| ✨ | `Feat` | 새로운 기능 구현 |
| 🐛 | `Fix` | 버그 수정 |
| 🚑 | `Hotfix` | 긴급 버그 수정 |
| ♻️ | `Refactor` | 기능 변경 없는 코드 구조 개선 |
| 🔧 | `Settings` | 개발 환경 및 설정 파일 변경 |
| 🗃️ | `Comment` | 주석 추가 및 수정 |
| ➕ | `Dependency/Plugin` | 라이브러리 및 플러그인 추가 |
| 📝 | `Docs` | 문서 추가 및 수정 |
| 🔀 | `Merge` | 브랜치 병합 |
| 🚀 | `Deploy` | 배포 관련 작업 |
| 🚚 | `Rename` | 파일 및 클래스 이름 변경 |
| 🔥 | `Remove` | 파일 및 코드 삭제 |
| ⏪ | `Revert` | 이전 커밋으로 복구 |
| ✅ | `Test` | 테스트 코드 추가 및 수정 |

## 커밋 작성 규칙

- 제목 끝에 마침표를 붙이지 않습니다.
- 한 커밋에는 하나의 논리적 변경만 포함합니다.
- `수정`, `작업`, `변경`처럼 모호한 표현만 사용하지 않습니다.
- 설정, 기능, 테스트 변경은 가능한 한 분리합니다.
- 커밋 메시지는 한국어로 작성합니다.

```text
🐛 Fix: 오류 수정                         ❌
🐛 Fix: 로그인 토큰 만료 검증 오류 수정    ✅
```

---

# 🔀 Workflow

1. 작업할 GitHub Issue를 생성합니다.
2. 로컬 `develop` 브랜치를 최신 상태로 변경합니다.
3. `develop`에서 작업 브랜치를 생성합니다.
4. 기능을 개발합니다.
5. 테스트를 실행합니다.
6. Commit Convention에 맞게 커밋합니다.
7. 원격 저장소에 Push합니다.
8. `develop` 브랜치로 Pull Request를 생성합니다.
9. GitHub Actions CI 결과를 확인합니다.
10. 팀원들의 코드 리뷰를 진행합니다.
11. 승인 후 병합합니다.
12. Merge가 완료되면 작업 브랜치를 삭제합니다.
13. 배포 시 `develop`을 `main`으로 병합합니다.
14. `main` Push 이후 CD Workflow를 통해 배포합니다.

## Merge 규칙

- `main`, `develop` 브랜치에 직접 Push하지 않습니다.
- Pull Request를 통해서만 병합합니다.
- 최소 1명의 리뷰 승인을 받습니다.
- CI가 정상적으로 통과한 이후 병합합니다.
- 충돌은 Pull Request 작성자가 해결합니다.
- Merge 후 작업 브랜치를 삭제합니다.
- 병합 방식은 `Squash and merge`를 권장합니다.

---

# 💻 Getting Started

## Requirements

다음 환경이 필요합니다.

```text
Java 21
Git
Docker / Docker Compose
```

Gradle은 프로젝트에 Gradle Wrapper가 포함되어 있으므로 별도 설치가 필요하지 않습니다.

설치된 Java 버전을 확인합니다.

```bash
java -version
```

Java 21이 출력되는지 확인합니다.

Git 버전을 확인합니다.

```bash
git --version
```

Docker를 사용하는 경우 다음 명령어도 확인합니다.

```bash
docker --version
docker compose version
```

---

# 📥 Repository Clone

저장소를 Clone합니다.

```bash
git clone https://github.com/dangdang-2026/dangdang-be.git
cd dangdang-be
```

개발 브랜치로 이동합니다.

```bash
git switch develop
```

---

# 🐘 Local Database Setup

로컬 개발 환경은 Docker Compose를 사용하여 PostgreSQL과 Redis를 실행할 수 있도록 구성되어 있습니다.

```bash
docker compose -f docker/docker-compose.local.yml up -d
```

실행되는 서비스:

| Service | Version | Port |
| --- | --- | --- |
| PostgreSQL | PostgreSQL 16 + pgvector | `5432` |
| Redis | Redis 7 | `6379` |

PostgreSQL 기본 설정:

```text
Database : dangdang
Username : dangdang
Password : dangdang
Port     : 5432
```

실행 중인 컨테이너를 확인합니다.

```bash
docker compose -f docker/docker-compose.local.yml ps
```

종료하려면 다음 명령어를 사용합니다.

```bash
docker compose -f docker/docker-compose.local.yml down
```

DB 데이터는 Docker Volume에 저장되므로 일반적인 `down` 명령으로는 삭제되지 않습니다.

DB 데이터까지 완전히 삭제해야 하는 경우에만 다음 명령어를 사용합니다.

```bash
docker compose -f docker/docker-compose.local.yml down -v
```

> `-v`를 사용하면 로컬 PostgreSQL 데이터가 삭제되므로 주의합니다.

---

# ▶️ Application Run

## Windows

```bash
gradlew.bat bootRun
```

또는 PowerShell에서:

```powershell
.\gradlew.bat bootRun
```

## macOS / Linux

```bash
./gradlew bootRun
```

정상 실행 후 기본 서버 주소는 다음과 같습니다.

```text
http://localhost:8080
```

---

# 🔐 Environment Variables

Spring Boot 애플리케이션에서 사용하는 주요 DB 환경변수는 다음과 같습니다.

| Variable | Description | Local Default |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/dangdang` |
| `DB_USER` | PostgreSQL 사용자 | `dangdang` |
| `DB_PASSWORD` | PostgreSQL 비밀번호 | `dangdang` |

환경변수를 지정하지 않으면 로컬 기본값을 사용합니다.

운영 환경에서는 반드시 별도의 안전한 값으로 설정합니다.

---

# 🐳 Docker

프로젝트는 Docker 기반 배포 환경을 사용합니다.

## Dockerfile

```text
docker/Dockerfile
```

Docker Image는 Multi-stage Build 방식으로 생성합니다.

### Build Stage

```text
eclipse-temurin:21-jdk
```

Gradle을 사용하여 Spring Boot JAR를 생성합니다.

```bash
./gradlew bootJar -x test --no-daemon
```

### Runtime Stage

```text
eclipse-temurin:21-jre
```

운영 Container에는 JDK 전체가 아니라 JRE만 포함합니다.

애플리케이션은 보안을 위해 root 계정이 아닌 별도의 `app` 사용자로 실행됩니다.

```text
USER app
```

기본 애플리케이션 포트는 다음과 같습니다.

```text
8080
```

Container 시간대는 다음과 같이 설정되어 있습니다.

```text
Asia/Seoul
```

---

# 🐳 Docker Compose

Docker Compose 설정은 로컬과 운영 환경을 분리합니다.

## Local

```text
docker/docker-compose.local.yml
```

로컬에서는 Spring Boot를 Docker Container로 실행하지 않습니다.

Docker에서는 PostgreSQL과 Redis만 실행하고 Spring Boot는 IDE 또는 `bootRun`으로 실행합니다.

```text
Spring Boot → Local 실행
PostgreSQL → Docker
Redis      → Docker
```

## Production

```text
docker/docker-compose.yml
```

운영 환경에서는 다음 서비스를 Docker Compose로 관리합니다.

| Service | Description |
| --- | --- |
| `app` | Spring Boot Backend |
| `db` | PostgreSQL + pgvector |
| `redis` | Redis |
| `dozzle` | Docker Container Log Viewer |

Spring Boot Container는 외부에 직접 노출하지 않고 서버의 localhost `8080`에 바인딩됩니다.

```text
127.0.0.1:8080:8080
```

PostgreSQL 데이터는 Docker Volume을 통해 유지합니다.

```text
pgdata
```

---

# 🔐 Production Environment

운영 환경용 환경변수 예시는 다음 파일에서 확인합니다.

```text
docker/.env.example
```

현재 환경변수 구조:

```env
DOCKER_REPO=
DB_NAME=dangdang
DB_USER=dangdang
DB_PASSWORD=
REDIS_PASSWORD=
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Seoul
```

실제 비밀번호나 서버 정보는 Git에 Commit하지 않습니다.

`.gitignore`에서 다음 파일들을 제외하도록 설정되어 있습니다.

```text
.env
*.env
```

단, 예제 파일은 Commit할 수 있도록 허용합니다.

```text
!.env.example
```

운영 환경의 실제 `.env` 값은 GitHub Secret을 통해 서버로 전달합니다.

---

# 🧪 Test

프로젝트는 JUnit Platform 기반으로 테스트를 실행합니다.

## Windows

```bash
gradlew.bat test
```

또는:

```powershell
.\gradlew.bat test
```

## macOS / Linux

```bash
./gradlew test
```

전체 Build와 Test를 함께 수행하려면 다음 명령어를 사용합니다.

## Windows

```bash
gradlew.bat clean build
```

## macOS / Linux

```bash
./gradlew clean build
```

Pull Request를 생성하기 전에 최소한 Build와 Test가 정상적으로 통과하는지 확인합니다.

---

# ✅ Code Check

현재 프로젝트에는 별도의 Checkstyle, Spotless 등의 코드 포맷터/정적 분석 도구가 적용되어 있지 않습니다.

Pull Request 생성 전 다음 검사를 기본으로 수행합니다.

## Build

```bash
./gradlew build
```

Windows:

```bash
gradlew.bat build
```

## Test

```bash
./gradlew test
```

Windows:

```bash
gradlew.bat test
```

빌드와 테스트를 모두 통과한 후 Pull Request를 생성합니다.

---

# 🔄 CI

GitHub Actions를 사용하여 Pull Request의 Build와 Test를 자동 검사합니다.

Workflow:

```text
.github/workflows/ci.yml
```

CI는 다음 브랜치로 Pull Request가 생성되면 실행됩니다.

```text
develop
main
```

CI 환경:

```text
Ubuntu Latest
Java 21
Gradle
PostgreSQL 16 + pgvector
```

Workflow에서 PostgreSQL Service Container를 실행한 뒤 다음 명령어를 수행합니다.

```bash
./gradlew build --no-daemon
```

따라서 Pull Request 병합 전 서버 Build와 Test 실패 여부를 확인할 수 있습니다.

---

# 🚀 CD

운영 배포는 GitHub Actions를 통해 자동화되어 있습니다.

Workflow:

```text
.github/workflows/cd.yml
```

CD는 다음 경우 실행됩니다.

```text
main 브랜치 Push
GitHub Actions 수동 실행
```

## Deployment Flow

```text
main Push
   ↓
GitHub Actions
   ↓
Docker Image Build
   ↓
Docker Hub Push
   ↓
docker-compose.yml 서버 전송
   ↓
SSH 서버 접속
   ↓
운영 .env 생성
   ↓
Docker Hub Login
   ↓
docker compose pull
   ↓
docker compose up -d
   ↓
Health Check
```

Docker Image에는 다음 두 Tag를 생성합니다.

```text
latest
Git Commit SHA
```

예시:

```text
repository:latest
repository:a1b2c3d...
```

배포가 완료되면 다음 Endpoint를 사용하여 최대 30회 Health Check를 수행합니다.

```text
http://127.0.0.1:8080/actuator/health
```

정상 상태가 확인되면 배포가 성공 처리됩니다.

Health Check가 계속 실패하면 애플리케이션의 최근 Docker Log를 출력하고 배포를 실패 처리합니다.

---

# 🔑 GitHub Actions Secrets

CD Workflow에서 사용하는 GitHub Secrets는 다음과 같습니다.

| Secret | Description |
| --- | --- |
| `DOCKER_USERNAME` | Docker Hub 사용자명 |
| `DOCKER_PASSWORD` | Docker Hub 인증 정보 |
| `DOCKER_REPO` | Docker Image Repository |
| `SERVER_HOST` | 배포 서버 주소 |
| `SERVER_USER` | 서버 SSH 사용자 |
| `SERVER_SSH_KEY` | 서버 SSH Private Key |
| `ENV_PROD` | 운영 서버 `.env` 전체 내용 |

Secret 값은 코드 또는 README에 직접 작성하지 않습니다.

GitHub Repository의 Actions Secret을 통해서만 관리합니다.

---

# 🩺 Deployment Health Check

CD Workflow는 배포 이후 애플리케이션이 실제로 정상 실행되는지 확인합니다.

다음 주소를 검사합니다.

```text
http://127.0.0.1:8080/actuator/health
```

최대 30번 검사하며 각 검사 사이에는 3초의 간격을 둡니다.

정상 응답이 확인되지 않으면 다음 명령을 통해 최근 애플리케이션 로그를 출력합니다.

```bash
docker compose logs app --tail 50
```

이후 Workflow를 실패 처리합니다.

---

# 📜 Container Logging

운영 Docker Compose에는 **Dozzle**이 포함되어 있습니다.

Dozzle은 Docker Container의 로그를 웹 UI에서 확인하기 위한 도구입니다.

현재 서버 내부 포트는 다음과 같이 설정되어 있습니다.

```text
127.0.0.1:9999
```

외부 공개 여부 및 접근 방식은 서버 인프라 설정에 따라 관리합니다.

---

# 🤖 Code Review

프로젝트에는 CodeRabbit 설정 파일이 포함되어 있습니다.

```text
.coderabbit.yaml
```

CodeRabbit Review 언어는 한국어로 설정되어 있습니다.

자동 리뷰는 보조 수단으로 사용하며, 최종 Merge 전 팀원의 코드 리뷰를 함께 진행합니다.

---

# 📋 Current Backend Configuration

현재 `develop` 브랜치 기준 주요 Backend 설정은 다음과 같습니다.

| Feature | Status |
| --- | --- |
| Spring Boot 프로젝트 생성 | ✅ |
| Java 21 | ✅ |
| Spring MVC | ✅ |
| Spring Data JPA | ✅ |
| Validation | ✅ |
| Lombok | ✅ |
| PostgreSQL 전환 | ✅ |
| pgvector PostgreSQL Image | ✅ |
| Spring Boot Actuator | ✅ |
| Swagger / OpenAPI | ✅ |
| Dockerfile | ✅ |
| Local Docker Compose | ✅ |
| Production Docker Compose | ✅ |
| Redis Container | ✅ |
| Redis Spring 연동 | ⏳ |
| GitHub Actions CI | ✅ |
| GitHub Actions CD | ✅ |
| Docker Hub 배포 | ✅ |
| Server 자동 배포 | ✅ |
| Deployment Health Check | ✅ |
| Dozzle | ✅ |
| Flyway / Liquibase | ⏳ |

---

# 🔗 Repository

| Repository | Link |
| --- | --- |
| Frontend | [dangdang-fe](https://github.com/dangdang-2026/dangdang-fe) |
| Backend | [dangdang-be](https://github.com/dangdang-2026/dangdang-be) |

---

# 🌐 Server

현재 Backend 운영 서버 주소:

```text
https://dang-dang.site/
```

Swagger:

```text
https://dang-dang.site/swagger-ui.html
```

Health Check:

```text
https://dang-dang.site/actuator/health
```
