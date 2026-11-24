-- API 키 프로젝트 단위 구조(명세 #57). 앱 기동 전에 적용한다(ddl-auto: validate).
-- - api_projects는 프로젝트 하나에 키 하나다. 키 원문 대신 SHA-256 16진수 해시만 두고, 첫 승인 전에는 key_hash가 null이다.
--   issued_at은 키 발급·재발급 시각, expires_at은 만료 시각이다(모두 Asia/Seoul, null이면 아직 발급 전 또는 만료 없음).
-- - api_project_fields는 프로젝트의 분야별 신청·승인 상태다. 관리자 API의 applicationId가 이 테이블의 id다.
--   decided_by는 처리한 관리자의 userId이고 자동 승인이면 null이다. 프로젝트를 지울 때 앱이 함께 지운다.
-- - unique 제약 이름(uk_api_projects_user_id_name, uk_api_project_fields_project_id_field)은
--   앱이 동시 요청 충돌을 409로 바꾸는 데 쓰는 계약이라 바꾸지 않는다.
-- - 엔티티가 FK를 두지 않아 FK 없이 만든다(Hibernate create 결과와 같음).
-- - 이 구조는 사용자당 키 1개였던 api_keys, api_key_applications(#35~#48)를 대체한다. 옮길 데이터가 없으면 먼저 지운다.
--   drop table if exists api_key_applications;
--   drop table if exists api_keys;
create table api_projects (
    id         bigint       not null auto_increment,
    user_id    bigint       not null,
    name       varchar(30)  not null,
    purpose    varchar(200) not null,
    key_hash   varchar(64),
    issued_at  datetime(6),
    expires_at datetime(6),
    created_at datetime(6)  not null,
    primary key (id),
    unique key uk_api_projects_user_id_name (user_id, name),
    unique key uk_api_projects_key_hash (key_hash)
) engine = InnoDB default charset = utf8mb4;

create table api_project_fields (
    id            bigint       not null auto_increment,
    project_id    bigint       not null,
    field         enum ('CLUB','SCHEDULE','STUDENT','STUDY_SUPERVISOR','TEACHER') not null,
    status        enum ('APPROVED','PENDING','REJECTED') not null,
    reject_reason varchar(200),
    applied_at    datetime(6)  not null,
    decided_at    datetime(6),
    decided_by    bigint,
    primary key (id),
    unique key uk_api_project_fields_project_id_field (project_id, field)
) engine = InnoDB default charset = utf8mb4;
