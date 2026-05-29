package ru.agenteec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.agenteec.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    List<User> findTop10ByOrderByRatingBulletDesc();
    List<User> findTop10ByOrderByRatingBlitzDesc();
    List<User> findTop10ByOrderByRatingRapidDesc();
    List<User> findTop10ByOrderByRatingClassicalDesc();
}