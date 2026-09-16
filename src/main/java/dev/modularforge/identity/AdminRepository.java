package dev.modularforge.identity;

import dev.modularforge.identity.model.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@org.springframework.data.repository.NoRepositoryBean
public interface AdminRepository extends dev.modularforge.shared.persistence.EntityRepository<Admin> {

    Optional<Admin> findByUsername(String username);

    Optional<Admin> findByEmail(String email);

    Optional<Admin> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<Admin> findAllActiveAdmins();

    List<Admin> findByLevelLessThanEqualAndActiveTrue(Integer maxLevel);

    List<Admin> findByLevelAndActiveTrue(Integer level);

    // Super Admin count
    long countSuperAdmins();

    Page<Admin> findByLevelGreaterThanEqual(Integer level, Pageable pageable);

    Page<Admin> findByLevel(Integer level, Pageable pageable);
}