package com.gachisa.user.repository

import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserProvider
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findByProviderAndProviderId(provider: UserProvider, providerId: String): Optional<User>

    @Query(
        value = "SELECT u FROM User u WHERE " +
            "(:email IS NULL OR u.email LIKE CONCAT('%', :email, '%')) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:status IS NULL OR u.status = :status)",
        countQuery = "SELECT COUNT(u) FROM User u WHERE " +
            "(:email IS NULL OR u.email LIKE CONCAT('%', :email, '%')) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:status IS NULL OR u.status = :status)",
    )
    fun search(
        @Param("email") email: String?,
        @Param("role") role: UserRole?,
        @Param("status") status: UserStatus?,
        pageable: Pageable,
    ): Page<User>
}
