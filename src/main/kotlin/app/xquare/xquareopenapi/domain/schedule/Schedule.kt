package app.xquare.xquareopenapi.domain.schedule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

// 나이스 일정과 학교 일정을 한 테이블에 두고 source로 구분한다.
// 나이스는 행 id를 주지 않아 (날짜, 제목)으로 같은 일정인지 판단한다(SyncNeisSchedulesUseCase).
@Entity
@Table(
    name = "schedules",
    indexes = [Index(name = "idx_schedules_source_date", columnList = "source, schedule_date")],
)
class Schedule(
    date: LocalDate,
    title: String,
    content: String?,
    source: ScheduleSource,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "schedule_date", nullable = false)
    var date: LocalDate = date
        protected set

    // 관리자 입력은 50자까지지만 나이스 행사명 길이는 정해져 있지 않아 컬럼은 넉넉히 둔다.
    @Column(nullable = false, length = TITLE_COLUMN_LENGTH)
    var title: String = title
        protected set

    @Column(length = CONTENT_COLUMN_LENGTH)
    var content: String? = content
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val source: ScheduleSource = source

    // 관리자가 학교 일정을 통째로 바꾼다. 나이스 일정에는 쓰지 않는다(UpdateScheduleUseCase가 막는다).
    fun update(date: LocalDate, title: String, content: String?) {
        this.date = date
        this.title = title
        this.content = content
    }

    // 나이스 동기화가 같은 일정(날짜·제목이 같음)의 내용을 최신으로 바꾼다.
    fun updateFromNeis(content: String?) {
        this.content = content
    }

    companion object {
        const val TITLE_COLUMN_LENGTH = 100
        const val CONTENT_COLUMN_LENGTH = 1000
    }
}
