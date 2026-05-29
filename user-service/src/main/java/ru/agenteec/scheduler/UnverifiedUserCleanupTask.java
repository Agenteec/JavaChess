package ru.agenteec.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.agenteec.repository.UserRepository;
import ru.agenteec.entity.User;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class UnverifiedUserCleanupTask {

    private final UserRepository userRepository;

    public UnverifiedUserCleanupTask(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupUnverifiedUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);

        List<User> unverified = userRepository.findAll().stream()
                .filter(user -> !user.isVerified() && user.getCreatedAt().isBefore(threshold))
                .toList();

        if (!unverified.isEmpty()) {
            userRepository.deleteAll(unverified);
            System.out.println(">>> [ПЛАНИРОВЩИК] Удалено " + unverified.size() + " неверифицированных аккаунтов по истечению 2 часов.");
        }
    }
}