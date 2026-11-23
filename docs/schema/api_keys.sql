-- API 키(#35~#48). 앱 기동 전에 적용한다(ddl-auto: validate).
-- - api_keys는 사용자당 한 행이고 키 원문 대신 SHA-256 16진수 해시만 둔다. issued_at은 첫 발급 또는 마지막 재발급 시각(Asia/Seoul)이다.
-- - api_key_applications는 분야별 활용신청 기록이다. 키를 지울 때 앱이 함께 지운다.
-- - unique 제약 이름(uk_api_keys_user_id, uk_api_keys_key_hash, uk_api_key_applications_api_key_id_field)은
--   앱이 동시 신청 충돌을 409로 바꾸는 데 쓰는 계약이라 바꾸지 않는다.
-- - 엔티티가 FK를 두지 않아 FK 없이 만든다(Hibernate create 결과와 같음).
create table api_keys (
    id        bigint      not null auto_increment,
    issued_at datetime(6) not null,
    user_id   bigint      not null,
    key_hash  varchar(64) not null,
    primary key (id),
    unique key uk_api_keys_user_id (user_id),
    unique key uk_api_keys_key_hash (key_hash)
) engine = InnoDB default charset = utf8mb4;

create table api_key_applications (
    id           bigint       not null auto_increment,
    api_key_id   bigint       not null,
    field        enum ('CLUB','SCHEDULE','STUDENT','STUDY_SUPERVISOR','TEACHER') not null,
    type         enum ('ADD_FIELD','FIRST') not null,
    service_name varchar(30)  not null,
    purpose      varchar(200) not null,
    applied_at   datetime(6)  not null,
    primary key (id),
    unique key uk_api_key_applications_api_key_id_field (api_key_id, field)
) engine = InnoDB default charset = utf8mb4;
