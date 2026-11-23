-- 앱 기동 전 적용한다(ddl-auto: validate). 기존 테이블을 수정하거나 참조하지 않는다.
-- occurred_at은 UTC 완료 시각이다. 로컬 시각 및 요청 식별자는 저장하지 않는다.
create table api_usage_logs (
    id           bigint       not null auto_increment,
    occurred_at  datetime(6)  not null,
    http_method  varchar(8)   not null,
    route        varchar(160) not null,
    http_status  int          not null,
    duration_ms  bigint       not null,
    primary key (id),
    key ix_api_usage_logs_occurred_id (occurred_at, id)
) engine = InnoDB default charset = utf8mb4;
