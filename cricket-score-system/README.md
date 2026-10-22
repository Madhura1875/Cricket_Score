# CricketLive - Real-Time Cricket Score Management System

A Spring Boot application that behaves like a live cricket scoring platform. It keeps match state,
processes every delivery (runs, extras, wickets, strike rotation, overs, innings, results), exposes
everything through REST APIs, and pushes live updates to a responsive web dashboard.

Because there is no real data feed, a built-in **simulator** plays matches ball by ball so the whole
platform is live the moment you start it. You can also score matches yourself from the dashboard or the API.

> **Status note:** the code was written and syntax-checked in an environment that could not download
> Maven dependencies, so it has **not been compiled or run yet**. Run `mvn test` first (step 4 below).
> If anything fails to compile on your machine, the error message will point at the exact line.

---

## 1. What the app does

| Requirement | How it is covered |
|---|---|
| Fetch, process and display live match info | Simulator (or a scorer) creates deliveries -> `MatchService` processes them -> REST API and SSE stream serve them -> dashboard displays them |
| REST APIs | `/api/matches`, `/api/teams`, `/api/players/{id}/stats`, `/api/stats/...` |
| Real-time | Server-Sent Events at `/api/stream`; the dashboard updates without refreshing |
| Ongoing matches | Match list with LIVE / UPCOMING / COMPLETED filters, live score, run rate, target and required rate |
| Score updates | `POST /api/matches/{id}/balls` (manual) or the automatic simulator |
| Player statistics | Per-player career stats and Top-5 runs / wickets leaderboards, computed by the database |
| Match summaries | Full scorecards, fall of wickets, result text, top scorer, best bowler |
| Scalable | Stateless app, append-only ball events, indexes, row locking, database aggregates, Postgres profile |

---

## 2. Architecture

```
 Browser (index.html + app.js)
    |  REST (fetch)            ^  live updates (EventSource / SSE)
    v                          |
 Controllers  ---------->  MatchService  ---- after commit ---->  LiveUpdateService
 (REST layer)              (scoring engine)                        (broadcasts to browsers)
                              |   ^
              MatchSimulator -+   +-- ScorecardService / MatchViewService / StatsService
              (1 ball / tick)          (read models built from ball events)
                              |
                         Repositories (Spring Data JPA)
                              |
                    H2 (default)  or  PostgreSQL
```

Key ideas:

* **Every delivery is stored as a `BallEvent`.** Scorecards, commentary and player stats are derived from
  these rows, so the data is append-only and easy to audit.
* **The `CricketMatch` row only holds running totals** (score, wickets, balls, current batters and bowler),
  so the live view is cheap to read.
* **Updates are broadcast only after the database transaction commits**, so browsers never see data that
  was later rolled back.
* **A row lock (`PESSIMISTIC_WRITE`) plus `@Version`** stops two scorers, or a scorer and the simulator,
  from corrupting the same match.

---

## 3. Prerequisites (install these first)

| Tool | Version | Check with |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.9 or newer | `mvn -v` |
| Git | any recent | `git --version` |
| IDE (optional) | IntelliJ IDEA Community or VS Code with the "Extension Pack for Java" | |

No database installation is needed: the default database is in-memory H2.

---

## 4. Run it (quick start)

```bash
cd cricket-score-system

# 1. run the automated tests (play full matches and check the scoring rules)
mvn test

# 2. start the application
mvn spring-boot:run
```

Then open **http://localhost:8080** in your browser.

What you will see:
* `Mumbai Mariners vs Delhi Dynamos` is **LIVE** and scores one ball every 2.5 seconds.
* `Chennai Chargers vs Bengaluru Blazers` is **UPCOMING** - open it and press *Start match (toss)*.
* `Mumbai Mariners vs Chennai Chargers` is **COMPLETED** - open it to see the scorecard and match summary.
* Press *Pause simulation* on a live match to score balls yourself with the run buttons.

Build a runnable jar instead:

```bash
mvn clean package
java -jar target/cricket-score-system-1.0.0.jar
```

Useful URLs:

| URL | Purpose |
|---|---|
| http://localhost:8080 | Dashboard |
| http://localhost:8080/api/matches | All matches (JSON) |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/h2-console | Database console (JDBC URL `jdbc:h2:mem:cricketdb`, user `sa`, empty password) |

Configuration lives in `src/main/resources/application.properties`:

| Property | Default | Meaning |
|---|---|---|
| `server.port` | 8080 | Change if the port is busy |
| `cricket.simulation.enabled` | true | Turn the automatic simulator on or off |
| `cricket.simulation.interval-ms` | 2500 | Milliseconds between simulated balls (lower = faster) |
| `cricket.seed.enabled` | true | Create demo teams and matches at startup |

---

## 5. Project structure (every file and what it does)

```
cricket-score-system/
|-- pom.xml                                   Maven build: dependencies and Java version
|-- README.md                                 this guide
|-- .gitignore                                files Git must not track
|-- src/main/java/com/cricket/scoreboard/
|   |-- CricketScoreApplication.java          application entry point (main method, enables scheduling)
|   |-- config/
|   |   `-- WebConfig.java                    CORS so a separate frontend can call /api
|   |-- model/                                database entities and enums
|   |   |-- Team.java                         a team
|   |   |-- Player.java                       a player (role, team)
|   |   |-- CricketMatch.java                 current state of a match (score, batters, bowler, result)
|   |   |-- BallEvent.java                    one delivery - the source of all scorecards and stats
|   |   |-- MatchStatus.java                  UPCOMING, LIVE, COMPLETED
|   |   |-- PlayerRole.java                   BATSMAN, BOWLER, ALL_ROUNDER, WICKET_KEEPER
|   |   |-- ExtraType.java                    NONE, WIDE, NO_BALL, BYE, LEG_BYE
|   |   `-- DismissalType.java                BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED
|   |-- repository/                           database access (Spring Data JPA)
|   |   |-- TeamRepository.java
|   |   |-- PlayerRepository.java
|   |   |-- CricketMatchRepository.java       includes the row-locking query
|   |   `-- BallEventRepository.java          ball queries and the stats aggregations
|   |-- dto/
|   |   `-- Dtos.java                         every request and response shape of the API
|   |-- service/                              the business logic
|   |   |-- MatchService.java                 scoring engine: toss, ball processing, overs, innings, result
|   |   |-- ScorecardService.java             builds batting / bowling scorecards from ball events
|   |   |-- MatchViewService.java             converts a match into the dashboard JSON
|   |   |-- StatsService.java                 player stats and leaderboards
|   |   |-- LiveUpdateService.java            Server-Sent Events broadcaster
|   |   |-- PlayerDirectory.java              in-memory player and squad lookup
|   |   |-- BallGenerator.java                realistic random deliveries for the simulator
|   |   |-- MatchSimulator.java               scheduled job that plays one ball per tick
|   |   `-- DataInitializer.java              seeds demo teams, players and matches
|   |-- controller/                           REST endpoints
|   |   |-- MatchController.java              /api/matches/...
|   |   |-- TeamController.java               /api/teams/...
|   |   |-- StatsController.java              /api/players/{id}/stats, /api/stats/...
|   |   `-- StreamController.java             /api/stream (live events)
|   |-- exception/                            clean JSON error responses (400, 404, 409)
|   |   |-- NotFoundException.java
|   |   |-- BadRequestException.java
|   |   `-- GlobalExceptionHandler.java
|   `-- util/
|       `-- Fmt.java                          overs and rounding helpers
|-- src/main/resources/
|   |-- application.properties                default configuration (H2)
|   |-- application-postgres.properties       PostgreSQL profile
|   `-- static/                               the dashboard, served by Spring Boot itself
|       |-- index.html                        page layout
|       |-- css/style.css                     responsive styling
|       `-- js/app.js                         REST calls, live stream, rendering
`-- src/test/java/com/cricket/scoreboard/
    `-- MatchEngineTest.java                  tests for scoring rules and scorecard consistency
```

---

## 6. How to build it step by step from scratch

Use this if you want to type the project yourself instead of using the provided files.

1. **Install** JDK 17+, Maven, Git and an IDE (section 3).
2. **Generate the skeleton** at https://start.spring.io with: Project *Maven*, Language *Java*,
   Spring Boot *3.3.x*, Group `com.cricket`, Artifact `cricket-score-system`, Packaging *Jar*, Java *17*.
   Add dependencies: **Spring Web, Spring Data JPA, Validation, Spring Boot Actuator, H2 Database, PostgreSQL Driver**.
   Download, unzip, open in the IDE.
3. **Create packages** under `com.cricket.scoreboard`: `model`, `repository`, `dto`, `service`, `controller`, `exception`, `util`, `config`.
4. **Write the data model** (`model/`): the four enums, then `Team`, `Player`, `CricketMatch`, `BallEvent`.
5. **Write the repositories** (`repository/`): they are interfaces, Spring generates the implementations.
6. **Write `Dtos.java`** so the API never exposes entities directly.
7. **Write the engine** (`service/`), in this order: `PlayerDirectory` -> `ScorecardService` -> `MatchViewService`
   -> `LiveUpdateService` -> `MatchService` -> `StatsService` -> `BallGenerator` -> `MatchSimulator` -> `DataInitializer`.
8. **Write the controllers** (`controller/`) and the exception handler (`exception/`).
9. **Configure** `application.properties` (database, simulator speed, seeding).
10. **Build the dashboard** in `src/main/resources/static/` (`index.html`, `css/style.css`, `js/app.js`).
    Spring Boot serves anything in `static/` automatically.
11. **Write tests** in `src/test/java` and run `mvn test`.
12. **Run** with `mvn spring-boot:run` and open http://localhost:8080.
13. **Put it on Git** (section 8).

---

## 7. REST API reference

| Method | Path | Description |
|---|---|---|
| GET | `/api/matches?status=LIVE` | List matches (status optional: LIVE, UPCOMING, COMPLETED) |
| GET | `/api/matches/{id}` | One match with score, batters, bowler, recent balls |
| POST | `/api/matches` | Create a match |
| POST | `/api/matches/{id}/start` | Toss and start the first innings |
| POST | `/api/matches/{id}/balls` | Score one delivery |
| GET | `/api/matches/{id}/balls?limit=30` | Ball-by-ball commentary, newest first |
| GET | `/api/matches/{id}/scorecard` | Full scorecard for both innings |
| POST | `/api/matches/{id}/simulation?enabled=false` | Pause or resume automatic simulation |
| POST | `/api/matches/{id}/fast-forward` | Play the rest of a live match instantly |
| GET | `/api/teams` | All teams |
| GET | `/api/teams/{id}/players` | A team's squad |
| GET | `/api/players/{id}/stats` | Career statistics of a player |
| GET | `/api/stats/top-batters?limit=5` | Leaderboard: most runs |
| GET | `/api/stats/top-bowlers?limit=5` | Leaderboard: most wickets |
| GET | `/api/stream` | Live Server-Sent Events (event name: `match`) |

Try it from a terminal:

```bash
# create a 5-over match between teams 3 and 4, scored manually (simulate=false)
curl -X POST localhost:8080/api/matches -H "Content-Type: application/json" \
  -d '{"team1Id":3,"team2Id":4,"totalOvers":5,"venue":"My Ground","simulate":false}'

# start it (use the id returned above, for example 4)
curl -X POST localhost:8080/api/matches/4/start

# score a single, a four, a wide and a wicket
curl -X POST localhost:8080/api/matches/4/balls -H "Content-Type: application/json" -d '{"runs":1}'
curl -X POST localhost:8080/api/matches/4/balls -H "Content-Type: application/json" -d '{"runs":4}'
curl -X POST localhost:8080/api/matches/4/balls -H "Content-Type: application/json" -d '{"extraType":"WIDE","runs":0}'
curl -X POST localhost:8080/api/matches/4/balls -H "Content-Type: application/json" -d '{"wicket":true,"dismissalType":"BOWLED"}'

# scorecard, player stats, live stream
curl localhost:8080/api/matches/4/scorecard
curl localhost:8080/api/players/1/stats
curl -N localhost:8080/api/stream
```

Ball request fields: `runs` (0-7), `extraType` (`NONE`, `WIDE`, `NO_BALL`, `BYE`, `LEG_BYE`),
`wicket` (true/false), `dismissalType` (`BOWLED`, `CAUGHT`, `LBW`, `STUMPED`, `RUN_OUT`).
For extras, `runs` means runs actually run; the penalty run for wides and no-balls is added automatically.

Scoring rules implemented: odd runs swap the batters, the batters swap at the end of an over, wides and
no-balls do not count as legal balls, byes and leg-byes are not charged to the bowler, a new bowler takes
over each over, the innings ends on 10 wickets, all overs bowled or the target being reached, and the
result is stated as a win by runs / wickets or a tie.

---

## 8. Connect the project to Git and GitHub

**One-time setup on your computer**

```bash
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"
```

**Step 1 - create an empty repository on GitHub**
Go to https://github.com/new, name it `cricket-score-system`, leave "Add a README / .gitignore / licence"
**unticked** (the project already has them), click *Create repository*, and copy its URL.

**Step 2 - turn the project folder into a Git repository and push**

```bash
cd cricket-score-system
git init
git add .
git commit -m "Initial commit: Spring Boot cricket score system"
git branch -M main
git remote add origin https://github.com/<your-username>/cricket-score-system.git
git push -u origin main
```

**Authentication:** GitHub no longer accepts account passwords for `git push`. Either
* sign in with the GitHub CLI: `gh auth login` (easiest), or
* when Git asks for a password, paste a **Personal Access Token** (GitHub -> Settings -> Developer settings -> Tokens), or
* use SSH: add your key to GitHub, then use `git@github.com:<your-username>/cricket-score-system.git` as the remote URL.

**Everyday workflow**

```bash
git status                       # what changed
git add .                        # stage changes
git commit -m "Describe the change"
git push                         # upload

git pull                         # download changes from GitHub
git checkout -b feature/player-profiles   # new branch for a feature
git push -u origin feature/player-profiles
```

**Common problems**

| Message | Fix |
|---|---|
| `remote origin already exists` | `git remote set-url origin <url>` |
| `failed to push ... fetch first` / `rejected` | `git pull --rebase origin main`, then `git push` |
| `Authentication failed` | use a Personal Access Token or `gh auth login` |
| Files like `target/` appear in `git status` | `.gitignore` is in the wrong place; it must be in the project root |

---

## 9. Scaling and production notes

What already helps scalability:
* the app is **stateless** (all state is in the database), so you can run several instances behind a load balancer;
* ball events are **append-only** and **indexed** by match, batter and bowler;
* leaderboards and player stats use **database aggregates**, not in-memory loops;
* browsers receive pushes through **one lightweight SSE connection**, not by polling;
* writes to one match are serialised with a row lock, so scoring stays correct under concurrency.

Next steps for a real deployment:
1. **Use PostgreSQL:** `java -jar target/cricket-score-system-1.0.0.jar --spring.profiles.active=postgres`
   with `DB_URL`, `DB_USER`, `DB_PASSWORD` environment variables (profile provided, not yet tested).
2. **Several instances:** SSE connections are per instance. Publish score events to Redis pub/sub or Kafka
   and let every instance forward them to its own browsers (replace the body of `LiveUpdateService.publish`).
3. **Run the simulator on one instance only**, or guard `MatchSimulator.tick()` with a distributed lock
   such as ShedLock; set `cricket.simulation.enabled=false` everywhere else.
4. **Cache** the leaderboards (Spring Cache + Redis) if they get many reads.
5. **Real data:** replace `MatchSimulator` with a client that calls a licensed cricket data provider and
   feeds `MatchService.recordBall(...)`. Everything downstream (scorecards, stats, dashboard) keeps working.
6. **Security:** add Spring Security so only authorised scorers can call the `POST` endpoints.

Known simplifications: playing XI is the first 11 players of a team in fixed batting order; run-outs always
dismiss the striker; no super over, rain rules or retired batters; "caught" does not record the fielder.

---

## 10. Troubleshooting

| Problem | Fix |
|---|---|
| `release version 17 not supported` | Your JDK is older than 17. Install JDK 17 or 21 and check `java -version` |
| `Port 8080 was already in use` | Set `server.port=8081` in `application.properties` |
| Dashboard shows "Reconnecting..." | The backend stopped, or a proxy is buffering SSE; the page reconnects by itself |
| Nothing is scoring | Check `cricket.simulation.enabled=true`, and that the match is LIVE with simulation not paused |
| Data disappears after restart | H2 is in-memory by default; use the `jdbc:h2:file:./data/cricketdb` URL shown in `application.properties` |
