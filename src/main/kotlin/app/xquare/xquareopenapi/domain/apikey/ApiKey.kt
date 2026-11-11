package app.xquare.xquareopenapi.domain.apikey

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// 활용신청은 자동 승인이라 신청 내용과 발급한 키를 한 행에 둔다. 키 원문은 저장하지 않고 해시만 둔다.
@Entity
// 제약 이름은 동시 신청으로 사용자 키가 겹쳤는지 구분하는 데 쓰므로 직접 정한다.
@Table(
    name = "api_keys",
    uniqueConstraints = [
        UniqueConstraint(name = ApiKey.USER_ID_UNIQUE_CONSTRAINT, columnNames = ["user_id"]),
        UniqueConstraint(name = ApiKey.KEY_HASH_UNIQUE_CONSTRAINT, columnNames = ["key_hash"]),
    ],
)
class ApiKey(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 30)
    val serviceName: String,

    @Column(nullable = false, length = 200)
    val purpose: String,

    keyHash: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "key_hash", nullable = false, length = 64)
    var keyHash: String = keyHash
        protected set

    // 해시를 바꾸면 이전 키로는 더 이상 찾을 수 없어서 바로 폐기된다.
    fun reissue(keyHash: String) {
        this.keyHash = keyHash
    }

    companion object {
        const val USER_ID_UNIQUE_CONSTRAINT = "uk_api_keys_user_id"
        const val KEY_HASH_UNIQUE_CONSTRAINT = "uk_api_keys_key_hash"
    }
}
