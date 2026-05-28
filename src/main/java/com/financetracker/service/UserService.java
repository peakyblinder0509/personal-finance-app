package com.financetracker.service;

import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.exception.UnauthorizedException;
import com.financetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword) {
        log.debug("Registration attempt for email={}", email);

        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected — email already in use: {}", email);
            throw new IllegalStateException("Email already registered: " + email);
        }

        User saved = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .build());

        log.info("User registered: id={}, email={}", saved.getId(), email);
        return saved;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    public User authenticate(String email, String rawPassword) {
        log.debug("Authentication attempt for email={}", email);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("Failed authentication attempt for email={}", email);
            throw new UnauthorizedException("Invalid credentials");
        }

        log.info("User authenticated: id={}, email={}", user.getId(), email);
        return user;
    }
}
