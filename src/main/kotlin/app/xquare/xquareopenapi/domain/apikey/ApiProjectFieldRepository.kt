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

    // 관리자 신청 목록. status·type이 null이면 거르지 않고, fields(분야·단계 필터를 합친 결과, 비지 않음)에 든 분야만 본다.
    // keywordPattern은 escapeLike()를 거친 부분 일치 패턴이고, 프로젝트 이름이나 신청자 이름에 들어 있으면 고른다. null이면 거르지 않는다.
    // 신청자 행은 FK가 없어 지워졌을 수 있으므로 left join이다.
    @Query(
        """
        select f from ApiProjectField f
        join ApiProject p on p.id = f.projectId
        left join User u on u.id = p.userId
        where (:status is null or f.status = :status)
          and (:type is null or f.applicationType = :type)
          and f.field in :fields
          and (:keywordPattern is null or p.name like :keywordPattern escape '!' or u.name like :keywordPattern escape '!')
        """,
    )
    fun search(
        status: ApplicationStatus?,
        type: ApplicationType?,
        fields: Collection<ApiKeyField>,
        keywordPattern: String?,
        pageable: Pageable,
    ): Page<ApiProjectField>

    @Modifying
    @Query("delete from ApiProjectField f where f.projectId = :projectId")
    fun deleteAllByProjectId(projectId: Long): Int

    @Query("select f.field from ApiProjectField f where f.projectId = :projectId and f.status = :status")
    fun findFieldsByProjectIdAndStatus(projectId: Long, status: ApplicationStatus): List<ApiKeyField>
}
