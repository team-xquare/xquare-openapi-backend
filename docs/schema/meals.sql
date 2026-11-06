-- 급식(#21) 테이블. 운영 DB(MySQL 8)는 ddl-auto: validate라 앱을 띄우기 전에 먼저 만들어야 한다.
-- 엔티티 domain/meal/Meal.kt 매핑 그대로다. Hibernate MySQLDialect가 만드는 DDL을 뽑아 맞췄다.
-- MySQL에서 직접 실행해 보지는 않았다(Docker가 없어서). 실행 전에 한 번 확인해 주세요.
--
-- 계약:
-- - unique 제약 이름 uk_meals_meal_date는 동시 등록·수정이 같은 날짜로 겹쳤을 때 409 MEAL_ALREADY_EXISTS로 바꾸는 데 쓴다.
--   이름이 다르면 구분하지 못하고 500이 난다.
-- - breakfast·lunch·dinner는 메뉴 목록을 JSON 배열 문자열로 저장한다(메뉴 50자 × 20개가 들어가는 길이).
-- - edited_by_admin이 1이면 관리자가 등록·수정한 급식이라 나이스 동기화가 덮어쓰지 않는다.

create table meals (
    id              bigint        not null auto_increment,
    meal_date       date          not null,
    breakfast       varchar(3000) not null,
    lunch           varchar(3000) not null,
    dinner          varchar(3000) not null,
    edited_by_admin bit           not null,
    primary key (id),
    constraint uk_meals_meal_date unique (meal_date)
) engine = InnoDB default charset = utf8mb4;
