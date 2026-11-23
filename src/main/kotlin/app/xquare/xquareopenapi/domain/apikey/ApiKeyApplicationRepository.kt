package app.xquare.xquareopenapi.domain.apikey

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ApiKeyApplicationRepository : JpaRepository<ApiKeyApplication, Long> {
    fun existsByApiKeyIdAndField(apiKeyId: Long, field: ApiKeyField): Boolean

    // id가 늘어나는 순서가 신청 순서다.
    fun findAllByApiKeyIdOrderByIdAsc(apiKeyId: Long): List<ApiKeyApplication>

    @Query("select a.field from ApiKeyApplication a where a.apiKeyId = :apiKeyId")
    fun findFieldsByApiKeyId(apiKeyId: Long): List<ApiKeyField>

    // 관리자 목록. 조건이 null이면 거르지 않고, 둘 다 주면 AND로 묶는다.
    @Query(
        """
        select a from ApiKeyApplication a
        where (:field is null or a.field = :field)
          and (:type is null or a.type = :type)
        """,
    )
    fun search(field: ApiKeyField?, type: ApiKeyApplicationType?, pageable: Pageable): Page<ApiKeyApplication>

    @Modifying
    @Query("delete from ApiKeyApplication a where a.apiKeyId = :apiKeyId")
    fun deleteAllByApiKeyId(apiKeyId: Long): Int
}
