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
            response.getWriter().write("<h1>403 — Access Denied</h1><p>Required role: " + requiredRole + "</p>");
            response.flushBuffer();
            return;
        }
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding("UTF-8");

        String basePath = request.getRequestURI().replace("/index.html", "").replaceAll("/+$", "");
        String metricsUrl = basePath + "/metrics.json";

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>FlashAPI Dashboard</title>
                    <link rel="icon" type="image/svg+xml" href="https://raw.githubusercontent.com/HackermanMe/spring-flashapi/main/docs/assets/favicon.svg">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        :root {
                            --bg: #0f1117;
                            --surface: #1a1d27;
                            --border: #2a2d3a;
                            --text: #e4e4e7;
                            --text-muted: #9ca3af;
                            --accent: #6366f1;
                            --accent-glow: rgba(99, 102, 241, 0.15);
                            --green: #22c55e;
                            --red: #ef4444;
                            --orange: #f59e0b;
                            --blue: #3b82f6;
                        }
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; }
                        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 32px; }
                        .header h1 { font-size: 24px; font-weight: 600; }
                        .header h1 span { color: var(--accent); }
                        .status { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-muted); }
                        .status-dot { width: 8px; height: 8px; border-radius: 50%%; background: var(--green); animation: pulse 2s infinite; }
                        @keyframes pulse { 0%%, 100%% { opacity: 1; } 50%% { opacity: 0.5; } }
                        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; margin-bottom: 24px; }
                        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
                        .card-header { font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted); margin-bottom: 12px; }
                        .card-value { font-size: 32px; font-weight: 700; }
                        .card-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }
                        .entities-table { width: 100%%; border-collapse: collapse; }
                        .entities-table th { text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted); padding: 8px 12px; border-bottom: 1px solid var(--border); }
                        .entities-table td { padding: 10px 12px; border-bottom: 1px solid var(--border); font-size: 14px; }
                        .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500; }
                        .badge-green { background: rgba(34,197,94,0.15); color: var(--green); }
                        .badge-blue { background: rgba(59,130,246,0.15); color: var(--blue); }
                        .badge-orange { background: rgba(245,158,11,0.15); color: var(--orange); }
                        .badge-red { background: rgba(239,68,68,0.15); color: var(--red); }
                        .badge-muted { background: rgba(156,163,175,0.1); color: var(--text-muted); }
                        .events-list { max-height: 300px; overflow-y: auto; }
                        .event-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--border); font-size: 13px; }
                        .event-time { color: var(--text-muted); font-variant-numeric: tabular-nums; min-width: 70px; }
                        .event-op { font-weight: 600; min-width: 60px; }
                        .event-op.CREATE { color: var(--green); }
                        .event-op.UPDATE { color: var(--blue); }
                        .event-op.DELETE { color: var(--red); }
                        .event-op.SEARCH { color: var(--orange); }
                        .event-op.EXPORT { color: var(--accent); }
                        .event-op.BULK { color: var(--orange); }
                        .event-entity { color: var(--text); }
                        .event-id { color: var(--text-muted); }
                        .bar-container { display: flex; gap: 2px; height: 20px; align-items: end; margin-top: 8px; }
                        .bar { border-radius: 2px; min-width: 4px; }
                        .refresh-info { font-size: 12px; color: var(--text-muted); }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1><span>⚡</span> FlashAPI Dashboard</h1>
                        <div class="status">
                            <div class="status-dot"></div>
                            <span id="uptime">Loading...</span>
                            <span class="refresh-info">• auto-refresh 5s</span>
                        </div>
                    </div>
                    <div class="grid" id="stats-grid"></div>
                    <div class="grid" style="grid-template-columns: 1fr 1fr;">
                        <div class="card">
                            <div class="card-header">Entities</div>
                            <div id="entities-content"></div>
                        </div>
                        <div class="card">
                            <div class="card-header">Recent Activity</div>
                            <div class="events-list" id="events-list"></div>
                        </div>
                    </div>
                    <script>
                        const METRICS_URL = '%s';
                        function formatUptime(s) {
                            const d = Math.floor(s/86400), h = Math.floor((s%%86400)/3600), m = Math.floor((s%%3600)/60);
                            return (d > 0 ? d+'d ' : '') + h+'h '+m+'m';
                        }
                        function render(data) {
                            document.getElementById('uptime').textContent = 'Up ' + formatUptime(data.uptimeSeconds);
                            const grid = document.getElementById('stats-grid');
                            grid.innerHTML = `
                                <div class="card">
                                    <div class="card-header">Total Operations</div>
                                    <div class="card-value">${data.totals.total.toLocaleString()}</div>
                                    <div class="card-sub">${data.totals.creates} creates • ${data.totals.updates} updates • ${data.totals.deletes} deletes</div>
                                </div>
                                <div class="card">
                                    <div class="card-header">Entities Registered</div>
                                    <div class="card-value">${Object.keys(data.entities).length}</div>
                                    <div class="card-sub">${Object.values(data.entities).filter(e=>e.webhookEnabled).length} with webhooks • ${Object.values(data.entities).filter(e=>e.auditEnabled).length} audited</div>
                                </div>
                                <div class="card">
                                    <div class="card-header">Webhooks</div>
                                    <div class="card-value">${data.webhooks.sent.toLocaleString()}</div>
                                    <div class="card-sub" style="color:${data.webhooks.failed>0?'var(--red)':'var(--text-muted)'}">
                                        ${data.webhooks.failed} failed • ${data.webhooks.retries} retries • ${data.webhooks.targetUrls.length} target${data.webhooks.targetUrls.length!==1?'s':''}
                                    </div>
                                </div>
                                <div class="card">
                                    <div class="card-header">Reads & Searches</div>
                                    <div class="card-value">${(data.totals.reads + data.totals.searches).toLocaleString()}</div>
                                    <div class="card-sub">${data.totals.reads} list/get • ${data.totals.searches} searches • ${data.totals.exports} exports</div>
                                </div>
                            `;
                            const entities = Object.values(data.entities);
                            let tbl = '<table class="entities-table"><thead><tr><th>Entity</th><th>Operations</th><th>Features</th></tr></thead><tbody>';
                            entities.forEach(e => {
                                const badges = [];
                                if(e.softDelete) badges.push('<span class="badge badge-orange">soft-delete</span>');
                                if(e.webhookEnabled) badges.push('<span class="badge badge-blue">webhook</span>');
                                if(e.auditEnabled) badges.push('<span class="badge badge-green">audit</span>');
                                if(e.rateLimited) badges.push('<span class="badge badge-red">rate-limit</span>');
                                if(e.multiTenant) badges.push('<span class="badge badge-muted">multi-tenant</span>');
                                tbl += `<tr><td><strong>${e.name}</strong></td><td>${e.count}</td><td>${badges.join(' ')}</td></tr>`;
                            });
                            tbl += '</tbody></table>';
                            document.getElementById('entities-content').innerHTML = tbl;
                            const evList = document.getElementById('events-list');
                            if(data.recentEvents.length === 0) {
                                evList.innerHTML = '<div style="color:var(--text-muted);padding:20px;text-align:center">No activity yet</div>';
                            } else {
                                evList.innerHTML = data.recentEvents.map(ev => {
                                    const t = new Date(ev.timestamp).toLocaleTimeString();
                                    return `<div class="event-row"><span class="event-time">${t}</span><span class="event-op ${ev.operation}">${ev.operation}</span><span class="event-entity">${ev.entity}</span><span class="event-id">${ev.entityId||''}</span></div>`;
                                }).join('');
                            }
                        }
                        async function refresh() {
                            try {
                                const res = await fetch(METRICS_URL);
                                const data = await res.json();
                                render(data);
                            } catch(e) { console.error('Dashboard refresh failed:', e); }
                        }
                        refresh();
                        setInterval(refresh, 5000);
                    </script>
                </body>
                </html>
                """.formatted(metricsUrl);

        response.getWriter().write(html);
        response.flushBuffer();
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
