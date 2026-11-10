package app.xquare.xquareopenapi.domain.notice

import app.xquare.xquareopenapi.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(name = "notices")
class Notice(
    title: String,
    content: String,
    author: User,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    var title: String = title
        protected set

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    var content: String = content
        protected set

    // 작성자. 수정한 관리자로 바뀌지 않는다. 작성자 사용자를 지우면 공지는 남고 DB가 null로 바꾼다(ON DELETE SET NULL).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var author: User? = author
        protected set

    @Column(nullable = false)
    var createdAt: LocalDateTime = createdAt
        protected set

    // 수정된 적이 없으면 null이다.
    @Column
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        const val TITLE_MAX_LENGTH = 100
        const val CONTENT_MAX_LENGTH = 4000
    }
}
