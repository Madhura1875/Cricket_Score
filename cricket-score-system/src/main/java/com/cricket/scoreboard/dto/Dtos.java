package com.cricket.scoreboard.dto;

import java.time.Instant;
import java.util.List;

import com.cricket.scoreboard.model.DismissalType;
import com.cricket.scoreboard.model.ExtraType;
import com.cricket.scoreboard.model.MatchStatus;
import com.cricket.scoreboard.model.Player;
import com.cricket.scoreboard.model.PlayerRole;
import com.cricket.scoreboard.model.Team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** All request/response shapes of the REST API in one place. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- requests ----------

    public record CreateMatchRequest(
            @NotNull Long team1Id,
            @NotNull Long team2Id,
            @Min(1) @Max(50) Integer totalOvers,
            String venue,
            Boolean simulate) {
    }

    /**
     * One delivery. "runs" means: runs off the bat (NONE / NO_BALL), runs taken on a WIDE,
     * or byes / leg-byes (BYE / LEG_BYE). The server adds the extra penalty run itself.
     */
    public record BallRequest(
            @Min(0) @Max(7) Integer runs,
            ExtraType extraType,
            Boolean wicket,
            DismissalType dismissalType) {
    }

    // ---------- catalog ----------

    public record TeamDto(Long id, String name, String shortName) {
    }

    public record PlayerDto(Long id, String name, PlayerRole role, Long teamId, String teamName) {
    }

    // ---------- scoring views ----------

    public record BatterLine(Long playerId, String name, int runs, int balls, int fours, int sixes,
                             double strikeRate, boolean out, String dismissal) {
    }

    public record BowlerLine(Long playerId, String name, String overs, int maidens, int runs, int wickets,
                             double economy) {
    }

    public record BallDto(Long id, int innings, String over, String label, int runs, boolean wicket,
                          String commentary, String batter, String bowler, Instant time) {
    }

    public record InningsScore(String teamShort, int runs, int wickets, String overs) {
    }

    public record InningsSummary(int innings, String teamName, String teamShort, int runs, int wickets,
                                 String overs, int extras, List<BatterLine> batting,
                                 List<BowlerLine> bowling, List<String> fallOfWickets) {
    }

    public record Scorecard(Long matchId, String title, MatchStatus status, String result,
                            List<InningsSummary> innings) {
    }

    /** Everything the dashboard needs to draw a match; also the payload of the live SSE stream. */
    public record MatchDto(
            Long id, String title, String venue, MatchStatus status, int totalOvers, String tossSummary,
            TeamDto team1, TeamDto team2, int innings, TeamDto battingTeam,
            int runs, int wickets, String overs, double runRate,
            Integer target, Integer runsRequired, Integer ballsRemaining, Double requiredRate,
            BatterLine striker, BatterLine nonStriker, BowlerLine bowler,
            InningsScore firstInnings, String result, boolean simulated,
            List<BallDto> recentBalls, Instant createdAt) {
    }

    // ---------- statistics ----------

    public record PlayerStatsDto(
            Long playerId, String name, String team, PlayerRole role, long matches,
            long runs, long ballsFaced, long fours, long sixes, long dismissals,
            Double average, double strikeRate,
            long ballsBowled, String oversBowled, long runsConceded, long wickets, double economy) {
    }

    public record LeaderEntry(Long playerId, String name, String team, long value) {
    }

    // ---------- mappers ----------

    public static TeamDto team(Team t) {
        return t == null ? null : new TeamDto(t.getId(), t.getName(), t.getShortName());
    }

    public static PlayerDto player(Player p) {
        return new PlayerDto(p.getId(), p.getName(), p.getRole(), p.getTeam().getId(), p.getTeam().getName());
    }
}
