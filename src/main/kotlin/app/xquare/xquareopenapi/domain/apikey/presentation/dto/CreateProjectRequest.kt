package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 프로젝트는 이름만으로 만든다. 분야·사용 목적·이메일 인증은 분야 신청(POST /projects/{id}/fields)에서 받는다.
data class CreateProjectRequest(
    // 같은 사용자 안에서 겹치지 않아야 한다.
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,
)
