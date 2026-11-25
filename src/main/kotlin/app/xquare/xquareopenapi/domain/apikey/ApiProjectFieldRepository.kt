package app.xquare.xquareopenapi.domain.apikey

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ApiProjectFieldRepository : JpaRepository<ApiProjectField, Long> {
    // id가 늘어나는 순서가 처음 신청한 순서다.
    fun findAllByProjectIdOrderByIdAsc(projectId: Long): List<ApiProjectField>

    fun findAllByProjectIdInOrderByIdAsc(projectIds: Collection<Long>): List<ApiProjectField>

    // 관리자 신청 목록. status가 null이면 거르지 않는다.
    @Query("select f from ApiProjectField f where (:status is null or f.status = :status)")
    fun search(status: ApplicationStatus?, pageable: Pageable): Page<ApiProjectField>

    @Modifying
    @Query("delete from ApiProjectField f where f.projectId = :projectId")
    fun deleteAllByProjectId(projectId: Long): Int

    @Query("select f.field from ApiProjectField f where f.projectId = :projectId and f.status = :status")
    fun findFieldsByProjectIdAndStatus(projectId: Long, status: ApplicationStatus): List<ApiKeyField>
}
