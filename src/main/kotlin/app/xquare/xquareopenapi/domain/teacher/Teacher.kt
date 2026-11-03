package app.xquare.xquareopenapi.domain.teacher

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "teachers")
class Teacher(
    name: String,
    grade: Int?,
    classNum: Int?,
    subject: TeacherSubject,
    office: String,
    isManager: Boolean,
    position: TeacherPosition,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, length = 10)
    var name: String = name
        protected set

    // 담임이 아니면 grade, classNum 모두 null이다.
    var grade: Int? = grade
        protected set

    var classNum: Int? = classNum
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var subject: TeacherSubject = subject
        protected set

    @Column(nullable = false, length = 30)
    var office: String = office
        protected set

    @Column(nullable = false)
    var isManager: Boolean = isManager
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var position: TeacherPosition = position
        protected set

    fun update(
        name: String,
        grade: Int?,
        classNum: Int?,
        subject: TeacherSubject,
        office: String,
        isManager: Boolean,
        position: TeacherPosition,
    ) {
        this.name = name
        this.grade = grade
        this.classNum = classNum
        this.subject = subject
        this.office = office
        this.isManager = isManager
        this.position = position
    }
}
