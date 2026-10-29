package edu.dbms.education.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a row from the `users` table to Spring Security's {@link UserDetails}.
 *
 * DBMS concept: the `users` table stores credentials separately from the
 * `person` table (IS-A relationship exists for Faculty/Student/Registrar but
 * not for Admin). person_id is nullable – an Admin user may have no person row.
 *
 * Role mapping:
 *   DB ENUM value  →  Spring GrantedAuthority
 *   'Admin'        →  ROLE_ADMIN
 *   'Registrar'    →  ROLE_REGISTRAR
 *   'Faculty'      →  ROLE_FACULTY
 *   'Student'      →  ROLE_STUDENT
 *
 * The ROLE_ prefix is the Spring Security convention required by
 * @PreAuthorize("hasRole('ADMIN')") and hasAnyRole(...).
 */
@Getter
@AllArgsConstructor
public class AppUser implements UserDetails {

    private final int     userId;
    private final Integer personId;   // nullable – Admin may have no person row
    private final String  username;
    private final String  passwordHash;
    private final String  dbRole;     // raw ENUM value: Admin/Registrar/Faculty/Student
    private final boolean active;

    // ── UserDetails contract ──────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Prepend ROLE_ so @PreAuthorize("hasRole('ADMIN')") works
        return List.of(new SimpleGrantedAuthority("ROLE_" + dbRole.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired()    { return true; }

    @Override
    public boolean isAccountNonLocked()     { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
