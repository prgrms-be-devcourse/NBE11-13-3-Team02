package com.gachisa.user.repository;

import com.gachisa.user.entity.User;
import com.gachisa.user.entity.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderAndProviderId(UserProvider provider, String providerId);
}
