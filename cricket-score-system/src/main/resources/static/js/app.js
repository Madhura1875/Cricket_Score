/* CricketLive dashboard - plain JavaScript, no build step.
 * Data: REST (/api/...) for first load and actions, Server-Sent Events (/api/stream) for live pushes. */
(function () {
  'use strict';

  const $ = (sel, root = document) => root.querySelector(sel);
  const state = {
    matches: new Map(),
    selected: null,
    filter: 'ALL',
    tab: 'commentary',
    scorecard: null,
    scorecardFor: null,
    teams: [],
  };

  // ---------------------------------------------------------------- helpers
  function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  async function api(path, options = {}) {
    const res = await fetch('/api' + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    if (!res.ok) {
      let message = res.statusText;
      try { message = (await res.json()).message || message; } catch (e) { /* ignore */ }
      throw new Error(message);
    }
    return res.status === 204 ? null : res.json();
  }

  function toast(message) {
    const el = $('#toast');
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => el.classList.remove('show'), 3500);
  }

  function throttle(fn, ms) {
    let last = 0, timer = null;
    return (...args) => {
      const now = Date.now();
      if (now - last >= ms) { last = now; fn(...args); return; }
      clearTimeout(timer);
      timer = setTimeout(() => { last = Date.now(); fn(...args); }, ms - (now - last));
    };
  }

  const statusPill = s => `<span class="pill ${s.toLowerCase()}">${s}</span>`;

  function ballClass(label) {
    if (!label) return '';
    if (label === 'W') return 'wicket';
    if (label === '4') return 'four';
    if (label === '6') return 'six';
    if (/^(Wd|Nb|\d+b|\d+lb)/.test(label)) return 'extra';
    return '';
  }

  // ---------------------------------------------------------------- match list
  function sortedMatches() {
    const order = { LIVE: 0, UPCOMING: 1, COMPLETED: 2 };
    return [...state.matches.values()]
      .filter(m => state.filter === 'ALL' || m.status === state.filter)
      .sort((a, b) => order[a.status] - order[b.status] || b.id - a.id);
  }

  function scoreLine(m) {
    if (m.status === 'UPCOMING') return `<span class="muted">${m.totalOvers} overs - not started</span>`;
    const parts = [];
    if (m.firstInnings) parts.push(`${esc(m.firstInnings.teamShort)} ${m.firstInnings.runs}/${m.firstInnings.wickets}`);
    parts.push(`<strong>${esc(m.battingTeam.shortName)} ${m.runs}/${m.wickets}</strong> (${m.overs})`);
    return parts.join(' &nbsp;|&nbsp; ');
  }

  function renderList() {
    const list = sortedMatches();
    $('#matchList').innerHTML = list.length ? list.map(m => `
      <button class="match-card ${m.id === state.selected ? 'selected' : ''}" data-id="${m.id}" type="button">
        <div class="row"><span class="title">${esc(m.team1.shortName)} vs ${esc(m.team2.shortName)}</span>${statusPill(m.status)}</div>
        <div class="score">${scoreLine(m)}</div>
        <div class="sub">${m.status === 'COMPLETED' ? esc(m.result) : esc(m.venue)}</div>
      </button>`).join('') : '<p class="muted">No matches here yet.</p>';
  }

  // ---------------------------------------------------------------- detail
  function chaseText(m) {
    if (m.innings !== 2 || m.status !== 'LIVE') return '';
    const rr = m.requiredRate != null ? ` &middot; required rate <strong>${m.requiredRate.toFixed(2)}</strong>` : '';
    return `<div class="hero-note"><strong>${esc(m.battingTeam.name)}</strong> need <strong>${m.runsRequired}</strong> runs from <strong>${m.ballsRemaining}</strong> balls${rr}</div>`;
  }

  function atCreaseTable(m) {
    if (m.status !== 'LIVE' || !m.striker) return '';
    const bat = (b, mark) => `<tr><td>${mark ? '<span class="striker-mark">*</span> ' : ''}${esc(b.name)}</td><td>${b.runs}</td><td>${b.balls}</td><td>${b.fours}</td><td>${b.sixes}</td><td>${b.strikeRate.toFixed(1)}</td></tr>`;
    const bw = m.bowler;
    return `
      <div class="block table-wrap">
        <h3>At the crease</h3>
        <table>
          <tr><th>Batter</th><th>R</th><th>B</th><th>4s</th><th>6s</th><th>SR</th></tr>
          ${bat(m.striker, true)}${bat(m.nonStriker, false)}
        </table>
        <table style="margin-top:10px">
          <tr><th>Bowler</th><th>O</th><th>M</th><th>R</th><th>W</th><th>Econ</th></tr>
          <tr><td>${esc(bw.name)}</td><td>${bw.overs}</td><td>${bw.maidens}</td><td>${bw.runs}</td><td>${bw.wickets}</td><td>${bw.economy.toFixed(2)}</td></tr>
        </table>
      </div>`;
  }

  function recentChips(m) {
    if (!m.recentBalls.length || m.status === 'UPCOMING') return '';
    const chips = m.recentBalls.slice(0, 8).reverse()
      .map(b => `<span class="ball ${ballClass(b.label)}" title="${esc(b.over)}">${esc(b.label)}</span>`).join('');
    return `<div class="block"><h3>Recent balls</h3><div class="over-chips">${chips}</div></div>`;
  }

  function controls(m) {
    if (m.status === 'UPCOMING') {
      return `<div class="controls"><button class="btn primary" data-action="start" type="button">Start match (toss)</button></div>`;
    }
    if (m.status === 'LIVE') {
      return `<div class="controls">
        <button class="btn ${m.simulated ? 'danger' : 'primary'}" data-action="sim" type="button">${m.simulated ? 'Pause simulation' : 'Resume simulation'}</button>
        <button class="btn" data-action="ff" type="button">Fast-forward to the end</button>
        <span class="hint">${m.simulated ? 'Auto-scoring is on. Pause it to score balls yourself.' : 'Simulation paused - you are the scorer.'}</span>
      </div>`;
    }
    return '';
  }

  function manualPanel(m) {
    if (m.status !== 'LIVE' || m.simulated) return '';
    const run = n => `<button class="btn small" data-ball='{"runs":${n}}' type="button">${n}</button>`;
    return `
      <div class="manual block">
        <h3>Score a ball</h3>
        <div class="buttons">
          ${[0, 1, 2, 3, 4, 6].map(run).join('')}
          <button class="btn small" data-ball='{"extraType":"WIDE","runs":0}' type="button">Wide</button>
          <button class="btn small" data-ball='{"extraType":"NO_BALL","runs":0}' type="button">No ball</button>
          <button class="btn small" data-ball='{"extraType":"BYE","runs":1}' type="button">Bye</button>
          <button class="btn small" data-ball='{"extraType":"LEG_BYE","runs":1}' type="button">Leg bye</button>
        </div>
        <div class="buttons">
          <select id="dismissal">
            <option value="BOWLED">Bowled</option><option value="CAUGHT" selected>Caught</option>
            <option value="LBW">LBW</option><option value="STUMPED">Stumped</option><option value="RUN_OUT">Run out</option>
          </select>
          <button class="btn small danger" data-action="wicket" type="button">Wicket!</button>
        </div>
      </div>`;
  }

  function commentaryList(m) {
    if (!m.recentBalls.length) return '<p class="muted" style="margin-top:12px">No balls bowled yet.</p>';
    return `<ul class="commentary">${m.recentBalls.map(b => `
      <li class="${b.wicket ? 'out' : ''}"><span class="ball ${ballClass(b.label)}">${esc(b.label)}</span><span>${esc(b.commentary)}</span></li>`).join('')}</ul>`;
  }

  function scorecardView() {
    const sc = state.scorecard;
    const current = state.matches.get(state.selected);
    if (current && current.status === 'UPCOMING') return '<p class="muted" style="margin-top:12px">The match has not started.</p>';
    if (!sc || state.scorecardFor !== state.selected) return '<p class="muted" style="margin-top:12px">Loading scorecard...</p>';
    if (!sc.innings.length) return '<p class="muted" style="margin-top:12px">The match has not started.</p>';
    return sc.innings.map(inn => `
      <div class="innings-title"><span>${esc(inn.teamName)} - innings ${inn.innings}</span><span>${inn.runs}/${inn.wickets} (${inn.overs} ov)</span></div>
      <div class="table-wrap"><table>
        <tr><th>Batter</th><th>R</th><th>B</th><th>4s</th><th>6s</th><th>SR</th></tr>
        ${inn.batting.map(b => `<tr><td>${esc(b.name)}<span class="how">${esc(b.dismissal)}</span></td><td>${b.runs}</td><td>${b.balls}</td><td>${b.fours}</td><td>${b.sixes}</td><td>${b.strikeRate.toFixed(1)}</td></tr>`).join('')}
        <tr><td>Extras</td><td colspan="5">${inn.extras}</td></tr>
      </table></div>
      <div class="table-wrap" style="margin-top:10px"><table>
        <tr><th>Bowler</th><th>O</th><th>M</th><th>R</th><th>W</th><th>Econ</th></tr>
        ${inn.bowling.map(b => `<tr><td>${esc(b.name)}</td><td>${b.overs}</td><td>${b.maidens}</td><td>${b.runs}</td><td>${b.wickets}</td><td>${b.economy.toFixed(2)}</td></tr>`).join('')}
      </table></div>
      ${inn.fallOfWickets.length ? `<div class="fow"><strong>Fall of wickets:</strong> ${inn.fallOfWickets.map(esc).join(', ')}</div>` : ''}
    `).join('');
  }

  function matchSummary(m) {
    const sc = state.scorecard;
    if (m.status !== 'COMPLETED' || !sc || state.scorecardFor !== m.id) return '';
    const allBat = sc.innings.flatMap(i => i.batting.map(b => ({ ...b, team: i.teamShort })));
    const allBowl = sc.innings.flatMap(i => i.bowling.map(b => ({ ...b, team: i.teamShort })));
    const topBat = allBat.sort((a, b) => b.runs - a.runs)[0];
    const topBowl = allBowl.sort((a, b) => b.wickets - a.wickets || a.runs - b.runs)[0];
    const sixes = sc.innings.reduce((n, i) => n + i.batting.reduce((s, b) => s + b.sixes, 0), 0);
    const fours = sc.innings.reduce((n, i) => n + i.batting.reduce((s, b) => s + b.fours, 0), 0);
    return `
      <div class="block"><h3>Match summary</h3><div class="summary-grid">
        ${topBat ? `<div class="summary-card"><div class="label">Top scorer</div><div class="value">${esc(topBat.name)}</div><div class="sub">${topBat.runs} (${topBat.balls}) &middot; ${esc(topBat.team)}</div></div>` : ''}
        ${topBowl ? `<div class="summary-card"><div class="label">Best bowler</div><div class="value">${esc(topBowl.name)}</div><div class="sub">${topBowl.wickets}/${topBowl.runs} in ${topBowl.overs} ov &middot; ${esc(topBowl.team)}</div></div>` : ''}
        <div class="summary-card"><div class="label">Boundaries</div><div class="value">${fours} fours, ${sixes} sixes</div><div class="sub">across both innings</div></div>
      </div></div>`;
  }

  function renderDetail() {
    const m = state.matches.get(state.selected);
    const root = $('#detail');
    if (!m) { root.innerHTML = '<div class="empty">Select a match to see the live score.</div>'; return; }

    const batting = m.battingTeam;
    const hero = m.status === 'UPCOMING'
      ? `<div class="hero-teams"><div class="hero-team"><div class="name">${esc(m.team1.name)}</div></div><div class="muted">vs</div><div class="hero-team"><div class="name">${esc(m.team2.name)}</div></div></div>`
      : `<div class="hero-teams">
          <div class="hero-team"><div class="name">${esc(batting.name)}${m.status === 'LIVE' ? ' - batting' : ''}</div>
            <div class="big">${m.runs}/${m.wickets}<small>(${m.overs} ov) RR ${m.runRate.toFixed(2)}</small></div></div>
          ${m.firstInnings ? `<div class="hero-team other"><div class="name">1st innings - ${esc(m.firstInnings.teamShort)}</div><div class="big">${m.firstInnings.runs}/${m.firstInnings.wickets}<small>(${m.firstInnings.overs})</small></div></div>` : ''}
        </div>`;

    const tabs = `
      <div class="tabs">
        <button class="tab ${state.tab === 'commentary' ? 'active' : ''}" data-tab="commentary" type="button">Commentary</button>
        <button class="tab ${state.tab === 'scorecard' ? 'active' : ''}" data-tab="scorecard" type="button">Scorecard</button>
      </div>
      <div id="tabBody">${state.tab === 'commentary' ? commentaryList(m) : scorecardView()}</div>`;

    root.innerHTML = `
      <div class="hero">
        <div class="hero-top">${statusPill(m.status)}<span>${esc(m.title)}</span><span>&middot; ${esc(m.venue)}</span><span>&middot; ${m.totalOvers} overs</span></div>
        ${hero}
        ${chaseText(m)}
        ${m.tossSummary ? `<div class="hero-note muted">${esc(m.tossSummary)}</div>` : ''}
        ${m.status === 'COMPLETED' && m.result ? `<div class="result-banner">${esc(m.result)}</div>` : ''}
      </div>
      ${controls(m)}
      ${matchSummary(m)}
      ${atCreaseTable(m)}
      ${recentChips(m)}
      ${manualPanel(m)}
      ${tabs}`;
  }

  // ---------------------------------------------------------------- data loading
  async function loadScorecard(force) {
    const id = state.selected;
    if (!id) return;
    const m = state.matches.get(id);
    if (!m || m.status === 'UPCOMING') { state.scorecard = null; return; }
    if (!force && state.scorecardFor === id && m.status === 'COMPLETED') return;
    try {
      const sc = await api(`/matches/${id}/scorecard`);
      if (state.selected !== id) return;
      state.scorecard = sc;
      state.scorecardFor = id;
      renderDetail();
    } catch (e) { /* the next update will retry */ }
  }
  const loadScorecardThrottled = throttle(() => loadScorecard(true), 1500);

  async function loadLeaders() {
    try {
      const [bat, bowl] = await Promise.all([api('/stats/top-batters?limit=5'), api('/stats/top-bowlers?limit=5')]);
      const row = (e, unit) => `<li><span class="who">${esc(e.name)}<small>${esc(e.team)}</small></span><span class="val">${e.value}${unit}</span></li>`;
      $('#topBat').innerHTML = bat.length ? bat.map(e => row(e, '')).join('') : '<li class="muted">No data yet</li>';
      $('#topBowl').innerHTML = bowl.length ? bowl.map(e => row(e, '')).join('') : '<li class="muted">No data yet</li>';
    } catch (e) { /* ignore */ }
  }
  const loadLeadersThrottled = throttle(loadLeaders, 6000);

  async function loadMatches() {
    const list = await api('/matches');
    list.forEach(m => state.matches.set(m.id, m));
    if (state.selected == null && list.length) {
      const live = list.find(m => m.status === 'LIVE') || list[0];
      state.selected = live.id;
    }
    renderList();
    renderDetail();
    loadScorecard(true);
    loadLeaders();
  }

  function selectMatch(id) {
    state.selected = id;
    state.scorecard = null;
    state.scorecardFor = null;
    renderList();
    renderDetail();
    loadScorecard(true);
  }

  // ---------------------------------------------------------------- live stream
  function setConn(on) {
    const el = $('#conn');
    el.classList.toggle('on', on);
    el.classList.toggle('off', !on);
    $('#connText').textContent = on ? 'Live - connected' : 'Reconnecting...';
  }

  function onMatchUpdate(m) {
    const isNew = !state.matches.has(m.id);
    state.matches.set(m.id, m);
    renderList();
    if (m.id === state.selected) {
      renderDetail();
      if (state.tab === 'scorecard' || m.status === 'COMPLETED') loadScorecardThrottled();
    }
    if (isNew && state.selected == null) selectMatch(m.id);
    loadLeadersThrottled();
  }

  function connectStream() {
    const es = new EventSource('/api/stream');
    es.onopen = () => { setConn(true); loadMatches().catch(() => {}); };
    es.onerror = () => setConn(false);
    es.addEventListener('match', ev => onMatchUpdate(JSON.parse(ev.data)));
  }

  // ---------------------------------------------------------------- actions
  async function run(promise) {
    try { return await promise; } catch (e) { toast(e.message); return null; }
  }

  function postBall(body) {
    return run(api(`/matches/${state.selected}/balls`, { method: 'POST', body: JSON.stringify(body) }));
  }

  document.addEventListener('click', async ev => {
    const card = ev.target.closest('.match-card');
    if (card) { selectMatch(Number(card.dataset.id)); return; }

    const chip = ev.target.closest('.chip');
    if (chip) {
      state.filter = chip.dataset.filter;
      document.querySelectorAll('.chip').forEach(c => c.classList.toggle('active', c === chip));
      renderList();
      return;
    }

    const tab = ev.target.closest('.tab');
    if (tab) {
      state.tab = tab.dataset.tab;
      renderDetail();
      if (state.tab === 'scorecard') loadScorecard(true);
      return;
    }

    const ballBtn = ev.target.closest('[data-ball]');
    if (ballBtn) { await postBall(JSON.parse(ballBtn.dataset.ball)); return; }

    const action = ev.target.closest('[data-action]');
    if (!action || state.selected == null) return;
    const id = state.selected;
    const m = state.matches.get(id);
    switch (action.dataset.action) {
      case 'start': await run(api(`/matches/${id}/start`, { method: 'POST' })); break;
      case 'sim': await run(api(`/matches/${id}/simulation?enabled=${!m.simulated}`, { method: 'POST' })); break;
      case 'ff': await run(api(`/matches/${id}/fast-forward`, { method: 'POST' })); break;
      case 'wicket': await postBall({ wicket: true, dismissalType: $('#dismissal').value, runs: 0 }); break;
      default: break;
    }
  });

  // ---------------------------------------------------------------- new match dialog
  async function openNewMatch() {
    if (!state.teams.length) state.teams = await run(api('/teams')) || [];
    const options = state.teams.map(t => `<option value="${t.id}">${esc(t.name)}</option>`).join('');
    $('#team1').innerHTML = options;
    $('#team2').innerHTML = options;
    if (state.teams.length > 1) $('#team2').selectedIndex = 1;
    $('#formError').textContent = '';
    $('#newMatchDialog').showModal();
  }

  $('#newMatchBtn').addEventListener('click', openNewMatch);
  $('#cancelNew').addEventListener('click', () => $('#newMatchDialog').close());
  $('#newMatchForm').addEventListener('submit', async ev => {
    ev.preventDefault();
    try {
      const created = await api('/matches', {
        method: 'POST',
        body: JSON.stringify({
          team1Id: Number($('#team1').value),
          team2Id: Number($('#team2').value),
          totalOvers: Number($('#overs').value),
          venue: $('#venue').value,
          simulate: $('#simulate').checked,
        }),
      });
      state.matches.set(created.id, created);
      $('#newMatchDialog').close();
      selectMatch(created.id);
    } catch (e) {
      $('#formError').textContent = e.message;
    }
  });

  // ---------------------------------------------------------------- boot
  loadMatches().catch(e => toast(e.message));
  connectStream();
})();
