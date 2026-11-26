# API별 중요도 (초안)

**팀 검토용 초안입니다. 등급은 확정이 아니며 검토 후 바뀔 수 있습니다.**

- 결론: 핵심도 상은 인증 5개, 프로젝트·키 4개, 급식·시간표·일정 조회 5개로 모두 14개입니다. QA·모니터링은 이 14개와 API 키 인증 필터부터 봅니다.
- 기준 commit: `develop` `96c59f3`. 컨트롤러 매핑, `SecurityConfig`, 응답 DTO로 확인한 54개 API입니다. Swagger·프리플라이트·HEAD 자동 응답은 뺐습니다.
- 접근 단계와 필드는 [api-data-access-matrix.md](api-data-access-matrix.md), [api-key-project-spec.md](api-key-project-spec.md), [openapi.yaml](openapi.yaml)을 따릅니다.

## 기준

### 1. 서비스 핵심도

| 등급 | 뜻 |
|---|---|
| 상 | 없으면 플랫폼이 돌지 않거나, 외부 개발자가 주로 쓰러 오는 API |
| 중 | 운영에 필요한 관리자 흐름, 자주 쓰는 조회 |
| 하 | 기준 데이터(학생·교직원·급식 등)의 관리자 등록·수정·삭제, 드물게 쓰는 API |

API 키 인증 자체(`ApiKeyAuthenticationFilter`, `X-API-Key` 검사·만료·분야 검사)는 엔드포인트가 아니라 표에 없지만 **상**으로 봅니다. 이것이 막히면 키로 여는 조회 API가 모두 막힙니다.

### 2. 개인정보 민감도

요청·성공 응답에 들어가는 필드로 정합니다.

| 등급 | 해당 필드 |
|---|---|
| 높음 | 학생·사용자 학번, 실명, 생년월일, 이메일 |
| 보통 | 교직원 실명과 담당 정보, 계정 자격증명(비밀번호·토큰·API 키 원문). 자격증명은 개인정보는 아니지만 유출되면 계정·키를 빼앗깁니다. |
| 낮음 | 내부 id, 프로젝트 이름·목적, 반려 사유, 공지 작성자 이름 |
| 없음 | 급식·시간표·일정 같은 학교 공개 정보, 운영 메타데이터, 응답 필드가 `message`뿐인 API |

### 접근 표기

- 없음: 키·토큰 없이 호출(공개 `PUBLIC` 또는 인증 전 API)
- 키(교내)/키(제한): 해당 분야가 승인된 `X-API-Key`. 교내 `SCHOOL`은 자동 승인, 제한 `RESTRICTED`는 관리자 승인
- JWT: 로그인 사용자(USER/ADMIN)
- ADMIN: ADMIN JWT

## 표

### 인증·내 정보

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `POST /auth/email/otp` | 상 | 높음 | 없음 | 가입·비밀번호 재설정·프로젝트 신청이 모두 이메일 인증에서 시작함. 요청에 이메일 |
| `POST /auth/email/otp/verify` | 상 | 높음 | 없음 | `emailVerifiedToken`을 발급하는 유일한 경로. 요청에 이메일 |
| `POST /auth/signup` | 상 | 높음 | 없음 | 계정이 없으면 키 신청을 못 함. 학번·실명·이메일 저장 |
| `POST /auth/login` | 상 | 보통 | 없음 | 모든 JWT API의 입구. 비밀번호·토큰 |
| `POST /auth/reissue` | 상 | 보통 | 없음 | 실패하면 로그인 세션이 끊김. 리프레시 토큰 |
| `POST /auth/password/reset` | 중 | 보통 | 없음 | 계정 복구용, 사용 빈도 낮음. 새 비밀번호 |
| `GET /users/me` | 중 | 높음 | JWT | 프론트 화면에서 자주 호출. 학번·실명·이메일 응답 |

### 프로젝트·API 키

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `POST /projects` | 상 | 보통 | JWT | 키를 받는 첫 단계. 교내 분야가 있으면 키 원문 응답 |
| `POST /projects/{projectId}/fields` | 상 | 보통 | JWT | 분야 추가 신청. 첫 승인 시 키 원문 응답 가능 |
| `POST /projects/{projectId}/api-key/reissue` | 상 | 보통 | JWT | 제한 분야만 신청한 경우 키 원문을 받는 유일한 경로. 키 원문 응답 |
| `POST /projects/{projectId}/api-key/renew` | 상 | 보통 | JWT | 키가 60일 뒤 만료되므로 갱신이 없으면 키가 끊김. 이메일 인증 토큰 |
| `GET /projects` | 중 | 낮음 | JWT | 내 프로젝트 목록. 키 원문 없음 |
| `GET /projects/{projectId}` | 중 | 낮음 | JWT | 내 프로젝트 상세. 이름·목적·분야 상태 |
| `DELETE /projects/{projectId}` | 하 | 없음 | JWT | 드물게 씀. `message`만 응답 |

### 관리자(신청·사용 기록)

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /admin/api-applications` | 중 | 낮음 | ADMIN | 승인 대기 목록. 신청자는 `userId`만 |
| `GET /admin/api-applications/{applicationId}` | 중 | 높음 | ADMIN | 승인 판단용. 신청자 아이디·실명·이메일·학번 응답 |
| `POST /admin/api-applications/{applicationId}/approve` | 중 | 높음 | ADMIN | 제한 분야 키 발급에 필요한 운영 흐름. 응답이 상세와 같음 |
| `POST /admin/api-applications/{applicationId}/reject` | 중 | 높음 | ADMIN | 운영 흐름. 응답이 상세와 같음, 요청에 반려 사유 |
| `GET /admin/api-usage-logs` | 중 | 없음 | ADMIN | 사용량·오류 파악. 메서드·경로 템플릿·상태·시간만 저장 |

### 급식

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /meals` | 상 | 없음 | 없음 | 외부 개발자 대표 수요, 공개 API |
| `GET /meals/{mealId}` | 상 | 없음 | 없음 | 급식 공개 조회 |
| `POST /meals` | 하 | 없음 | ADMIN | 기준 데이터 관리 |
| `PATCH /meals/{mealId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |

### 시간표

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /timetables` | 상 | 없음 | 없음 | 외부 개발자 대표 수요, 공개 API |

### 학사 일정

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /schedules` | 상 | 없음 | 키(교내) | 일정 목록. 자동 승인 분야라 수요가 큼 |
| `GET /schedules/{scheduleId}` | 상 | 없음 | 키(교내) | 일정 단건 조회 |
| `POST /schedules` | 하 | 없음 | ADMIN | 기준 데이터 관리 |
| `PATCH /schedules/{scheduleId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |
| `DELETE /schedules/{scheduleId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |

### 자습 감독

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /study-supervisors` | 중 | 보통 | 키(교내) | 분야 조회. 감독 교사 실명 |
| `GET /study-supervisors/day` | 중 | 보통 | 키(교내) | 분야 조회. 감독 교사 실명 |
| `POST /study-supervisors` | 하 | 낮음 | ADMIN | 기준 데이터 관리. 교사 id |
| `PATCH /study-supervisors/{studySupervisorId}` | 하 | 낮음 | ADMIN | 기준 데이터 관리. 교사 id |
| `DELETE /study-supervisors/{studySupervisorId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |

### 동아리

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /clubs` | 중 | 높음 | 키(제한) | 분야 조회. 부원·팀장 학번·실명, 담당 교사 실명 |
| `GET /clubs/{clubId}` | 중 | 높음 | 키(제한) | 분야 조회. 목록과 같은 필드 |
| `POST /clubs` | 하 | 낮음 | ADMIN | 기준 데이터 관리. 학생·교사 id |
| `PATCH /clubs/{clubId}` | 하 | 낮음 | ADMIN | 기준 데이터 관리. 학생·교사 id |
| `DELETE /clubs/{clubId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |

### 교직원

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /teachers` | 중 | 보통 | 키(제한) | 분야 조회. 실명·담당 학년반·과목·교무실 |
| `GET /teachers/{teacherId}` | 중 | 보통 | 키(제한) | 분야 조회. 목록과 같은 필드 |
| `POST /teachers` | 하 | 보통 | ADMIN | 기준 데이터 관리. 실명·담당 정보 입력 |
| `PATCH /teachers/{teacherId}` | 하 | 보통 | ADMIN | 기준 데이터 관리. 실명·담당 정보 입력 |
| `DELETE /teachers/{teacherId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |

### 학생

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /students` | 중 | 높음 | 키(제한) | 분야 조회. 학번·실명·생년월일·이메일 |
| `GET /students/{studentId}` | 중 | 높음 | 키(제한) | 분야 조회. 목록과 같은 필드 |
| `POST /students` | 하 | 높음 | ADMIN | 기준 데이터 관리. 학번·실명·생년월일·이메일 입력 |
| `PATCH /students/{studentId}` | 하 | 높음 | ADMIN | 기준 데이터 관리. 학번·실명·생년월일·이메일 입력 |
| `DELETE /students/{studentId}` | 하 | 없음 | ADMIN | 기준 데이터 관리 |

### 공지

| API | 핵심도 | 민감도 | 접근 | 이유 |
|---|---|---|---|---|
| `GET /notices` | 중 | 낮음 | JWT | 로그인 사용자 공지 목록. 작성자 이름 |
| `GET /notices/{noticeId}` | 중 | 낮음 | JWT | 공지 상세. 작성자 이름 |
| `POST /notices` | 하 | 없음 | ADMIN | 관리자 작성 |
| `PATCH /notices/{noticeId}` | 하 | 없음 | ADMIN | 관리자 수정 |
| `DELETE /notices/{noticeId}` | 하 | 없음 | ADMIN | 관리자 삭제 |

## 우선순위 요약

1. **QA·모니터링 우선 대상(핵심도 상, 14개 + 키 인증)**
   - 인증: `POST /auth/email/otp`, `/auth/email/otp/verify`, `/auth/signup`, `/auth/login`, `/auth/reissue`
   - 프로젝트·키: `POST /projects`, `/projects/{projectId}/fields`, `/projects/{projectId}/api-key/reissue`, `/projects/{projectId}/api-key/renew`
   - 조회: `GET /meals`, `/meals/{mealId}`, `/timetables`, `/schedules`, `/schedules/{scheduleId}`
   - API 키 인증 필터(만료·분야 검사 포함)
2. **개인정보 점검 우선 대상(민감도 높음)**: 학생·동아리 조회(키 제한 단계), `GET /users/me`, 관리자 신청 상세·승인·반려, 가입·OTP, 학생 등록·수정.
3. **나머지(핵심도 중·하)**: 관리자 승인 흐름과 사용 기록, 분야 조회는 정기 회귀 테스트로, 기준 데이터 관리 CRUD는 변경이 있을 때만 확인합니다.

## 검토할 점

- 관리자 승인·반려는 제한 분야 키 발급에 꼭 필요하지만, 호출 빈도가 낮아 중으로 두었습니다. 상으로 올릴지 정해야 합니다.
- `POST /auth/password/reset`을 상(계정 복구)으로 볼지 정해야 합니다.
