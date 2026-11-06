package app.xquare.xquareopenapi.domain.club

// 명세에는 예시(ROOM_2_1, ROOM_2_2)만 있어서 같은 형식(ROOM_층_실 번호)으로 1~5층, 1~9실을 둔다.
// 학교의 실 목록이 정해지면 여기만 고친다. DB에는 이름 문자열로 저장한다.
enum class ClubRoom {
    ROOM_1_1, ROOM_1_2, ROOM_1_3, ROOM_1_4, ROOM_1_5, ROOM_1_6, ROOM_1_7, ROOM_1_8, ROOM_1_9,
    ROOM_2_1, ROOM_2_2, ROOM_2_3, ROOM_2_4, ROOM_2_5, ROOM_2_6, ROOM_2_7, ROOM_2_8, ROOM_2_9,
    ROOM_3_1, ROOM_3_2, ROOM_3_3, ROOM_3_4, ROOM_3_5, ROOM_3_6, ROOM_3_7, ROOM_3_8, ROOM_3_9,
    ROOM_4_1, ROOM_4_2, ROOM_4_3, ROOM_4_4, ROOM_4_5, ROOM_4_6, ROOM_4_7, ROOM_4_8, ROOM_4_9,
    ROOM_5_1, ROOM_5_2, ROOM_5_3, ROOM_5_4, ROOM_5_5, ROOM_5_6, ROOM_5_7, ROOM_5_8, ROOM_5_9,
}
