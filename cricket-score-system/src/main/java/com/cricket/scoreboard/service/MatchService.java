package com.cricket.scoreboard.service;

import java.util.List;
import java.util.Random;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.cricket.scoreboard.dto.Dtos.BallDto;
import com.cricket.scoreboard.dto.Dtos.BallRequest;
import com.cricket.scoreboard.dto.Dtos.CreateMatchRequest;
import com.cricket.scoreboard.dto.Dtos.MatchDto;
import com.cricket.scoreboard.dto.Dtos.Scorecard;
import com.cricket.scoreboard.exception.BadRequestException;
import com.cricket.scoreboard.exception.NotFoundException;
import com.cricket.scoreboard.model.BallEvent;
import com.cricket.scoreboard.model.CricketMatch;
import com.cricket.scoreboard.model.DismissalType;
import com.cricket.scoreboard.model.ExtraType;
import com.cricket.scoreboard.model.MatchStatus;
import com.cricket.scoreboard.model.Player;
import com.cricket.scoreboard.model.PlayerRole;
import com.cricket.scoreboard.model.Team;
import com.cricket.scoreboard.repository.BallEventRepository;
import com.cricket.scoreboard.repository.CricketMatchRepository;
import com.cricket.scoreboard.repository.TeamRepository;

/**
 * The scoring engine: creates matches, runs the toss, applies every delivery
 * (runs, extras, wickets, strike rotation, over and innings changes) and finishes the match.
 */
@Service
public class MatchService {

    private static final int MAX_WICKETS = 10;

    private final CricketMatchRepository matches;
    private final TeamRepository teams;
    private final BallEventRepository balls;
    private final PlayerDirectory players;
    private final MatchViewService view;
    private final ScorecardService scorecards;
    private final BallGenerator generator;
    private final LiveUpdateService live;
    private final Random random = new Random();

    public MatchService(CricketMatchRepository matches, TeamRepository teams, BallEventRepository balls,
                        PlayerDirectory players, MatchViewService view, ScorecardService scorecards,
                        BallGenerator generator, LiveUpdateService live) {
        this.matches = matches;
        this.teams = teams;
        this.balls = balls;
        this.players = players;
        this.view = view;
        this.scorecards = scorecards;
        this.generator = generator;
        this.live = live;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<MatchDto> list(MatchStatus status) {
        PageRequest page = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "id"));
        List<CricketMatch> found = status == null
                ? matches.findAll(page).getContent()
                : matches.findByStatus(status, page);
        return found.stream().map(view::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MatchDto get(Long id) {
        return view.toDto(find(id));
    }

    @Transactional(readOnly = true)
    public Scorecard scorecard(Long id) {
        return scorecards.scorecard(find(id));
    }

    @Transactional(readOnly = true)
    public List<BallDto> commentary(Long id, int limit) {
        find(id);
        int size = Math.max(1, Math.min(limit, 200));
        return balls.findByMatchIdOrderByIdDesc(id, PageRequest.of(0, size)).stream().map(view::toBallDto).toList();
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public MatchDto create(CreateMatchRequest req) {
        if (req.team1Id().equals(req.team2Id())) {
            throw new BadRequestException("A team cannot play against itself");
        }
        Team t1 = teams.findById(req.team1Id())
                .orElseThrow(() -> new NotFoundException("Team " + req.team1Id() + " not found"));
        Team t2 = teams.findById(req.team2Id())
                .orElseThrow(() -> new NotFoundException("Team " + req.team2Id() + " not found"));
        if (players.squad(t1.getId()).size() < 11 || players.squad(t2.getId()).size() < 11) {
            throw new BadRequestException("Each team needs at least 11 players");
        }
        CricketMatch m = new CricketMatch();
        m.setTeam1(t1);
        m.setTeam2(t2);
        m.setTitle(t1.getName() + " vs " + t2.getName());
        m.setVenue(req.venue() == null || req.venue().isBlank() ? "Cricket Arena" : req.venue().trim());
        m.setTotalOvers(req.totalOvers() == null ? 20 : req.totalOvers());
        m.setSimulated(req.simulate() == null || req.simulate());
        matches.save(m);
        return publish(m);
    }

    @Transactional
    public MatchDto start(Long id) {
        CricketMatch m = lock(id);
        if (m.getStatus() != MatchStatus.UPCOMING) {
            throw new BadRequestException("Match has already started");
        }
        Team tossWinner = random.nextBoolean() ? m.getTeam1() : m.getTeam2();
        boolean electsToBat = random.nextBoolean();
        Team batting = electsToBat ? tossWinner : otherTeam(m, tossWinner);

        m.setTossSummary(tossWinner.getName() + " won the toss and elected to " + (electsToBat ? "bat" : "field"));
        m.setBattingTeam(batting);
        m.setInnings(1);
        m.setStatus(MatchStatus.LIVE);
        setupInnings(m);
        return publish(m);
    }

    @Transactional
    public MatchDto recordBall(Long id, BallRequest req) {
        CricketMatch m = lock(id);
        requireLive(m);
        applyBall(m, req);
        return publish(m);
    }

    @Transactional
    public MatchDto setSimulation(Long id, boolean enabled) {
        CricketMatch m = lock(id);
        if (m.getStatus() == MatchStatus.COMPLETED) {
            throw new BadRequestException("Match is already completed");
        }
        m.setSimulated(enabled);
        return publish(m);
    }

    /** Plays the rest of the match instantly (used for seeding and demos). */
    @Transactional
    public MatchDto fastForward(Long id) {
        CricketMatch m = lock(id);
        requireLive(m);
        int guard = 0;
        while (m.getStatus() == MatchStatus.LIVE && guard++ < 2000) {
            applyBall(m, generator.generate(m));
        }
        return publish(m);
    }

    // ------------------------------------------------------------------ engine

    private void applyBall(CricketMatch m, BallRequest req) {
        ExtraType extra = req.extraType() == null ? ExtraType.NONE : req.extraType();
        int runs = req.runs() == null ? 0 : req.runs();
        boolean wicket = Boolean.TRUE.equals(req.wicket());
        DismissalType dismissal = DismissalType.NONE;

        if (wicket) {
            dismissal = (req.dismissalType() == null || req.dismissalType() == DismissalType.NONE)
                    ? DismissalType.CAUGHT : req.dismissalType();
            if (extra == ExtraType.NO_BALL && dismissal != DismissalType.RUN_OUT) {
                throw new BadRequestException("Only a run-out is possible on a no-ball");
            }
            if (extra == ExtraType.WIDE && dismissal != DismissalType.RUN_OUT && dismissal != DismissalType.STUMPED) {
                throw new BadRequestException("Only a stumping or run-out is possible on a wide");
            }
            if ((extra == ExtraType.BYE || extra == ExtraType.LEG_BYE) && dismissal != DismissalType.RUN_OUT) {
                throw new BadRequestException("Only a run-out is possible on byes / leg-byes");
            }
            if (dismissal != DismissalType.RUN_OUT && runs != 0) {
                throw new BadRequestException("Runs must be 0 for this type of dismissal");
            }
        }

        int runsOffBat;
        int extraRuns;
        boolean legal;
        switch (extra) {
            case WIDE -> { runsOffBat = 0; extraRuns = 1 + runs; legal = false; }
            case NO_BALL -> { runsOffBat = runs; extraRuns = 1; legal = false; }
            case BYE, LEG_BYE -> { runsOffBat = 0; extraRuns = runs; legal = true; }
            default -> { runsOffBat = runs; extraRuns = 0; legal = true; }
        }
        int total = runsOffBat + extraRuns;
        int charged = runsOffBat + (extra == ExtraType.WIDE || extra == ExtraType.NO_BALL ? extraRuns : 0);

        int legalBefore = m.getLegalBalls();
        BallEvent e = new BallEvent();
        e.setMatchId(m.getId());
        e.setInnings(m.getInnings());
        e.setOverNumber(legalBefore / 6);
        e.setBallInOver(legalBefore % 6 + 1);
        e.setBatsmanId(m.getStrikerId());
        e.setNonStrikerId(m.getNonStrikerId());
        e.setBowlerId(m.getBowlerId());
        e.setRunsOffBat(runsOffBat);
        e.setExtraType(extra);
        e.setExtraRuns(extraRuns);
        e.setTotalRuns(total);
        e.setRunsCharged(charged);
        e.setLegalBall(legal);
        e.setWicket(wicket);
        e.setDismissalType(dismissal);
        e.setBowlerWicket(wicket && dismissal != DismissalType.RUN_OUT);
        e.setDismissedPlayerId(wicket ? m.getStrikerId() : null);
        e.setLabel(label(extra, runs, wicket));
        e.setCommentary(commentary(e, extra, runs, wicket, dismissal));
        balls.save(e);

        // ---- update running totals
        m.setRuns(m.getRuns() + total);
        if (legal) {
            m.setLegalBalls(m.getLegalBalls() + 1);
        }
        if (wicket) {
            m.setWickets(m.getWickets() + 1);
        }

        // ---- batters: new batter on a wicket, otherwise swap ends on odd runs
        if (wicket) {
            if (m.getWickets() < MAX_WICKETS) {
                List<Player> order = players.squad(m.getBattingTeam().getId());
                m.setStrikerId(order.get(m.getWickets() + 1).getId());
            }
        } else if (runs % 2 == 1) {
            swapEnds(m);
        }
        boolean overDone = legal && m.getLegalBalls() % 6 == 0;
        if (overDone) {
            swapEnds(m);
        }

        // ---- end of innings / match, or next bowler
        boolean allOut = m.getWickets() >= MAX_WICKETS;
        boolean oversDone = m.getLegalBalls() >= m.getTotalOvers() * 6;
        boolean chased = m.getInnings() == 2 && m.getRuns() >= m.getTarget();
        if (allOut || oversDone || chased) {
            endInnings(m);
        } else if (overDone) {
            m.setBowlerId(pickBowler(m, m.getLegalBalls() / 6));
        }
    }

    private void endInnings(CricketMatch m) {
        if (m.getInnings() == 1) {
            m.setFirstInningsRuns(m.getRuns());
            m.setFirstInningsWickets(m.getWickets());
            m.setFirstInningsBalls(m.getLegalBalls());
            m.setTarget(m.getRuns() + 1);
            m.setBattingTeam(fieldingTeam(m));
            m.setInnings(2);
            m.setRuns(0);
            m.setWickets(0);
            m.setLegalBalls(0);
            setupInnings(m);
            return;
        }
        Team batting = m.getBattingTeam();
        Team fielding = fieldingTeam(m);
        int ballsLeft = m.getTotalOvers() * 6 - m.getLegalBalls();
        if (m.getRuns() >= m.getTarget()) {
            m.setResult(batting.getName() + " won by " + (MAX_WICKETS - m.getWickets()) + " wickets ("
                    + ballsLeft + " balls left)");
        } else if (m.getRuns() == m.getTarget() - 1) {
            m.setResult("Match tied");
        } else {
            m.setResult(fielding.getName() + " won by " + (m.getTarget() - 1 - m.getRuns()) + " runs");
        }
        m.setStatus(MatchStatus.COMPLETED);
        m.setSimulated(false);
        m.setStrikerId(null);
        m.setNonStrikerId(null);
        m.setBowlerId(null);
    }

    private void setupInnings(CricketMatch m) {
        List<Player> order = players.squad(m.getBattingTeam().getId());
        m.setStrikerId(order.get(0).getId());
        m.setNonStrikerId(order.get(1).getId());
        m.setBowlerId(pickBowler(m, 0));
    }

    /** Bowlers rotate through the bowling-capable players, so nobody bowls two overs in a row. */
    private Long pickBowler(CricketMatch m, int overIndex) {
        List<Player> squad = players.squad(fieldingTeam(m).getId());
        List<Player> pool = squad.stream()
                .filter(p -> p.getRole() == PlayerRole.BOWLER || p.getRole() == PlayerRole.ALL_ROUNDER)
                .toList();
        if (pool.isEmpty()) {
            pool = squad;
        }
        return pool.get(overIndex % pool.size()).getId();
    }

    private void swapEnds(CricketMatch m) {
        Long tmp = m.getStrikerId();
        m.setStrikerId(m.getNonStrikerId());
        m.setNonStrikerId(tmp);
    }

    private Team fieldingTeam(CricketMatch m) {
        return otherTeam(m, m.getBattingTeam());
    }

    private Team otherTeam(CricketMatch m, Team team) {
        return team.getId().equals(m.getTeam1().getId()) ? m.getTeam2() : m.getTeam1();
    }

    private String label(ExtraType extra, int runs, boolean wicket) {
        if (wicket) {
            return "W";
        }
        return switch (extra) {
            case WIDE -> runs > 0 ? "Wd+" + runs : "Wd";
            case NO_BALL -> runs > 0 ? "Nb+" + runs : "Nb";
            case BYE -> runs + "b";
            case LEG_BYE -> runs + "lb";
            default -> String.valueOf(runs);
        };
    }

    private String commentary(BallEvent e, ExtraType extra, int runs, boolean wicket, DismissalType dismissal) {
        Player bowler = players.get(e.getBowlerId());
        Player batter = players.get(e.getBatsmanId());
        String text;
        if (wicket) {
            String how = switch (dismissal) {
                case BOWLED -> "bowled";
                case LBW -> "given out lbw";
                case STUMPED -> "stumped";
                case RUN_OUT -> "run out";
                default -> "caught";
            };
            text = "OUT! " + batter.getName() + " is " + how + ".";
        } else {
            text = switch (extra) {
                case WIDE -> runs > 0 ? "wide, plus " + runs + " run(s) taken" : "wide";
                case NO_BALL -> "no ball, " + runsText(runs) + " off the bat";
                case BYE -> runs + " bye(s)";
                case LEG_BYE -> runs + " leg bye(s)";
                default -> runs == 4 ? "FOUR!" : runs == 6 ? "SIX!" : runsText(runs);
            };
        }
        return e.getOverNumber() + "." + e.getBallInOver() + "  " + bowler.getName() + " to "
                + batter.getName() + ", " + text;
    }

    private String runsText(int runs) {
        return runs == 0 ? "no run" : runs + (runs == 1 ? " run" : " runs");
    }

    // ------------------------------------------------------------------ helpers

    private CricketMatch find(Long id) {
        return matches.findById(id).orElseThrow(() -> new NotFoundException("Match " + id + " not found"));
    }

    private CricketMatch lock(Long id) {
        return matches.findForUpdate(id).orElseThrow(() -> new NotFoundException("Match " + id + " not found"));
    }

    private void requireLive(CricketMatch m) {
        if (m.getStatus() != MatchStatus.LIVE) {
            throw new BadRequestException("Match is not live (status: " + m.getStatus() + ")");
        }
    }

    /** Builds the DTO now, but only pushes it to browsers once the transaction has really committed. */
    private MatchDto publish(CricketMatch m) {
        MatchDto dto = view.toDto(m);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    live.publish("match", dto);
                }
            });
        } else {
            live.publish("match", dto);
        }
        return dto;
    }
}
