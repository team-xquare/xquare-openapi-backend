# 배포

스테이징과 운영은 같은 방식으로 배포한다. 서버 안의 self-hosted runner가 커밋을 받아 그 자리에서 이미지를 빌드하고 `docker compose`로 띄운다.

| | 스테이징 | 운영 |
|---|---|---|
| 서버 | LXC `XQUARE-openapi-server-stag` (10.0.1.20) | LXC `XQUARE-openapi-server` (10.0.1.19) |
| 워크플로 | `.github/workflows/deploy-stag.yml` | `.github/workflows/deploy-prod.yml` |
| 트리거 | `develop` push, 수동 실행 | `main` push, 수동 실행 |
| runner (이름 / 라벨) | `xquare-openapi-stag` / `stag` | `xquare-openapi-prod` / `prod` |

두 서버 모두 배포 폴더는 `/srv/xquare-openapi-backend`이고, 앱은 호스트 8080 포트로 열린다. 외부 도메인에서 이 포트로 보내는 프록시는 이 레포 밖에서 관리한다.

## 배포 흐름

1. `ubuntu-latest`에서 `./gradlew test`.
2. 서버의 runner가 배포 폴더에서 `git fetch` 후 `$GITHUB_SHA`로 checkout.
3. `docker compose build app && docker compose up -d app`. MySQL·Redis 컨테이너는 건드리지 않는다.
4. `localhost:8080/`이 401을 돌려줄 때까지 기다린다(인증이 필요한 경로라 401이면 앱이 요청을 받고 있는 것이다).
5. 남은 이미지 정리(`docker image prune -f`).

## 서버를 새로 준비할 때

워크플로는 이미 준비된 서버에 배포만 한다. 새 서버는 root로 아래를 한 번 해 둔다.

1. Docker 공식 apt 저장소로 `docker-ce`, `docker-ce-cli`, `containerd.io`, `docker-buildx-plugin`, `docker-compose-plugin` 설치.
2. `runner` 사용자를 만들고 `docker` 그룹에 넣는다.
3. `runner`로 `/srv/xquare-openapi-backend`에 레포를 clone한다(공개 레포라 인증 불필요).
4. 같은 폴더에 `.env`를 만든다. 소유자 `runner`, 권한 600. 키는 아래 "환경변수" 참고.
5. `docker compose up -d --wait mysql redis` 후 스키마를 만든다(아래 "스키마").
6. `docker compose build app && docker compose up -d app`.
7. GitHub Actions runner를 `runner` 사용자로 `~/actions-runner`에 설치하고 `./config.sh --labels <stag|prod> --name <이름>`으로 등록한 뒤, root로 `./svc.sh install runner && ./svc.sh start`해서 systemd 서비스로 돌린다. 등록 토큰은 `gh api -X POST repos/team-xquare/xquare-openapi-backend/actions/runners/registration-token --jq .token`(레포 admin 권한 필요).

compose의 모든 서비스가 `restart: unless-stopped`라 서버가 재부팅돼도 다시 뜬다.

## 환경변수 (`.env`)

`compose.yml`이 `app`에 `.env`를 통째로 넘기고, `DB_URL`·`REDIS_HOST`·`REDIS_PORT`는 compose가 직접 넣는다.

- `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`: MySQL 컨테이너가 처음 뜰 때 이 값으로 계정을 만든다. 볼륨이 생긴 뒤에 바꾸면 반영되지 않는다.
- `JWT_SECRET`: 32바이트 이상. 서버마다 따로 만든다(`openssl rand -hex 32`). 그래서 스테이징 토큰은 운영에서 통하지 않는다.
- `JPA_DDL_AUTO=validate`
- `CORS_ALLOWED_ORIGINS`: 프론트엔드 origin. 비우면 브라우저의 다른 origin 요청이 모두 막힌다.
- 선택: `RESEND_API_KEY`, `MAIL_FROM`(없으면 OTP 메일 발송 실패), `NEIS_API_KEY`(없으면 급식·학사일정 동기화를 건너뜀). 그 밖의 키는 `src/main/resources/application.yaml` 참고.

`.env`는 커밋하지 않고 서버에만 둔다. 값을 바꾼 뒤에는 `docker compose up -d app`으로 컨테이너를 다시 만들어야 반영된다.

## 스키마

`ddl-auto: validate`라 앱이 뜨기 전에 테이블이 있어야 한다. 마이그레이션 도구는 없다.

- 운영은 스테이징 DB에서 `mysqldump --no-data`로 뽑은 스키마를 그대로 넣어 만들었다. 새 서버도 같은 방법이 가장 확실하다.
- 엔티티를 바꾸는 PR은 운영·스테이징 DB에 직접 DDL을 실행해야 한다. 배포만 하면 validate에서 막혀 앱이 뜨지 않는다. `docs/schema/*.sql`에 테이블별 DDL이 일부 있다.
