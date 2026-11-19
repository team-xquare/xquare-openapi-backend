package app.xquare.xquareopenapi.domain.club.presentation

import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubResponse
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubSearchRequest
import app.xquare.xquareopenapi.domain.club.usecase.CreateClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.DeleteClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.GetClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.GetClubsUseCase
import app.xquare.xquareopenapi.domain.club.usecase.UpdateClubUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 권한(조회는 USER, 등록·수정·삭제는 ADMIN)은 SecurityConfig에서 검사한다.
@RestController
@RequestMapping("/clubs")
class ClubController(
    private val getClubsUseCase: GetClubsUseCase,
    private val createClubUseCase: CreateClubUseCase,
    private val getClubUseCase: GetClubUseCase,
    private val updateClubUseCase: UpdateClubUseCase,
    private val deleteClubUseCase: DeleteClubUseCase,
) {

    @GetMapping
    fun getClubs(@ModelAttribute @Valid request: ClubSearchRequest): PageResponse<ClubResponse> =
        getClubsUseCase.execute(request)

    @PostMapping
    fun createClub(@RequestBody @Valid request: ClubRequest): ResponseEntity<MessageResponse> {
        val clubId = createClubUseCase.execute(request)
        return ResponseEntity.created(URI.create("/clubs/$clubId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{clubId}")
    fun getClub(@PathVariable clubId: Long): ClubResponse =
        getClubUseCase.execute(clubId)

    @PatchMapping("/{clubId}")
    fun updateClub(@PathVariable clubId: Long, @RequestBody @Valid request: ClubRequest): MessageResponse {
        updateClubUseCase.execute(clubId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{clubId}")
    fun deleteClub(@PathVariable clubId: Long): MessageResponse {
        deleteClubUseCase.execute(clubId)
        return MessageResponse("삭제되었습니다.")
    }
}
