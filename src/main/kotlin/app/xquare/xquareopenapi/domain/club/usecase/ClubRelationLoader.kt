package app.xquare.xquareopenapi.domain.club.usecase

import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Component

class ClubRelations(
    val members: List<Student>,
    val manager: Student,
    val teacher: Teacher?,
    // 수정할 동아리의 기존 구성원. 새 구성원과 같이 잠갔다. 등록이면 비어 있다.
    val previousMembers: List<Student>,
)

// 동아리 등록·수정이 같이 쓴다. 요청의 팀장·구성원·담당 교직원을 읽고 다른 동아리와 겹치는지 검사한다.
@Component
class ClubRelationLoader(
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
) {

    // clubId는 수정할 동아리 id다. 등록이면 null. 호출하는 쪽의 트랜잭션 안에서 불러야 학생 행 잠금이 유지된다.
    // previousMemberIds는 수정할 동아리의 기존 구성원 id다. 소속을 풀 학생이라 새 구성원과 같이 잠근다.
    fun load(request: ClubRequest, clubId: Long?, previousMemberIds: Collection<Long> = emptyList()): ClubRelations {
        if (request.manager !in request.member) throw XquareException(ErrorCode.MANAGER_NOT_IN_MEMBER)
        // 같은 id를 여러 번 보내면 한 번으로 친다.
        val memberIds = request.member.toSet()
        // 다른 동아리 등록·수정이 같은 학생을 동시에 넣지 못하게 학생 행을 잠근다.
        // 기존 구성원을 따로 늦게 잠그면 잠금 순서가 다른 요청과 엇갈려 데드락이 나므로, 한 번에 id 순으로 잠근다(#26 리뷰).
        // 이 규칙으로 막는 건 동아리 API끼리의 데드락이다. 학생·교직원 삭제와의 순서 차이는 UpdateClubUseCase 주석 참고.
        val locked = studentRepository.findAllByIdInForUpdate(memberIds + previousMemberIds)
        val members = locked.filter { it.id in memberIds }
        if (members.size != memberIds.size) throw XquareException(ErrorCode.STUDENT_NOT_FOUND)
        val teacher = if (request.teacher == null) {
            null
        } else {
            // 학생처럼 잠그고 읽어서, 읽은 뒤 교직원 삭제가 먼저 커밋되지 못하게 한다(#26 리뷰).
            teacherRepository.findByIdForUpdate(request.teacher) ?: throw XquareException(ErrorCode.TEACHER_NOT_FOUND)
        }
        if (clubRepository.existsByRoomExcept(request.room, clubId)) throw XquareException(ErrorCode.ROOM_ALREADY_IN_USE)
        if (members.any { it.belongsToOtherClub(clubId) }) throw XquareException(ErrorCode.STUDENT_ALREADY_IN_CLUB)
        if (request.teacher != null && clubRepository.existsByTeacherExcept(request.teacher, clubId)) {
            throw XquareException(ErrorCode.TEACHER_ALREADY_IN_CLUB)
        }
        return ClubRelations(
            members,
            members.single { it.id == request.manager },
            teacher,
            locked.filter { it.id in previousMemberIds },
        )
    }
}
