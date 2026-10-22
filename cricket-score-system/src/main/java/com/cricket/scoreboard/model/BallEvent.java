package com.cricket.scoreboard.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One delivery. Scorecards, player statistics and commentary are all derived from these rows,
 * which makes the system append-only and easy to audit or replay.
 */
@Entity
@Table(name = "ball_events", indexes = {
        @Index(name = "idx_ball_match_innings", columnList = "match_id, innings"),
        @Index(name = "idx_ball_batsman", columnList = "batsman_id"),
        @Index(name = "idx_ball_bowler", columnList = "bowler_id")
})
public class BallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id")
    private Long matchId;
    private int innings;
    /** 0-based over index. */
    private int overNumber;
    /** 1-based number of the ball being bowled in the over. */
    private int ballInOver;

    @Column(name = "batsman_id")
    private Long batsmanId;
    private Long nonStrikerId;
    @Column(name = "bowler_id")
    private Long bowlerId;

    private int runsOffBat;
    @Enumerated(EnumType.STRING)
    private ExtraType extraType = ExtraType.NONE;
    private int extraRuns;
    private int totalRuns;
    /** Runs charged to the bowler (bat runs + wides + no-balls, not byes/leg-byes). */
    private int runsCharged;
    private boolean legalBall;

    private boolean wicket;
    @Enumerated(EnumType.STRING)
    private DismissalType dismissalType = DismissalType.NONE;
    private Long dismissedPlayerId;
    /** True when the wicket is credited to the bowler (not a run-out). */
    private boolean bowlerWicket;

    private String label;
    @Column(length = 300)
    private String commentary;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public int getInnings() { return innings; }
    public void setInnings(int innings) { this.innings = innings; }
    public int getOverNumber() { return overNumber; }
    public void setOverNumber(int overNumber) { this.overNumber = overNumber; }
    public int getBallInOver() { return ballInOver; }
    public void setBallInOver(int ballInOver) { this.ballInOver = ballInOver; }
    public Long getBatsmanId() { return batsmanId; }
    public void setBatsmanId(Long batsmanId) { this.batsmanId = batsmanId; }
    public Long getNonStrikerId() { return nonStrikerId; }
    public void setNonStrikerId(Long nonStrikerId) { this.nonStrikerId = nonStrikerId; }
    public Long getBowlerId() { return bowlerId; }
    public void setBowlerId(Long bowlerId) { this.bowlerId = bowlerId; }
    public int getRunsOffBat() { return runsOffBat; }
    public void setRunsOffBat(int runsOffBat) { this.runsOffBat = runsOffBat; }
    public ExtraType getExtraType() { return extraType; }
    public void setExtraType(ExtraType extraType) { this.extraType = extraType; }
    public int getExtraRuns() { return extraRuns; }
    public void setExtraRuns(int extraRuns) { this.extraRuns = extraRuns; }
    public int getTotalRuns() { return totalRuns; }
    public void setTotalRuns(int totalRuns) { this.totalRuns = totalRuns; }
    public int getRunsCharged() { return runsCharged; }
    public void setRunsCharged(int runsCharged) { this.runsCharged = runsCharged; }
    public boolean isLegalBall() { return legalBall; }
    public void setLegalBall(boolean legalBall) { this.legalBall = legalBall; }
    public boolean isWicket() { return wicket; }
    public void setWicket(boolean wicket) { this.wicket = wicket; }
    public DismissalType getDismissalType() { return dismissalType; }
    public void setDismissalType(DismissalType dismissalType) { this.dismissalType = dismissalType; }
    public Long getDismissedPlayerId() { return dismissedPlayerId; }
    public void setDismissedPlayerId(Long dismissedPlayerId) { this.dismissedPlayerId = dismissedPlayerId; }
    public boolean isBowlerWicket() { return bowlerWicket; }
    public void setBowlerWicket(boolean bowlerWicket) { this.bowlerWicket = bowlerWicket; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getCommentary() { return commentary; }
    public void setCommentary(String commentary) { this.commentary = commentary; }
    public Instant getCreatedAt() { return createdAt; }
}
