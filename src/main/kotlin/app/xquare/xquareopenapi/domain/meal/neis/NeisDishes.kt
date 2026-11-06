package app.xquare.xquareopenapi.domain.meal.neis

// 나이스 DDISH_NM은 "배추김치 (9)<br/>단무지 <br/>깻잎찜(오븐) (5.6.13)"처럼 온다.
// <br/>로 나누고, 끝의 알레르기 번호 괄호만 지운다. 메뉴 이름 안의 괄호("깻잎찜(오븐)")는 남긴다.
fun parseDishes(ddishNm: String): List<String> =
    ddishNm.split(DISH_SEPARATOR)
        .map { it.trim().replace(ALLERGY_NUMBERS, "") }
        .filter { it.isNotEmpty() }

private const val DISH_SEPARATOR = "<br/>"
private val ALLERGY_NUMBERS = Regex("""\s*\(\d+(\.\d+)*\)$""")
