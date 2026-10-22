package com.cricket.scoreboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cricket.scoreboard.model.CricketMatch;
import com.cricket.scoreboard.model.MatchStatus;
import com.cricket.scoreboard.repository.CricketMatchRepository;

/**
 * Plays one delivery per tick in every live match that has simulation switched on.
 * This is what makes the platform "live" without a real data feed.
 *
 * Set cricket.simulation.enabled=false to turn it off (for example when real scorers use the API).
 * If you run several instances, guard tick() with a distributed lock such as ShedLock.
 */
@Component
@ConditionalOnProperty(name = "cricket.simulation.enabled", havingValue = "true", matchIfMissing = true)
public class MatchSimulator {

    private static final Logger log = LoggerFactory.getLogger(MatchSimulator.class);

    private final CricketMatchRepository matches;
    private final MatchService service;
    private final BallGenerator generator;

    public MatchSimulator(CricketMatchRepository matches, MatchService service, BallGenerator generator) {
        this.matches = matches;
        this.service = service;
        this.generator = generator;
    }

    @Scheduled(fixedDelayString = "${cricket.simulation.interval-ms:2500}",
            initialDelayString = "${cricket.simulation.initial-delay-ms:3000}")
    public void tick() {
        for (CricketMatch m : matches.findByStatusAndSimulatedTrue(MatchStatus.LIVE)) {
            try {
                service.recordBall(m.getId(), generator.generate(m));
            } catch (Exception e) {
                log.warn("Simulation tick skipped for match {}: {}", m.getId(), e.getMessage());
            }
        }
    }
}
