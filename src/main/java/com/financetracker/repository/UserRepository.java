package com.financetracker.repository;

import com.financetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

// JpaRepository<Entity, PrimaryKeyType> gives you save, findById, findAll,
// delete, count, and more — all implemented automatically at runtime.
public interface UserRepository extends JpaRepository<User, UUID> {

    // Spring Data reads "findBy Email" and generates:
    // SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // Generates: SELECT COUNT(*) > 0 FROM users WHERE email = ?
    // More efficient than findByEmail when you only need a yes/no answer.
    boolean existsByEmail(String email);
}   
