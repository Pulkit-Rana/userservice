package com.syncnest.userservice.repository;

import com.syncnest.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailAndDeletedAtIsNullFetchProfile(@Param("email") String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByGoogleSubAndDeletedAtIsNull(String googleSub);

    @Query("select u from User u where u.email = :email and u.deletedAt is not null")
    Optional<User> findDeletedByEmail(@Param("email") String email);

    @Query("select u from User u where u.googleSub = :googleSub and u.deletedAt is not null")
    Optional<User> findDeletedByGoogleSub(@Param("googleSub") String googleSub);

    @Query("select u from User u where u.deletedAt is not null")
    List<User> findAllSoftDeleted();

    /** Soft-deleted accounts whose {@code deletedAt} is older than the retention cutoff (eligible for hard delete). */
    @Query("select u from User u where u.deletedAt is not null and u.deletedAt < :cutoff")
    List<User> findSoftDeletedWithDeletedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}

