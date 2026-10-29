package edu.dbms.education.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Creates, signs, and validates HS256 JWT tokens.
 *
 * Token claims:
 *   sub       – username  (standard JWT subject)
 *   userId    – users.user_id  (int)
 *   personId  – users.person_id  (Integer, omitted when null)
 *   role      – raw DB ENUM value: Admin / Registrar / Faculty / Student
 *
 * Algorithm: HMAC-SHA256 (HS256) – symmetric, single-secret, appropriate
 * for a monolithic service where the issuer and verifier are the same JVM.
 *
 * The secret is injected from application.yml (app.jwt.secret) and decoded
 * from Base64 once at startup. Keys.hmacShaKeyFor ensures the byte array
 * is long enough for HS256 (≥32 bytes = 256 bits).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secretBase64;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey secretKey;

    // Custom claim names – kept as constants to avoid typos
    static final String CLAIM_USER_ID   = "userId";
    static final String CLAIM_PERSON_ID = "personId";
    static final String CLAIM_ROLE      = "role";

    /** Decode the Base64 secret once at startup into a typed SecretKey. */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        this.secretKey  = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Token generation ─────────────────────────────────────────────────

    /**
     * Builds a signed JWT for a successfully authenticated user.
     *
     * @param user the authenticated AppUser (loaded from users table)
     * @return     compact, URL-safe JWT string: header.payload.signature
     */
    public String generateToken(AppUser user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_ROLE,    user.getDbRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey);   // jjwt 0.12 infers HS256 from key type

        // Only embed personId when it's non-null (Admin may lack a person row)
        if (user.getPersonId() != null) {
            builder.claim(CLAIM_PERSON_ID, user.getPersonId());
        }

        return builder.compact();
    }

    // ── Token parsing ─────────────────────────────────────────────────────

    /**
     * Parses and validates a JWT string, returning its claims.
     *
     * Throws {@link JwtException} (or a subclass) for any validation failure:
     *   - {@link ExpiredJwtException}        – token has expired
     *   - {@link io.jsonwebtoken.MalformedJwtException} – bad structure
     *   - {@link io.jsonwebtoken.security.SignatureException} – tampered
     *
     * The filter catches JwtException and returns 401.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Quick validity check without re-throwing – used in the filter to
     * decide whether to attempt full parsing.
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return false;
        }
    }

    // ── Claim extractors ──────────────────────────────────────────────────

    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public int getUserIdFromToken(String token) {
        return parseToken(token).get(CLAIM_USER_ID, Integer.class);
    }

    public Integer getPersonIdFromToken(String token) {
        return parseToken(token).get(CLAIM_PERSON_ID, Integer.class); // null-safe
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get(CLAIM_ROLE, String.class);
    }
}
