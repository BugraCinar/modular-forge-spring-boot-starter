package dev.modularforge.identity.persistence.mongo;
import dev.modularforge.identity.*;
import org.springframework.data.mongodb.repository.Query;

import dev.modularforge.identity.model.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@org.springframework.context.annotation.Profile("mongodb")
public interface MongoAdminRepository extends AdminRepository, org.springframework.data.mongodb.repository.MongoRepository<Admin, Long> {

    Optional<Admin> findByUsername(String username);

    Optional<Admin> findByEmail(String email);

    Optional<Admin> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query(value="{'isActive':true}")
    List<Admin> findAllActiveAdmins();

    @Query(value="{'level':{'$lte':?0},'isActive':true}")
    List<Admin> findByLevelLessThanEqualAndActiveTrue(Integer maxLevel);

    @Query(value="{'level':?0,'isActive':true}")
    List<Admin> findByLevelAndActiveTrue(Integer level);

    // Super Admin count
    @Query(value="{'level':0}", count=true)
    long countSuperAdmins();

    Page<Admin> findByLevelGreaterThanEqual(Integer level, Pageable pageable);

    Page<Admin> findByLevel(Integer level, Pageable pageable);
}