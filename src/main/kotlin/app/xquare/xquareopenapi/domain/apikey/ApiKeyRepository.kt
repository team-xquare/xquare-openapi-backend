package app.xquare.xquareopenapi.domain.apikey

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun findByUserId(userId: Long): ApiKey?

    fun findByKeyHash(keyHash: String): ApiKey?

    // 있는 행만 id로 잠근다. 없는 사용자 키를 user_id로 잠그면 MySQL이 gap lock을 잡아 동시 첫 신청끼리 교착될 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ApiKey k where k.id = :id")
    fun findByIdForUpdate(id: Long): ApiKey?
}
