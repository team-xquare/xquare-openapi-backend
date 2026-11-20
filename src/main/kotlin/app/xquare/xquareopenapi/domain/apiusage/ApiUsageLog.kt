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
@Table(name = "api_usage_logs", indexes = [Index(name = "ix_api_usage_logs_occurred_id", columnList = "occurred_at, id")])
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
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
