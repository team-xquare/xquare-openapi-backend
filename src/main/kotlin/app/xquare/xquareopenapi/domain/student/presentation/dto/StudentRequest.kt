package app.xquare.xquareopenapi.domain.student.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾼다.
// 학번 범위는 명세가 INVALID_INPUT이 아니라 INVALID_STUDENT_NUMBER로 응답하라고 해서 Student가 검사한다.
@Schema(requiredProperties = ["studentNumber", "name", "birthDate", "email"])
data class StudentRequest(
    @field:Schema(description = "학번(1000~3999 범위 4자리)", minimum = "1000", maximum = "3999", example = "2315")
    val studentNumber: Int,

    @field:NotBlank
    @field:Size(max = 10)
    @field:Schema(description = "이름", example = "김준서")
    val name: String,

    @field:Schema(description = "생일", example = "2009-07-28")
    val birthDate: LocalDate,

    @field:Email
    @field:Pattern(regexp = "^[^@\\s]+@dsm\\.hs\\.kr$", message = "@dsm.hs.kr 이메일만 사용할 수 있습니다.")
    @field:Schema(description = "이메일(@dsm.hs.kr)", example = "junseo@dsm.hs.kr")
    val email: String,
)
