-- QA용 mock 데이터(계정·프로젝트·분야 신청·사용 기록). 스테이징처럼 QA를 하는 DB에만 넣는다. 운영 DB에는 넣지 않는다.
-- 먼저 docs/schema/migrations/의 2026-11-28-*.sql을 모두 적용해야 한다(빈 프로젝트·분야별 목적·구분·서비스 URL·사용 기록 확장).
-- 다시 실행해도 된다: 앞부분에서 qa_로 시작하는 계정과 그 계정의 프로젝트·신청·사용 기록을 지우고 새로 넣는다.
--
-- 계정 (비밀번호는 모두 Qa-test1234!)
--   qa_admin  ADMIN  관리자 화면(신청 승인·반려, 사용 기록)
--   qa_user1  USER   프로젝트 2개: 키 발급·공개/교내 승인, 제한 대기·반려
--   qa_user2  USER   관리자가 승인한 제한 분야가 있는 프로젝트
--   qa_user3  USER   분야를 아직 신청하지 않은 빈 프로젝트(키 없음)
--   qa_user4  USER   만료된 키
--
-- API 키 원문 (X-API-Key 헤더에 넣는다. DB에는 SHA-256 해시만 있다)
--   급식 알리미   xq_qa_meal_alimi_key_0000000000000000000000001   MEAL·SCHEDULE 승인
--   학생 인증 봇  xq_qa_student_bot_key_000000000000000000000002   STUDENT 대기, CLUB 반려 → 학생·동아리 조회는 403
--   시간표 위젯   xq_qa_timetable_widget_key_00000000000000003     STUDY_SUPERVISOR·TEACHER 승인
--   만료 테스트   xq_qa_expired_key_00000000000000000000000004     SCHEDULE 승인이지만 만료 → 401 API_KEY_EXPIRED
--
-- 시각: api_projects·api_project_fields는 한국 시간, api_usage_logs.occurred_at은 UTC로 저장한다(앱과 같음).

set names utf8mb4;
set @now = convert_tz(utc_timestamp(6), '+00:00', '+09:00');

-- 1) 이전 mock 데이터 지우기
delete l from api_usage_logs l join users u on u.id = l.user_id where u.username like 'qa!_%' escape '!';
delete f from api_project_fields f join api_projects p on p.id = f.project_id join users u on u.id = p.user_id where u.username like 'qa!_%' escape '!';
delete p from api_projects p join users u on u.id = p.user_id where u.username like 'qa!_%' escape '!';
delete from users where username like 'qa!_%' escape '!';

-- 2) 계정
insert into users (username, password, student_number, name, email, role) values
    ('qa_admin', '$2a$10$aJhUCpaUS1mJJriP5jX.0.L/jAqeoi.tAK4b9nwCWr2z6rDJQMore', 1101, 'QA관리자', 'qa.admin@dsm.hs.kr', 'ADMIN'),
    ('qa_user1', '$2a$10$aJhUCpaUS1mJJriP5jX.0.L/jAqeoi.tAK4b9nwCWr2z6rDJQMore', 2101, 'QA학생일', 'qa.user1@dsm.hs.kr', 'USER'),
    ('qa_user2', '$2a$10$aJhUCpaUS1mJJriP5jX.0.L/jAqeoi.tAK4b9nwCWr2z6rDJQMore', 2202, 'QA학생이', 'qa.user2@dsm.hs.kr', 'USER'),
    ('qa_user3', '$2a$10$aJhUCpaUS1mJJriP5jX.0.L/jAqeoi.tAK4b9nwCWr2z6rDJQMore', 3103, 'QA학생삼', 'qa.user3@dsm.hs.kr', 'USER'),
    ('qa_user4', '$2a$10$aJhUCpaUS1mJJriP5jX.0.L/jAqeoi.tAK4b9nwCWr2z6rDJQMore', 1304, 'QA학생사', 'qa.user4@dsm.hs.kr', 'USER');
set @admin = (select id from users where username = 'qa_admin');
set @u1 = (select id from users where username = 'qa_user1');
set @u2 = (select id from users where username = 'qa_user2');
set @u3 = (select id from users where username = 'qa_user3');
set @u4 = (select id from users where username = 'qa_user4');

-- 3) 프로젝트
insert into api_projects (user_id, name, service_url, key_hash, issued_at, expires_at, created_at) values
    (@u1, '급식 알리미', 'https://meal-alimi.example.com', '83ee34d41b677c0d7dbbeff57c21e84bdce5c20429fee16f429ed51de97e1cdc',
        @now - interval 10 day, @now + interval 50 day, @now - interval 10 day);
set @p1 = last_insert_id();
insert into api_projects (user_id, name, service_url, key_hash, issued_at, expires_at, created_at) values
    (@u1, '학생 인증 봇', null, '61325d19b4c3790dd8324515288937903ab3806d9230c9e49373c511e97bb6aa',
        @now - interval 3 day, @now + interval 57 day, @now - interval 3 day);
set @p2 = last_insert_id();
insert into api_projects (user_id, name, service_url, key_hash, issued_at, expires_at, created_at) values
    (@u2, '시간표 위젯', 'https://widget.example.com', '037d67a252a2397ba95d193932c1c6368922b7b153ef3cd7ef6ea7a6f105890f',
        @now - interval 7 day, @now + interval 53 day, @now - interval 7 day);
set @p3 = last_insert_id();
insert into api_projects (user_id, name, service_url, key_hash, issued_at, expires_at, created_at) values
    (@u3, '준비 중 프로젝트', null, null, null, null, @now - interval 1 day);
set @p4 = last_insert_id();
insert into api_projects (user_id, name, service_url, key_hash, issued_at, expires_at, created_at) values
    (@u4, '만료 테스트', null, '5df1c3778e7891173ed00215828d3ea6146b70d015f1de28fb196bf1ca494be9',
        @now - interval 70 day, @now - interval 10 day, @now - interval 70 day);
set @p5 = last_insert_id();

-- 4) 분야 신청 (관리자 신청 목록: 대기 2건, 승인 1건, 반려 1건 + 자동 승인)
insert into api_project_fields (project_id, field, purpose, application_type, status, reject_reason, applied_at, decided_at, decided_by) values
    (@p1, 'MEAL', '반 단톡방에 매일 급식을 올리려고 합니다.', 'FIRST', 'APPROVED', null, @now - interval 10 day, @now - interval 10 day, null),
    (@p1, 'SCHEDULE', '급식과 함께 학사 일정도 알려 주려고 합니다.', 'FIRST', 'APPROVED', null, @now - interval 10 day, @now - interval 10 day, null),
    (@p2, 'STUDENT', '동아리 지원서에서 학번으로 학생을 확인하려고 합니다.', 'FIRST', 'PENDING', null, @now - interval 3 day, null, null),
    (@p2, 'CLUB', '동아리 목록을 보여 주려고 합니다.', 'ADDITIONAL', 'REJECTED', '부원 실명이 필요한 이유를 구체적으로 적어 주세요.', @now - interval 2 day, @now - interval 1 day, @admin),
    (@p3, 'STUDY_SUPERVISOR', '오늘의 자습 감독 선생님을 위젯에 보여 줍니다.', 'FIRST', 'APPROVED', null, @now - interval 7 day, @now - interval 7 day, null),
    (@p3, 'TEACHER', '감독 선생님의 담당 과목을 함께 보여 줍니다.', 'ADDITIONAL', 'APPROVED', null, @now - interval 6 day, @now - interval 5 day, @admin),
    (@p3, 'CLUB', '선생님이 맡은 동아리도 보여 주려고 합니다.', 'ADDITIONAL', 'PENDING', null, @now - interval 4 hour, null, null),
    (@p5, 'SCHEDULE', '학사 일정 알림', 'FIRST', 'APPROVED', null, @now - interval 70 day, @now - interval 70 day, null);

-- 5) 사용 기록 (관리자 사용 기록 화면: 성공·401·403·만료·공개 호출, 단계 필터)
set @utc = utc_timestamp(6);
insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms, project_id, user_id, key_prefix, error_code, access_level) values
    (@utc - interval 50 minute, 'GET', '/meals', 200, 12, @p1, @u1, 'xq_qa_meal_', null, 'PUBLIC'),
    (@utc - interval 45 minute, 'GET', '/schedules', 200, 18, @p1, @u1, 'xq_qa_meal_', null, 'SCHOOL'),
    (@utc - interval 40 minute, 'GET', '/schedules/{scheduleId}', 404, 9, @p1, @u1, 'xq_qa_meal_', 'SCHEDULE_NOT_FOUND', 'SCHOOL'),
    (@utc - interval 35 minute, 'GET', '/students', 403, 6, @p2, @u1, 'xq_qa_stude', 'API_KEY_FIELD_FORBIDDEN', 'RESTRICTED'),
    (@utc - interval 30 minute, 'GET', '/clubs', 403, 5, @p2, @u1, 'xq_qa_stude', 'API_KEY_FIELD_FORBIDDEN', 'RESTRICTED'),
    (@utc - interval 25 minute, 'GET', '/study-supervisors/day', 200, 21, @p3, @u2, 'xq_qa_timet', null, 'SCHOOL'),
    (@utc - interval 20 minute, 'GET', '/teachers', 200, 34, @p3, @u2, 'xq_qa_timet', null, 'RESTRICTED'),
    (@utc - interval 15 minute, 'GET', '/teachers/{teacherId}', 200, 15, @p3, @u2, 'xq_qa_timet', null, 'RESTRICTED'),
    (@utc - interval 10 minute, 'GET', '/schedules', 401, 4, @p5, @u4, 'xq_qa_expir', 'API_KEY_EXPIRED', 'SCHOOL'),
    (@utc - interval 8 minute, 'POST', '/projects/{projectId}/fields', 201, 40, null, @u3, null, null, null),
    (@utc - interval 5 minute, 'POST', '/admin/api-applications/{applicationId}/approve', 200, 27, null, @admin, null, null, null),
    (@utc - interval 2 minute, 'GET', '/timetables', 200, 210, null, @u1, null, null, 'PUBLIC');
