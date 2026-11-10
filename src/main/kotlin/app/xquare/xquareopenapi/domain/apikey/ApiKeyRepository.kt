package app.xquare.xquareopenapi.domain.apikey

import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun existsByUserId(userId: Long): Boolean

    fun findByUserId(userId: Long): ApiKey?

    fun findByKeyHash(keyHash: String): ApiKey?
}
