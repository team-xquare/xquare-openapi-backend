package app.xquare.xquareopenapi.domain.meal.neis

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NeisDishesTest {

    @Test
    fun `br 태그로 나누고 끝의 알레르기 번호를 지운다`() {
        // 2026-10-02 대덕소프트웨어마이스터고 조식 DDISH_NM 그대로
        assertEquals(
            listOf("참치김치볶음밥", "우동장국", "팝콘만두/칠리소스", "단무지", "초코라떼"),
            parseDishes("참치김치볶음밥 (5.9.16.18)<br/>우동장국 (5.6.9.13)<br/>팝콘만두/칠리소스 (1.5.6.10.12.13.16.18)<br/>단무지 <br/>초코라떼 (2)"),
        )
    }

    @Test
    fun `메뉴 이름 안의 괄호는 남긴다`() {
        assertEquals(
            listOf("깻잎찜(오븐)", "연탄오리불고기(고추장)", "찹쌀밥(보조밥)", "포도C주스"),
            parseDishes("깻잎찜(오븐) (5.6.13)<br/>연탄오리불고기(고추장) <br/>찹쌀밥(보조밥) <br/>포도C주스 (13)"),
        )
    }

    @Test
    fun `알레르기 번호 괄호가 메뉴 이름에 붙어 있어도 지운다`() {
        assertEquals(listOf("배추김치", "우유"), parseDishes("배추김치(9)<br/>우유(2)"))
    }

    @Test
    fun `빈 항목은 뺀다`() {
        assertEquals(emptyList(), parseDishes(""))
        assertEquals(listOf("백미밥", "김치"), parseDishes(" <br/>백미밥 <br/><br/>김치 (9)<br/>"))
    }
}
