package edu.dbms.education.auth;

import lombok.Getter;

/**
 * Payload returned inside ResponseEnvelope on successful login.
 *
 * Shape (inside the envelope's "data" field):
 * {
 *   "token":    "eyJhbGci...",
 *   "type":     "Bearer",
 *   "userId":   1,
 *   "personId": 42,          // null for Admin users with no person row
 *   "role":     "Admin"
 * }
 *
 * The client stores the token and sends it as:
 *   Authorization: Bearer <token>
 *
 * NOTE: 'type' is a constant – not part of the constructor – so we write
 * the constructor manually rather than use @AllArgsConstructor, which would
 * try to include the final-with-initialiser field and cause a compile error.
 */
@Getter
public class TokenResponse {

    private final String  token;
    private final String  type     = "Bearer";   // always "Bearer" – not a constructor param
    private final int     userId;
    private final Integer personId;              // nullable for Admin
    private final String  role;

    public TokenResponse(String token, int userId, Integer personId, String role) {
        this.token    = token;
        this.userId   = userId;
        this.personId = personId;
        this.role     = role;
    }
}
