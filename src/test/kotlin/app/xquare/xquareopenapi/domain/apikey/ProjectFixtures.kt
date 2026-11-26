package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.time.ZoneId

// 신청 API를 거치지 않고 프로젝트를 저장소에 바로 만든다. rawKey를 주면 그 키를 발급한 상태로 만든다.
fun ApiProjectRepository.saveProject(
    userId: Long,
    name: String = "테스트 프로젝트",
    rawKey: String? = null,
): ApiProject {
    val now = LocalDateTime.now(SEOUL)
    val project = ApiProject(userId, name, now)
    if (rawKey != null) {
        project.issueKey(now)
        ReflectionTestUtils.setField(project, "keyHash", ApiKeySecret.hash(rawKey))
    }
    return save(project)
}

// 분야를 원하는 상태로 바로 저장한다. 승인·반려 API를 거치지 않으려고 상태는 리플렉션으로 넣는다.
fun ApiProjectFieldRepository.saveField(
    projectId: Long,
    field: ApiKeyField,
    status: ApplicationStatus = ApplicationStatus.APPROVED,
): ApiProjectField {
    val projectField = ApiProjectField(projectId, field, "테스트 목적", ApplicationType.FIRST, LocalDateTime.now(SEOUL))
    ReflectionTestUtils.setField(projectField, "status", status)
    return save(projectField)
}

// rawKey를 발급한 프로젝트에 승인된 분야를 붙여 만든다. 조회 API 테스트가 키로 인증할 때 쓴다.
fun saveApiKey(
    projectRepository: ApiProjectRepository,
    fieldRepository: ApiProjectFieldRepository,
    userId: Long,
    rawKey: String,
    vararg approved: ApiKeyField,
): ApiProject {
    val project = projectRepository.saveProject(userId, name = rawKey.take(30), rawKey = rawKey)
    approved.forEach { fieldRepository.saveField(project.id!!, it) }
    return project
}

// 프로젝트 만들기·재발급은 로그인한 사용자의 이메일을 확인하므로 실제 사용자 행이 있어야 한다.
fun UserRepository.saveUser(username: String, email: String = "$username@dsm.hs.kr"): User =
    save(User(username = username, password = "encoded", studentNumber = 2315, name = "김준서", email = email))

private val SEOUL = ZoneId.of("Asia/Seoul")
