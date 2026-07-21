package io.github.hackermanme.flashapi.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public final class DashboardController {

    private final MetricsCollector metricsCollector;
    private final String requiredRole;

    public DashboardController(MetricsCollector metricsCollector, String requiredRole) {
        this.metricsCollector = metricsCollector;
        this.requiredRole = requiredRole;
    }

    public ResponseEntity<String> serveMetrics(HttpServletRequest request, HttpServletResponse response) {
        if (!isAuthorized()) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Access denied. Required role: " + requiredRole + "\"}");
        }
        FlashMetrics snapshot = metricsCollector.snapshot();
        String json = toJson(snapshot);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Access-Control-Allow-Origin", "*")
                .body(json);
    }

    public void serveUi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isAuthorized()) {
            response.setStatus(403);
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.getWriter().write("<h1>403 - Access Denied</h1><p>Required role: " + requiredRole + "</p>");
            response.flushBuffer();
            return;
        }
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding("UTF-8");

        String basePath = request.getRequestURI().replace("/index.html", "").replaceAll("/+$", "");
        String metricsUrl = basePath + "/metrics.json";

        String html = dashboardHtml(metricsUrl);

        response.getWriter().write(html);
        response.flushBuffer();
    }

    private static String dashboardHtml(String metricsUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>FlashAPI Dashboard</title>
                    <link rel="icon" type="image/png" href="https://raw.githubusercontent.com/HackermanMe/spring-flashapi/main/docs/assets/favicon.png">
                    <style>
                        * { box-sizing: border-box; }
                        :root {
                            --canvas: #f5f7f1;
                            --paper: #ffffff;
                            --ink: #19192a;
                            --muted: #697069;
                            --line: #d9dfd3;
                            --green: #73ad43;
                            --green-dark: #3f762e;
                            --green-soft: #edf5e8;
                            --gold: #d9b84f;
                            --blue: #3b6f89;
                            --red: #c34d43;
                            --shadow: 0 18px 45px rgba(25, 25, 42, 0.08);
                        }
                        html { min-height: 100%; background: var(--canvas); }
                        body {
                            min-height: 100vh;
                            margin: 0;
                            color: var(--ink);
                            font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                            background:
                                linear-gradient(90deg, rgba(25,25,42,0.035) 1px, transparent 1px),
                                linear-gradient(180deg, rgba(25,25,42,0.035) 1px, transparent 1px),
                                radial-gradient(circle at top left, rgba(115,173,67,0.18), transparent 34rem),
                                var(--canvas);
                            background-size: 44px 44px, 44px 44px, auto, auto;
                        }
                        button { font: inherit; }
                        .shell { width: min(1480px, calc(100% - 40px)); margin: 0 auto; padding: 24px 0 36px; }
                        .topbar {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 24px;
                            margin-bottom: 18px;
                            padding: 14px 18px;
                            background: rgba(255,255,255,0.72);
                            border: 1px solid rgba(217,223,211,0.9);
                            border-radius: 8px;
                            box-shadow: var(--shadow);
                            backdrop-filter: blur(18px);
                        }
                        .brand { display: flex; align-items: center; gap: 16px; min-width: 0; }
                        .brand-mark {
                            width: 170px;
                            height: 52px;
                            object-fit: contain;
                            object-position: left center;
                            filter: drop-shadow(0 8px 16px rgba(25,25,42,0.14));
                        }
                        .eyebrow {
                            margin: 0 0 4px;
                            color: var(--green-dark);
                            font-size: 11px;
                            font-weight: 800;
                            letter-spacing: 0;
                            text-transform: uppercase;
                        }
                        h1 { margin: 0; font-size: clamp(24px, 3vw, 42px); line-height: 1; letter-spacing: 0; }
                        .top-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
                        .status-pill {
                            display: inline-flex;
                            align-items: center;
                            gap: 9px;
                            min-height: 38px;
                            padding: 0 12px;
                            border: 1px solid var(--line);
                            border-radius: 999px;
                            background: var(--paper);
                            color: var(--muted);
                            font-size: 13px;
                            font-weight: 700;
                            font-variant-numeric: tabular-nums;
                            white-space: nowrap;
                        }
                        .status-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--green); box-shadow: 0 0 0 5px rgba(115,173,67,0.14); }
                        .icon-button {
                            display: inline-grid;
                            place-items: center;
                            width: 38px;
                            height: 38px;
                            border: 1px solid var(--line);
                            border-radius: 8px;
                            background: var(--paper);
                            color: var(--ink);
                            cursor: pointer;
                            transition: border-color .16s ease, transform .16s ease, box-shadow .16s ease;
                        }
                        .icon-button:hover { border-color: var(--green); box-shadow: 0 8px 18px rgba(115,173,67,0.18); transform: translateY(-1px); }
                        .icon-button:focus-visible { outline: 3px solid rgba(115,173,67,0.3); outline-offset: 2px; }
                        .hero {
                            display: grid;
                            grid-template-columns: minmax(0, 1.25fr) minmax(320px, .75fr);
                            gap: 16px;
                            align-items: stretch;
                            margin-bottom: 16px;
                        }
                        .panel {
                            background: rgba(255,255,255,0.86);
                            border: 1px solid rgba(217,223,211,0.95);
                            border-radius: 8px;
                            box-shadow: var(--shadow);
                            overflow: hidden;
                        }
                        .hero-metric {
                            position: relative;
                            min-height: 320px;
                            padding: 26px;
                            display: grid;
                            grid-template-columns: minmax(0, .9fr) minmax(280px, 1.1fr);
                            gap: 22px;
                            align-items: end;
                        }
                        .hero-metric:before {
                            content: "";
                            position: absolute;
                            inset: 0;
                            background:
                                linear-gradient(135deg, rgba(115,173,67,0.12), transparent 45%),
                                linear-gradient(90deg, rgba(25,25,42,0.045) 1px, transparent 1px);
                            background-size: auto, 24px 100%;
                            pointer-events: none;
                        }
                        .hero-stack, .chart-wrap { position: relative; z-index: 1; }
                        .metric-label { margin: 0 0 10px; color: var(--muted); font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: 0; }
                        .hero-value {
                            margin: 0;
                            font-size: clamp(56px, 8vw, 104px);
                            line-height: .86;
                            font-weight: 900;
                            font-variant-numeric: tabular-nums;
                            letter-spacing: 0;
                        }
                        .hero-sub { max-width: 42ch; margin: 18px 0 0; color: var(--muted); font-size: 15px; line-height: 1.55; }
                        .chart-wrap { min-height: 255px; display: grid; align-items: end; }
                        .chart-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
                        .chart-title h2, .section-title h2 { margin: 0; font-size: 15px; letter-spacing: 0; }
                        .chart-title span { color: var(--muted); font-size: 12px; font-weight: 700; }
                        .chart { width: 100%; height: 210px; display: block; }
                        .chart text { fill: var(--muted); font-size: 11px; font-weight: 700; }
                        .side-grid { display: grid; gap: 16px; }
                        .health-card { padding: 22px; min-height: 152px; }
                        .health-row { display: flex; justify-content: space-between; align-items: baseline; gap: 14px; }
                        .health-value { margin: 0; font-size: 42px; line-height: 1; font-weight: 900; font-variant-numeric: tabular-nums; }
                        .health-note { margin: 10px 0 0; color: var(--muted); font-size: 13px; line-height: 1.45; }
                        .target-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 14px; }
                        .target-chip {
                            max-width: 100%;
                            padding: 5px 8px;
                            border-radius: 999px;
                            background: var(--green-soft);
                            color: var(--green-dark);
                            font-size: 11px;
                            font-weight: 800;
                            overflow: hidden;
                            text-overflow: ellipsis;
                            white-space: nowrap;
                        }
                        .kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
                        .kpi { padding: 18px; min-height: 150px; display: grid; align-content: space-between; gap: 14px; }
                        .kpi-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
                        .kpi-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 8px; background: var(--green-soft); color: var(--green-dark); }
                        .kpi-value { margin: 0; font-size: 34px; line-height: 1; font-weight: 900; font-variant-numeric: tabular-nums; }
                        .kpi-sub { margin: 6px 0 0; color: var(--muted); font-size: 13px; line-height: 1.42; }
                        .trend { height: 34px; display: grid; grid-template-columns: repeat(16, 1fr); align-items: end; gap: 3px; }
                        .trend i { display: block; min-height: 4px; border-radius: 3px 3px 0 0; background: linear-gradient(180deg, var(--green), var(--green-dark)); opacity: .88; }
                        .content-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(360px, .8fr); gap: 16px; align-items: start; }
                        .section-title {
                            min-height: 58px;
                            padding: 18px 20px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 12px;
                            border-bottom: 1px solid var(--line);
                        }
                        .section-title p { margin: 4px 0 0; color: var(--muted); font-size: 12px; }
                        .table-wrap { overflow-x: auto; }
                        table { width: 100%; border-collapse: collapse; }
                        th {
                            padding: 12px 18px;
                            color: var(--muted);
                            font-size: 11px;
                            font-weight: 900;
                            text-align: left;
                            text-transform: uppercase;
                            letter-spacing: 0;
                            border-bottom: 1px solid var(--line);
                            white-space: nowrap;
                        }
                        td { padding: 14px 18px; border-bottom: 1px solid var(--line); vertical-align: middle; font-size: 14px; }
                        tbody tr:hover { background: rgba(115,173,67,0.06); }
                        .entity-name { font-weight: 900; }
                        .entity-count { font-weight: 900; font-variant-numeric: tabular-nums; }
                        .feature-list { display: flex; flex-wrap: wrap; gap: 6px; }
                        .badge {
                            display: inline-flex;
                            align-items: center;
                            min-height: 22px;
                            padding: 0 8px;
                            border-radius: 999px;
                            border: 1px solid var(--line);
                            background: var(--paper);
                            color: var(--muted);
                            font-size: 11px;
                            font-weight: 800;
                            white-space: nowrap;
                        }
                        .badge.green { border-color: rgba(115,173,67,0.28); background: var(--green-soft); color: var(--green-dark); }
                        .badge.blue { border-color: rgba(59,111,137,0.22); background: #edf5f7; color: var(--blue); }
                        .badge.gold { border-color: rgba(217,184,79,0.3); background: #fbf6df; color: #80651d; }
                        .badge.red { border-color: rgba(195,77,67,0.24); background: #f9ebe9; color: var(--red); }
                        .op-stack { width: 160px; height: 10px; display: flex; overflow: hidden; border-radius: 999px; background: #edf0e8; }
                        .op-stack i { min-width: 3px; }
                        .events-list { max-height: 522px; overflow: auto; padding: 8px 0; }
                        .event-row {
                            display: grid;
                            grid-template-columns: 76px 82px minmax(0, 1fr);
                            gap: 12px;
                            align-items: center;
                            padding: 12px 18px;
                            border-bottom: 1px solid var(--line);
                        }
                        .event-time { color: var(--muted); font-size: 12px; font-weight: 800; font-variant-numeric: tabular-nums; }
                        .event-op { font-size: 11px; font-weight: 900; text-transform: uppercase; letter-spacing: 0; }
                        .event-main { min-width: 0; }
                        .event-entity { display: block; overflow: hidden; color: var(--ink); font-size: 14px; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
                        .event-id { display: block; margin-top: 3px; overflow: hidden; color: var(--muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
                        .CREATE { color: var(--green-dark); }
                        .READ { color: var(--blue); }
                        .UPDATE { color: #80651d; }
                        .DELETE { color: var(--red); }
                        .SEARCH { color: #7b5fc7; }
                        .EXPORT { color: #2e7d76; }
                        .BULK { color: #925f24; }
                        .empty-state { padding: 34px 18px; color: var(--muted); text-align: center; font-weight: 700; }
                        .error-banner {
                            display: none;
                            margin-bottom: 16px;
                            padding: 12px 14px;
                            border: 1px solid rgba(195,77,67,0.28);
                            border-radius: 8px;
                            background: #f9ebe9;
                            color: var(--red);
                            font-size: 13px;
                            font-weight: 800;
                        }
                        @media (max-width: 1040px) {
                            .hero, .hero-metric, .content-grid { grid-template-columns: 1fr; }
                            .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                        }
                        @media (max-width: 680px) {
                            .shell { width: min(100% - 24px, 1480px); padding-top: 12px; }
                            .topbar { align-items: flex-start; flex-direction: column; }
                            .brand { align-items: flex-start; flex-direction: column; gap: 8px; }
                            .brand-mark { width: 150px; height: 44px; }
                            .top-actions { justify-content: flex-start; }
                            .hero-metric, .health-card, .kpi { padding: 16px; }
                            .kpi-grid { grid-template-columns: 1fr; }
                            .event-row { grid-template-columns: 64px 70px minmax(0, 1fr); padding: 12px 14px; }
                            th, td { padding: 12px 14px; }
                        }
                        @media (prefers-reduced-motion: no-preference) {
                            .status-dot { animation: livePulse 2.4s ease-in-out infinite; }
                            @keyframes livePulse { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(.72); opacity: .62; } }
                        }
                    </style>
                </head>
                <body>
                    <main class="shell">
                        <div class="topbar">
                            <div class="brand">
                                <img class="brand-mark" src="https://raw.githubusercontent.com/HackermanMe/spring-flashapi/main/docs/assets/logo.svg" alt="FlashAPI">
                                <div>
                                    <p class="eyebrow">Runtime metrics</p>
                                    <h1>Dashboard</h1>
                                </div>
                            </div>
                            <div class="top-actions">
                                <div class="status-pill"><span class="status-dot"></span><span id="uptime">Loading</span></div>
                                <div class="status-pill" id="last-refresh">Waiting for metrics</div>
                                <button class="icon-button" id="refresh-button" type="button" title="Refresh now" aria-label="Refresh now">
                                    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none">
                                        <path d="M20 6v5h-5M4 18v-5h5M18.2 9A7 7 0 0 0 6.4 6.6L4 9m2 6a7 7 0 0 0 11.6 2.4L20 15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                    </svg>
                                </button>
                            </div>
                        </div>
                        <div class="error-banner" id="error-banner"></div>
                        <section class="hero">
                            <div class="panel hero-metric">
                                <div class="hero-stack">
                                    <p class="metric-label">Total operations</p>
                                    <p class="hero-value" id="total-ops">0</p>
                                    <p class="hero-sub" id="hero-sub">Collecting live CRUD, search, export, bulk and webhook activity from this FlashAPI instance.</p>
                                </div>
                                <div class="chart-wrap">
                                    <div class="chart-title">
                                        <h2>Operation mix</h2>
                                        <span id="chart-total">0 events</span>
                                    </div>
                                    <svg class="chart" id="operations-chart" role="img" aria-label="Operation breakdown"></svg>
                                </div>
                            </div>
                            <div class="side-grid">
                                <div class="panel health-card">
                                    <div class="health-row">
                                        <div>
                                            <p class="metric-label">Webhook delivery</p>
                                            <p class="health-value" id="webhook-rate">100%</p>
                                        </div>
                                        <span class="badge green" id="webhook-status">healthy</span>
                                    </div>
                                    <p class="health-note" id="webhook-note">No failures recorded.</p>
                                    <div class="target-list" id="target-list"></div>
                                </div>
                                <div class="panel health-card">
                                    <div class="health-row">
                                        <div>
                                            <p class="metric-label">Registered entities</p>
                                            <p class="health-value" id="entity-count">0</p>
                                        </div>
                                        <span class="badge blue" id="feature-count">0 features</span>
                                    </div>
                                    <p class="health-note" id="entity-note">Feature coverage will appear as soon as entities are detected.</p>
                                </div>
                            </div>
                        </section>
                        <section class="kpi-grid" id="kpi-grid"></section>
                        <section class="content-grid">
                            <div class="panel">
                                <div class="section-title">
                                    <div>
                                        <h2>Entities</h2>
                                        <p id="entities-subtitle">Operation volume and enabled capabilities</p>
                                    </div>
                                </div>
                                <div class="table-wrap" id="entities-content"></div>
                            </div>
                            <div class="panel">
                                <div class="section-title">
                                    <div>
                                        <h2>Recent activity</h2>
                                        <p>Latest 100 captured operations</p>
                                    </div>
                                </div>
                                <div class="events-list" id="events-list"></div>
                            </div>
                        </section>
                    </main>
                    <script>
                        const METRICS_URL = "__METRICS_URL__";
                        const OP_COLORS = {
                            CREATE: '#73ad43',
                            READ: '#3b6f89',
                            UPDATE: '#d9b84f',
                            DELETE: '#c34d43',
                            SEARCH: '#7b5fc7',
                            EXPORT: '#2e7d76',
                            BULK: '#925f24'
                        };
                        const OP_LABELS = [
                            ['CREATE', 'Creates'],
                            ['READ', 'Reads'],
                            ['UPDATE', 'Updates'],
                            ['DELETE', 'Deletes'],
                            ['SEARCH', 'Searches'],
                            ['EXPORT', 'Exports'],
                            ['BULK', 'Bulk']
                        ];

                        function escapeHtml(value) {
                            return String(value ?? '').replace(/[&<>"']/g, ch => ({
                                '&': '&amp;',
                                '<': '&lt;',
                                '>': '&gt;',
                                '"': '&quot;',
                                "'": '&#39;'
                            }[ch]));
                        }

                        function number(value) {
                            return Number(value || 0).toLocaleString();
                        }

                        function formatUptime(seconds) {
                            const s = Number(seconds || 0);
                            const d = Math.floor(s / 86400);
                            const h = Math.floor((s % 86400) / 3600);
                            const m = Math.floor((s % 3600) / 60);
                            return (d > 0 ? d + 'd ' : '') + h + 'h ' + m + 'm';
                        }

                        function trendBars(seed) {
                            const value = Math.max(1, Number(seed || 0));
                            return Array.from({ length: 16 }, (_, i) => {
                                const height = 18 + ((value + i * 11) % 17);
                                return `<i style="height:${height}px"></i>`;
                            }).join('');
                        }

                        function operationRowsFromTotals(totals) {
                            return [
                                { key: 'CREATE', label: 'Creates', value: totals.creates || 0, color: OP_COLORS.CREATE },
                                { key: 'READ', label: 'Reads', value: totals.reads || 0, color: OP_COLORS.READ },
                                { key: 'UPDATE', label: 'Updates', value: totals.updates || 0, color: OP_COLORS.UPDATE },
                                { key: 'DELETE', label: 'Deletes', value: totals.deletes || 0, color: OP_COLORS.DELETE },
                                { key: 'SEARCH', label: 'Searches', value: totals.searches || 0, color: OP_COLORS.SEARCH },
                                { key: 'EXPORT', label: 'Exports', value: totals.exports || 0, color: OP_COLORS.EXPORT },
                                { key: 'BULK', label: 'Bulk', value: totals.bulkOps || 0, color: OP_COLORS.BULK }
                            ];
                        }

                        function operationRowsFromEntity(entity) {
                            const ops = entity.operations || {};
                            return OP_LABELS.map(([key, label]) => ({
                                key,
                                label,
                                value: ops[key] || 0,
                                color: OP_COLORS[key]
                            }));
                        }

                        function renderChart(totals) {
                            const rows = operationRowsFromTotals(totals);
                            const max = Math.max(1, ...rows.map(row => row.value));
                            const svg = document.getElementById('operations-chart');
                            const width = 560;
                            const height = 210;
                            const gap = 14;
                            const barWidth = Math.max(28, (width - 68 - gap * (rows.length - 1)) / rows.length);
                            const plotHeight = 132;
                            const bars = rows.map((row, index) => {
                                const x = 46 + index * (barWidth + gap);
                                const barHeight = Math.max(row.value > 0 ? 8 : 2, (row.value / max) * plotHeight);
                                const y = 150 - barHeight;
                                return `
                                    <rect x="${x}" y="${y}" width="${barWidth}" height="${barHeight}" rx="5" fill="${row.color}"></rect>
                                    <text x="${x + barWidth / 2}" y="174" text-anchor="middle">${row.key}</text>
                                    <text x="${x + barWidth / 2}" y="${Math.max(18, y - 8)}" text-anchor="middle">${number(row.value)}</text>
                                `;
                            }).join('');
                            svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
                            svg.innerHTML = `<line x1="32" y1="150" x2="540" y2="150" stroke="#d9dfd3" stroke-width="1"></line>${bars}`;
                        }

                        function renderKpis(data) {
                            const totals = data.totals;
                            const cards = [
                                ['Reads', totals.reads, `${number(totals.searches)} searches included in discovery flows`, 'READ'],
                                ['Writes', totals.creates + totals.updates + totals.deletes, `${number(totals.creates)} create / ${number(totals.updates)} update / ${number(totals.deletes)} delete`, 'CREATE'],
                                ['Exports', totals.exports, `${number(totals.bulkOps)} bulk operations captured`, 'EXPORT'],
                                ['Events', data.recentEvents.length, `Showing ${number(Math.min(data.recentEvents.length, 100))} recent records`, 'BULK']
                            ];
                            document.getElementById('kpi-grid').innerHTML = cards.map(([label, value, sub, op]) => `
                                <article class="panel kpi">
                                    <div>
                                        <div class="kpi-top">
                                            <p class="metric-label">${label}</p>
                                            <span class="kpi-icon" style="color:${OP_COLORS[op]}">
                                                <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none">
                                                    <path d="M4 19V5M4 19h16M8 16v-5M12 16V8M16 16v-8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                                                </svg>
                                            </span>
                                        </div>
                                        <p class="kpi-value">${number(value)}</p>
                                        <p class="kpi-sub">${escapeHtml(sub)}</p>
                                    </div>
                                    <div class="trend" aria-hidden="true">${trendBars(value)}</div>
                                </article>
                            `).join('');
                        }

                        function renderEntities(data) {
                            const entities = Object.values(data.entities || {}).sort((a, b) => b.count - a.count);
                            document.getElementById('entity-count').textContent = number(entities.length);
                            const featureTotal = entities.reduce((sum, e) => sum + [e.softDelete, e.auditEnabled, e.webhookEnabled, e.rateLimited, e.multiTenant].filter(Boolean).length, 0);
                            document.getElementById('feature-count').textContent = `${number(featureTotal)} features`;
                            document.getElementById('entity-note').textContent = `${number(entities.filter(e => e.auditEnabled).length)} audited, ${number(entities.filter(e => e.webhookEnabled).length)} webhook-enabled, ${number(entities.filter(e => e.multiTenant).length)} multi-tenant.`;
                            document.getElementById('entities-subtitle').textContent = `${number(entities.length)} entities sorted by activity`;
                            if (entities.length === 0) {
                                document.getElementById('entities-content').innerHTML = '<div class="empty-state">No registered entities yet</div>';
                                return;
                            }
                            const rows = entities.map(e => {
                                const badges = [];
                                if (e.softDelete) badges.push('<span class="badge gold">soft-delete</span>');
                                if (e.webhookEnabled) badges.push('<span class="badge blue">webhook</span>');
                                if (e.auditEnabled) badges.push('<span class="badge green">audit</span>');
                                if (e.rateLimited) badges.push('<span class="badge red">rate-limit</span>');
                                if (e.multiTenant) badges.push('<span class="badge">multi-tenant</span>');
                                const segments = operationRowsFromEntity(e).map(row => {
                                    const width = e.count > 0 ? Math.max(3, (row.value / e.count) * 100) : 0;
                                    return row.value > 0 ? `<i style="width:${width}%; background:${row.color}"></i>` : '';
                                }).join('');
                                return `
                                    <tr>
                                        <td><span class="entity-name">${escapeHtml(e.name)}</span></td>
                                        <td><span class="entity-count">${number(e.count)}</span></td>
                                        <td><div class="op-stack" title="${number(e.count)} operations">${segments || '<i style="width:0"></i>'}</div></td>
                                        <td><div class="feature-list">${badges.join('') || '<span class="badge">standard</span>'}</div></td>
                                    </tr>
                                `;
                            }).join('');
                            document.getElementById('entities-content').innerHTML = `
                                <table>
                                    <thead><tr><th>Entity</th><th>Ops</th><th>Mix</th><th>Capabilities</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            `;
                        }

                        function renderWebhooks(data) {
                            const sent = data.webhooks.sent || 0;
                            const failed = data.webhooks.failed || 0;
                            const retries = data.webhooks.retries || 0;
                            const total = sent + failed;
                            const rate = total === 0 ? 100 : Math.round((sent / total) * 100);
                            document.getElementById('webhook-rate').textContent = `${rate}%`;
                            const statusEl = document.getElementById('webhook-status');
                            statusEl.className = `badge ${failed > 0 ? 'red' : 'green'}`;
                            statusEl.textContent = failed > 0 ? 'attention' : 'healthy';
                            document.getElementById('webhook-note').textContent = `${number(sent)} sent, ${number(failed)} failed, ${number(retries)} retries.`;
                            const targets = data.webhooks.targetUrls || [];
                            document.getElementById('target-list').innerHTML = targets.length
                                ? targets.map(url => `<span class="target-chip" title="${escapeHtml(url)}">${escapeHtml(url)}</span>`).join('')
                                : '<span class="target-chip">No targets configured</span>';
                        }

                        function renderEvents(data) {
                            const events = data.recentEvents || [];
                            const list = document.getElementById('events-list');
                            if (events.length === 0) {
                                list.innerHTML = '<div class="empty-state">No activity yet</div>';
                                return;
                            }
                            list.innerHTML = events.map(ev => {
                                const time = new Date(ev.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                                const id = ev.entityId ? `#${escapeHtml(ev.entityId)}` : escapeHtml(ev.status || 'OK');
                                return `
                                    <div class="event-row">
                                        <span class="event-time">${time}</span>
                                        <span class="event-op ${escapeHtml(ev.operation)}">${escapeHtml(ev.operation)}</span>
                                        <span class="event-main">
                                            <span class="event-entity">${escapeHtml(ev.entity)}</span>
                                            <span class="event-id">${id}</span>
                                        </span>
                                    </div>
                                `;
                            }).join('');
                        }

                        function render(data) {
                            document.getElementById('total-ops').textContent = number(data.totals.total);
                            document.getElementById('chart-total').textContent = `${number(data.totals.total)} events`;
                            document.getElementById('uptime').textContent = `Up ${formatUptime(data.uptimeSeconds)}`;
                            document.getElementById('last-refresh').textContent = `Updated ${new Date(data.generatedAt).toLocaleTimeString()}`;
                            document.getElementById('hero-sub').textContent = `${number(data.totals.reads + data.totals.searches)} read/search operations and ${number(data.totals.creates + data.totals.updates + data.totals.deletes)} writes recorded since startup.`;
                            renderChart(data.totals);
                            renderKpis(data);
                            renderEntities(data);
                            renderWebhooks(data);
                            renderEvents(data);
                        }

                        async function refresh() {
                            const error = document.getElementById('error-banner');
                            try {
                                const res = await fetch(METRICS_URL, { headers: { Accept: 'application/json' } });
                                if (!res.ok) throw new Error(`Metrics request failed with ${res.status}`);
                                const data = await res.json();
                                error.style.display = 'none';
                                render(data);
                            } catch (e) {
                                error.textContent = `Dashboard refresh failed: ${e.message}`;
                                error.style.display = 'block';
                            }
                        }

                        document.getElementById('refresh-button').addEventListener('click', refresh);
                        refresh();
                        setInterval(refresh, 5000);
                    </script>
                </body>
                </html>
                """.replace("__METRICS_URL__", metricsUrl);
    }

    @SuppressWarnings("unchecked")
    private boolean isAuthorized() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderClass.getMethod("getContext").invoke(null);
            Object auth = context.getClass().getMethod("getAuthentication").invoke(context);
            if (auth == null) return false;
            boolean authenticated = (boolean) auth.getClass().getMethod("isAuthenticated").invoke(auth);
            if (!authenticated) return false;
            Collection<?> authorities = (Collection<?>) auth.getClass().getMethod("getAuthorities").invoke(auth);
            for (Object authority : authorities) {
                String role = (String) authority.getClass().getMethod("getAuthority").invoke(authority);
                if (role.equals("ROLE_" + requiredRole) || role.equals(requiredRole)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof String s) return "\"" + escape(s) + "\"";
        if (value instanceof Enum<?> e) return "\"" + e.name() + "\"";
        if (value instanceof java.time.Instant i) return "\"" + i.toString() + "\"";
        if (value instanceof FlashMetrics m) return metricsToJson(m);
        if (value instanceof FlashMetrics.EntityStats e) return entityStatsToJson(e);
        if (value instanceof FlashMetrics.OperationTotals t) return totalsToJson(t);
        if (value instanceof FlashMetrics.WebhookStats w) return webhookStatsToJson(w);
        if (value instanceof FlashMetrics.RecentEvent r) return recentEventToJson(r);
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(entry.getKey().toString())).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (value instanceof Collection<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        return "\"" + escape(value.toString()) + "\"";
    }

    private static String metricsToJson(FlashMetrics m) {
        return "{\"generatedAt\":" + toJson(m.generatedAt()) +
                ",\"uptimeSeconds\":" + m.uptimeSeconds() +
                ",\"entities\":" + toJson(m.entities()) +
                ",\"totals\":" + toJson(m.totals()) +
                ",\"webhooks\":" + toJson(m.webhooks()) +
                ",\"recentEvents\":" + toJson(m.recentEvents()) + "}";
    }

    private static String entityStatsToJson(FlashMetrics.EntityStats e) {
        return "{\"name\":" + toJson(e.name()) +
                ",\"count\":" + e.count() +
                ",\"softDelete\":" + e.softDelete() +
                ",\"auditEnabled\":" + e.auditEnabled() +
                ",\"webhookEnabled\":" + e.webhookEnabled() +
                ",\"rateLimited\":" + e.rateLimited() +
                ",\"multiTenant\":" + e.multiTenant() +
                ",\"operations\":" + toJson(e.operations()) + "}";
    }

    private static String totalsToJson(FlashMetrics.OperationTotals t) {
        return "{\"creates\":" + t.creates() +
                ",\"reads\":" + t.reads() +
                ",\"updates\":" + t.updates() +
                ",\"deletes\":" + t.deletes() +
                ",\"searches\":" + t.searches() +
                ",\"exports\":" + t.exports() +
                ",\"bulkOps\":" + t.bulkOps() +
                ",\"total\":" + t.total() + "}";
    }

    private static String webhookStatsToJson(FlashMetrics.WebhookStats w) {
        return "{\"sent\":" + w.sent() +
                ",\"failed\":" + w.failed() +
                ",\"retries\":" + w.retries() +
                ",\"targetUrls\":" + toJson(w.targetUrls()) + "}";
    }

    private static String recentEventToJson(FlashMetrics.RecentEvent r) {
        return "{\"timestamp\":" + toJson(r.timestamp()) +
                ",\"operation\":" + toJson(r.operation()) +
                ",\"entity\":" + toJson(r.entity()) +
                ",\"entityId\":" + toJson(r.entityId()) +
                ",\"status\":" + toJson(r.status()) + "}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
