package app.xquare.xquareopenapi.domain.meal.neis

import org.springframework.boot.context.properties.ConfigurationProperties

// 기본 학교는 대덕소프트웨어마이스터고등학교(대전광역시교육청 G10, 학교 코드 7430310)다. 나이스 schoolInfo API로 확인했다.
// apiKey가 비어 있으면 급식 동기화를 건너뛴다.
@ConfigurationProperties(prefix = "neis")
data class NeisProperties(
    val apiKey: String,
    val officeCode: String,
    val schoolCode: String,
)
