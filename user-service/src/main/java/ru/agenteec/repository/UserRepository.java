package ru.agenteec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.agenteec.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}