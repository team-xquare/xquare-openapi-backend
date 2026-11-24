package app.xquare.xquareopenapi.domain.apikey

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

// 프로젝트 하나에 키 하나. 분야별 신청·승인 상태는 ApiProjectField에 두고, 여기에는 키 해시만 둔다. 키 원문은 저장하지 않는다.
// 키는 프로젝트의 분야가 처음 승인될 때 발급되므로 그 전에는 key_hash가 null이다.
@Entity
// 제약 이름은 같은 이름의 프로젝트가 겹쳤는지 구분하는 계약이라 직접 정한다.
@Table(
    name = "api_projects",
    uniqueConstraints = [
        UniqueConstraint(name = ApiProject.USER_ID_NAME_UNIQUE_CONSTRAINT, columnNames = ["user_id", "name"]),
        UniqueConstraint(name = ApiProject.KEY_HASH_UNIQUE_CONSTRAINT, columnNames = ["key_hash"]),
    ],
)
class ApiProject(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 30)
    val name: String,

    @Column(nullable = false, length = 200)
    val purpose: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "key_hash", length = 64)
    var keyHash: String? = null
        protected set

    // 지금 키 값을 발급한 시각. 재발급하면 바뀐다.
    @Column(name = "issued_at")
    var issuedAt: LocalDateTime? = null
        protected set

    // 만료 시각. null이면 만료되지 않는다.
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
        protected set

    val keyIssued: Boolean get() = keyHash != null

    // 새 키를 만들어 해시만 남기고 원문을 돌려준다. 이미 키가 있으면 해시가 바뀌어 이전 키로는 더 이상 찾을 수 없다.
    fun issueKey(now: LocalDateTime): String {
        val rawKey = ApiKeySecret.generate()
        keyHash = ApiKeySecret.hash(rawKey)
        issuedAt = now
        return rawKey
    }

    companion object {
        const val USER_ID_NAME_UNIQUE_CONSTRAINT = "uk_api_projects_user_id_name"
        const val KEY_HASH_UNIQUE_CONSTRAINT = "uk_api_projects_key_hash"

        // 사용자당 프로젝트 개수 상한
        const val MAX_PER_USER = 5
    }
}
