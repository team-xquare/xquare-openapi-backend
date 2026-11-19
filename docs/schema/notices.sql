-- 공지(#29) 테이블. 운영 DB(MySQL 8)는 ddl-auto: validate라 앱을 띄우기 전에 먼저 만들어야 한다.
-- 엔티티 domain/notice/Notice.kt 매핑 그대로다. Hibernate MySQLDialect가 만드는 DDL을 뽑아 맞췄다.
-- MySQL에서 직접 실행해 보지는 않았다(Docker가 없어서). 실행 전에 한 번 확인해 주세요.
-- users 테이블은 이미 있다고 본다.
--
-- 계약:
-- - author_id FK는 ON DELETE SET NULL이어야 한다. 작성자 사용자를 지워도 공지는 남고 author가 null로 나온다.
--   빠뜨려도 ddl-auto: validate는 FK 동작을 검사하지 않아 앱은 뜨고, 공지를 쓴 사용자를 지울 때 무결성 오류가 난다.
--   FK 이름은 앱이 쓰지 않으므로 바꿔도 된다.
-- - created_at·updated_at은 서울 시간(Clock 빈)으로 초까지만 저장한다. updated_at은 수정된 적이 없으면 null.
-- - title 100자, content 4000자는 요청 검증(NoticeRequest)과 같은 길이다. 늘리려면 둘을 같이 고친다.
--   utf8mb4에서도 한 행이 MySQL 행 크기 제한(65,535바이트) 안에 들어간다.

create table notices (
    id         bigint        not null auto_increment,
    title      varchar(100)  not null,
    content    varchar(4000) not null,
    -- 작성자. 사용자를 지우면 null이 된다.
    author_id  bigint,
    created_at datetime(6)   not null,
    updated_at datetime(6),
    primary key (id),
    constraint fk_notices_author foreign key (author_id) references users (id) on delete set null
) engine = InnoDB default charset = utf8mb4;
