package app.xquare.xquareopenapi.domain.apikey

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ApiProjectFieldRepository : JpaRepository<ApiProjectField, Long> {
    // id가 늘어나는 순서가 처음 신청한 순서다.
    fun findAllByProjectIdOrderByIdAsc(projectId: Long): List<ApiProjectField>

    @Query("select f.field from ApiProjectField f where f.projectId = :projectId and f.status = :status")
    fun findFieldsByProjectIdAndStatus(projectId: Long, status: ApplicationStatus): List<ApiKeyField>
}
