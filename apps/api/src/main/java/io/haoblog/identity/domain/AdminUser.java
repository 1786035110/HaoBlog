package io.haoblog.identity.domain;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_user")
public class AdminUser {
    @Id
    private UUID id = UuidV7.generate();
    @Column(nullable = false, unique = true, length = 64)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 32)
    private String role = "ADMIN";
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminUser() {}

    public AdminUser(String username, String passwordHash, Instant now) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailure(Instant now, int maxFailures, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
        }
        updatedAt = now;
    }

    public void recordSuccess(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
}
