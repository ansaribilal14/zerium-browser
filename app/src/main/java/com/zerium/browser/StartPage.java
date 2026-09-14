package com.zerium.browser;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the built-in start page shown on new tabs.
 *
 * The shortcut grid is dynamic: user-defined tiles when configured in
 * Settings, otherwise an automatic blend of the most-visited sites from
 * history and the built-in defaults (history entries fill the front slots,
 * defaults fill the rest, deduplicated by host). Search engines come from
 * Utils, so custom engines are honoured in the search pill too.
 */
public final class StartPage {

    private StartPage() {}

    private static final int TILE_COUNT = 8;

    private static final String[][] DEFAULT_TILES = {
            {"Wikipedia", "https://en.wikipedia.org", "W", "#202122"},
            {"YouTube", "https://www.youtube.com", "\u25b6", "#ff0000"},
            {"GitHub", "https://github.com", "G", "#24292f"},
            {"Reddit", "https://www.reddit.com", "R", "#ff4500"},
            {"Hacker News", "https://news.ycombinator.com", "Y", "#ff6600"},
            {"MDN", "https://developer.mozilla.org", "M", "#3b5998"},
            {"Gmail", "https://mail.google.com", "@", "#ea4335"},
            {"X", "https://x.com", "X", "#272a30"},
    };

    private static final String[] TILE_COLORS = {
            "#4355b9", "#c2185b", "#00796b", "#ef6c00", "#5e35b1",
            "#2e7d32", "#0288d1", "#6d4c41", "#455a64", "#ad1457"
    };

    private static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static String searchAction(Prefs prefs) {
        String action = Utils.searchTemplate(prefs, prefs.searchEngine());
        // Replace %s with a token the page JS fills in.
        return action.replace("%s", "\u0001ZERIUMQ\u0001");
    }

    /** Resolved tile: name, url, icon glyph, color. */
    private static class Tile {
        final String name, url, icon, color;
        Tile(String name, String url, String icon, String color) {
            this.name = name; this.url = url; this.icon = icon; this.color = color;
        }
    }

    private static List<Tile> resolveTiles(Context c, Prefs prefs) {
        List<Utils.Engine> custom = Utils.parseHomeTiles(prefs.homeTiles(), TILE_COUNT);
        if (!custom.isEmpty()) {
            List<Tile> out = new ArrayList<>();
            for (Utils.Engine e : custom) {
                out.add(new Tile(e.name, e.query, monogram(e.name), colorFor(e.name)));
            }
            return out;
        }
        return autoTiles(c);
    }

    /** Most-visited history sites first, defaults fill the remaining slots. */
    private static List<Tile> autoTiles(Context c) {
        List<Tile> out = new ArrayList<>();
        Set<String> usedHosts = new HashSet<>();
        Set<String> defaultHosts = new HashSet<>();
        for (String[] d : DEFAULT_TILES) {
            defaultHosts.add(hostKey(d[1]));
        }
        try {
            HistoryDB history = new HistoryDB(c);
            List<HistoryDB.Entry> top = history.topSites(TILE_COUNT * 3);
            history.close();
            for (HistoryDB.Entry e : top) {
                if (out.size() >= TILE_COUNT) break;
                String url = e.url;
                if (url == null || url.startsWith("data:") || url.startsWith("about:")) continue;
                String host = Utils.hostOf(url);
                if (host == null) continue;
                // Search-result pages make poor shortcuts.
                if (isSearchResult(url, host)) continue;
                // One shortcut per site; defaults win their slot.
                String key = hostKey(url);
                if (!usedHosts.add(key)) continue;
                if (defaultHosts.contains(key)) continue;
                String name = e.title == null || e.title.isEmpty() ? host : e.title;
                if (name.length() > 14) name = name.substring(0, 13) + "\u2026";
                out.add(new Tile(name, url, monogram(host), colorFor(host)));
            }
        } catch (Exception ignored) {}
        for (String[] d : DEFAULT_TILES) {
            if (out.size() >= TILE_COUNT) break;
            if (usedHosts.contains(hostKey(d[1]))) continue;
            out.add(new Tile(d[0], d[1], d[2], d[3]));
        }
        return out;
    }

    private static boolean isSearchResult(String url, String host) {
        String u = url.toLowerCase();
        return u.contains("/search?") || u.contains("search?q=") || u.contains("/results?q=")
                || (host.contains("google.") && u.contains("/search"))
                || (host.contains("bing.com") && u.contains("/search"))
                || (host.contains("duckduckgo.com") && u.contains("?q="))
                || (host.contains("search.brave.com") && u.contains("?q="));
    }

    private static String hostKey(String url) {
        String h = Utils.hostOf(url);
        if (h == null) return url == null ? "" : url;
        if (h.startsWith("www.")) h = h.substring(4);
        return h;
    }

    private static String monogram(String seed) {
        if (seed == null || seed.isEmpty()) return "?";
        char ch = Character.toUpperCase(seed.trim().charAt(0));
        return String.valueOf(ch);
    }

    private static String colorFor(String seed) {
        int hash = 0;
        if (seed != null) for (int i = 0; i < seed.length(); i++) hash = hash * 31 + seed.charAt(i);
        return TILE_COLORS[Math.abs(hash) % TILE_COLORS.length];
    }

    public static String html(Context c, Prefs prefs) {
        String action = searchAction(prefs);
        long totalBlocked = prefs.totalBlocked();
        String blockedText = totalBlocked > 0
                ? totalBlocked + " ads and trackers blocked so far"
                : "Blocking enabled from your first page load";
        String version = versionName(c);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
          .append("<title>Zerium</title><style>")
          // Theme tokens
          .append(":root{--bg:#f6f7fb;--fg:#1b1b1f;--muted:#5f5f6b;--card:#ffffff;--accent:#4355b9;")
          .append("--accent2:#7c9cff;--border:#e2e2ea;--chip:#f3f3f8}")
          .append("@media (prefers-color-scheme: dark){:root{--bg:#0e1016;--fg:#e4e2e6;--muted:#9c9ba4;")
          .append("--card:#171a23;--accent:#b6c4ff;--accent2:#4355b9;--border:#2a2d38;--chip:#1f232e}}")
          .append("*{box-sizing:border-box;margin:0;padding:0}")
          .append("body{font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);")
          .append("min-height:100vh;display:flex;flex-direction:column;align-items:center;")
          .append("justify-content:center;padding:24px}")
          // Hero
          .append(".logo{font-size:40px;font-weight:800;letter-spacing:-1.5px;line-height:1;")
          .append("background:linear-gradient(135deg,var(--accent),var(--accent2));")
          .append("-webkit-background-clip:text;background-clip:text;color:transparent}")
          .append(".logo b{color:var(--accent);-webkit-text-fill-color:var(--accent)}")
          .append(".tag{color:var(--muted);font-size:13px;margin:8px 0 30px;letter-spacing:.2px}")
          // Search
          .append("form{width:100%;max-width:580px;margin-bottom:30px}")
          .append(".search{display:flex;align-items:center;background:var(--card);")
          .append("border:1px solid var(--border);border-radius:24px;padding:4px 4px 4px 18px;")
          .append("box-shadow:0 4px 18px rgba(20,25,60,.06);transition:border-color .15s}")
          .append(".search:focus-within{border-color:var(--accent)}")
          .append(".search svg{flex:none;opacity:.55}")
          .append("input{flex:1;min-width:0;padding:13px 12px;font-size:16px;border:0;outline:none;")
          .append("background:transparent;color:var(--fg)}")
          .append("button{flex:none;border:0;border-radius:18px;padding:11px 20px;font-size:14px;")
          .append("font-weight:600;color:#fff;background:var(--accent);cursor:pointer}")
          .append("button:active{opacity:.85}")
          // Tiles
          .append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(84px,1fr));")
          .append("gap:10px;width:100%;max-width:580px}")
          .append("a.tile{display:flex;flex-direction:column;align-items:center;gap:8px;")
          .append("padding:14px 6px 12px;background:var(--card);border:1px solid var(--border);")
          .append("border-radius:16px;text-decoration:none;color:var(--fg);font-size:12px;")
          .append("transition:transform .1s,border-color .15s}")
          .append("a.tile:active{transform:scale(.96);border-color:var(--accent)}")
          .append(".ic{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;")
          .append("justify-content:center;color:#fff;font-size:19px;font-weight:700}")
          // Stat card
          .append(".stat{margin-top:30px;width:100%;max-width:580px;display:flex;align-items:center;")
          .append("gap:10px;background:var(--chip);border:1px solid var(--border);border-radius:14px;")
          .append("padding:12px 16px;color:var(--muted);font-size:12.5px}")
          .append(".dot{flex:none;width:8px;height:8px;border-radius:50%;background:var(--accent)}")
          .append(".stat b{color:var(--fg);font-weight:600}")
          .append("</style></head><body>")
          .append("<div class='logo'>Zerium<b>.</b></div>")
          .append("<div class='tag'>Fast &middot; private &middot; open source</div>")
          .append("<form onsubmit=\"var q=document.getElementById('q').value.trim();")
          .append("if(q){var t='").append(action).append("';")
          .append("location.href=t.replace('\\u0001ZERIUMQ\\u0001',encodeURIComponent(q));}")
          .append("return false;\">")
          .append("<div class='search'>")
          .append("<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='currentColor'")
          .append(" stroke-width='2.4' stroke-linecap='round'><circle cx='11' cy='11' r='7'/>")
          .append("<path d='M20 20l-3.6-3.6'/></svg>")
          .append("<input id='q' type='search' placeholder='Search or type a URL' autocomplete='off' autofocus>")
          .append("<button type='submit'>Go</button>")
          .append("</div></form>")
          .append("<div class='grid'>");
        for (Tile t : resolveTiles(c, prefs)) {
            sb.append(tile(t.icon, t.color, t.name, t.url));
        }
        sb.append("</div>")
          .append("<div class='stat'><span class='dot'></span><span><b>")
          .append(blockedText).append("</b> &middot; Zerium v")
          .append(version).append(" &mdash; no telemetry, ever.</span></div>")
          .append("</body></html>");
        return sb.toString();
    }

    private static String tile(String icon, String color, String name, String url) {
        String safeName = name == null ? "" : name.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        String safeUrl = url == null ? "" : url.replace("&", "&amp;")
                .replace("'", "&#39;").replace("\"", "&quot;");
        String safeIcon = icon == null ? "" : icon.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        return "<a class='tile' href='" + safeUrl + "'><span class='ic' style='background:"
                + color + "'>" + safeIcon + "</span>" + safeName + "</a>";
    }
}
