package backend.config;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class WebSecurityConfig {

    @Autowired
    private SecurityContextRepository securityContextRepository;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity serverHttpSecurity) {
        return serverHttpSecurity.csrf().disable()
                .formLogin().disable()
                .httpBasic().disable()
                .exceptionHandling()
                .authenticationEntryPoint((exchange, e) ->
                        Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)))
                .and()
                .securityContextRepository(securityContextRepository)
                .authorizeExchange()
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/api/register", "/api/quickSetup/validateSession/*").permitAll()
                .anyExchange().authenticated()
                .and().build();
    }

    @Component
    protected class SecurityContextRepository implements ServerSecurityContextRepository {

        private static final String BEARER_PREFIX = "Bearer ";

        private static final String VALIDATE_TOKEN_ENDPOINT = "/api/auth/validate/{token}";

        @Value("${auth.server.url}")
        private String authServer;

        @Autowired
        private Logger logger;

        @Autowired
        private WebClient webClient;

        private final Map<String, TokenInfo> tokenCache = Collections.synchronizedMap(new LinkedHashMap<String, TokenInfo>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TokenInfo> eldest) {
                return size() > 5000;
            }
        });

        @Override
        public Mono<Void> save(ServerWebExchange swe, SecurityContext sc) {
            throw new UnsupportedOperationException("Not supported yet...");
        }

        @Override
        public Mono<SecurityContext> load(ServerWebExchange swe) {
            ServerHttpRequest request = swe.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String authToken = authHeader.substring(BEARER_PREFIX.length());
                return Mono.justOrEmpty(tokenCache.get(authToken))
                        .filter(TokenInfo::isValid)
                        .switchIfEmpty(validateToken(authToken)
                                .map(TokenInfo::new)
                                .doOnNext(tokenInfo -> tokenCache.put(authToken, tokenInfo)))
                        .map(tokenInfo -> new UsernamePasswordAuthenticationToken(
                                tokenInfo.getUserId(), authToken,
                                tokenInfo.getGrantedAuthorities()))
                        .doOnNext(auth -> SecurityContextHolder.getContext().setAuthentication(auth))
                        .map(SecurityContextImpl::new);
            } else {
                return Mono.empty();
            }
        }

        private Mono<ValidationResponse> validateToken(String token) {
            return webClient.get()
                    .uri(authServer + VALIDATE_TOKEN_ENDPOINT, token)
                    .retrieve()
                    .bodyToMono(ValidationResponse.class)
                    .onErrorResume(e -> {
                        logger.warn("Cannot validate token via auth server: " + e.getMessage());
                        return Mono.empty();
                    });
        }
    }

    private static class TokenInfo {

        private static final int TOKEN_NEXT_VALIDATION_MINUTES = 15;

        private String userId;
        private List<SimpleGrantedAuthority> grantedAuthorities;
        private Date revalidateAt;

        public TokenInfo(ValidationResponse validationResponse) {
            this.userId = validationResponse.getId();
            this.grantedAuthorities = validationResponse.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            Calendar now = Calendar.getInstance();
            now.add(Calendar.MINUTE, TOKEN_NEXT_VALIDATION_MINUTES);
            this.revalidateAt = now.getTime();
        }

        public String getUserId() {
            return userId;
        }

        public List<SimpleGrantedAuthority> getGrantedAuthorities() {
            return grantedAuthorities;
        }

        public boolean isValid() {
            return revalidateAt.after(new Date());
        }
    }

    private static class ValidationResponse {

        private String id;
        private List<String> roles;

        public ValidationResponse() {
        }

        public String getId() {
            return id;
        }

        public List<String> getRoles() {
            return roles;
        }
    }
}
