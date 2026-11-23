-- 학사 일정(OPN-68, #43) 테이블. 운영 DB(MySQL 8)는 ddl-auto: validate라 앱을 띄우기 전에 먼저 만들어야 한다.
-- 엔티티 domain/schedule/Schedule.kt 매핑 그대로다. MySQL 8.4(Testcontainers)에서 Hibernate가 만든 SHOW CREATE TABLE과
-- 컬럼·타입·인덱스가 같고, 이 DDL로 만든 테이블이 Hibernate 스키마 검증(validate)을 통과하는 것을 확인했다.
--
-- 계약:
-- - source는 NEIS(나이스 동기화)·SCHOOL(관리자 등록)이다. 값을 더하면 ScheduleSource와 함께 ALTER TABLE로 enum 목록도 맞춘다.
-- - title 100자, content 1000자는 Schedule.TITLE_COLUMN_LENGTH·CONTENT_COLUMN_LENGTH다. 나이스 값은 이 길이로 잘라 저장한다.
-- - idx_schedules_source_date는 동기화가 그 달의 NEIS 행을 읽을 때 쓴다.

create table schedules (
    id            bigint                  not null auto_increment,
    schedule_date date                    not null,
    title         varchar(100)            not null,
    content       varchar(1000)           null,
    source        enum ('NEIS', 'SCHOOL') not null,
    primary key (id),
    index idx_schedules_source_date (source, schedule_date)
) engine = InnoDB default charset = utf8mb4;
