package edu.dbms.education.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dbms.education.api.ErrorEnvelope;
import edu.dbms.education.auth.AppUserDetailsService;
import edu.dbms.education.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Spring Security configuration – JWT stateless authentication.
 *
 * Key decisions:
 *
 * 1. STATELESS sessions – no HttpSession, no JSESSIONID cookie.
 *    Every request must carry "Authorization: Bearer <token>".
 *
 * 2. JwtAuthenticationFilter runs BEFORE UsernamePasswordAuthenticationFilter.
 *    It reads the JWT, validates it, and populates SecurityContextHolder.
 *    No DB call is made on every request – the role/ids are in the signed token.
 *
 * 3. DaoAuthenticationProvider + BCryptPasswordEncoder handle login only
 *    (POST /api/auth/login).  BCrypt cost=10 is the Spring Security default.
 *
 * 4. @EnableMethodSecurity enables @PreAuthorize at service/controller level.
 *    Route-level rules below are kept broad; fine-grained access control is
 *    expressed with @PreAuthorize so it's co-located with the business logic.
 *
 * 5. AuthenticationEntryPoint returns ErrorEnvelope{ error: { code: "UNAUTHORIZED" } }
 *    with HTTP 401 – consistent with the project envelope contract.
 *
 * 6. AccessDeniedHandler returns ErrorEnvelope{ error: { code: "FORBIDDEN" } }
 *    with HTTP 403 – same envelope shape.
 *
 * Route summary:
 *   PUBLIC  – /api/auth/**   (login)
 *   PUBLIC  – /api/health    (liveness probe)
 *   AUTH    – everything else under /api/**
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // activates @PreAuthorize / @PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService  userDetailsService;
    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper           objectMapper;   // injected from Spring MVC auto-config

    // ── Password encoding ─────────────────────────────────────────────────

    /**
     * BCrypt with cost factor 10 (Spring default).
     * Used by DaoAuthenticationProvider during login and by DataInitializer
     * to hash demo passwords at startup.
     *
     * DBMS concept: the password_hash column stores a BCrypt digest.
     * Even with full read access to the users table a DBA cannot recover the
     * plaintext; BCrypt's random salt ensures rainbow-table attacks fail.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Authentication provider ───────────────────────────────────────────

    /**
     * Wires AppUserDetailsService + BCryptPasswordEncoder into the standard
     * DaoAuthenticationProvider used during POST /api/auth/login.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the AuthenticationManager for injection into AuthController.
     * Spring Boot 3 / Security 6 requires explicit exposure via the
     * AuthenticationConfiguration.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── Security filter chain ─────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disabled – REST API, no browser form submissions
            .csrf(csrf -> csrf.disable())

            // No HttpSession – token is the only state
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Route access rules ────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Login and health are always public
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                // Every other /api/** route requires a valid token.
                // Fine-grained role checks are on @PreAuthorize annotations.
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )

            // ── Authentication provider ───────────────────────────────────
            .authenticationProvider(authenticationProvider())

            // ── JWT filter – runs before Spring's own auth filter ─────────
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

            // ── 401 Unauthorized – missing / invalid token ────────────────
            // Returns ErrorEnvelope{ error: { code: "UNAUTHORIZED" } } JSON.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorEnvelope body = new ErrorEnvelope(
                            "UNAUTHORIZED",
                            "Authentication required. Provide a valid Bearer token.",
                            List.of()    // no SQL trace on auth failures
                    );
                    objectMapper.writeValue(response.getOutputStream(), body);
                })

                // ── 403 Forbidden – valid token but insufficient role ─────
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorEnvelope body = new ErrorEnvelope(
                            "FORBIDDEN",
                            "You do not have permission to access this resource.",
                            List.of()
                    );
                    objectMapper.writeValue(response.getOutputStream(), body);
                })
            );

        return http.build();
    }
}
