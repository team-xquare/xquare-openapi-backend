package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// 프로젝트는 이름(과 선택 입력인 서비스 URL)만으로 만든다. 분야·사용 목적·이메일 인증은 분야 신청(POST /projects/{id}/fields)에서 받는다.
// 수정(PATCH /projects/{id})도 같은 DTO로 모든 값을 받아 통째로 바꾼다.
data class CreateProjectRequest(
    // 같은 사용자 안에서 겹치지 않아야 한다.
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    // http·https 주소. 없으면 null
    @field:Size(max = SERVICE_URL_MAX_LENGTH)
    @field:Pattern(regexp = SERVICE_URL_PATTERN)
    val serviceUrl: String? = null,
)
