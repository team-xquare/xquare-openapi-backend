package app.xquare.xquareopenapi.domain.club

import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.teacher.Teacher
import org.springframework.data.jpa.domain.Specification

// 동아리 검색 조건. 구성원 이름 개수만큼 조건이 늘어나서 JPQL 대신 Specification으로 만든다.
object ClubSearchSpecification {

    // name, members는 escapeLike()를 거친 값이어야 한다. 조건은 모두 AND로 묶는다.
    fun of(name: String?, members: List<String>, room: ClubRoom?, teacher: String?): Specification<Club> {
        val conditions = mutableListOf<Specification<Club>>()
        if (name != null) conditions.add(nameContains(name))
        for (member in members) conditions.add(hasMemberNameContaining(member))
        if (room != null) conditions.add(roomIs(room))
        if (teacher != null) conditions.add(teacherNameIs(teacher))
        return Specification.allOf(conditions)
    }

    private fun nameContains(name: String) = Specification<Club> { root, _, cb ->
        cb.like(root.get("name"), "%$name%", '!')
    }

    // 구성원 중 이름이 member를 포함하는 학생이 있는 동아리. 여러 명을 주면 모두 있는 동아리만 남는다.
    private fun hasMemberNameContaining(member: String) = Specification<Club> { root, query, cb ->
        val subquery = query.subquery(Long::class.javaObjectType)
        val student = subquery.from(Student::class.java)
        subquery.select(student.get("id")).where(
            cb.equal(student.get<Club>("club"), root),
            cb.like(student.get("name"), "%$member%", '!'),
        )
        cb.exists(subquery)
    }

    private fun roomIs(room: ClubRoom) = Specification<Club> { root, _, cb ->
        cb.equal(root.get<ClubRoom>("room"), room)
    }

    // 교직원 검색처럼 이름은 정확히 일치하는 것만 찾는다.
    private fun teacherNameIs(teacher: String) = Specification<Club> { root, _, cb ->
        cb.equal(root.get<Teacher>("teacher").get<String>("name"), teacher)
    }
}
