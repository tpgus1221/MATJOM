package com.matjom.matjom.user.repository;

import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndProvider(String email, AuthProvider provider);
}
