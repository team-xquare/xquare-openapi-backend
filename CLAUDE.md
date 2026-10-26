# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 사용자 지침

- 테스트 기법에 관한 내용입니다. 
  - 기능당 테스트 필수.
  - 테스트에 대한 뮤테이션 테스트도 진행 필수.
  - 뮤테이션 생존시 그것에 대한 보고 후 해결 작업 진행
  - 루프를 이용하여 최대 5회 최소 3회 시행해주세요.
- /docs을 기반으로 작업해주세요.   
API 스펙이 있습니다.
- 작업 내용 마지막 정리 할때 두괄식 사용해주세요.



## 명령어

- 전체 테스트: `./gradlew test`
- 테스트 하나: `./gradlew test --tests 'app.xquare.xquareopenapi.domain.auth.SignupTest'`
  - 메서드 이름이 한글 백틱 이름이라 메서드 단위는 와일드카드로 고른다: `--tests '*SignupTest.가입하면*'`
- Gradle이 테스트 결과를 캐시한다. 코드 변경 없이 환경만 바뀌었으면(예: Docker를 켬) `--rerun`을 붙여야 다시 실행된다.
- 뮤테이션 테스트: `./gradlew pitest` → 리포트는 `build/reports/pitest/index.html`
  - `avoidCallsTo = kotlin.jvm.internal, org.slf4j`: Kotlin 컴파일러가 넣는 null 체크와 로그 호출을 지우는 뮤턴트는 동작이 바뀌지 않으므로 제외한다. 이 값을 지정하면 PIT의 기본 제외 목록(로깅 라이브러리)이 덮어써지므로, 로깅 라이브러리도 직접 넣어야 한다.
  - `mutators = ALL, -EXPERIMENTAL_MEMBER_VARIABLE`: 가장 넓은 뮤테이터 그룹을 쓰되 생성자 필드 대입 제거만 뺀다. Spring 빈은 캐시된 컨텍스트에서 한 번만 생성되므로 바뀐 생성자가 실행되지 않아 가짜 생존자만 생긴다.
  - Kotlin 컴파일러가 만든 바이트코드에서 나오는 등가 뮤턴트(테스트로 죽일 수 없음):
    - 인라인 함수(`apply`, `firstOrNull` 등)의 마커 변수(`$i$f$...`, `$i$a$...`)를 0→1로 바꾸는 뮤턴트
    - 기본 인자 비트마스크를 +1 하는 뮤턴트. 기본값이 없는 첫 번째 인자의 비트가 켜질 뿐이다.
    - null이 될 수 없는 프로퍼티에 `a?.b ?: c`를 쓸 때 생기는 두 번째 null 검사를 바꾸는 뮤턴트
    - 생존자로 나오면 `javap -c -l -p`로 해당 줄의 바이트코드를 보고 위 경우인지 확인한다. 핵심 로직에서는 `if (a != null) a.b else c`처럼 써서 피했다(`LoginUseCase`, `SignupUseCase.save`).
  - `timeoutConstInMillis = 30000`: Testcontainers 테스트가 뮤턴트를 처음 돌릴 때 컨테이너를 띄우느라 기본 제한 시간을 넘겨 TIMED_OUT이 난다. 30초로 늘려도 가끔 남는데, 같은 뮤턴트가 다른 회차에서 KILLED로 나오면 환경 문제로 본다.
  - `config` 패키지는 대상에서 뺐다. Spring 컨텍스트가 뜰 때 한 번만 실행되고 PIT은 캐시된 컨텍스트를 재사용해서, 바뀐 코드가 실행되지 않아 가짜 생존자만 생긴다. 생성자에서 한 번만 도는 코드도 같은 이유로 Spring 테스트로는 못 잡으니 단위 테스트(`JwtProviderTest`처럼)로 검증한다.
- `RedisAuthRepositoriesTest`와 `ReissueConcurrencyTest`는 Redis를, `UserUniqueConstraintMySqlTest`는 MySQL을 Testcontainers로 띄운다. Docker가 꺼져 있으면 실패하지 않고 건너뛴다.
- `ReissueConcurrencyTest`는 실제 HTTP 서버에 같은 리프레시 토큰으로 8개 요청을 동시에 보내는 검증을 3회 반복한다. 성공 1개·401 응답 7개, Redis에 저장된 토큰과 성공 응답의 일치, 새 토큰의 재발급과 이전 토큰 재사용 거부를 확인한다. 테스트 종료 시 컨텍스트를 닫으므로 H2 DB 이름도 다른 테스트와 분리한다.

## 로컬 실행에 필요한 환경변수

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`(32바이트 이상이어야 하고, 짧으면 앱이 뜨지 않음).
`CORS_ALLOWED_ORIGINS`는 쉼표로 구분한 허용 origin 목록이다(예: `http://localhost:3000,https://example.com`). 비워 두면 브라우저의 다른 origin 요청이 모두 막힌다.
`JPA_DDL_AUTO`는 기본값이 `validate`다. 마이그레이션 도구가 없으므로 빈 DB에서는 테이블이 먼저 있어야 한다.

`src/test/resources/application.yaml`은 main 설정을 덮는 게 아니라 통째로 대신한다(H2, 테스트용 JWT secret). 새 설정 키를 추가하면 두 파일 모두에 넣는다.

## 명세

요구사항은 노션 "xquare open API" 페이지에 있다. 그 안에 "기능 명세서"와 "API 명세서" 두 데이터베이스가 있다. 둘이 다를 때는 **API 명세서**를 따른다. 예를 들어 회원가입은 `/api/v1/auth/register`가 아니라 `/auth/signup`이고, 기본 권한은 `MEMBER`가 아니라 `USER`다.

## 구조와 규칙

- 패키지 구성: 기능별 `domain/<기능>/{presentation, usecase, repository}`, 공통 기능은 `global/{error, jwt}`, Spring 설정은 `config/`.
  - 비즈니스 로직은 API 하나당 UseCase 클래스 하나(`SignupUseCase.execute`)로 나눈다. OpenAPI 특성상 기능이 계속 늘어나서 서비스 하나에 몰리지 않게 하려는 것이다(PR #3 리뷰).
- 에러 처리: `XquareException(ErrorCode.X)`를 던지면 `GlobalExceptionHandler`가 `{ "code", "message" }` 응답으로 바꾼다. 새 에러는 명세의 에러 코드 이름 그대로 `ErrorCode`에 추가한다. 입력 검증 실패와 JSON 파싱 실패는 모두 `400 INVALID_INPUT`이다.
- 보안: `SecurityConfig`는 `POST /auth/signup`, `/auth/login`, `/auth/reissue` 외의 모든 요청에 인증을 요구한다. 공개 엔드포인트를 새로 만들면 여기에 `permitAll`을 추가해야 하고, 빠뜨리면 403이 난다. `ERROR` 디스패치(`/error` 포워드)도 허용해 둬야 한다. 막으면 415·500 같은 원래 응답이 모두 403으로 바뀐다. MockMvc는 이 포워드를 흉내 내지 않으므로 `ErrorDispatchTest`처럼 실제 서버로 확인한다. `PasswordEncoder`(BCrypt) 빈도 여기서 만든다.
- CORS: `SecurityConfig`의 `cors { }`가 `corsConfigurationSource` 빈을 쓴다. 설정 값은 `global/cors/CorsProperties`에 있고, 허용 메서드는 GET/POST/PUT/PATCH/DELETE, 허용 헤더는 `Authorization`/`Content-Type`이다. 토큰을 헤더로 주고받으므로 credentials(쿠키)는 허용하지 않는다. preflight는 인증 검사보다 먼저 처리돼서 토큰 없이 통과한다.
- Redis 저장소는 인터페이스(`EmailVerifiedTokenRepository`, `RefreshTokenRepository`) 뒤에 둔다. 인증 API 테스트(`SignupTest`, `LoginTest`, `ReissueTest`)는 `FakeAuthRepositories`를 `@Import`해 `@Primary` 가짜 구현으로 바꿔 끼워서 Redis 없이 돈다. 저장소 인터페이스에 메서드를 추가하면 가짜 구현에도 넣는다.
- Redis 키 형식은 다른 기능과 맞춰야 하는 계약이다:
  - `email_verified_token:{token}` → 이메일. 이메일 OTP 인증 API(아직 없음)가 TTL 600초로 저장하고, 회원가입이 읽은 뒤 지운다.
  - `refresh_token:{userId}` → 리프레시 토큰. TTL은 `jwt.refresh-expiration`. 사용자당 하나라 다시 로그인하면 이전 토큰은 폐기된다(사용자가 정함). 재발급은 저장값이 요청 토큰과 같을 때만 Lua 스크립트로 한 번에 바꾼다(`replace`). GET과 SET을 따로 하면 동시 재발급이나 그 사이의 재로그인 때 폐기된 토큰이 새 토큰을 덮어쓴다.
- `users`의 unique 제약 이름(`uk_users_username`, `uk_users_email`)도 계약이다. 동시 가입으로 중복 검사를 통과한 요청이 제약에 걸리면, `UserUniqueConstraint`가 이 이름으로 어떤 값이 겹쳤는지 구분해 409로 바꾼다. 운영 DB의 제약 이름이 다르면 구분하지 못하고 500이 난다.
- JWT: `sub`=userId, `type`=`access`/`refresh`, 액세스 토큰에만 `role`을 넣는다. 모든 토큰에 `jti`(UUID)를 넣는다. 빼면 같은 초에 발급한 토큰이 똑같아져, 재발급 직후에도 폐기한 토큰이 통한다. `generateRefreshToken`은 토큰과 TTL을 함께 돌려주며, Redis에 저장할 때 이 TTL을 그대로 쓴다. `parseRefreshToken`은 서명 오류·만료·액세스 토큰이면 예외 대신 null을 돌려준다.

## 버전별 주의점

- Spring Boot 4이고 Jackson 3를 쓴다(`tools.jackson.*` 패키지). 테스트용 자동 설정 패키지도 옮겨졌다. 예: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest`.
- Kotlin DTO의 Bean Validation 어노테이션은 `@field:`를 붙여야 검증이 적용된다.
- JPA 엔티티는 `build.gradle.kts`의 `allOpen` 설정으로 자동 open 처리된다. Hibernate 지연 로딩 프록시 때문에 필요하다.

## 협업

- 커밋과 PR 제목은 `feat:`/`fix:`/`chore:` 형식의 영어로 쓰고, PR 본문은 한국어로 쓴다.
- 앞 PR이 아직 머지되지 않았으면 그 브랜치 위에 쌓아서 PR을 올리고, 본문에 "#N 위에 쌓인 PR입니다"라고 적는다.
- PR은 직접 머지하지 않는다. 내가 올린 PR이 아닌 다른 PR이 있다면 서브 에이전트를 이용하여 리뷰 진행한다.