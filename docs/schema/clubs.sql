-- 동아리(#25) 테이블과 students.club_id. 운영 DB(MySQL 8)는 ddl-auto: validate라 앱을 띄우기 전에 먼저 반영해야 한다.
-- 엔티티 domain/club/Club.kt, domain/student/Student.kt 매핑 그대로다. Hibernate MySQLDialect가 만드는 DDL을 뽑아 맞췄다.
-- MySQL에서 직접 실행해 보지는 않았다(Docker가 없어서). 실행 전에 한 번 확인해 주세요.
-- students·teachers 테이블은 이미 있다고 보고(#18, #19), students에는 컬럼과 FK만 추가한다.
--
-- 계약:
-- - FK 3개는 모두 ON DELETE SET NULL이어야 한다. 학생·교직원·동아리를 JPQL로 지울 때 연관을 DB가 풀어 준다.
--   빠뜨려도 ddl-auto: validate는 FK 동작을 검사하지 않아 앱은 뜨고, 첫 삭제에서 무결성 오류로 500이 난다.
-- - FK 이름 fk_clubs_manager, fk_clubs_teacher도 계약이다. 동아리 저장이 그 사이 지워진 팀장·교직원 때문에 외래 키에 걸리면
--   404 STUDENT_NOT_FOUND, TEACHER_NOT_FOUND로 바꾸는 데 쓴다(#26 리뷰). 이름이 다르면 500이 난다. fk_students_club 이름은 앱이 쓰지 않는다.
-- - unique 제약 이름 uk_clubs_room, uk_clubs_teacher는 동시 등록·수정이 제약에 걸렸을 때
--   400 ROOM_ALREADY_IN_USE, 409 TEACHER_ALREADY_IN_CLUB으로 바꾸는 데 쓴다. 이름이 다르면 구분하지 못하고 500이 난다.
-- - room은 Hibernate가 MySQL에서 만드는 그대로 enum이다. ClubRoom 값을 더하거나 바꾸면 이 목록도 ALTER로 맞춰야 한다.
--   저장된 값을 enum에서 지우거나 이름을 바꾸면 그 행을 읽을 때 500이 나므로, 운영 데이터가 생기기 전에 실 목록을 확정한다.

create table clubs (
    id         bigint       not null auto_increment,
    name       varchar(20)  not null,
    logo       varchar(255) not null,
    room       enum ('ROOM_1_1','ROOM_1_2','ROOM_1_3','ROOM_1_4','ROOM_1_5','ROOM_1_6','ROOM_1_7','ROOM_1_8','ROOM_1_9',
                     'ROOM_2_1','ROOM_2_2','ROOM_2_3','ROOM_2_4','ROOM_2_5','ROOM_2_6','ROOM_2_7','ROOM_2_8','ROOM_2_9',
                     'ROOM_3_1','ROOM_3_2','ROOM_3_3','ROOM_3_4','ROOM_3_5','ROOM_3_6','ROOM_3_7','ROOM_3_8','ROOM_3_9',
                     'ROOM_4_1','ROOM_4_2','ROOM_4_3','ROOM_4_4','ROOM_4_5','ROOM_4_6','ROOM_4_7','ROOM_4_8','ROOM_4_9',
                     'ROOM_5_1','ROOM_5_2','ROOM_5_3','ROOM_5_4','ROOM_5_5','ROOM_5_6','ROOM_5_7','ROOM_5_8','ROOM_5_9') not null,
    -- 팀장 학생. 학생을 지우면 null이 되고, 관리자가 수정으로 새 팀장을 정한다.
    manager_id bigint,
    -- 담당 교직원. 없을 수 있다.
    teacher_id bigint,
    primary key (id),
    constraint uk_clubs_room unique (room),
    constraint uk_clubs_teacher unique (teacher_id),
    constraint fk_clubs_manager foreign key (manager_id) references students (id) on delete set null,
    constraint fk_clubs_teacher foreign key (teacher_id) references teachers (id) on delete set null
) engine = InnoDB default charset = utf8mb4;

-- 학생의 소속 동아리. 동아리를 지우면 null이 된다.
alter table students add column club_id bigint;
alter table students add constraint fk_students_club foreign key (club_id) references clubs (id) on delete set null;
