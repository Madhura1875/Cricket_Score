package com.cricket.scoreboard.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.cricket.scoreboard.exception.NotFoundException;
import com.cricket.scoreboard.model.Player;
import com.cricket.scoreboard.repository.PlayerRepository;

/**
 * In-memory lookup for players and squads. Players never change while the app runs,
 * so every delivery can resolve names without touching the database.
 */
@Component
public class PlayerDirectory {

    private final PlayerRepository repository;
    private final Map<Long, Player> byId = new ConcurrentHashMap<>();
    private final Map<Long, List<Player>> squads = new ConcurrentHashMap<>();

    public PlayerDirectory(PlayerRepository repository) {
        this.repository = repository;
    }

    public Player get(Long id) {
        Player cached = byId.get(id);
        if (cached != null) {
            return cached;
        }
        Player loaded = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Player " + id + " not found"));
        byId.put(id, loaded);
        return loaded;
    }

    /** Playing XI = the first 11 players of the team, in batting order. */
    public List<Player> squad(Long teamId) {
        List<Player> cached = squads.get(teamId);
        if (cached != null) {
            return cached;
        }
        List<Player> all = repository.findByTeamIdOrderByIdAsc(teamId);
        List<Player> xi = List.copyOf(all.size() > 11 ? all.subList(0, 11) : all);
        squads.put(teamId, xi);
        return xi;
    }
}
