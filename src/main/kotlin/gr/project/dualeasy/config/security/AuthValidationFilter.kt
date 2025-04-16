package gr.project.dualeasy.config.security

import gr.project.dualeasy.config.model.AuthInfoResponse
import gr.project.dualeasy.config.model.SecurityProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class AuthValidationFilter(
    private val securityProps: SecurityProperties
) : GlobalFilter, Ordered {

    private val webClient = WebClient.builder().baseUrl(securityProps.authServiceUrl).build()

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.uri.path

        if (securityProps.publicPaths.any { path == it || path.startsWith("$it/") }) {
            return chain.filter(exchange)
        }

        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: return unauthorized(exchange)

        if (!authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange)
        }

        val token = authHeader.removePrefix("Bearer ").trim()

        return webClient.get()
            .uri { uriBuilder -> uriBuilder.path("/auth/validate").queryParam("token", token).build() }
            .retrieve()
            .onStatus({ it.is4xxClientError || it.is5xxServerError }) { Mono.error(RuntimeException("Token validation failed")) }
            .bodyToMono(AuthInfoResponse::class.java)
            .flatMap { authInfo ->
                val mutatedRequest = exchange.request.mutate()
                    .header("X-Client-Id", authInfo.clientId)
                    .header("X-Role", authInfo.role)
                    .build()

                chain.filter(exchange.mutate().request(mutatedRequest).build())
            }
            .onErrorResume {
                unauthorized(exchange)
            }
    }

    override fun getOrder(): Int = -1

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
