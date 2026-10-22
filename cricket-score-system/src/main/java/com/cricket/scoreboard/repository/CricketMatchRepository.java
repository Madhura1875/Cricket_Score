package com.cricket.scoreboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.scoreboard.model.CricketMatch;
import com.cricket.scoreboard.model.MatchStatus;

import jakarta.persistence.LockModeType;

public interface CricketMatchRepository extends JpaRepository<CricketMatch, Long> {

    List<CricketMatch> findByStatus(MatchStatus status, Pageable pageable);

    List<CricketMatch> findByStatusAndSimulatedTrue(MatchStatus status);

    /** Row lock so two scorers (or a scorer and the simulator) can never update the same match at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CricketMatch m where m.id = :id")
    Optional<CricketMatch> findForUpdate(@Param("id") Long id);
}
