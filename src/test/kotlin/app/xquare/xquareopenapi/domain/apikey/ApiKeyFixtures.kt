package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository

// 신청 API를 거치지 않고, 분야를 신청한 키를 저장소에 바로 만든다. 첫 분야는 FIRST, 나머지는 ADD_FIELD로 남긴다.
fun saveApiKey(
    apiKeyRepository: ApiKeyRepository,
    apiKeyApplicationRepository: ApiKeyApplicationRepository,
    userId: Long,
    rawKey: String,
    vararg fields: ApiKeyField,
): ApiKey {
    val apiKey = apiKeyRepository.save(ApiKey(userId, ApiKeySecret.hash(rawKey)))
    fields.forEachIndexed { index, field ->
        val type = if (index == 0) ApiKeyApplicationType.FIRST else ApiKeyApplicationType.ADD_FIELD
        apiKeyApplicationRepository.save(ApiKeyApplication(apiKey.id!!, field, type, "테스트 서비스", "테스트 목적"))
    }
    return apiKey
}

// 활용신청·재발급은 로그인한 사용자의 이메일을 확인하므로 실제 사용자 행이 있어야 한다.
fun UserRepository.saveUser(username: String, email: String = "$username@dsm.hs.kr"): User =
    save(User(username = username, password = "encoded", studentNumber = 2315, name = "김준서", email = email))
