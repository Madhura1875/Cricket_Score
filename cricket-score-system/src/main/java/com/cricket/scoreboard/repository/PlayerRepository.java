package com.cricket.scoreboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.scoreboard.model.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeamIdOrderByIdAsc(Long teamId);
}
