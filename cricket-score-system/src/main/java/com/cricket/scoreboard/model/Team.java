package com.cricket.scoreboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, length = 5)
    private String shortName;

    protected Team() {
    }

    public Team(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
}
