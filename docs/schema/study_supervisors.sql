-- 자습 감독(#27) 테이블. 운영 DB(MySQL 8)는 ddl-auto: validate라 앱을 띄우기 전에 먼저 만들어야 한다.
-- 엔티티 domain/studysupervisor/StudySupervisor.kt, StudySupervisorAssignment.kt 매핑 그대로다. Hibernate MySQLDialect가 만드는 DDL을 뽑아 맞췄다.
-- MySQL에서 직접 실행해 보지는 않았다(Docker가 없어서). 실행 전에 한 번 확인해 주세요.
-- 실행 순서: study_supervisor_assignments가 teachers를 참조하므로 teachers 테이블(#19)이 먼저 있어야 한다.
-- 이 파일 안에서는 study_supervisors를 먼저 만든다.
--
-- 계약:
-- - FK 2개는 모두 ON DELETE CASCADE여야 한다. 자습 감독 정보를 지우면 그 날짜의 배정이, 교직원을 지우면 그 교직원의 배정만 DB가 지운다.
--   빠뜨려도 ddl-auto: validate는 FK 동작을 검사하지 않아 앱은 뜨고, 자습 감독 삭제나 감독 교직원 삭제에서 무결성 오류로 500이 난다.
--   FK 이름은 앱이 쓰지 않으므로 바꿔도 된다.
-- - unique 제약 이름 uk_study_supervisors_supervision_date는 동시 등록·수정으로 날짜가 겹쳤을 때
--   409 SUPERVISION_ALREADY_EXISTS로 바꾸는 데 쓴다. 이름이 다르면 구분하지 못하고 500이 난다.
-- - uk_study_supervisor_assignments_floor는 한 날짜에 같은 층 배정이 두 개 생기지 않게 막는다(요청 검증이 먼저 막는다).
-- - floor_number는 요청 검증이 2~4층만 받는다(StudySupervisor.FLOORS). DB에는 범위 제약이 없다.

create table study_supervisors (
    id               bigint not null auto_increment,
    supervision_date date   not null,
    primary key (id),
    constraint uk_study_supervisors_supervision_date unique (supervision_date)
) engine = InnoDB;

create table study_supervisor_assignments (
    id                  bigint  not null auto_increment,
    -- 자습 감독 정보. 정보를 지우면 배정도 지워진다.
    study_supervisor_id bigint  not null,
    floor_number        integer not null,
    -- 감독 교직원. 교직원을 지우면 이 배정만 지워지고 그 층은 teacher가 null로 나온다.
    teacher_id          bigint  not null,
    primary key (id),
    constraint uk_study_supervisor_assignments_floor unique (study_supervisor_id, floor_number),
    constraint fk_study_supervisor_assignments_study_supervisor foreign key (study_supervisor_id) references study_supervisors (id) on delete cascade,
    constraint fk_study_supervisor_assignments_teacher foreign key (teacher_id) references teachers (id) on delete cascade
) engine = InnoDB;
