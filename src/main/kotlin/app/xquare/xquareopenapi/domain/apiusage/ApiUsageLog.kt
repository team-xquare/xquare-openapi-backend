package app.xquare.xquareopenapi.domain.apiusage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "api_usage_logs",
    indexes = [
        Index(name = "ix_api_usage_logs_occurred_id", columnList = "occurred_at, id"),
        Index(name = "ix_api_usage_logs_project_occurred", columnList = "project_id, occurred_at"),
    ],
)
class ApiUsageLog(
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime,
    @Column(name = "http_method", nullable = false, length = 8)
    val method: String,
    @Column(nullable = false, length = 160)
    val route: String,
    @Column(name = "http_status", nullable = false)
    val status: Int,
    @Column(name = "duration_ms", nullable = false)
    val durationMs: Long,
    // 아래는 모두 없을 수 있다. 프로젝트·사용자는 FK 없이 id만 남겨 삭제돼도 기록은 유지한다.
    @Column(name = "project_id")
    val projectId: Long? = null,
    @Column(name = "user_id")
    val userId: Long? = null,
    @Column(name = "key_prefix", length = KEY_PREFIX_MAX_LENGTH)
    val keyPrefix: String? = null,
    @Column(name = "error_code", length = ERROR_CODE_MAX_LENGTH)
    val errorCode: String? = null,
    // UsageAccessLevel 이름. 다른 단계 값이 생겨도 스키마를 바꾸지 않도록 문자열로 둔다.
    @Column(name = "access_level", length = ACCESS_LEVEL_MAX_LENGTH)
    val accessLevel: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        // "xq_" 접두사 + 무작위 8글자
        const val KEY_PREFIX_MAX_LENGTH = 11
        const val ERROR_CODE_MAX_LENGTH = 40
        const val ACCESS_LEVEL_MAX_LENGTH = 10
    }
}
