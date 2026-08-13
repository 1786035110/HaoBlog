package io.haoblog.identity.application;

import io.haoblog.identity.domain.AdminUser;
import io.haoblog.identity.persistence.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthenticationServiceTest {
    private final Instant now = Instant.parse("2026-08-13T00:00:00Z");
    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final AdminUser user = new AdminUser("admin", new BCryptPasswordEncoder().encode("correct"), now);
    private final AdminAuthenticationService service = new AdminAuthenticationService(
            repository, new BCryptPasswordEncoder(), Clock.fixed(now, ZoneOffset.UTC), 5, Duration.ofMinutes(15));

    @Test
    void acceptsCorrectPasswordAndResetsFailureState() {
        when(repository.findForAuthentication("admin")).thenReturn(Optional.of(user));

        var authentication = service.authenticate("admin", "correct");

        assertEquals("admin", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void recordsWrongPasswordsAndLocksOnTheSixthAttempt() {
        when(repository.findForAuthentication("admin")).thenReturn(Optional.of(user));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> service.authenticate("admin", "wrong"));
        }
        assertEquals(5, user.getFailedLoginAttempts());
        assertThrows(AdminAccountLockedException.class, () -> service.authenticate("admin", "correct"));
    }
}
