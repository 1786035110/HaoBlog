package io.haoblog.identity.persistence;

import io.haoblog.identity.domain.AdminUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AdminUser user where user.username = :username")
    Optional<AdminUser> findForAuthentication(@Param("username") String username);
}
