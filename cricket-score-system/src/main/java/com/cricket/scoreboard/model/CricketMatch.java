package com.cricket.scoreboard.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Current state of a match. Every delivery is stored separately as a {@link BallEvent};
 * this row only keeps the running totals so reads stay cheap.
 */
@Entity
@Table(name = "cricket_matches", indexes = @Index(name = "idx_match_status", columnList = "status"))
public class CricketMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String venue;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team1_id")
    private Team team1;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team2_id")
    private Team team2;

    private int totalOvers;

    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.UPCOMING;

    private String tossSummary;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batting_team_id")
    private Team battingTeam;

    /** 0 = not started, 1 or 2 = innings in progress / finished. */
    private int innings;
    private int runs;
    private int wickets;
    private int legalBalls;
    /** Runs the chasing side needs to win (innings 2 only). */
    private int target;

    private Long strikerId;
    private Long nonStrikerId;
    private Long bowlerId;

    private int firstInningsRuns;
    private int firstInningsWickets;
    private int firstInningsBalls;

    private String result;
    private boolean simulated;
    private Instant createdAt = Instant.now();

    /** Optimistic locking safety net (scoring also takes a row lock). */
    @Version
    private Long version;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public Team getTeam1() { return team1; }
    public void setTeam1(Team team1) { this.team1 = team1; }
    public Team getTeam2() { return team2; }
    public void setTeam2(Team team2) { this.team2 = team2; }
    public int getTotalOvers() { return totalOvers; }
    public void setTotalOvers(int totalOvers) { this.totalOvers = totalOvers; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public String getTossSummary() { return tossSummary; }
    public void setTossSummary(String tossSummary) { this.tossSummary = tossSummary; }
    public Team getBattingTeam() { return battingTeam; }
    public void setBattingTeam(Team battingTeam) { this.battingTeam = battingTeam; }
    public int getInnings() { return innings; }
    public void setInnings(int innings) { this.innings = innings; }
    public int getRuns() { return runs; }
    public void setRuns(int runs) { this.runs = runs; }
    public int getWickets() { return wickets; }
    public void setWickets(int wickets) { this.wickets = wickets; }
    public int getLegalBalls() { return legalBalls; }
    public void setLegalBalls(int legalBalls) { this.legalBalls = legalBalls; }
    public int getTarget() { return target; }
    public void setTarget(int target) { this.target = target; }
    public Long getStrikerId() { return strikerId; }
    public void setStrikerId(Long strikerId) { this.strikerId = strikerId; }
    public Long getNonStrikerId() { return nonStrikerId; }
    public void setNonStrikerId(Long nonStrikerId) { this.nonStrikerId = nonStrikerId; }
    public Long getBowlerId() { return bowlerId; }
    public void setBowlerId(Long bowlerId) { this.bowlerId = bowlerId; }
    public int getFirstInningsRuns() { return firstInningsRuns; }
    public void setFirstInningsRuns(int v) { this.firstInningsRuns = v; }
    public int getFirstInningsWickets() { return firstInningsWickets; }
    public void setFirstInningsWickets(int v) { this.firstInningsWickets = v; }
    public int getFirstInningsBalls() { return firstInningsBalls; }
    public void setFirstInningsBalls(int v) { this.firstInningsBalls = v; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }
    public Instant getCreatedAt() { return createdAt; }
}
