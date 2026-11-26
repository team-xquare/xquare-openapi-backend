-- 기존 api_usage_logs에 프로젝트·사용자·키 접두사·오류 코드·단계를 더한다.
-- 새 앱은 ddl-auto: validate라 배포 전에 스테이징·운영 DB에 먼저 적용해야 한다. 이전 앱은 새 열을 쓰지 않아 먼저 적용해도 그대로 동작한다.
-- 모두 nullable이고 끝에 붙이므로 MySQL 8.0.29 이상에서는 즉시(INSTANT) 열 추가로 처리된다. 과거 행은 null로 남고 백필하지 않는다.
alter table api_usage_logs
    add column project_id   bigint      null,
    add column user_id      bigint      null,
    add column key_prefix   varchar(11) null,
    add column error_code   varchar(40) null,
    add column access_level varchar(10) null;

-- 프로젝트별 사용 기록 조회(projectId + 기간)용 인덱스. 온라인(INPLACE, LOCK=NONE)으로 만들어진다.
alter table api_usage_logs
    add key ix_api_usage_logs_project_occurred (project_id, occurred_at);

-- 롤백(이전 앱으로 되돌린 뒤 필요할 때만):
-- alter table api_usage_logs
--     drop key ix_api_usage_logs_project_occurred,
--     drop column project_id, drop column user_id, drop column key_prefix, drop column error_code, drop column access_level;
