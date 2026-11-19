package app.xquare.xquareopenapi.domain.apikey

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ApiKeyApplicationRepository : JpaRepository<ApiKeyApplication, Long> {
    fun existsByApiKeyIdAndField(apiKeyId: Long, field: ApiKeyField): Boolean

    // id가 늘어나는 순서가 신청 순서다.
    fun findAllByApiKeyIdOrderByIdAsc(apiKeyId: Long): List<ApiKeyApplication>

    @Query("select a.field from ApiKeyApplication a where a.apiKeyId = :apiKeyId")
    fun findFieldsByApiKeyId(apiKeyId: Long): List<ApiKeyField>

    @Modifying
    @Query("delete from ApiKeyApplication a where a.apiKeyId = :apiKeyId")
    fun deleteAllByApiKeyId(apiKeyId: Long): Int
}
