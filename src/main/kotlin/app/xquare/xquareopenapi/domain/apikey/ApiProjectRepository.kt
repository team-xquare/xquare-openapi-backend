package app.xquare.xquareopenapi.domain.apikey

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ApiProjectRepository : JpaRepository<ApiProject, Long> {
    fun findByKeyHash(keyHash: String): ApiProject?

    fun countByUserId(userId: Long): Long

    fun existsByUserIdAndName(userId: Long, name: String): Boolean

    // id가 늘어나는 순서가 만든 순서다.
    fun findAllByUserIdOrderByIdAsc(userId: Long): List<ApiProject>

    // 있는 행만 id로 잠근다. 같은 프로젝트의 분야 신청·승인·재발급·삭제를 한 줄로 세운다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ApiProject p where p.id = :id")
    fun findByIdForUpdate(id: Long): ApiProject?
}
