package edu.dbms.education.auth;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/login
 *
 * Authentication flow:
 *   1. Parse + validate the request body (@Valid – 400 on blank fields).
 *   2. Delegate to AuthenticationManager which calls:
 *        a. AppUserDetailsService.loadUserByUsername(username)
 *           → SELECT from `users` WHERE username = ? (traced)
 *        b. BCryptPasswordEncoder.matches(rawPassword, storedHash)
 *   3. On success: stamp last_login (UPDATE users … – traced), generate JWT.
 *   4. Return ResponseEnvelope{ data: TokenResponse, trace: [...] }
 *      The trace includes the SELECT query from step 2a and the UPDATE from 3.
 *
 * DBMS concept: the SELECT uses a parameterised query (? placeholder).
 * Both the SQL text and the username bind value appear separately in the
 * trace, demonstrating exactly how prepared statements prevent SQL injection.
 *
 * On failure AuthenticationManager throws BadCredentialsException which is
 * caught by GlobalExceptionHandler and returned as HTTP 401.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      tokenProvider;
    private final UserRepository        userRepository;
    private final SqlTraceService       traceService;

    @PostMapping("/login")
    public ResponseEntity<ResponseEnvelope<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        // ── Authenticate (triggers DB lookup + BCrypt verify) ─────────────
        // UsernamePasswordAuthenticationToken carries the raw plaintext password.
        // DaoAuthenticationProvider hashes it and compares with password_hash.
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Cast is safe – our AppUserDetailsService returns AppUser
        AppUser user = (AppUser) auth.getPrincipal();

        // ── Stamp last_login (parameterised UPDATE, adds to trace) ─────────
        userRepository.stampLastLogin(user.getUserId());

        // ── Generate JWT (contains userId, personId, role) ────────────────
        String token = tokenProvider.generateToken(user);
        log.debug("Issued JWT for user '{}' role '{}'", user.getUsername(), user.getDbRole());

        TokenResponse payload = new TokenResponse(
                token,
                user.getUserId(),
                user.getPersonId(),
                user.getDbRole()
        );

        // ── Return envelope with trace ────────────────────────────────────
        // traceService.getEntries() includes the SELECT and UPDATE from above
        return ResponseEntity.ok(
                ResponseEnvelope.ok(payload, traceService.getEntries())
        );
    }
}
