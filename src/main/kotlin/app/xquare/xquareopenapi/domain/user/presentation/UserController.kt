package app.xquare.xquareopenapi.domain.user.presentation

import app.xquare.xquareopenapi.domain.user.presentation.dto.MyInfoResponse
import app.xquare.xquareopenapi.domain.user.usecase.GetMyInfoUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자(USER, ADMIN) 모두 쓸 수 있다. SecurityConfig의 anyRequest authenticated에 걸린다.
@RestController
@RequestMapping("/users")
class UserController(
    private val getMyInfoUseCase: GetMyInfoUseCase,
) {

    // principal은 JwtAuthenticationFilter가 넣은 사용자 id다.
    @GetMapping("/me")
    fun getMyInfo(@AuthenticationPrincipal userId: Long): MyInfoResponse =
        getMyInfoUseCase.execute(userId)
}
