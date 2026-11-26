package app.xquare.xquareopenapi.domain.apikey

import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiProjectFieldTest {

    @Test
    fun `분야의 단계는 급식이 공개, 일정, 자습 감독이 교내이고 학생, 교직원, 동아리가 제한이다`() {
        assertEquals(
            mapOf(
                ApiKeyField.SCHEDULE to AccessLevel.SCHOOL,
                ApiKeyField.CLUB to AccessLevel.RESTRICTED,
                ApiKeyField.STUDY_SUPERVISOR to AccessLevel.SCHOOL,
                ApiKeyField.TEACHER to AccessLevel.RESTRICTED,
                ApiKeyField.STUDENT to AccessLevel.RESTRICTED,
                ApiKeyField.MEAL to AccessLevel.PUBLIC,
            ),
            ApiKeyField.entries.associateWith { it.level },
        )
    }

    @Test
    fun `교내 분야는 신청과 동시에 승인되고 처리 시각은 신청 시각이다`() {
        val field = ApiProjectField(1L, ApiKeyField.SCHEDULE, "테스트 목적", ApplicationType.FIRST, NOW)

        assertEquals(ApplicationStatus.APPROVED, field.status)
        assertEquals(NOW, field.appliedAt)
        assertEquals(NOW, field.decidedAt)
        assertNull(field.decidedBy)
        assertNull(field.rejectReason)
    }

    @Test
    fun `공개 분야(급식)도 신청과 동시에 승인되고 처리 시각은 신청 시각이다`() {
        val field = ApiProjectField(1L, ApiKeyField.MEAL, "급식 알림", ApplicationType.ADDITIONAL, NOW)

        assertEquals(ApplicationStatus.APPROVED, field.status)
        assertEquals(NOW, field.decidedAt)
        assertEquals("급식 알림", field.purpose)
        assertEquals(ApplicationType.ADDITIONAL, field.applicationType)
    }

    @Test
    fun `제한 분야는 승인 대기로 시작한다`() {
        val field = ApiProjectField(1L, ApiKeyField.STUDENT, "테스트 목적", ApplicationType.FIRST, NOW)

        assertEquals(ApplicationStatus.PENDING, field.status)
        assertNull(field.decidedAt)
    }

    @Test
    fun `반려된 분야를 다시 신청하면 대기로 돌아가고 처리 기록을 지운다`() {
        val field = ApiProjectField(1L, ApiKeyField.TEACHER, "테스트 목적", ApplicationType.FIRST, NOW)
        reject(field)

        field.resubmit("다시 신청한 목적", LATER)

        assertEquals(ApplicationStatus.PENDING, field.status)
        assertEquals("다시 신청한 목적", field.purpose)
        assertEquals(ApplicationType.FIRST, field.applicationType)
        assertEquals(LATER, field.appliedAt)
        assertNull(field.decidedAt)
        assertNull(field.decidedBy)
        assertNull(field.rejectReason)
    }

    @Test
    fun `교내 분야를 다시 신청하면 그 시각으로 바로 승인된다`() {
        val field = ApiProjectField(1L, ApiKeyField.SCHEDULE, "테스트 목적", ApplicationType.FIRST, NOW)
        reject(field)

        field.resubmit("다시 신청한 목적", LATER)

        assertEquals(ApplicationStatus.APPROVED, field.status)
        assertEquals(LATER, field.decidedAt)
        assertNull(field.decidedBy)
        assertNull(field.rejectReason)
    }

    @Test
    fun `관리자가 승인하면 승인 상태와 처리 시각, 처리한 관리자를 남긴다`() {
        val field = ApiProjectField(1L, ApiKeyField.STUDENT, "테스트 목적", ApplicationType.FIRST, NOW)

        field.approve(9L, LATER)

        assertEquals(ApplicationStatus.APPROVED, field.status)
        assertEquals(LATER, field.decidedAt)
        assertEquals(9L, field.decidedBy)
        assertNull(field.rejectReason)
        assertEquals(NOW, field.appliedAt)
    }

    @Test
    fun `관리자가 반려하면 사유와 처리 기록을 남긴다`() {
        val field = ApiProjectField(1L, ApiKeyField.TEACHER, "테스트 목적", ApplicationType.FIRST, NOW)

        field.reject(9L, "목적이 불분명합니다.", LATER)

        assertEquals(ApplicationStatus.REJECTED, field.status)
        assertEquals("목적이 불분명합니다.", field.rejectReason)
        assertEquals(LATER, field.decidedAt)
        assertEquals(9L, field.decidedBy)
    }

    @Test
    fun `키를 발급하면 원문의 해시만 남기고 발급 시각을 기록한다`() {
        val project = ApiProject(1L, "급식 알리미", NOW)
        assertFalse(project.keyIssued)

        val rawKey = project.issueKey(LATER)

        assertTrue(project.keyIssued)
        assertTrue(rawKey.startsWith("xq_"))
        assertEquals(ApiKeySecret.hash(rawKey), project.keyHash)
        assertEquals(LATER, project.issuedAt)
        assertEquals(LATER.plusDays(60), project.expiresAt)
    }

    @Test
    fun `갱신하면 키 값과 발급 시각은 그대로 두고 만료 시각만 60일 뒤로 늦춘다`() {
        val project = ApiProject(1L, "급식 알리미", NOW)
        project.issueKey(NOW)
        val keyHash = project.keyHash

        project.renewKey(LATER.plusDays(30))

        assertEquals(keyHash, project.keyHash)
        assertEquals(NOW, project.issuedAt)
        assertEquals(LATER.plusDays(90), project.expiresAt)
    }

    private fun reject(field: ApiProjectField) {
        ReflectionTestUtils.setField(field, "status", ApplicationStatus.REJECTED)
        ReflectionTestUtils.setField(field, "decidedAt", NOW)
        ReflectionTestUtils.setField(field, "decidedBy", 1L)
        ReflectionTestUtils.setField(field, "rejectReason", "목적이 불분명합니다.")
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 24, 10, 0)
        private val LATER = NOW.plusDays(1)
    }
}
