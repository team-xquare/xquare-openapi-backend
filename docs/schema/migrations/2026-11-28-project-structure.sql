-- 프로젝트 구조 변경(빈 프로젝트, 분야별 사용 목적, 신청 구분, 급식 MEAL 분야). 새 버전 앱을 띄우기 전에 적용한다(ddl-auto: validate).
-- 1) 분야별 사용 목적·구분 칼럼을 더하고, 기존 행은 프로젝트의 목적을 그대로 옮기고 구분은 FIRST로 채운다.
--    (기존 신청이 첫 신청이었는지 추가였는지는 남아 있지 않아 모두 FIRST로 본다.)
alter table api_project_fields
    add column purpose varchar(200) null after field,
    add column application_type enum ('ADDITIONAL','FIRST') null after purpose;

update api_project_fields f
    join api_projects p on p.id = f.project_id
set f.purpose = p.purpose,
    f.application_type = 'FIRST';

alter table api_project_fields
    modify column purpose varchar(200) not null,
    modify column application_type enum ('ADDITIONAL','FIRST') not null,
    modify column field enum ('CLUB','MEAL','SCHEDULE','STUDENT','STUDY_SUPERVISOR','TEACHER') not null;

-- 2) 프로젝트의 사용 목적은 분야로 옮겼으므로 지운다.
alter table api_projects drop column purpose;
