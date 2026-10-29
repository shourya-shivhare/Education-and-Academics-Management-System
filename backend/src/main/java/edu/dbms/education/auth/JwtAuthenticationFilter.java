package edu.dbms.education.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepts every request and, if a valid Bearer token is present, populates
 * the SecurityContext so @PreAuthorize annotations downstream can evaluate.
 *
 * Lifecycle:
 *   1. Extract the token from "Authorization: Bearer <token>".
 *   2. Validate signature + expiry via JwtTokenProvider.
 *   3. Build an Authentication object with the role from the token claims
 *      (no DB call required – the role is embedded in the signed token).
 *   4. Store it in SecurityContextHolder for this request.
 *   5. Continue the filter chain (never short-circuits; 401/403 responses
 *      are produced by the AuthenticationEntryPoint / AccessDeniedHandler).
 *
 * Design: claims are read from the token directly, avoiding a DB round-trip
 * on every authenticated request. The token contains userId, personId, and
 * role – everything downstream controllers need.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull  HttpServletRequest  request,
                                    @NonNull  HttpServletResponse response,
                                    @NonNull  FilterChain         filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                if (tokenProvider.isTokenValid(token)) {
                    int     userId   = tokenProvider.getUserIdFromToken(token);
                    Integer personId = tokenProvider.getPersonIdFromToken(token);
                    String  role     = tokenProvider.getRoleFromToken(token);
                    String  username = tokenProvider.getUsernameFromToken(token);

                    // Build authority from the role stored in the token.
                    // ROLE_ prefix is already absent in the DB ENUM value
                    // (stored as "Admin" not "ROLE_ADMIN"), so prepend it.
                    var authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());

                    // Principal: our custom AuthenticatedUser (not UserDetails).
                    // This avoids a DB call on every request.
                    var principal = new AuthenticatedUser(userId, personId, username, role);

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,               // credentials cleared post-auth
                            List.of(authority)
                    );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT auth set for user '{}' role '{}'", username, role);
                }
            } catch (JwtException ex) {
                // Token is present but invalid – clear any stale context.
                // The response stays a plain 200 pass-through here; if the
                // route requires auth, AuthenticationEntryPoint fires next.
                SecurityContextHolder.clearContext();
                log.debug("JWT rejected: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Extracts the raw token string from "Authorization: Bearer <token>". */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
