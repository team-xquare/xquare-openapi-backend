# API 키 프로젝트 단위 구조 · 명세 (초안)

11/20 선생님 피드백 반영안입니다. **API 키는 프로젝트마다 하나**이고, **제한 분야는 관리자가 승인**합니다.
프론트 연동용으로 먼저 공유하는 명세이고, 구현은 이 문서가 확정된 뒤 PR을 나눠 올립니다.
⚠️ 표시는 확정이 필요한 결정입니다.

## 1. 현재(develop)와 바뀌는 점

| 항목 | 현재 develop (#35~#48) | 바뀐 구조 |
|---|---|---|
| 키 단위 | 사용자당 키 1개 | **프로젝트당 키 1개**, 사용자는 프로젝트 여러 개 |
| 승인 | 분야 신청 즉시 사용 | 교내 분야는 자동 승인, 제한 분야는 **관리자 승인 대기** |
| 키 발급 시점 | 첫 신청 | 프로젝트의 **첫 승인** 시점 |
| 만료 | 없음(`expiresAt` 항상 null) | **발급·갱신 후 60일** |
| 갱신 | 없음 | 이메일 인증 후 만료일만 연장, **키 값 그대로** |
| 재발급·삭제 | 사용자 단위 | 프로젝트 단위 |
| 관리자 | 신청 목록·상세 | 목록·상세 + **승인·반려(사유)** |

## 2. 접근 단계

| 단계 | 의미 | 키 필요 | 승인 |
|---|---|---|---|
| 공개 `PUBLIC` | 누구나 | 없음 | - |
| 교내 `SCHOOL` | 교내 정보, 개인정보 최소 | 필요 | 신청 즉시 자동 승인 |
| 제한 `RESTRICTED` | 개인정보 포함 | 필요 | 관리자 승인 후 |

### 분야별 단계 ⚠️ (11/21 데이터·접근 표 기준 제안)

| 분야 | 조회 API | 단계(제안) |
|---|---|---|
| 급식 | `GET /meals`, `GET /meals/{mealId}` | 공개 |
| 시간표 | `GET /timetables` | 공개 |
| 일정 `SCHEDULE` | `GET /schedules/**` | 교내 |
| 동아리 `CLUB` | `GET /clubs`, `GET /clubs/{clubId}` | 교내 |
| 자습 감독 `STUDY_SUPERVISOR` | `GET /study-supervisors`, `GET /study-supervisors/day` | 교내 |
| 교직원 `TEACHER` | `GET /teachers`, `GET /teachers/{teacherId}` | 제한 |
| 학생 `STUDENT` | `GET /students`, `GET /students/{studentId}` | 제한 |

- 단계는 분야에 붙고 코드 상수로 둡니다(관리자 화면에서 바꾸지 않음).
- 키 인증은 `X-API-Key` 헤더 그대로입니다. 승인된 분야만 통과합니다.
- 공지·내 정보·키 관리 API는 지금처럼 JWT(로그인)로 엽니다.

## 3. 데이터 모델

```
users 1 ─── N api_projects 1 ─── N api_project_fields
```

**api_projects**: 프로젝트 = 키 1개

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| user_id | bigint | 신청자 |
| name | varchar(30) | 서비스(프로젝트) 이름. 같은 사용자 안에서 unique |
| purpose | varchar(200) | 사용 목적 |
| key_hash | varchar(64) null, unique | SHA-256. **첫 승인 전에는 null** |
| issued_at | datetime(6) null | 키 발급·재발급 시각 |
| expires_at | datetime(6) null | 만료 시각 = 발급/갱신 + 60일 |
| created_at | datetime(6) | |

**api_project_fields**: 프로젝트의 분야별 신청·승인 상태

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| project_id | bigint | unique(project_id, field) |
| field | enum | `SCHEDULE`, `CLUB`, `STUDY_SUPERVISOR`, `TEACHER`, `STUDENT` |
| status | enum | `PENDING`, `APPROVED`, `REJECTED` |
| reject_reason | varchar(200) null | 반려 사유 |
| applied_at | datetime(6) | |
| decided_at | datetime(6) null | 승인·반려 시각 |
| decided_by | bigint null | 처리한 관리자 |

- 교내 분야는 신청과 동시에 `APPROVED`, 제한 분야는 `PENDING`.
- 반려된 분야는 다시 신청하면 `PENDING`으로 돌아갑니다 ⚠️.
- 기존 `api_keys`, `api_key_applications`(develop, 데이터 없음)는 새 테이블로 교체합니다. 스테이징에도 기존 데이터가 없어서 이관은 필요 없습니다.

## 4. 키 수명

| 시점 | 동작 |
|---|---|
| 첫 승인 (자동 또는 관리자) | 키 발급. `issued_at = now`, `expires_at = now + 60일` |
| 키 원문 전달 | 아래 4.1 ⚠️ |
| 갱신 `POST /projects/{id}/api-key/renew` | 이메일 인증 → `expires_at = now + 60일`, 키 값 그대로 |
| 재발급 `POST /projects/{id}/api-key/reissue` | 이메일 인증 → 새 키, 기존 키 즉시 폐기, `expires_at = now + 60일` |
| 만료 후 | 인증 실패 `401 API_KEY_EXPIRED`. 갱신하면 다시 사용 |
| 삭제 `DELETE /projects/{id}` | 프로젝트·분야·키 모두 삭제 |

### 4.1 키 원문 전달 ⚠️
DB에는 해시만 두므로 원문은 한 번만 보여 줄 수 있습니다.
- 교내 분야가 있는 첫 신청: 신청 응답에 원문을 바로 줍니다.
- 제한 분야만 신청해 승인을 기다리는 경우, 제안은 **승인 후 사용자가 `POST /projects/{id}/api-key/reissue`로 직접 받는 것**입니다. 관리자 승인 응답에는 원문을 주지 않습니다.
- 대안: 승인 시 키를 만들어 두고 "처음 한 번 보기" API를 둡니다. 원문을 서버가 잠시 보관해야 해서 비추천합니다.

## 5. API

모두 JSON, 시각은 `+09:00` 오프셋 RFC 3339입니다. 사용자 API는 JWT, 관리자 API는 ADMIN JWT입니다.

### 사용자

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/projects` | 프로젝트 만들기 + 분야 신청 (이메일 인증) |
| POST | `/projects/{projectId}/fields` | 분야 추가 신청 (이메일 인증) |
| GET | `/projects` | 내 프로젝트 목록 (키 발급 여부·만료일·분야별 상태) |
| GET | `/projects/{projectId}` | 내 프로젝트 상세 |
| POST | `/projects/{projectId}/api-key/renew` | 키 갱신 (이메일 인증, 키 값 유지) |
| POST | `/projects/{projectId}/api-key/reissue` | 키 재발급 (이메일 인증, 새 원문 반환) |
| DELETE | `/projects/{projectId}` | 프로젝트 삭제 |

`POST /projects` 요청
```json
{
  "name": "급식 알리미",
  "purpose": "반 단톡방에 매일 급식을 올리려고 합니다.",
  "fields": ["SCHEDULE", "STUDENT"],
  "emailVerifiedToken": "3f1c2a9e-..."
}
```
응답 `201`
```json
{
  "projectId": 1,
  "apiKey": "xq_...",
  "fields": [
    { "field": "SCHEDULE", "level": "SCHOOL", "status": "APPROVED" },
    { "field": "STUDENT", "level": "RESTRICTED", "status": "PENDING" }
  ]
}
```
- `apiKey`는 이번 요청으로 키가 처음 발급됐을 때만 값이 있고, 아니면 null입니다.

`GET /projects` 응답 `200`
```json
[
  {
    "projectId": 1,
    "name": "급식 알리미",
    "keyIssued": true,
    "issuedAt": "2026-11-24T10:00:00+09:00",
    "expiresAt": "2027-01-23T10:00:00+09:00",
    "fields": [
      { "field": "SCHEDULE", "level": "SCHOOL", "status": "APPROVED", "rejectReason": null },
      { "field": "STUDENT", "level": "RESTRICTED", "status": "REJECTED", "rejectReason": "사용 목적에 학생 개인정보가 필요하지 않습니다." }
    ]
  }
]
```
- 키 원문은 목록에 내려 주지 않습니다(해시만 저장).

### 관리자

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/admin/api-applications?status=PENDING&page=0&size=20` | 분야 신청 목록 (상태 필터) |
| GET | `/admin/api-applications/{applicationId}` | 상세 (신청자, 프로젝트, 목적) |
| POST | `/admin/api-applications/{applicationId}/approve` | 승인. 프로젝트 첫 승인이면 키 발급 |
| POST | `/admin/api-applications/{applicationId}/reject` | 반려. 요청 `{ "reason": "..." }` (1~200자) |

- 여기서 `applicationId`는 `api_project_fields.id`입니다.
- `PENDING`이 아닌 신청을 승인·반려하면 `409 APPLICATION_ALREADY_DECIDED`.

### 에러 코드 (신규·변경)

| 코드 | 상태 | 상황 |
|---|---|---|
| `INVALID_API_KEY` | 401 | 키 없음·틀림 (기존) |
| `API_KEY_EXPIRED` | 401 | 만료된 키 |
| `API_KEY_FIELD_FORBIDDEN` | 403 | 승인되지 않은 분야 (기존, 의미 확장) |
| `PROJECT_NOT_FOUND` | 404 | 없거나 내 프로젝트가 아님 |
| `PROJECT_NAME_DUPLICATED` | 409 | 같은 이름의 내 프로젝트가 있음 |
| `PROJECT_LIMIT_EXCEEDED` | 409 | 내 프로젝트가 이미 5개(사용자당 상한) |
| `API_KEY_NOT_ISSUED` | 409 | 승인 전이라 갱신·재발급할 키가 없음 |
| `FIELD_ALREADY_APPLIED` | 409 | 이미 신청(대기·승인)한 분야 |
| `APPLICATION_NOT_FOUND` | 404 | 없는 신청 |
| `APPLICATION_ALREADY_DECIDED` | 409 | 이미 처리된 신청 |

## 6. 확정이 필요한 것 ⚠️

1. 분야별 단계(2절 표). 특히 일정·동아리·자습 감독을 교내로 둘지
2. 제한 분야만 신청한 경우 키 원문 전달 방식(4.1)
3. 반려된 분야 재신청 허용 여부
4. 사용자당 프로젝트 개수 상한(제안: 5개)
5. 만료 전 알림 메일 여부(제안: 이번 범위 밖)

## 7. 구현 순서 (PR 단위)

1. 프로젝트·분야 테이블 + 신청·자동 승인·키 발급 (기존 `/api-keys` 대체)
2. 조회 API 키 인증: 단계별 검사, 동아리·자습 감독·일정 추가(#51 포함), 만료 검사
3. 내 프로젝트 목록·상세, 재발급·삭제
4. 관리자 목록·상세·승인·반려
5. 60일 만료·갱신 API
