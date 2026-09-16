package dev.modularforge.identity.persistence.jpa;

import dev.modularforge.identity.*;
import dev.modularforge.identity.AdminRepository;

import dev.modularforge.identity.model.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@org.springframework.context.annotation.Profile("!mongodb")
public interface JpaAdminRepository extends AdminRepository, JpaRepository<Admin, Long> {
    @Override <S extends Admin> S saveAndFlush(S entity);
    @Override void flush();

    Optional<Admin> findByUsername(String username);

    Optional<Admin> findByEmail(String email);

    Optional<Admin> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT a FROM Admin a WHERE a.isActive = true")
    List<Admin> findAllActiveAdmins();

    @Query("SELECT a FROM Admin a WHERE a.level <= :maxLevel AND a.isActive = true")
    List<Admin> findByLevelLessThanEqualAndActiveTrue(@Param("maxLevel") Integer maxLevel);

    @Query("SELECT a FROM Admin a WHERE a.level = :level AND a.isActive = true")
    List<Admin> findByLevelAndActiveTrue(@Param("level") Integer level);

    @Query("SELECT COUNT(a) FROM Admin a WHERE a.level = 0") // Super Admin count
    long countSuperAdmins();

    Page<Admin> findByLevelGreaterThanEqual(Integer level, Pageable pageable);

    Page<Admin> findByLevel(Integer level, Pageable pageable);
}