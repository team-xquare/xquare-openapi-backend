package app.xquare.xquareopenapi.domain.meal

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.databind.json.JsonMapper

// 메뉴 목록을 JSON 배열 문자열(["백미밥","된장찌개"])로 저장한다. 메뉴 이름에 어떤 글자가 있어도 구분자와 섞이지 않는다.
@Converter
class MenuListConverter : AttributeConverter<List<String>, String> {

    override fun convertToDatabaseColumn(attribute: List<String>): String =
        jsonMapper.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): List<String> =
        jsonMapper.readValue(dbData, Array<String>::class.java).toList()

    companion object {
        private val jsonMapper = JsonMapper.builder().build()
    }
}
