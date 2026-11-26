-- 프로젝트 서비스 URL(선택 입력). 2026-11-28-project-structure.sql 다음에 적용한다(ddl-auto: validate).
alter table api_projects add column service_url varchar(255) null after name;
