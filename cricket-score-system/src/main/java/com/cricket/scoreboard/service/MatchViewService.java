package com.cricket.scoreboard.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.cricket.scoreboard.dto.Dtos;
import com.cricket.scoreboard.dto.Dtos.BallDto;
import com.cricket.scoreboard.dto.Dtos.BatterLine;
import com.cricket.scoreboard.dto.Dtos.BowlerLine;
import com.cricket.scoreboard.dto.Dtos.InningsScore;
import com.cricket.scoreboard.dto.Dtos.InningsSummary;
import com.cricket.scoreboard.dto.Dtos.MatchDto;
import com.cricket.scoreboard.model.BallEvent;
import com.cricket.scoreboard.model.CricketMatch;
import com.cricket.scoreboard.model.MatchStatus;
import com.cricket.scoreboard.model.Team;
import com.cricket.scoreboard.repository.BallEventRepository;
import com.cricket.scoreboard.util.Fmt;

/** Converts a match entity into the dashboard-friendly {@link MatchDto}. */
@Component
public class MatchViewService {

    private static final int RECENT_BALLS = 15;

    private final BallEventRepository balls;
    private final PlayerDirectory players;
    private final ScorecardService scorecards;

    public MatchViewService(BallEventRepository balls, PlayerDirectory players, ScorecardService scorecards) {
        this.balls = balls;
        this.players = players;
        this.scorecards = scorecards;
    }

    public MatchDto toDto(CricketMatch m) {
        InningsSummary current = m.getInnings() > 0 ? scorecards.innings(m, m.getInnings()) : null;
        boolean live = m.getStatus() == MatchStatus.LIVE && current != null;

        BatterLine striker = live ? batterLine(current, m.getStrikerId()) : null;
        BatterLine nonStriker = live ? batterLine(current, m.getNonStrikerId()) : null;
        BowlerLine bowler = live ? bowlerLine(current, m.getBowlerId()) : null;

        int legal = m.getLegalBalls();
        double runRate = legal > 0 ? Fmt.round2(m.getRuns() * 6.0 / legal) : 0.0;

        Integer target = null, runsRequired = null, ballsRemaining = null;
        Double requiredRate = null;
        InningsScore firstInnings = null;
        if (m.getInnings() == 2) {
            target = m.getTarget();
            runsRequired = Math.max(0, m.getTarget() - m.getRuns());
            ballsRemaining = Math.max(0, m.getTotalOvers() * 6 - legal);
            requiredRate = ballsRemaining > 0 ? Fmt.round2(runsRequired * 6.0 / ballsRemaining) : null;
            Team first = m.getBattingTeam().getId().equals(m.getTeam1().getId()) ? m.getTeam2() : m.getTeam1();
            firstInnings = new InningsScore(first.getShortName(), m.getFirstInningsRuns(),
                    m.getFirstInningsWickets(), Fmt.overs(m.getFirstInningsBalls()));
        }

        List<BallDto> recent = balls.findByMatchIdOrderByIdDesc(m.getId(), PageRequest.of(0, RECENT_BALLS))
                .stream().map(this::toBallDto).toList();

        return new MatchDto(m.getId(), m.getTitle(), m.getVenue(), m.getStatus(), m.getTotalOvers(),
                m.getTossSummary(), Dtos.team(m.getTeam1()), Dtos.team(m.getTeam2()), m.getInnings(),
                Dtos.team(m.getBattingTeam()), m.getRuns(), m.getWickets(), Fmt.overs(legal), runRate,
                target, runsRequired, ballsRemaining, requiredRate, striker, nonStriker, bowler,
                firstInnings, m.getResult(), m.isSimulated(), recent, m.getCreatedAt());
    }

    public BallDto toBallDto(BallEvent e) {
        return new BallDto(e.getId(), e.getInnings(), e.getOverNumber() + "." + e.getBallInOver(), e.getLabel(),
                e.getTotalRuns(), e.isWicket(), e.getCommentary(), players.get(e.getBatsmanId()).getName(),
                players.get(e.getBowlerId()).getName(), e.getCreatedAt());
    }

    private BatterLine batterLine(InningsSummary innings, Long playerId) {
        return innings.batting().stream().filter(b -> b.playerId().equals(playerId)).findFirst()
                .orElseGet(() -> new BatterLine(playerId, players.get(playerId).getName(), 0, 0, 0, 0, 0.0,
                        false, "not out"));
    }

    private BowlerLine bowlerLine(InningsSummary innings, Long playerId) {
        return innings.bowling().stream().filter(b -> b.playerId().equals(playerId)).findFirst()
                .orElseGet(() -> new BowlerLine(playerId, players.get(playerId).getName(), "0.0", 0, 0, 0, 0.0));
    }
}
