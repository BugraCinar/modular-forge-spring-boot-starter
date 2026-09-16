package dev.modularforge.identity.persistence.jpa;

import dev.modularforge.identity.*;
import dev.modularforge.identity.UserRepository;


import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@org.springframework.context.annotation.Profile("!mongodb")
public interface JpaUserRepository extends UserRepository, JpaRepository<User, Long> {
    @Override <S extends User> S saveAndFlush(S entity);
    @Override void flush();

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    Page<User> findByEmailVerified(Boolean emailVerified, Pageable pageable);

    Page<User> findByUserType(UserType userType, Pageable pageable);

    Page<User> findByIsActiveAndEmailVerified(Boolean isActive, Boolean emailVerified, Pageable pageable);
    List<User> findByIsActiveFalseAndAdminDeactivatedFalseAndAnonymisedAtIsNullAndDeactivatedAtBefore(LocalDateTime cutoff);
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:isActive       IS NULL OR u.isActive       = :isActive)
              AND (:emailVerified  IS NULL OR u.emailVerified  = :emailVerified)
              AND (:userType       IS NULL OR u.userType       = :userType)
            """)
    Page<User> findWithFilters(
            @Param("search")        String search,
            @Param("isActive")      Boolean isActive,
            @Param("emailVerified") Boolean emailVerified,
            @Param("userType")      UserType userType,
            Pageable pageable);
}
