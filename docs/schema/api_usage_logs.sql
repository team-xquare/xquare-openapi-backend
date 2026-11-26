-- 앱 기동 전 적용한다(ddl-auto: validate). 기존 테이블을 수정하거나 참조하지 않는다.
-- occurred_at은 UTC 완료 시각이다. 로컬 시각 및 요청 식별자는 저장하지 않는다.
-- project_id·user_id는 FK 없이 id만 둔다. 프로젝트나 사용자가 지워져도 기록은 남는다.
-- key_prefix는 API 키 원문의 앞 11글자다. 키 원문과 해시는 저장하지 않는다.
-- access_level은 UsageAccessLevel 이름(PUBLIC/SCHOOL/RESTRICTED) 문자열이다.
-- 이미 테이블이 있는 DB는 migrations/2026-11-28-api-usage-logs-detail.sql을 적용한다.
create table api_usage_logs (
    id           bigint       not null auto_increment,
    occurred_at  datetime(6)  not null,
    http_method  varchar(8)   not null,
    route        varchar(160) not null,
    http_status  int          not null,
    duration_ms  bigint       not null,
    project_id   bigint       null,
    user_id      bigint       null,
    key_prefix   varchar(11)  null,
    error_code   varchar(40)  null,
    access_level varchar(10)  null,
    primary key (id),
    key ix_api_usage_logs_occurred_id (occurred_at, id),
    key ix_api_usage_logs_project_occurred (project_id, occurred_at)
) engine = InnoDB default charset = utf8mb4;
