package com.cricket.scoreboard.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cricket.scoreboard.dto.Dtos.BatterLine;
import com.cricket.scoreboard.dto.Dtos.BowlerLine;
import com.cricket.scoreboard.dto.Dtos.InningsSummary;
import com.cricket.scoreboard.dto.Dtos.Scorecard;
import com.cricket.scoreboard.model.BallEvent;
import com.cricket.scoreboard.model.CricketMatch;
import com.cricket.scoreboard.model.ExtraType;
import com.cricket.scoreboard.model.Team;
import com.cricket.scoreboard.repository.BallEventRepository;
import com.cricket.scoreboard.util.Fmt;

/** Builds batting / bowling scorecards by folding over the ball-by-ball events. */
@Service
public class ScorecardService {

    private static final class Bat {
        final Long id;
        int runs, balls, fours, sixes;
        boolean out;
        String how = "";

        Bat(Long id) {
            this.id = id;
        }
    }

    private static final class Bowl {
        final Long id;
        int balls, runs, wickets, maidens;

        Bowl(Long id) {
            this.id = id;
        }
    }

    private final BallEventRepository balls;
    private final PlayerDirectory players;

    public ScorecardService(BallEventRepository balls, PlayerDirectory players) {
        this.balls = balls;
        this.players = players;
    }

    public Scorecard scorecard(CricketMatch m) {
        List<InningsSummary> list = new ArrayList<>();
        for (int i = 1; i <= m.getInnings(); i++) {
            list.add(innings(m, i));
        }
        return new Scorecard(m.getId(), m.getTitle(), m.getStatus(), m.getResult(), list);
    }

    public InningsSummary innings(CricketMatch m, int inningsNo) {
        return build(m, inningsNo, balls.findByMatchIdAndInningsOrderByIdAsc(m.getId(), inningsNo));
    }

    private Team battingTeamFor(CricketMatch m, int inningsNo) {
        Team current = m.getBattingTeam();
        if (inningsNo == m.getInnings()) {
            return current;
        }
        return current.getId().equals(m.getTeam1().getId()) ? m.getTeam2() : m.getTeam1();
    }

    private InningsSummary build(CricketMatch m, int inningsNo, List<BallEvent> events) {
        Map<Long, Bat> bats = new LinkedHashMap<>();
        Map<Long, Bowl> bowls = new LinkedHashMap<>();
        Map<String, int[]> overs = new HashMap<>(); // "bowlerId|over" -> {legal balls, runs charged}
        List<String> fallOfWickets = new ArrayList<>();
        int total = 0, wickets = 0, extras = 0, legal = 0;

        for (BallEvent e : events) {
            Bat striker = bats.computeIfAbsent(e.getBatsmanId(), Bat::new);
            bats.computeIfAbsent(e.getNonStrikerId(), Bat::new);
            striker.runs += e.getRunsOffBat();
            if (e.getExtraType() != ExtraType.WIDE) {
                striker.balls++;
            }
            if (e.getRunsOffBat() == 4) {
                striker.fours++;
            }
            if (e.getRunsOffBat() == 6) {
                striker.sixes++;
            }

            Bowl bowler = bowls.computeIfAbsent(e.getBowlerId(), Bowl::new);
            if (e.isLegalBall()) {
                bowler.balls++;
            }
            bowler.runs += e.getRunsCharged();
            if (e.isBowlerWicket()) {
                bowler.wickets++;
            }
            int[] over = overs.computeIfAbsent(e.getBowlerId() + "|" + e.getOverNumber(), k -> new int[2]);
            if (e.isLegalBall()) {
                over[0]++;
            }
            over[1] += e.getRunsCharged();

            total += e.getTotalRuns();
            extras += e.getExtraRuns();
            if (e.isLegalBall()) {
                legal++;
            }
            if (e.isWicket()) {
                wickets++;
                Bat dismissed = bats.computeIfAbsent(e.getDismissedPlayerId(), Bat::new);
                dismissed.out = true;
                dismissed.how = dismissalText(e);
                fallOfWickets.add(wickets + "-" + total + " (" + players.get(e.getDismissedPlayerId()).getName()
                        + ", " + e.getOverNumber() + "." + e.getBallInOver() + " ov)");
            }
        }

        // a maiden is a full over of six legal balls without a single run charged to the bowler
        for (Map.Entry<String, int[]> entry : overs.entrySet()) {
            if (entry.getValue()[0] == 6 && entry.getValue()[1] == 0) {
                Long bowlerId = Long.valueOf(entry.getKey().split("\\|")[0]);
                bowls.get(bowlerId).maidens++;
            }
        }

        List<BatterLine> batting = new ArrayList<>();
        for (Bat b : bats.values()) {
            double sr = b.balls > 0 ? Fmt.round1(b.runs * 100.0 / b.balls) : 0.0;
            batting.add(new BatterLine(b.id, players.get(b.id).getName(), b.runs, b.balls, b.fours, b.sixes, sr,
                    b.out, b.out ? b.how : "not out"));
        }
        List<BowlerLine> bowling = new ArrayList<>();
        for (Bowl b : bowls.values()) {
            double eco = b.balls > 0 ? Fmt.round2(b.runs * 6.0 / b.balls) : 0.0;
            bowling.add(new BowlerLine(b.id, players.get(b.id).getName(), Fmt.overs(b.balls), b.maidens, b.runs,
                    b.wickets, eco));
        }

        Team team = battingTeamFor(m, inningsNo);
        return new InningsSummary(inningsNo, team.getName(), team.getShortName(), total, wickets,
                Fmt.overs(legal), extras, batting, bowling, fallOfWickets);
    }

    private String dismissalText(BallEvent e) {
        String bowler = players.get(e.getBowlerId()).getName();
        return switch (e.getDismissalType()) {
            case BOWLED -> "b " + bowler;
            case CAUGHT -> "caught b " + bowler;
            case LBW -> "lbw b " + bowler;
            case STUMPED -> "st b " + bowler;
            case RUN_OUT -> "run out";
            default -> "out";
        };
    }
}
