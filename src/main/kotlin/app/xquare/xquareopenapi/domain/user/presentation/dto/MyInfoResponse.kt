package app.xquare.xquareopenapi.domain.user.presentation.dto

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.User

// 비밀번호 해시는 응답에 넣지 않는다.
data class MyInfoResponse(
    val id: Long,
    val username: String,
    val name: String,
    val studentNumber: Int,
    val email: String,
    val role: Role,
) {
    companion object {
        fun from(user: User) = MyInfoResponse(
            id = user.id!!,
            username = user.username,
            name = user.name,
            studentNumber = user.studentNumber,
            email = user.email,
            role = user.role,
        )
    }
}
