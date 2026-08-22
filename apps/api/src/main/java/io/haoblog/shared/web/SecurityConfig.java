package io.haoblog.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    CookieSerializer cookieSerializer(@Value("${haoblog.security.session-cookie-secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("HAOBLOG_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecurityContextRepository securityContextRepository,
                                            CsrfTokenRepository csrfTokenRepository,
                                            ProblemResponseWriter problemResponseWriter) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                problemResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                        "UNAUTHENTICATED", "Authentication required", "Authentication is required");
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) -> {
            boolean csrf = exception instanceof CsrfException;
            problemResponseWriter.write(request, response, HttpStatus.FORBIDDEN,
                    csrf ? "CSRF_INVALID" : "FORBIDDEN",
                    csrf ? "Invalid CSRF token" : "Access denied",
                    csrf ? "A valid CSRF token is required" : "Access is denied");
        };

        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/public/**", "/rss.xml", "/sitemap.xml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/csrf").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .build();
    }
}
