package com.zerium.browser;

import android.content.Context;

/** Builds the built-in start page shown on new tabs. */
public final class StartPage {

    private StartPage() {}

    private static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    public static String html(Context c, Prefs prefs) {
        int engine = prefs.searchEngine();
        String action = Utils.ENGINE_QUERIES[Math.max(0, Math.min(engine, Utils.ENGINE_QUERIES.length - 1))];
        // Replace %s with a token the page JS fills in.
        action = action.replace("%s", "\u0001ZERIUMQ\u0001");
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
          .append("<div class='grid'>")
          .append(tile("W", "#202122", "Wikipedia", "https://en.wikipedia.org"))
          .append(tile("▶", "#ff0000", "YouTube", "https://www.youtube.com"))
          .append(tile("G", "#24292f", "GitHub", "https://github.com"))
          .append(tile("R", "#ff4500", "Reddit", "https://www.reddit.com"))
          .append(tile("Y", "#ff6600", "Hacker News", "https://news.ycombinator.com"))
          .append(tile("M", "#3b5998", "MDN", "https://developer.mozilla.org"))
          .append(tile("@", "#ea4335", "Gmail", "https://mail.google.com"))
          .append(tile("X", "#272a30", "X", "https://x.com"))
          .append("</div>")
          .append("<div class='stat'><span class='dot'></span><span><b>")
          .append(blockedText).append("</b> &middot; Zerium v")
          .append(version).append(" &mdash; no telemetry, ever.</span></div>")
          .append("</body></html>");
        return sb.toString();
    }

    private static String tile(String icon, String color, String name, String url) {
        return "<a class='tile' href='" + url + "'><span class='ic' style='background:" + color
                + "'>" + icon + "</span>" + name + "</a>";
    }
}
