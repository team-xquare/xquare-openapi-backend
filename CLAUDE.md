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
  - `avoidCallsTo = kotlin.jvm.internal`: Kotlin 컴파일러가 넣는 null 체크를 지우는 뮤턴트는 동작이 바뀌지 않으므로 제외한다.
  - `config` 패키지는 대상에서 뺐다. Spring 컨텍스트가 뜰 때 한 번만 실행되고 PIT은 캐시된 컨텍스트를 재사용해서, 바뀐 코드가 실행되지 않아 가짜 생존자만 생긴다. 생성자에서 한 번만 도는 코드도 같은 이유로 Spring 테스트로는 못 잡으니 단위 테스트(`JwtProviderTest`처럼)로 검증한다.
- `RedisAuthRepositoriesTest`는 Testcontainers로 Redis를 띄운다. Docker가 꺼져 있으면 실패하지 않고 건너뛴다.

## 로컬 실행에 필요한 환경변수

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`(32바이트 이상이어야 하고, 짧으면 앱이 뜨지 않음).
`JPA_DDL_AUTO`는 기본값이 `validate`다. 마이그레이션 도구가 없으므로 빈 DB에서는 테이블이 먼저 있어야 한다.

`src/test/resources/application.yaml`은 main 설정을 덮는 게 아니라 통째로 대신한다(H2, 테스트용 JWT secret). 새 설정 키를 추가하면 두 파일 모두에 넣는다.

## 명세

요구사항은 노션 "xquare open API" 페이지에 있다. 그 안에 "기능 명세서"와 "API 명세서" 두 데이터베이스가 있다. 둘이 다를 때는 **API 명세서**를 따른다. 예를 들어 회원가입은 `/api/v1/auth/register`가 아니라 `/auth/signup`이고, 기본 권한은 `MEMBER`가 아니라 `USER`다.

## 구조와 규칙

- 패키지 구성: 기능별 `domain/<기능>/{presentation, usecase, repository}`, 공통 기능은 `global/{error, jwt}`, Spring 설정은 `config/`.
  - 비즈니스 로직은 API 하나당 UseCase 클래스 하나(`SignupUseCase.execute`)로 나눈다. OpenAPI 특성상 기능이 계속 늘어나서 서비스 하나에 몰리지 않게 하려는 것이다(PR #3 리뷰).
- 에러 처리: `XquareException(ErrorCode.X)`를 던지면 `GlobalExceptionHandler`가 `{ "code", "message" }` 응답으로 바꾼다. 새 에러는 명세의 에러 코드 이름 그대로 `ErrorCode`에 추가한다. 입력 검증 실패와 JSON 파싱 실패는 모두 `400 INVALID_INPUT`이다.
- 보안: `SecurityConfig`는 `POST /auth/signup` 외의 모든 요청에 인증을 요구한다. 공개 엔드포인트를 새로 만들면 여기에 `permitAll`을 추가해야 하고, 빠뜨리면 403이 난다. `PasswordEncoder`(BCrypt) 빈도 여기서 만든다.
- Redis 저장소는 인터페이스(`EmailVerifiedTokenRepository`, `RefreshTokenRepository`) 뒤에 둔다. `SignupTest`는 `@TestConfiguration`의 `@Primary` 가짜 구현으로 바꿔 끼워서 Redis 없이 돈다.
- Redis 키 형식은 다른 기능과 맞춰야 하는 계약이다:
  - `email_verified_token:{token}` → 이메일. 이메일 OTP 인증 API(아직 없음)가 TTL 600초로 저장하고, 회원가입이 읽은 뒤 지운다.
  - `refresh_token:{userId}` → 리프레시 토큰. TTL은 `jwt.refresh-expiration`.
- JWT: `sub`=userId, `type`=`access`/`refresh`, 액세스 토큰에만 `role`을 넣는다.

## 버전별 주의점

- Spring Boot 4이고 Jackson 3를 쓴다(`tools.jackson.*` 패키지). 테스트용 자동 설정 패키지도 옮겨졌다. 예: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest`.
- Kotlin DTO의 Bean Validation 어노테이션은 `@field:`를 붙여야 검증이 적용된다.
- JPA 엔티티는 `build.gradle.kts`의 `allOpen` 설정으로 자동 open 처리된다. Hibernate 지연 로딩 프록시 때문에 필요하다.

## 협업

- 커밋과 PR 제목은 `feat:`/`fix:`/`chore:` 형식의 영어로 쓰고, PR 본문은 한국어로 쓴다.
- 앞 PR이 아직 머지되지 않았으면 그 브랜치 위에 쌓아서 PR을 올리고, 본문에 "#N 위에 쌓인 PR입니다"라고 적는다.
- PR은 직접 머지하지 않는다. 내가 올린 PR이 아닌 다른 PR이 있다면 서브 에이전트를 이용하여 리뷰 진행한다.