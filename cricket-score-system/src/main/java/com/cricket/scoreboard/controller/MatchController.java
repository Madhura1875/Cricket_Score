package com.cricket.scoreboard.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.scoreboard.dto.Dtos.BallDto;
import com.cricket.scoreboard.dto.Dtos.BallRequest;
import com.cricket.scoreboard.dto.Dtos.CreateMatchRequest;
import com.cricket.scoreboard.dto.Dtos.MatchDto;
import com.cricket.scoreboard.dto.Dtos.Scorecard;
import com.cricket.scoreboard.model.MatchStatus;
import com.cricket.scoreboard.service.MatchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    /** GET /api/matches?status=LIVE */
    @GetMapping
    public List<MatchDto> list(@RequestParam(required = false) MatchStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public MatchDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDto create(@Valid @RequestBody CreateMatchRequest request) {
        return service.create(request);
    }

    /** Runs the toss and starts the first innings. */
    @PostMapping("/{id}/start")
    public MatchDto start(@PathVariable Long id) {
        return service.start(id);
    }

    /** Score one delivery manually. */
    @PostMapping("/{id}/balls")
    public MatchDto recordBall(@PathVariable Long id, @Valid @RequestBody BallRequest request) {
        return service.recordBall(id, request);
    }

    /** Ball-by-ball commentary, newest first. */
    @GetMapping("/{id}/balls")
    public List<BallDto> commentary(@PathVariable Long id, @RequestParam(defaultValue = "30") int limit) {
        return service.commentary(id, limit);
    }

    @GetMapping("/{id}/scorecard")
    public Scorecard scorecard(@PathVariable Long id) {
        return service.scorecard(id);
    }

    /** Switch automatic ball-by-ball simulation on or off for this match. */
    @PostMapping("/{id}/simulation")
    public MatchDto simulation(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setSimulation(id, enabled);
    }

    /** Instantly plays out the rest of a live match. */
    @PostMapping("/{id}/fast-forward")
    public MatchDto fastForward(@PathVariable Long id) {
        return service.fastForward(id);
    }
}
