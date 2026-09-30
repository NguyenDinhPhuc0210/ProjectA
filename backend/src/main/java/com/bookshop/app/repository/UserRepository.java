package com.bookshop.app.repository;

import com.bookshop.app.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository — all queries MUST filter deleted_at IS NULL.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailAndNotDeleted(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.phoneNumber = :phone AND u.deletedAt IS NULL")
    Optional<User> findByPhoneNumberAndNotDeleted(@Param("phone") String phoneNumber);

    @Query("SELECT u FROM User u WHERE u.resetToken = :token AND u.deletedAt IS NULL")
    Optional<User> findByResetTokenAndNotDeleted(@Param("token") String resetToken);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.role = com.bookshop.app.model.enums.Role.ROLE_ADMIN AND u.deletedAt IS NULL")
    boolean existsAdmin();

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findByIdAndNotDeleted(@Param("id") Long id);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmailAndNotDeleted(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phoneNumber = :phone AND u.deletedAt IS NULL AND u.id != :excludeId")
    boolean existsByPhoneNumberAndNotDeletedExcludingId(@Param("phone") String phoneNumber,
                                                        @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phoneNumber = :phone AND u.deletedAt IS NULL")
    boolean existsByPhoneNumberAndNotDeleted(@Param("phone") String phoneNumber);
}
