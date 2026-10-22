package com.cricket.scoreboard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.scoreboard.dto.Dtos;
import com.cricket.scoreboard.dto.Dtos.PlayerDto;
import com.cricket.scoreboard.dto.Dtos.TeamDto;
import com.cricket.scoreboard.exception.NotFoundException;
import com.cricket.scoreboard.repository.PlayerRepository;
import com.cricket.scoreboard.repository.TeamRepository;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teams;
    private final PlayerRepository players;

    public TeamController(TeamRepository teams, PlayerRepository players) {
        this.teams = teams;
        this.players = players;
    }

    @GetMapping
    public List<TeamDto> list() {
        return teams.findAll().stream().map(Dtos::team).toList();
    }

    @GetMapping("/{id}/players")
    public List<PlayerDto> squad(@PathVariable Long id) {
        if (!teams.existsById(id)) {
            throw new NotFoundException("Team " + id + " not found");
        }
        return players.findByTeamIdOrderByIdAsc(id).stream().map(Dtos::player).toList();
    }
}
