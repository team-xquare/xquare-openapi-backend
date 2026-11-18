# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 사용자 지침

- 테스트 기법에 관한 내용입니다. 
  - 기능당 테스트 필수.
  - 테스트에 대한 뮤테이션 테스트도 진행 필수.
  - 뮤테이션 생존시 그것에 대한 보고 후 해결 작업 진행
  - 기능 테스트는 루프를 이용하여 최대 5회 최소 3회 시행해주세요.
  - 뮤테이션 테스트(pitest)는 전체 대상으로 기능당 1회만 시행해주세요. 생존 뮤턴트는 보고하고, 아래 "명령어"의 등가 뮤턴트 목록에 해당하면(`javap`로 확인) 근거를 적고 끝냅니다. 아니면 테스트를 보강한 뒤 보강한 클래스만 대상으로 pitest를 다시 돌려(`-PpitTargets=...`) KILLED를 확인합니다.
- /docs을 기반으로 작업해주세요.   
API 스펙이 있습니다.



## 소통 

- 대화와 코드 주석은 한국어, 커밋 메시지는 영어로 쓴다. 
-  결론을 먼저 말하고, 이유와 상세 설명은 그 뒤에 쓴다. 
- 설계 판단(패턴 선택, 구조, 트레이드오프)이 들어간 작업은 왜 그렇게 했는지, 검토한 대안과 비교해 설명한다. 단순 수정은 짧게 보고한다. 
- 모르는 것은 "모른다", 확인하지 못한 것은 "확인 못 함"이라고 쓴다. 추측을 사실처럼 쓰지 않는다. 
- 라이브러리 API, 설정 키, 버전별 동작은 추측하지 않고 공식 문서나 소스 코드(의존성 소스 포함)로 확인한다.
- 작업 내용 마지막 정리 할때 두괄식 사용해주세요.



## 명령어

- 전체 테스트: `./gradlew test`
- 테스트 하나: `./gradlew test --tests 'app.xquare.xquareopenapi.domain.auth.SignupTest'`
  - 메서드 이름이 한글 백틱 이름이라 메서드 단위는 와일드카드로 고른다: `--tests '*SignupTest.가입하면*'`
- Gradle이 테스트 결과를 캐시한다. 코드 변경 없이 환경만 바뀌었으면(예: Docker를 켬) `--rerun`을 붙여야 다시 실행된다.
- 뮤테이션 테스트: `./gradlew pitest` → 리포트는 `build/reports/pitest/index.html`
  - 일부 클래스만: `./gradlew pitest -PpitTargets='app.xquare.xquareopenapi.domain.meal.*'` (쉼표로 여러 개). 기본값은 `domain.*`, `global.*` 전체.
  - 동시 실행 수: `-PpitThreads=2` (기본 4). 미니언 JVM 하나가 수백 MB를 쓰므로 메모리가 작은 머신에서는 줄인다.
  - `avoidCallsTo = kotlin.jvm.internal, org.slf4j`: Kotlin 컴파일러가 넣는 null 체크와 로그 호출을 지우는 뮤턴트는 동작이 바뀌지 않으므로 제외한다. 이 값을 지정하면 PIT의 기본 제외 목록(로깅 라이브러리)이 덮어써지므로, 로깅 라이브러리도 직접 넣어야 한다.
  - `mutators = ALL, -EXPERIMENTAL_MEMBER_VARIABLE`: 가장 넓은 뮤테이터 그룹을 쓰되 생성자 필드 대입 제거만 뺀다. Spring 빈은 캐시된 컨텍스트에서 한 번만 생성되므로 바뀐 생성자가 실행되지 않아 가짜 생존자만 생긴다.
  - Kotlin 컴파일러가 만든 바이트코드에서 나오는 등가 뮤턴트(테스트로 죽일 수 없음):
    - 인라인 함수(`apply`, `firstOrNull` 등)의 마커 변수(`$i$f$...`, `$i$a$...`)를 0→1로 바꾸는 뮤턴트
    - 기본 인자 비트마스크를 +1 하는 뮤턴트. 기본값이 없는 첫 번째 인자의 비트가 켜질 뿐이다.
    - null이 될 수 없는 프로퍼티에 `a?.b ?: c`를 쓸 때 생기는 두 번째 null 검사를 바꾸는 뮤턴트
    - vararg 배열 크기를 +1 하는 뮤턴트(`redisTemplate.execute(script, keys, *args)`, `RestClient.uri("/emails")`). 끝에 null 칸이 하나 생길 뿐이고, Lua 스크립트는 정해진 `KEYS`/`ARGV` 인덱스만 읽으며 URI에는 치환 변수가 없다.
    - Lua가 항상 정수를 돌려주는데 `execute(...) == 1L`의 nullable `Long` null 검사를 바꾸는 뮤턴트(`RedisRefreshTokenRepository.replace`)
    - 생존자로 나오면 `javap -c -l -p`로 해당 줄의 바이트코드를 보고 위 경우인지 확인한다. 핵심 로직에서는 `if (a != null) a.b else c`처럼 써서 피했다(`LoginUseCase`, `SignupUseCase.save`).
  - `timeoutConstInMillis = 30000`: Testcontainers 테스트가 뮤턴트를 처음 돌릴 때 컨테이너를 띄우느라 기본 제한 시간을 넘겨 TIMED_OUT이 난다. 30초로 늘려도 가끔 남는다. TIMED_OUT·RUN_ERROR가 나오면 해당 클래스만 대상으로 다시 돌려(`-PpitTargets=...`) KILLED로 나오면 환경 문제로 본다.
  - `config` 패키지는 대상에서 뺐다. Spring 컨텍스트가 뜰 때 한 번만 실행되고 PIT은 캐시된 컨텍스트를 재사용해서, 바뀐 코드가 실행되지 않아 가짜 생존자만 생긴다. 생성자에서 한 번만 도는 코드도 같은 이유로 Spring 테스트로는 못 잡으니 단위 테스트(`JwtProviderTest`처럼)로 검증한다.
- `RedisAuthRepositoriesTest`와 `ReissueConcurrencyTest`는 Redis를, `UserUniqueConstraintMySqlTest`, `StudentRepositoryMySqlTest`, `ApiKeyRepositoryMySqlTest`는 MySQL을 Testcontainers로 띄운다. Docker가 꺼져 있으면 실패하지 않고 건너뛴다.
- `ReissueConcurrencyTest`는 실제 HTTP 서버에 같은 리프레시 토큰으로 8개 요청을 동시에 보내는 검증을 3회 반복한다. 성공 1개·401 응답 7개, Redis에 저장된 토큰과 성공 응답의 일치, 새 토큰의 재발급과 이전 토큰 재사용 거부를 확인한다. 테스트 종료 시 컨텍스트를 닫으므로 H2 DB 이름도 다른 테스트와 분리한다.

## 로컬 실행에 필요한 환경변수

`RESEND_API_KEY`(빈 값이면 기동은 가능하나 메일 발송 실패), `MAIL_FROM`(예: `XQUARE <noreply@example.com>`), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`(32바이트 이상이어야 하고, 짧으면 앱이 뜨지 않음).
`CORS_ALLOWED_ORIGINS`는 쉼표로 구분한 허용 origin 목록이다(예: `http://localhost:3000,https://example.com`). 비워 두면 브라우저의 다른 origin 요청이 모두 막힌다.
`JPA_DDL_AUTO`는 기본값이 `validate`다. 마이그레이션 도구가 없으므로 빈 DB에서는 테이블이 먼저 있어야 한다.

`src/test/resources/application.yaml`은 main 설정을 덮는 게 아니라 통째로 대신한다(H2, 테스트용 JWT secret). 새 설정 키를 추가하면 두 파일 모두에 넣는다.

## 명세

요구사항은 노션 "xquare open API" 페이지에 있다. 그 안에 "기능 명세서"와 "API 명세서" 두 데이터베이스가 있다. 둘이 다를 때는 **API 명세서**를 따른다. 예를 들어 회원가입은 `/api/v1/auth/register`가 아니라 `/auth/signup`이고, 기본 권한은 `MEMBER`가 아니라 `USER`다.

## 구조와 규칙

- 패키지 구성: 기능별 `domain/<기능>/{presentation, usecase, repository}`, 공통 기능은 `global/{error, jwt}`, Spring 설정은 `config/`.
  - 비즈니스 로직은 API 하나당 UseCase 클래스 하나(`SignupUseCase.execute`)로 나눈다. OpenAPI 특성상 기능이 계속 늘어나서 서비스 하나에 몰리지 않게 하려는 것이다(PR #3 리뷰).
- 에러 처리: `XquareException(ErrorCode.X)`를 던지면 `GlobalExceptionHandler`가 `{ "code", "message" }` 응답으로 바꾼다. 새 에러는 명세의 에러 코드 이름 그대로 `ErrorCode`에 추가한다. 일반 입력 검증 실패와 JSON 파싱 실패는 `400 INVALID_INPUT`이다. OTP 발송·검증의 이메일 형식 오류(빈 문자열 포함)는 `400 INVALID_EMAIL_FORMAT`이다. Redis·DB 장애(`DataAccessException`)는 `500 INTERNAL_SERVER_ERROR`로 응답한다. 어느 제약인지 구분하지 못한 무결성 위반도 여기에 걸려 같은 500이 된다.
- 보안: `SecurityConfig`는 `POST /auth/signup`, `/auth/login`, `/auth/reissue`, `/auth/email/otp`, `/auth/email/otp/verify`와 Swagger 경로(`GET /swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) 외의 모든 요청에 인증을 요구한다. 공개 엔드포인트를 새로 만들면 여기에 `permitAll`을 추가해야 하고, 빠뜨리면 토큰 없는 요청이 `401 INVALID_TOKEN`이 된다. `ERROR` 디스패치(`/error` 포워드)도 허용해 둬야 한다. 막으면 415·500 같은 원래 응답이 모두 401로 바뀐다. MockMvc는 이 포워드를 흉내 내지 않으므로 `ErrorDispatchTest`처럼 실제 서버로 확인한다. `PasswordEncoder`(BCrypt) 빈도 여기서 만든다.
- 인증: `JwtAuthenticationFilter`가 `Authorization: Bearer {accessToken}`을 검증해 principal=userId, 권한=`ROLE_{Role}`로 인증한다. 토큰이 없거나 유효하지 않으면 인증하지 않고 넘기고, 인증이 필요한 경로면 `SecurityErrorResponder`가 `401 INVALID_TOKEN`, 권한이 부족하면 `403 FORBIDDEN`으로 응답한다. 이 둘은 Security 필터에서 막히므로 `GlobalExceptionHandler`를 거치지 않는다. 필터는 빈으로 등록하지 않는다(Spring Boot가 서블릿 필터로도 등록해 두 번 실행된다).
- API 키 인증: 조회 API(`GET`·`HEAD` `/students`, `/students/*`, `/teachers`, `/teachers/*`)는 `SecurityConfig.apiKeySecurityFilterChain`(`@Order(1)`)이 먼저 가져가 `X-API-Key`로만 인증한다. 이 체인에는 `ApiKeyAuthenticationFilter`만 있고 JWT 필터가 없어서, 토큰만 보내면 `401 INVALID_API_KEY`다. 반대로 JWT 체인에는 키 필터가 없어서 키로는 등록·수정·삭제를 못 한다. 필터는 키가 신청한 분야마다 `ApiKeyField.authority`(`API_KEY_FIELD_STUDENT` 등) 권한을 주고, 체인이 경로별로 그 권한을 검사해 없으면 `403 API_KEY_FIELD_FORBIDDEN`이다. 조회 API를 새로 만들면 `SecurityConfig.API_KEY_READ_PATHS`에 `경로 to 분야`를 한 줄 넣는다. 빠뜨리면 JWT 체인으로 가서 토큰으로 조회된다. `ApiKeyReadEndpointCoverageTest`가 GET 매핑을 전부 훑어서, 이 목록에도 예외 목록(`/error`, Swagger 등)에도 없는 경로가 있으면 실패한다. 키 없이 여는 GET(예: 급식)은 그 테스트의 예외 목록에 넣는다. Swagger 문서도 이 목록을 읽어 해당 GET에만 `X-API-Key` 인증(`apiKeyAuth`)을 붙인다. Spring MVC는 GET 매핑에 HEAD도 자동으로 응답하므로 매처는 GET과 HEAD를 함께 만든다.
- CORS: `SecurityConfig`의 `cors { }`가 `corsConfigurationSource` 빈을 쓴다. 설정 값은 `global/cors/CorsProperties`에 있고, 허용 메서드는 GET/POST/PUT/PATCH/DELETE, 허용 헤더는 `Authorization`/`Content-Type`/`X-API-Key`다. 토큰을 헤더로 주고받으므로 credentials(쿠키)는 허용하지 않는다. preflight는 인증 검사보다 먼저 처리돼서 토큰 없이 통과한다.
- 권한: `/students`, `/teachers` 등록·수정·삭제와 관리자 활용신청 목록·상세(`/api-key-applications`, `/api-key-applications/*`, 메서드 없이 걸어 HEAD도 막음)처럼 ADMIN 전용 API는 `SecurityConfig`에 `hasRole(ADMIN)`으로 메서드와 경로를 적는다. 나머지는 `authenticated`라서 USER와 ADMIN 모두 통과한다(조회 API는 위의 API 키 체인이 먼저 가져간다). ADMIN 계정을 만드는 API는 없어서 DB에서 `users.role`을 직접 바꿔야 한다.
- 수정 API(PATCH)는 등록과 같은 요청 DTO로 모든 필드를 받아 통째로 바꾼다(`StudentRequest`, `TeacherRequest`). 그래서 교직원의 nullable 필드(`grade`, `classNum`)를 null로 보내면 값을 지운다.
- 목록 조회는 `@ModelAttribute` 검색 DTO로 받는다. `page`(0 이상)·`size`(1~100)는 Bean Validation으로 검사하고, 응답은 `global/dto/PageResponse`. 쿼리 값의 타입이 틀리면 `GlobalExceptionHandler`가 `형식이 올바르지 않습니다.`로 바꿔 400을 준다.
  - 부분 일치 검색은 `like concat('%', :x, '%') escape '!'`로 쓰고, 값은 `escapeLike()`(`global/jpa`)를 거쳐 넘긴다. 그래야 입력의 `%`·`_`가 글자 그대로 찾아진다. MySQL은 문자열 안의 `\`를 이스케이프로 읽으므로 이스케이프 문자로 쓰지 않는다.
  - 쿼리에서 산술에 쓰는 검색 값(학생 `grade * 1000` 등)은 DTO에서 범위를 막는다. 막지 않으면 큰 값에서 넘쳐 500이 난다.
- 삭제는 조회 후 `delete(entity)`가 아니라 `@Modifying` JPQL delete로 지우고, 지운 행 수가 0이면 404를 던진다. 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 커밋하며 0행 삭제로 500이 난다(`DeleteStudentConcurrencyTest`).
- `students`의 unique 제약 이름 `uk_students_student_number`도 계약이다. 동시 등록·수정으로 중복 검사를 통과한 요청이 제약에 걸리면 이 이름으로 구분해 409로 바꾼다(`DataIntegrityViolationException.violates`).
- Redis 저장소는 인터페이스(`EmailVerifiedTokenRepository`, `RefreshTokenRepository`) 뒤에 둔다. 인증 API 테스트(`SignupTest`, `LoginTest`, `ReissueTest`)는 `FakeAuthRepositories`를 `@Import`해 `@Primary` 가짜 구현으로 바꿔 끼워서 Redis 없이 돈다. 저장소 인터페이스에 메서드를 추가하면 가짜 구현에도 넣는다.
- Redis 키 형식은 다른 기능과 맞춰야 하는 계약이다:
  - `email_otp:{email}` → 6자리 OTP. TTL 300초이며 재발송은 덮어쓴다. 검증은 Lua 스크립트로 비교·삭제를 원자적으로 수행한다. 발송 실패 시 저장값이 해당 발송의 OTP와 같을 때만 Lua로 OTP와 횟수 키를 삭제하고 삭제 실패는 로그만 남긴다.
  - `email_otp_attempts:{email}` → OTP 불일치 횟수. 검증 Lua에서 증가시키며 TTL은 OTP의 남은 PTTL과 맞춘다. 5회 불일치하면 OTP와 횟수 키를 폐기한다(5번째는 OTP_MISMATCH, 이후는 OTP_NOT_FOUND). 성공·재발송 시 횟수 키를 삭제하며 재발송 저장과 횟수 초기화도 원자적으로 처리한다.
  - `email_verified_token:{token}` → 이메일. 이메일 OTP 인증 API가 TTL 600초로 저장하고, 회원가입·API 키 활용신청·재발급이 읽은 뒤 지운다.
  - `refresh_token:{userId}` → 리프레시 토큰. TTL은 `jwt.refresh-expiration`. 사용자당 하나라 다시 로그인하면 이전 토큰은 폐기된다(사용자가 정함). 재발급은 저장값이 요청 토큰과 같을 때만 Lua 스크립트로 한 번에 바꾼다(`replace`). GET과 SET을 따로 하면 동시 재발급이나 그 사이의 재로그인 때 폐기된 토큰이 새 토큰을 덮어쓴다.
- API 키: `api_keys`에 키 해시를 사용자당 한 행으로 두고, 분야별 활용신청 기록(분야·구분 FIRST/ADD_FIELD·서비스 이름·사용 목적·신청 시각)은 `api_key_applications`에 키 id로 묶어 둔다. 첫 신청만 키를 발급하고 이후 신청은 분야만 더한다. 원문은 `ApiKeySecret.generate()`로 만들어 발급·재발급 응답에서만 내려주고 DB에는 `ApiKeySecret.hash()`(SHA-256 16진수)만 저장한다. 재발급은 해시만 바꿔서 기존 키를 바로 폐기하고 분야는 그대로 둔다. 삭제(`DELETE /api-keys/me`)는 키 행을 먼저 지운 뒤 신청 기록을 지운다. 내 키 조회(`GET /api-keys/me`)는 액세스 토큰으로 여는 GET이라 `ApiKeyReadEndpointCoverageTest`의 예외 목록에 있다. 발급·신청 시각은 `nowInSeoul()`(한국 시간)로 남긴다. 신청·재발급은 `ApiKeyEmailVerification`이 `email_verified_token`의 이메일과 로그인한 사용자의 이메일을 비교하고, 커밋 뒤 토큰을 지운다. 분야 추가는 키 행을 `findByIdForUpdate`로 잠근다(없는 행은 gap lock 교착 때문에 잠그지 않는다). unique 제약 이름 `uk_api_keys_user_id`, `uk_api_key_applications_api_key_id_field`도 409로 바꾸는 데 쓰는 계약이다. 테스트는 `ApiKeyFixtures.saveApiKey`로 분야를 신청한 키를 바로 만든다.
- `users`의 unique 제약 이름(`uk_users_username`, `uk_users_email`)도 계약이다. 동시 가입으로 중복 검사를 통과한 요청이 제약에 걸리면, `UserUniqueConstraint`가 이 이름으로 어떤 값이 겹쳤는지 구분해 409로 바꾼다. 운영 DB의 제약 이름이 다르면 구분하지 못하고 500이 난다.
- JWT: `sub`=userId, `type`=`access`/`refresh`, 액세스 토큰에만 `role`을 넣는다. 모든 토큰에 `jti`(UUID)를 넣는다. 빼면 같은 초에 발급한 토큰이 똑같아져, 재발급 직후에도 폐기한 토큰이 통한다. `generateRefreshToken`은 토큰과 TTL을 함께 돌려주며, Redis에 저장할 때 이 TTL을 그대로 쓴다. `parseRefreshToken`은 서명 오류·만료·액세스 토큰이면 예외 대신 null을 돌려준다.

- 메일: `OtpMailSender` 뒤의 `ResendOtpMailSender`가 Thymeleaf `email/otp` 템플릿을 렌더링해 Resend HTTP API로 보낸다. `resend.base-url`은 기본 `https://api.resend.com`이며 테스트에서는 로컬 HTTP 서버로 바꾼다. 연결 제한은 `resend.connect-timeout`(기본 5초), 읽기 제한은 `resend.read-timeout`(기본 10초)이며 초과 시 EMAIL_SEND_FAILED로 처리한다. 응답 읽기 타임아웃처럼 발송 여부를 알 수 없으면 OTP는 지우지 않는다. SMTP 설정은 사용하지 않는다.

## 버전별 주의점

- Spring Boot 4이고 Jackson 3를 쓴다(`tools.jackson.*` 패키지). 테스트용 자동 설정 패키지도 옮겨졌다. 예: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest`.
- Kotlin DTO의 Bean Validation 어노테이션은 `@field:`를 붙여야 검증이 적용된다.
- JPA 엔티티는 `build.gradle.kts`의 `allOpen` 설정으로 자동 open 처리된다. Hibernate 지연 로딩 프록시 때문에 필요하다.

## 협업

- 커밋과 PR 제목은 `feat:`/`fix:`/`chore:` 형식의 영어로 쓰고, PR 본문은 한국어로 쓴다.
- 앞 PR이 아직 머지되지 않았으면 그 브랜치 위에 쌓아서 PR을 올리고, 본문에 "#N 위에 쌓인 PR입니다"라고 적는다.
- PR은 직접 머지하지 않는다. 내가 올린 PR이 아닌 다른 PR이 있다면 서브 에이전트를 이용하여 리뷰 진행한다.
