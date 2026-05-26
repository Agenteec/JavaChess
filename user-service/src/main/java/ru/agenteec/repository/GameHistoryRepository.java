package ru.agenteec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.agenteec.entity.GameHistory;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
}