package com.cricket.scoreboard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.scoreboard.dto.Dtos.LeaderEntry;
import com.cricket.scoreboard.dto.Dtos.PlayerStatsDto;
import com.cricket.scoreboard.service.StatsService;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/players/{id}/stats")
    public PlayerStatsDto playerStats(@PathVariable Long id) {
        return stats.playerStats(id);
    }

    @GetMapping("/stats/top-batters")
    public List<LeaderEntry> topBatters(@RequestParam(defaultValue = "5") int limit) {
        return stats.topBatters(limit);
    }

    @GetMapping("/stats/top-bowlers")
    public List<LeaderEntry> topBowlers(@RequestParam(defaultValue = "5") int limit) {
        return stats.topBowlers(limit);
    }
}
