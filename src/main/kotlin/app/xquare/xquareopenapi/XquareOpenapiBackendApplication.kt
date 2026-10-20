package app.xquare.xquareopenapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class XquareOpenapiBackendApplication

fun main(args: Array<String>) {
    runApplication<XquareOpenapiBackendApplication>(*args)
}
