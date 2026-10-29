package edu.dbms.education.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Principal object stored inside the SecurityContext for authenticated requests.
 *
 * Provides typed access to the three IDs embedded in the JWT:
 *   userId   – users.user_id   (always present)
 *   personId – users.person_id (nullable; null for Admin users)
 *   role     – raw DB ENUM string: Admin / Registrar / Faculty / Student
 *
 * Static helper methods expose these values to any Spring bean without
 * needing to inject the SecurityContext manually.
 *
 * DBMS concept: faculty queries filter by faculty_id = personId,
 * student queries filter by student_id = personId. These ids are read
 * directly from the signed JWT claim – no extra DB lookup per request.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private final int     userId;
    private final Integer personId;  // null for Admin
    private final String  username;
    private final String  role;

    // ── Static helpers ────────────────────────────────────────────────────

    /**
     * Returns the current AuthenticatedUser from the SecurityContext.
     *
     * @throws IllegalStateException if no authentication is present or
     *                               the principal is not an AuthenticatedUser
     *                               (should never happen in protected routes).
     */
    public static AuthenticatedUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return user;
    }

    /**
     * Convenience – returns the person_id of the current user.
     * Faculty controllers use this to scope queries to their own records:
     *   WHERE faculty_id = AuthenticatedUser.currentPersonId()
     *
     * @throws IllegalStateException if personId is null (called from an Admin context)
     */
    public static int currentPersonId() {
        Integer pid = current().getPersonId();
        if (pid == null) {
            throw new IllegalStateException(
                    "Current user has no associated person record (Admin role)");
        }
        return pid;
    }

    /** Returns the userId of the currently authenticated user. */
    public static int currentUserId() {
        return current().getUserId();
    }
}
