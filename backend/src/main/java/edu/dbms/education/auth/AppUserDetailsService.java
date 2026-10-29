package edu.dbms.education.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} backed by the {@code users} table.
 *
 * Spring's {@link org.springframework.security.authentication.DaoAuthenticationProvider}
 * calls {@link #loadUserByUsername} during authentication. It then compares
 * the supplied plaintext password against the stored BCrypt hash via the
 * {@link org.springframework.security.crypto.password.PasswordEncoder} bean.
 *
 * We do NOT call BCrypt here – that is the DaoAuthenticationProvider's job.
 * This service only fetches the user record; separation of concerns keeps
 * password verification and user lookup independent.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Called by Spring Security's authentication machinery.
     *
     * Throws {@link UsernameNotFoundException} (not null) as required by the
     * contract; DaoAuthenticationProvider catches it and translates it to
     * {@link org.springframework.security.authentication.BadCredentialsException}
     * so the error message never leaks whether the username exists.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with username: " + username));
    }
}
