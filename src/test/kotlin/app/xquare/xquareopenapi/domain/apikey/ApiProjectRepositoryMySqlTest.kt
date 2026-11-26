package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.global.error.violates
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
    fun `관리자 목록은 상태로 거르고 null이면 전체를 읽는다`() {
        val project = projectRepository.saveAndFlush(project(2L, "급식 알리미"))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.SCHEDULE, "테스트 목적", ApplicationType.FIRST, NOW))
        fieldRepository.saveAndFlush(ApiProjectField(project.id!!, ApiKeyField.STUDENT, "테스트 목적", ApplicationType.FIRST, NOW))

        val pageable = org.springframework.data.domain.PageRequest.of(0, 10)
        assertEquals(listOf(ApiKeyField.STUDENT), fieldRepository.search(ApplicationStatus.PENDING, pageable).content.map { it.field })
        assertEquals(2, fieldRepository.search(null, pageable).totalElements)
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
