package com.cricket.scoreboard.service;

import java.util.Random;

import org.springframework.stereotype.Component;

import com.cricket.scoreboard.dto.Dtos.BallRequest;
import com.cricket.scoreboard.model.CricketMatch;
import com.cricket.scoreboard.model.DismissalType;
import com.cricket.scoreboard.model.ExtraType;

/**
 * Produces realistic random deliveries for simulated matches.
 * Batters get more aggressive in the powerplay, the death overs and when the chase gets steep.
 * A typical 20-over innings lands around 150-170 runs.
 */
@Component
public class BallGenerator {

    private final Random random = new Random();

    public BallRequest generate(CricketMatch m) {
        double aggression = aggression(m);
        double r = random.nextDouble();

        if (r < 0.035) {
            return new BallRequest(random.nextDouble() < 0.88 ? 0 : 1, ExtraType.WIDE, false, null);
        }
        if (r < 0.050) {
            return new BallRequest(pickRuns(aggression), ExtraType.NO_BALL, false, null);
        }
        if (r < 0.060) {
            return new BallRequest(1, ExtraType.BYE, false, null);
        }
        if (r < 0.075) {
            return new BallRequest(1, ExtraType.LEG_BYE, false, null);
        }
        if (random.nextDouble() < 0.045 * aggression) {
            DismissalType type = pickDismissal();
            int runs = type == DismissalType.RUN_OUT ? random.nextInt(2) : 0;
            return new BallRequest(runs, ExtraType.NONE, true, type);
        }
        return new BallRequest(pickRuns(aggression), ExtraType.NONE, false, null);
    }

    private double aggression(CricketMatch m) {
        int over = m.getLegalBalls() / 6;
        double factor = 1.0;
        if (over < 6) {
            factor = 1.1;
        } else if (over >= m.getTotalOvers() * 0.75) {
            factor = 1.35;
        }
        if (m.getInnings() == 2) {
            int needed = m.getTarget() - m.getRuns();
            int left = m.getTotalOvers() * 6 - m.getLegalBalls();
            if (left > 0 && needed * 6.0 / left > 10) {
                factor *= 1.25;
            }
        }
        return factor;
    }

    private int pickRuns(double aggression) {
        int[] runs = {0, 1, 2, 3, 4, 6};
        double[] weights = {40, 32, 9, 1, 11 * aggression, 5 * aggression};
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double pick = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick <= 0) {
                return runs[i];
            }
        }
        return 0;
    }

    private DismissalType pickDismissal() {
        double r = random.nextDouble();
        if (r < 0.25) return DismissalType.BOWLED;
        if (r < 0.70) return DismissalType.CAUGHT;
        if (r < 0.85) return DismissalType.LBW;
        if (r < 0.90) return DismissalType.STUMPED;
        return DismissalType.RUN_OUT;
    }
}
