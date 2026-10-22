package com.cricket.scoreboard.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.scoreboard.model.BallEvent;

public interface BallEventRepository extends JpaRepository<BallEvent, Long> {

    List<BallEvent> findByMatchIdAndInningsOrderByIdAsc(Long matchId, int innings);

    List<BallEvent> findByMatchIdOrderByIdDesc(Long matchId, Pageable pageable);

    // ---------- leaderboards ----------

    @Query("select b.batsmanId, sum(b.runsOffBat) from BallEvent b "
            + "group by b.batsmanId order by sum(b.runsOffBat) desc")
    List<Object[]> topBatters(Pageable pageable);

    @Query("select b.bowlerId, count(b) from BallEvent b where b.bowlerWicket = true "
            + "group by b.bowlerId order by count(b) desc")
    List<Object[]> topBowlers(Pageable pageable);

    // ---------- per-player career numbers (aggregated in the database) ----------

    @Query("select sum(b.runsOffBat) from BallEvent b where b.batsmanId = :pid")
    Long battingRuns(@Param("pid") Long playerId);

    @Query("select count(b) from BallEvent b where b.batsmanId = :pid "
            + "and b.extraType <> com.cricket.scoreboard.model.ExtraType.WIDE")
    long ballsFaced(@Param("pid") Long playerId);

    @Query("select count(b) from BallEvent b where b.batsmanId = :pid and b.runsOffBat = 4")
    long fours(@Param("pid") Long playerId);

    @Query("select count(b) from BallEvent b where b.batsmanId = :pid and b.runsOffBat = 6")
    long sixes(@Param("pid") Long playerId);

    @Query("select count(b) from BallEvent b where b.dismissedPlayerId = :pid")
    long dismissals(@Param("pid") Long playerId);

    @Query("select count(distinct b.matchId) from BallEvent b "
            + "where b.batsmanId = :pid or b.nonStrikerId = :pid or b.bowlerId = :pid")
    long matchesPlayed(@Param("pid") Long playerId);

    @Query("select count(b) from BallEvent b where b.bowlerId = :pid and b.legalBall = true")
    long ballsBowled(@Param("pid") Long playerId);

    @Query("select sum(b.runsCharged) from BallEvent b where b.bowlerId = :pid")
    Long runsConceded(@Param("pid") Long playerId);

    @Query("select count(b) from BallEvent b where b.bowlerId = :pid and b.bowlerWicket = true")
    long wicketsTaken(@Param("pid") Long playerId);
}
