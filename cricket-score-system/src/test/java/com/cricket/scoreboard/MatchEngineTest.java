package com.cricket.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.cricket.scoreboard.dto.Dtos.BallRequest;
import com.cricket.scoreboard.dto.Dtos.BatterLine;
import com.cricket.scoreboard.dto.Dtos.BowlerLine;
import com.cricket.scoreboard.dto.Dtos.CreateMatchRequest;
import com.cricket.scoreboard.dto.Dtos.InningsSummary;
import com.cricket.scoreboard.dto.Dtos.MatchDto;
import com.cricket.scoreboard.dto.Dtos.Scorecard;
import com.cricket.scoreboard.dto.Dtos.TeamDto;
import com.cricket.scoreboard.exception.BadRequestException;
import com.cricket.scoreboard.model.ExtraType;
import com.cricket.scoreboard.model.MatchStatus;
import com.cricket.scoreboard.service.MatchService;
import com.cricket.scoreboard.service.StatsService;

import com.cricket.scoreboard.repository.TeamRepository;
import com.cricket.scoreboard.dto.Dtos;

// the background simulator is switched off so the tests control every ball themselves
@SpringBootTest(properties = "cricket.simulation.enabled=false")
class MatchEngineTest {

    @Autowired MatchService matchService;
    @Autowired StatsService statsService;
    @Autowired TeamRepository teams;

    private MatchDto newManualMatch(int overs) {
        List<TeamDto> all = teams.findAll().stream().map(Dtos::team).toList();
        return matchService.create(new CreateMatchRequest(all.get(0).id(), all.get(1).id(), overs, "Test Ground", false));
    }

    @Test
    void seededCompletedMatchHasConsistentScorecard() {
        MatchDto completed = matchService.list(MatchStatus.COMPLETED).get(0);
        assertNotNull(completed.result());

        Scorecard card = matchService.scorecard(completed.id());
        assertEquals(2, card.innings().size());
        for (InningsSummary innings : card.innings()) {
            int batRuns = innings.batting().stream().mapToInt(BatterLine::runs).sum();
            assertEquals(innings.runs(), batRuns + innings.extras(), "bat runs + extras must equal team total");
            int bowlerWickets = innings.bowling().stream().mapToInt(BowlerLine::wickets).sum();
            assertTrue(bowlerWickets <= innings.wickets());
        }
        // innings 2 total in the scorecard matches the live match row
        assertEquals(completed.runs(), card.innings().get(1).runs());
    }

    @Test
    void strikeRotatesOnOddRunsAndBowlerChangesAfterAnOver() {
        MatchDto m = matchService.start(newManualMatch(5).id());
        assertEquals(MatchStatus.LIVE, m.status());
        String firstStriker = m.striker().name();
        String firstBowler = m.bowler().name();

        m = matchService.recordBall(m.id(), new BallRequest(1, ExtraType.NONE, false, null));
        assertEquals(1, m.runs());
        assertEquals("0.1", m.overs());
        assertNotEquals(firstStriker, m.striker().name(), "odd runs swap the strike");

        for (int i = 0; i < 5; i++) {
            m = matchService.recordBall(m.id(), new BallRequest(0, ExtraType.NONE, false, null));
        }
        assertEquals("1.0", m.overs());
        assertNotEquals(firstBowler, m.bowler().name(), "a new bowler takes over after each over");
    }

    @Test
    void wideDoesNotCountAsLegalBallAndWicketBringsNewBatter() {
        MatchDto m = matchService.start(newManualMatch(5).id());
        String striker = m.striker().name();

        m = matchService.recordBall(m.id(), new BallRequest(0, ExtraType.WIDE, false, null));
        assertEquals(1, m.runs());
        assertEquals("0.0", m.overs());

        m = matchService.recordBall(m.id(), new BallRequest(0, ExtraType.NONE, true, null));
        assertEquals(1, m.wickets());
        assertNotEquals(striker, m.striker().name());
    }

    @Test
    void invalidBallsAreRejected() {
        MatchDto m = matchService.start(newManualMatch(5).id());
        Long id = m.id();
        assertThrows(BadRequestException.class,
                () -> matchService.recordBall(id, new BallRequest(2, ExtraType.NONE, true, null)));
    }

    @Test
    void fullMatchFinishesWithAResultAndStatsAreAvailable() {
        MatchDto m = matchService.start(newManualMatch(5).id());
        m = matchService.fastForward(m.id());
        assertEquals(MatchStatus.COMPLETED, m.status());
        assertNotNull(m.result());
        assertTrue(statsService.topBatters(3).size() > 0);
        assertTrue(statsService.topBatters(1).get(0).value() > 0);
    }
}
