package app.xquare.xquareopenapi.domain.notice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface NoticeRepository : JpaRepository<Notice, Long> {

    // 응답에 작성자 이름을 넣으므로 같이 읽어 공지마다 한 번씩 더 조회하지 않게 한다.
    @EntityGraph(attributePaths = ["author"])
    @Query("select n from Notice n")
    fun findPage(pageable: Pageable): Page<Notice>

    @EntityGraph(attributePaths = ["author"])
    @Query("select n from Notice n where n.id = :id")
    fun findWithAuthorById(id: Long): Notice?

    // 조회 후 고치면 그 사이에 공지가 지워졌을 때 0행 수정으로 500이 난다. 한 번에 고치고 고친 행 수를 돌려준다.
    // 돌려주는 값은 조건에 맞은 행 수다(Connector/J 기본값). DB_URL에 useAffectedRows=true를 붙이면 안 된다(UpdateNoticeUseCase 참고).
    @Modifying
    @Query("update Notice n set n.title = :title, n.content = :content, n.updatedAt = :updatedAt where n.id = :id")
    fun updateByIdReturningCount(id: Long, title: String, content: String, updatedAt: LocalDateTime): Int

    // 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 커밋하며 0행 삭제로 500이 난다. 한 번에 지우고 지운 행 수를 돌려준다.
    @Modifying
    @Query("delete from Notice n where n.id = :id")
    fun deleteByIdReturningCount(id: Long): Int
}
