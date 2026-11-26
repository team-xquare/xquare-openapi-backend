package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.violates
import app.xquare.xquareopenapi.global.jpa.escapeLike
import org.springframework.data.domain.PageRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 409로 바꿀 때 쓰는 제약 이름과 잠금·조회 쿼리가 운영 DB인 MySQL에서도 같게 동작하는지 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApiProjectRepositoryMySqlTest @Autowired constructor(
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
) {

    @Test
    fun `같은 사용자의 프로젝트 이름이 겹치면 이름 제약 위반으로 구분한다`() {
        projectRepository.saveAndFlush(project(2L, "급식 알리미"))

        val e = assertFailsWith<DataIntegrityViolationException> {
            projectRepository.saveAndFlush(project(2L, "급식 알리미"))
        }

        assertTrue(e.violates(ApiProject.USER_ID_NAME_UNIQUE_CONSTRAINT))
        assertFalse(e.violates(ApiProjectField.FIELD_UNIQUE_CONSTRAINT))
    }

    @Test
    fun `키가 없는 프로젝트는 여러 개여도 키 제약에 걸리지 않는다`() {
        projectRepository.saveAndFlush(project(2L, "하나"))
        projectRepository.saveAndFlush(project(2L, "둘"))

        assertEquals(2, projectRepository.countByUserId(2L))
    }

    @Test
    fun `같은 프로젝트에 같은 분야를 두 번 넣으면 분야 제약 위반으로 구분한다`() {
        val project = projectRepository.saveAndFlush(project(2L, "급식 알리미"))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.CLUB, "테스트 목적", ApplicationType.FIRST, NOW))

        val e = assertFailsWith<DataIntegrityViolationException> {
            fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.CLUB, "테스트 목적", ApplicationType.FIRST, NOW))
        }

        assertTrue(e.violates(ApiProjectField.FIELD_UNIQUE_CONSTRAINT))
        assertFalse(e.violates(ApiProject.USER_ID_NAME_UNIQUE_CONSTRAINT))
    }

    @Test
    fun `프로젝트 잠금 조회는 있는 프로젝트를 돌려주고 없는 id면 null이다`() {
        val project = projectRepository.saveAndFlush(project(2L, "급식 알리미"))

        assertEquals(project.id, projectRepository.findByIdForUpdate(project.id!!)?.id)
        assertNull(projectRepository.findByIdForUpdate(project.id!! + 1))
    }

    @Test
    fun `키 해시로 프로젝트를 찾고 상태별 분야만 읽는다`() {
        val project = project(2L, "급식 알리미")
        val rawKey = project.issueKey(NOW)
        projectRepository.saveAndFlush(project)
        val other = projectRepository.saveAndFlush(project(3L, "다른 프로젝트"))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.STUDY_SUPERVISOR, "테스트 목적", ApplicationType.FIRST, NOW))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.STUDENT, "테스트 목적", ApplicationType.FIRST, NOW))
        fieldRepository.saveAndFlush(ApiProjectField(other.id!!, ApiKeyField.SCHEDULE, "테스트 목적", ApplicationType.FIRST, NOW))

        assertEquals(project.id, projectRepository.findByKeyHash(ApiKeySecret.hash(rawKey))?.id)
        assertEquals(listOf(ApiKeyField.STUDY_SUPERVISOR), fieldRepository.findFieldsByProjectIdAndStatus(project.id!!, ApplicationStatus.APPROVED))
        assertEquals(listOf(ApiKeyField.STUDENT), fieldRepository.findFieldsByProjectIdAndStatus(project.id!!, ApplicationStatus.PENDING))
        assertEquals(
            listOf(ApiKeyField.STUDY_SUPERVISOR, ApiKeyField.STUDENT),
            fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!).map { it.field },
        )
    }

    @Test
    fun `사용자의 프로젝트를 만든 순서로 읽고 여러 프로젝트의 분야를 한 번에 읽으며 프로젝트 분야만 지운다`() {
        val first = projectRepository.saveAndFlush(project(2L, "하나"))
        projectRepository.saveAndFlush(project(3L, "남의 것"))
        val second = projectRepository.saveAndFlush(project(2L, "둘"))
        val a = fieldRepository.saveAndFlush(ApiProjectField(first.id!!, ApiKeyField.CLUB, "테스트 목적", ApplicationType.FIRST, NOW))
        val b = fieldRepository.saveAndFlush(ApiProjectField(second.id!!, ApiKeyField.STUDENT, "테스트 목적", ApplicationType.FIRST, NOW))
        val c = fieldRepository.saveAndFlush(ApiProjectField(first.id!!, ApiKeyField.TEACHER, "테스트 목적", ApplicationType.FIRST, NOW))

        assertEquals(listOf(first.id, second.id), projectRepository.findAllByUserIdOrderByIdAsc(2L).map { it.id })
        assertEquals(
            listOf(a.id, b.id, c.id),
            fieldRepository.findAllByProjectIdInOrderByIdAsc(listOf(first.id!!, second.id!!)).map { it.id },
        )
        assertEquals(2, fieldRepository.deleteAllByProjectId(first.id!!))
        assertEquals(listOf(b.id), fieldRepository.findAll().map { it.id })
    }

    @Test
    fun `관리자 목록은 상태, 구분, 분야, 키워드로 거르고 null이면 거르지 않는다`() {
        val junseo = userRepository.saveUser("junseo").id!!
        val project = projectRepository.saveAndFlush(project(junseo, "급식_알리미"))
        // 신청자 행이 없는 프로젝트도 목록에서 빠지지 않는다(left join).
        val orphan = projectRepository.saveAndFlush(project(999L, "급식x알리미"))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.SCHEDULE, "테스트 목적", ApplicationType.FIRST, NOW))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.STUDENT, "테스트 목적", ApplicationType.ADDITIONAL, NOW))
        fieldRepository.saveAndFlush(ApiProjectField(orphan.id!!, ApiKeyField.CLUB, "테스트 목적", ApplicationType.FIRST, NOW))
        val all = ApiKeyField.entries
        val page = PageRequest.of(0, 10)
        fun search(status: ApplicationStatus? = null, type: ApplicationType? = null, fields: List<ApiKeyField> = all, keyword: String? = null) =
            fieldRepository.search(status, type, fields, keyword?.let { "%${it.escapeLike()}%" }, page).content.map { it.field }.toSet()

        assertEquals(setOf(ApiKeyField.SCHEDULE, ApiKeyField.STUDENT, ApiKeyField.CLUB), search())
        assertEquals(setOf(ApiKeyField.STUDENT, ApiKeyField.CLUB), search(status = ApplicationStatus.PENDING))
        assertEquals(setOf(ApiKeyField.STUDENT), search(type = ApplicationType.ADDITIONAL))
        assertEquals(setOf(ApiKeyField.SCHEDULE), search(fields = listOf(ApiKeyField.SCHEDULE, ApiKeyField.MEAL)))
        // 밑줄은 와일드카드가 아니라 글자 그대로 찾는다.
        assertEquals(setOf(ApiKeyField.SCHEDULE, ApiKeyField.STUDENT), search(keyword = "급식_"))
        // 신청자 이름(김준서)으로도 찾는다.
        assertEquals(setOf(ApiKeyField.SCHEDULE, ApiKeyField.STUDENT), search(keyword = "준서"))
        assertEquals(setOf(ApiKeyField.CLUB), search(keyword = "x알리"))
        assertEquals(emptySet(), search(keyword = "없는 이름"))
    }

    private fun project(userId: Long, name: String) = ApiProject(userId, name, NOW)

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 24, 10, 0)

        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
