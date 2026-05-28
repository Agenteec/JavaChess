package ru.agenteec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.agenteec.entity.GameHistory;
import java.util.List;
import java.util.Optional;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
    Optional<GameHistory> findByGameId(String gameId);

    List<GameHistory> findByWhitePlayerOrBlackPlayer(String white, String black);
}