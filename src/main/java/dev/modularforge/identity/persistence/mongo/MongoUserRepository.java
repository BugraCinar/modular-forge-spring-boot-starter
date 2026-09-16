package dev.modularforge.identity.persistence.mongo;
import dev.modularforge.identity.*;
import org.springframework.data.mongodb.repository.Query;


import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@org.springframework.context.annotation.Profile("mongodb")
public interface MongoUserRepository extends UserRepository, org.springframework.data.mongodb.repository.MongoRepository<User, Long> {

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
    @Query(value="?#{T(dev.modularforge.shared.persistence.mongo.MongoFilters).users([0],[1],[2],[3])}")
    Page<User> findWithFilters(
            String search,
            Boolean isActive,
            Boolean emailVerified,
            UserType userType,
            Pageable pageable);
}
