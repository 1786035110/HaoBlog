package io.haoblog.identity.application;

import io.haoblog.identity.domain.AdminUser;
import io.haoblog.identity.persistence.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthenticationServiceTest {
    private final Instant now = Instant.parse("2026-08-13T00:00:00Z");
    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final AdminUser user = new AdminUser("admin", new BCryptPasswordEncoder().encode("correct"), now);
    private final AdminAuthenticationService service = new AdminAuthenticationService(
            repository, new BCryptPasswordEncoder(), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void acceptsCorrectPasswordAndResetsFailureState() {
        when(repository.findForAuthentication("admin")).thenReturn(Optional.of(user));

        var authentication = service.authenticate("admin", "correct");

        assertEquals("admin", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void rejectsWrongPasswordWithoutChangingAccountLockState() {
        when(repository.findForAuthentication("admin")).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> service.authenticate("admin", "wrong"));
    }

    @Test
    void performsDummyBcryptMatchForUnknownUsername() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.findForAuthentication("missing")).thenReturn(Optional.empty());
        when(encoder.matches("wrong", "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu"))
                .thenReturn(false);
        AdminAuthenticationService service = new AdminAuthenticationService(
                repository, encoder, Clock.fixed(now, ZoneOffset.UTC));

        assertThrows(BadCredentialsException.class, () -> service.authenticate("missing", "wrong"));
        verify(encoder).matches("wrong", "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu");
    }
}
