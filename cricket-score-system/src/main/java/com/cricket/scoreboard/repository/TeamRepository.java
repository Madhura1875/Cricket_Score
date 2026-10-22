package com.cricket.scoreboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.scoreboard.model.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
