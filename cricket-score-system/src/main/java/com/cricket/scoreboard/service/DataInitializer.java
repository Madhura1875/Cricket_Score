package com.cricket.scoreboard.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.cricket.scoreboard.dto.Dtos.CreateMatchRequest;
import com.cricket.scoreboard.model.Player;
import com.cricket.scoreboard.model.PlayerRole;
import com.cricket.scoreboard.model.Team;
import com.cricket.scoreboard.repository.PlayerRepository;
import com.cricket.scoreboard.repository.TeamRepository;

/**
 * Seeds four fictional teams (11 invented players each) and three demo matches:
 * one LIVE (simulated), one UPCOMING and one already COMPLETED so stats and summaries have data.
 */
@Component
@ConditionalOnProperty(name = "cricket.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String[] FIRST = {
            "Aarav", "Vihaan", "Arjun", "Kabir", "Rohan", "Ishaan", "Dev", "Karan", "Yash", "Neil", "Sameer",
            "Rahul", "Aditya", "Nikhil", "Varun", "Harsh", "Tanay", "Om", "Rudra", "Parth", "Veer", "Lakshya",
            "Dhruv", "Manav", "Ayaan", "Kunal", "Rehan", "Siddharth", "Tushar", "Zayan", "Pranav", "Jai",
            "Mihir", "Shaan", "Abhay", "Naman", "Raghav", "Samar", "Vedant", "Anay", "Kian", "Ritvik",
            "Daksh", "Eshan"};
    private static final String[] LAST = {
            "Sharma", "Verma", "Iyer", "Nair", "Reddy", "Kulkarni", "Mehta", "Joshi", "Patil", "Singh", "Khan",
            "Das", "Bose", "Menon", "Gill", "Rao", "Kapoor", "Malhotra", "Bhatt", "Desai"};

    private static final PlayerRole[] ROLES = {
            PlayerRole.BATSMAN, PlayerRole.BATSMAN, PlayerRole.BATSMAN, PlayerRole.BATSMAN,
            PlayerRole.WICKET_KEEPER, PlayerRole.ALL_ROUNDER, PlayerRole.ALL_ROUNDER,
            PlayerRole.BOWLER, PlayerRole.BOWLER, PlayerRole.BOWLER, PlayerRole.BOWLER};

    private final TeamRepository teams;
    private final PlayerRepository players;
    private final MatchService matchService;

    public DataInitializer(TeamRepository teams, PlayerRepository players, MatchService matchService) {
        this.teams = teams;
        this.players = players;
        this.matchService = matchService;
    }

    @Override
    public void run(String... args) {
        if (teams.count() > 0) {
            return;
        }
        List<Team> created = new ArrayList<>();
        String[][] defs = {
                {"Mumbai Mariners", "MUM"}, {"Delhi Dynamos", "DEL"},
                {"Chennai Chargers", "CHN"}, {"Bengaluru Blazers", "BLR"}};
        int nameIndex = 0;
        for (String[] def : defs) {
            Team team = teams.save(new Team(def[0], def[1]));
            created.add(team);
            List<Player> squad = new ArrayList<>();
            for (PlayerRole role : ROLES) {
                String name = FIRST[nameIndex] + " " + LAST[(nameIndex * 7) % LAST.length];
                squad.add(new Player(name, role, team));
                nameIndex++;
            }
            players.saveAll(squad);
        }

        // 1) a live match that plays itself
        Long live = matchService.create(new CreateMatchRequest(
                created.get(0).getId(), created.get(1).getId(), 20, "Wankhede Park Arena", true)).id();
        matchService.start(live);

        // 2) an upcoming match, to be started from the dashboard
        matchService.create(new CreateMatchRequest(
                created.get(2).getId(), created.get(3).getId(), 20, "Chepauk Oval", true));

        // 3) a match that is already finished, so summaries and leaderboards are populated
        Long done = matchService.create(new CreateMatchRequest(
                created.get(0).getId(), created.get(2).getId(), 20, "Eden Gardens Ground", false)).id();
        matchService.start(done);
        matchService.fastForward(done);

        log.info("Seeded {} teams and 3 demo matches", created.size());
    }
}
