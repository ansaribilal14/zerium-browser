package com.zerium.browser;

import android.content.Context;

/** Builds the built-in start page shown on new tabs. */
public final class StartPage {

    private StartPage() {}

    public static String html(Context c, Prefs prefs) {
        int engine = prefs.searchEngine();
        String action = Utils.ENGINE_QUERIES[Math.max(0, Math.min(engine, Utils.ENGINE_QUERIES.length - 1))];
        // Replace %s with a token the page JS fills in.
        action = action.replace("%s", "\u0001ZERIUMQ\u0001");
        long totalBlocked = prefs.totalBlocked();
        String blockedText = totalBlocked > 0
                ? totalBlocked + " trackers and ads blocked so far"
                : "Blocking enabled from your first page load";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
          .append("<title>Zerium</title><style>")
          .append(":root{--bg:#ffffff;--fg:#1b1b1f;--muted:#5f5f6b;--card:#f3f3f8;--accent:#4355b9;--border:#e2e2ea}")
          .append("@media (prefers-color-scheme: dark){:root{--bg:#111318;--fg:#e4e2e6;--muted:#9c9ba4;--card:#1c1e26;--accent:#b6c4ff;--border:#2a2d38}}")
          .append("*{box-sizing:border-box;margin:0;padding:0}")
          .append("body{font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px}")
          .append(".logo{font-size:34px;font-weight:800;letter-spacing:-1px;margin-bottom:4px}")
          .append(".logo span{color:var(--accent)}")
          .append(".tag{color:var(--muted);font-size:13px;margin-bottom:28px}")
          .append("form{width:100%;max-width:560px;margin-bottom:28px}")
          .append("input{width:100%;padding:15px 18px;font-size:16px;border-radius:14px;border:1px solid var(--border);background:var(--card);color:var(--fg);outline:none}")
          .append("input:focus{border-color:var(--accent)}")
          .append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(96px,1fr));gap:12px;width:100%;max-width:560px}")
          .append("a.tile{display:flex;flex-direction:column;align-items:center;gap:8px;padding:16px 8px;background:var(--card);border:1px solid var(--border);border-radius:14px;text-decoration:none;color:var(--fg);font-size:13px}")
          .append("a.tile:active{border-color:var(--accent)}")
          .append(".ic{font-size:22px;font-weight:700;color:var(--accent)}")
          .append(".stat{margin-top:28px;color:var(--muted);font-size:12px}")
          .append("</style></head><body>")
          .append("<div class='logo'>Zerium<span>.</span></div>")
          .append("<div class='tag'>Fast, private, open source</div>")
          .append("<form onsubmit=\"var q=document.getElementById('q').value.trim();")
          .append("if(q){var t='").append(action).append("';")
          .append("location.href=t.replace('\\u0001ZERIUMQ\\u0001',encodeURIComponent(q));}")
          .append("return false;\">")
          .append("<input id='q' type='search' placeholder='Search or type a URL' autocomplete='off' autofocus>")
          .append("</form>")
          .append("<div class='grid'>")
          .append(tile("W", "Wikipedia", "https://en.wikipedia.org"))
          .append(tile("Y", "YouTube", "https://www.youtube.com"))
          .append(tile("G", "GitHub", "https://github.com"))
          .append(tile("R", "Reddit", "https://www.reddit.com"))
          .append(tile("H", "Hacker News", "https://news.ycombinator.com"))
          .append(tile("M", "MDN", "https://developer.mozilla.org"))
          .append("</div>")
          .append("<div class='stat'>").append(blockedText).append(" - Zerium v1.0.0</div>")
          .append("</body></html>");
        return sb.toString();
    }

    private static String tile(String icon, String name, String url) {
        return "<a class='tile' href='" + url + "'><span class='ic'>" + icon + "</span>" + name + "</a>";
    }
}
