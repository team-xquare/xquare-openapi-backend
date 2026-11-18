package app.xquare.xquareopenapi.domain.apikey

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ApiKeyApplicationRepository : JpaRepository<ApiKeyApplication, Long> {
    fun existsByApiKeyIdAndField(apiKeyId: Long, field: ApiKeyField): Boolean

    @Query("select a.field from ApiKeyApplication a where a.apiKeyId = :apiKeyId")
    fun findFieldsByApiKeyId(apiKeyId: Long): List<ApiKeyField>
}
