package app.xquare.xquareopenapi.domain.apiusage

enum class UsageHttpMethod {
    GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE, CONNECT, OTHER;

    companion object {
        fun normalize(method: String): UsageHttpMethod = entries.firstOrNull { it.name == method } ?: OTHER
    }
}
