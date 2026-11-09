package app.xquare.xquareopenapi.global.jpa

private val LIKE_SPECIAL_CHARS = Regex("[!%_]")

// 부분 일치 검색어의 %와 _를 와일드카드가 아닌 글자 그대로 찾게 한다. 쿼리에는 `escape '!'`를 함께 쓴다.
// MySQL은 문자열 리터럴 안의 '\'를 이스케이프로 읽어서 '\' 대신 '!'를 이스케이프 문자로 쓴다.
fun String.escapeLike(): String = LIKE_SPECIAL_CHARS.replace(this, "!$0")
