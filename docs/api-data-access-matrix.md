# API별 데이터 항목·접근 단계

2026년 11월 21일 공동 검토 초안. **정책 확정이나 운영 배포를 뜻하지 않습니다.**

- 기준: `develop` `1f265558cfd6c6741013ce3134716ec010e206e9`의 `docs/openapi.yaml` 요청/성공 응답과 컨트롤러 매핑, `SecurityConfig`의 인증 분기.
- 위 기준 시점에는 문서 41개 작업 중 37개가 구현돼 있었습니다. 아래 학사 일정 표만 #43과 OPN-68의 목록 추가를 반영했습니다. Swagger UI·건강 점검·프리플라이트는 표에서 제외합니다.
- 표는 필드 이름만 나열합니다. 요청 값, 실제 사용자 레코드, 비밀번호·OTP·토큰·API 키 값은 포함하지 않습니다. 필드의 타입·선택 여부·오류는 OpenAPI 명세를 참조합니다.

## 접근 범례

- JWT 없음: 세션용 Bearer JWT 불필요. 인증 POST의 경우 계정 정보, OTP, 인증 토큰 등 각 엔드포인트 조건은 별도 적용됩니다.
- 로그인 JWT: USER/ADMIN이 이용할 수 있습니다. `/users/me`는 해당 JWT의 본인 레코드만 반환합니다.
- ADMIN JWT: 관리 작업에 필요한 역할. API 키로 관리자 접근을 대신하지 않습니다.
- 분야 API 키(예정): 해당 분야를 신청한 `X-API-Key`. 아래 변경 예정 열은 현행 인가가 아니며 #34/#45/#51과 #43에 의존합니다.

## 함께 정할 항목

1. 학생 학번·이름·생년월일·이메일, 동아리 명단, 자습 감독 교직원 식별자: 서비스별 꼭 필요한 필드만 외부 API 키에 허용할지 항목별로 정합니다. 권한 변경과 응답 축소를 따로 검토합니다.
2. 운영 기록(#52): 요청/응답 본문·쿼리·원본 IP·자격증명은 저장하지 않고, 접근 역할·보존기간·저장 실패 처리를 합의합니다.
3. 학사 일정 조회의 분야 키 전환은 별도 정책 결정입니다. 시간표는 현재 NEIS이며 컴시간 연동은 별도입니다(#53).

## 인증

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `POST /auth/email/otp` | `body:email` | `message` | JWT 없음 | — |
| `POST /auth/email/otp/verify` | `body:email`, `body:otp` | `emailVerifiedToken` | JWT 없음 | — |
| `POST /auth/login` | `body:username`, `body:password` | `accessToken`, `refreshToken` | JWT 없음 | — |
| `POST /auth/reissue` | `body:refreshToken` | `accessToken`, `refreshToken` | JWT 없음 | — |
| `POST /auth/password/reset` | `body:emailVerifiedToken`, `body:newPassword` | `message` | JWT 없음 | — |
| `POST /auth/signup` | `body:username`, `body:password`, `body:studentNumber`, `body:name`, `body:email`, `body:emailVerifiedToken` | `accessToken`, `refreshToken` | JWT 없음 | — |

## 내 정보

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /users/me` | — | `id`, `username`, `name`, `studentNumber`, `email`, `role` | 로그인 JWT (USER/ADMIN) | — |

## 학생

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /students` | `query:name`, `query:grade`, `query:classNum`, `query:page`, `query:size` | `content[].id`, `content[].studentNumber`, `content[].grade`, `content[].classNum`, `content[].number`, `content[].name`, `content[].birthDate`, `content[].email`, `content[].club.id`, `content[].club.name`, `content[].club.logo`, `page`, `size`, `totalCount`, `totalPage` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (STUDENT) — 미반영 |
| `POST /students` | `body:studentNumber`, `body:name`, `body:birthDate`, `body:email` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /students/{studentId}` | `path:studentId` | `id`, `studentNumber`, `grade`, `classNum`, `number`, `name`, `birthDate`, `email`, `club.id`, `club.name`, `club.logo` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (STUDENT) — 미반영 |
| `PATCH /students/{studentId}` | `path:studentId`, `body:studentNumber`, `body:name`, `body:birthDate`, `body:email` | `message` | ADMIN JWT | — |
| `DELETE /students/{studentId}` | `path:studentId` | `message` | ADMIN JWT | — |

## 교직원

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /teachers` | `query:name`, `query:grade`, `query:classNum`, `query:subject`, `query:office`, `query:page`, `query:size` | `content[].id`, `content[].name`, `content[].grade`, `content[].classNum`, `content[].subject`, `content[].office`, `content[].isManager`, `content[].position`, `content[].club.id`, `content[].club.name`, `content[].club.logo`, `page`, `size`, `totalCount`, `totalPage` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (TEACHER) — 미반영 |
| `POST /teachers` | `body:name`, `body:grade`, `body:classNum`, `body:subject`, `body:office`, `body:isManager`, `body:position` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /teachers/{teacherId}` | `path:teacherId` | `id`, `name`, `grade`, `classNum`, `subject`, `office`, `isManager`, `position`, `club.id`, `club.name`, `club.logo` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (TEACHER) — 미반영 |
| `PATCH /teachers/{teacherId}` | `path:teacherId`, `body:name`, `body:grade`, `body:classNum`, `body:subject`, `body:office`, `body:isManager`, `body:position` | `message` | ADMIN JWT | — |
| `DELETE /teachers/{teacherId}` | `path:teacherId` | `message` | ADMIN JWT | — |

## 학사 일정

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `POST /schedules` | `body:date`, `body:title`, `body:content` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /schedules` | `query:startDate`, `query:finishDate` | `[].id`, `[].date`, `[].title`, `[].content`, `[].source` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (SCHEDULE) — 미반영 |
| `GET /schedules/{scheduleId}` | `path:scheduleId` | `id`, `date`, `title`, `content`, `source` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (SCHEDULE) — 미반영 |
| `PATCH /schedules/{scheduleId}` | `path:scheduleId`, `body:date`, `body:title`, `body:content` | `message` | ADMIN JWT | — |
| `DELETE /schedules/{scheduleId}` | `path:scheduleId` | `message` | ADMIN JWT | — |

## 시간표

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /timetables` | `query:grade`, `query:classNum`, `query:date` | `grade`, `classNum`, `startDate`, `finishDate`, `days[].date`, `days[].periods[].period`, `days[].periods[].subject` | 로그인 JWT (USER/ADMIN) | — |

## 자습 감독

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `POST /study-supervisors` | `body:date`, `body:supervisors[].floor`, `body:supervisors[].teacherId` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /study-supervisors` | `query:startDate`, `query:finishDate` | `startDate`, `finishDate`, `supervisions[].id`, `supervisions[].date`, `supervisions[].supervisors[].floor`, `supervisions[].supervisors[].teacher.teacherId`, `supervisions[].supervisors[].teacher.name`, `supervisions[].supervisors[].teacher` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (STUDY_SUPERVISOR) — 미반영 |
| `GET /study-supervisors/day` | `query:date` | `id`, `date`, `supervisors[].floor`, `supervisors[].teacher.teacherId`, `supervisors[].teacher.name`, `supervisors[].teacher` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (STUDY_SUPERVISOR) — 미반영 |
| `PATCH /study-supervisors/{studySupervisorId}` | `path:studySupervisorId`, `body:date`, `body:supervisors[].floor`, `body:supervisors[].teacherId` | `message` | ADMIN JWT | — |
| `DELETE /study-supervisors/{studySupervisorId}` | `path:studySupervisorId` | `message` | ADMIN JWT | — |

## 급식

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /meals` | `query:startDate`, `query:finishDate` | `startDate`, `finishDate`, `meals[].id`, `meals[].date`, `meals[].breakfast[]`, `meals[].lunch[]`, `meals[].dinner[]`, `totalCount` | JWT 없음 | — |
| `POST /meals` | `body:date`, `body:breakfast[]`, `body:lunch[]`, `body:dinner[]` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /meals/{mealId}` | `path:mealId` | `id`, `date`, `breakfast[]`, `lunch[]`, `dinner[]` | JWT 없음 | — |
| `PATCH /meals/{mealId}` | `path:mealId`, `body:date`, `body:breakfast[]`, `body:lunch[]`, `body:dinner[]` | `message` | ADMIN JWT | — |

## 동아리

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /clubs` | `query:name`, `query:member`, `query:room`, `query:teacher`, `query:page`, `query:size` | `content[].id`, `content[].name`, `content[].logo`, `content[].room`, `content[].manager.id`, `content[].manager.studentNumber`, `content[].manager.name`, `content[].manager`, `content[].member[].id`, `content[].member[].studentNumber`, `content[].member[].name`, `content[].teacher.id`, `content[].teacher.name`, `content[].teacher`, `page`, `size`, `totalCount`, `totalPage` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (CLUB) — 미반영 |
| `POST /clubs` | `body:name`, `body:logo`, `body:room`, `body:manager`, `body:member[]`, `body:teacher` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /clubs/{clubId}` | `path:clubId` | `id`, `name`, `logo`, `room`, `manager.id`, `manager.studentNumber`, `manager.name`, `manager`, `member[].id`, `member[].studentNumber`, `member[].name`, `teacher.id`, `teacher.name`, `teacher` | 로그인 JWT (USER/ADMIN) | 분야 API 키 (CLUB) — 미반영 |
| `PATCH /clubs/{clubId}` | `path:clubId`, `body:name`, `body:logo`, `body:manager`, `body:member[]`, `body:room`, `body:teacher` | `message` | ADMIN JWT | — |
| `DELETE /clubs/{clubId}` | `path:clubId` | `message` | ADMIN JWT | — |

## 공지

| API | 요청 필드 | 성공 응답 필드 | 현재 접근 | 변경 예정 |
|---|---|---|---|---|
| `GET /notices` | `query:page`, `query:size` | `notices[].id`, `notices[].title`, `notices[].author`, `notices[].createdAt`, `notices[].updatedAt`, `page`, `size`, `totalCount`, `totalPage` | 로그인 JWT (USER/ADMIN) | — |
| `POST /notices` | `body:title`, `body:content` | `message`, `header:Location` | ADMIN JWT | — |
| `GET /notices/{noticeId}` | `path:noticeId` | `id`, `title`, `content`, `author`, `createdAt`, `updatedAt` | 로그인 JWT (USER/ADMIN) | — |
| `PATCH /notices/{noticeId}` | `path:noticeId`, `body:title`, `body:content` | `message` | ADMIN JWT | — |
| `DELETE /notices/{noticeId}` | `path:noticeId` | `message` | ADMIN JWT | — |

## 기준 갱신 방법

컨트롤러 변경과 권한 변경을 머지할 때, `docs/openapi.yaml`과 생성 `/v3/api-docs`의 HTTP 메서드·경로·인증 표시를 함께 확인합니다. 정적 명세에 먼저 기록된 작업은 출시 전까지 현행 API로 안내하지 않습니다.
