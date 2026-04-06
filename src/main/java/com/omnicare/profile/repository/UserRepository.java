package com.omnicare.profile.repository;

import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    interface DevAccountSummary {
        UserRole getRole();

        String getName();

        String getEmail();
    }

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    List<User> findAllByPhoneNumber(String phoneNumber);

    long countByPhoneNumber(String phoneNumber);

    Optional<User> findByEmailVerificationTokenHash(String emailVerificationTokenHash);

    @Query("""
            select u.role as role, u.name as name, u.email as email
            from User u
            where u.email is not null
              and trim(u.email) <> ''
              and u.role in :roles
            order by u.role asc, u.name asc, u.email asc
            """)
    List<DevAccountSummary> findDevAccountSummaries(@Param("roles") Collection<UserRole> roles);
}
