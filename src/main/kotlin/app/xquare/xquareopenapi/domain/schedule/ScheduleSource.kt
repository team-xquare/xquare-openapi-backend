package app.xquare.xquareopenapi.domain.schedule

// 일정 출처. NEIS는 나이스 동기화가 만든 일정이라 관리자가 수정·삭제할 수 없고, SCHOOL은 관리자가 등록한 일정이라 동기화가 건드리지 않는다.
enum class ScheduleSource {
    NEIS,
    SCHOOL,
}
