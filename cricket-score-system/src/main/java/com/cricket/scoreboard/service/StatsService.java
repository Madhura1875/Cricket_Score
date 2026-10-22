package com.cricket.scoreboard.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.scoreboard.dto.Dtos.LeaderEntry;
import com.cricket.scoreboard.dto.Dtos.PlayerStatsDto;
import com.cricket.scoreboard.model.Player;
import com.cricket.scoreboard.repository.BallEventRepository;
import com.cricket.scoreboard.util.Fmt;

/** Career statistics, aggregated by the database from the ball-by-ball events. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final BallEventRepository balls;
    private final PlayerDirectory players;

    public StatsService(BallEventRepository balls, PlayerDirectory players) {
        this.balls = balls;
        this.players = players;
    }

    public PlayerStatsDto playerStats(Long id) {
        Player p = players.get(id);
        long runs = nz(balls.battingRuns(id));
        long faced = balls.ballsFaced(id);
        long dismissals = balls.dismissals(id);
        long bowled = balls.ballsBowled(id);
        long conceded = nz(balls.runsConceded(id));

        Double average = dismissals > 0 ? Fmt.round2((double) runs / dismissals) : null;
        double strikeRate = faced > 0 ? Fmt.round1(runs * 100.0 / faced) : 0.0;
        double economy = bowled > 0 ? Fmt.round2(conceded * 6.0 / bowled) : 0.0;

        return new PlayerStatsDto(p.getId(), p.getName(), p.getTeam().getName(), p.getRole(),
                balls.matchesPlayed(id), runs, faced, balls.fours(id), balls.sixes(id), dismissals,
                average, strikeRate, bowled, Fmt.overs((int) bowled), conceded, balls.wicketsTaken(id), economy);
    }

    public List<LeaderEntry> topBatters(int limit) {
        return toLeaders(balls.topBatters(PageRequest.of(0, clamp(limit))));
    }

    public List<LeaderEntry> topBowlers(int limit) {
        return toLeaders(balls.topBowlers(PageRequest.of(0, clamp(limit))));
    }

    private List<LeaderEntry> toLeaders(List<Object[]> rows) {
        return rows.stream().map(r -> {
            Player p = players.get(((Number) r[0]).longValue());
            return new LeaderEntry(p.getId(), p.getName(), p.getTeam().getShortName(), ((Number) r[1]).longValue());
        }).toList();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
