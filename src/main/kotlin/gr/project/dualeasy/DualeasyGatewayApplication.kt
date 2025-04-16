package gr.project.dualeasy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
class DualeasyGatewayApplication

fun main(args: Array<String>) {
    runApplication<DualeasyGatewayApplication>(*args)
}
