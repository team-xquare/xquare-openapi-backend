package app.xquare.xquareopenapi.domain.apikey

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.time.ZoneId

// 활용신청 기록. 키 하나에 분야마다 한 행이 쌓이고, 서비스 이름·사용 목적은 신청마다 따로 둔다.
@Entity
// 제약 이름은 같은 분야를 두 번 신청했는지 구분하는 계약이라 직접 정한다.
@Table(
    name = "api_key_applications",
    uniqueConstraints = [
        UniqueConstraint(name = ApiKeyApplication.FIELD_UNIQUE_CONSTRAINT, columnNames = ["api_key_id", "field"]),
    ],
)
class ApiKeyApplication(
    @Column(name = "api_key_id", nullable = false)
    val apiKeyId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 30)
    val field: ApiKeyField,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: ApiKeyApplicationType,

    @Column(nullable = false, length = 30)
    val serviceName: String,

    @Column(nullable = false, length = 200)
    val purpose: String,

    // 서버 시간대와 상관없이 한국 시간으로 남긴다.
    @Column(nullable = false)
    val appliedAt: LocalDateTime = LocalDateTime.now(SEOUL),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        const val FIELD_UNIQUE_CONSTRAINT = "uk_api_key_applications_api_key_id_field"
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
