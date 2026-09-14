package com.zerium.browser;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Small shared helpers. */
public final class Utils {

    public static final String[] ENGINE_NAMES = {
            "DuckDuckGo", "Startpage", "Brave Search", "Google", "Bing", "Wikipedia"
    };
    public static final String[] ENGINE_QUERIES = {
            "https://duckduckgo.com/?q=%s",
            "https://www.startpage.com/sp/search?query=%s",
            "https://search.brave.com/search?q=%s",
            "https://www.google.com/search?q=%s",
            "https://www.bing.com/search?q=%s",
            "https://en.wikipedia.org/w/index.php?search=%s"
    };
    /** Number of built-in engines; custom engine i is engine index BUILTIN_ENGINES + i. */
    public static final int BUILTIN_ENGINES = ENGINE_NAMES.length;

    /** One selectable search engine (built-in or user-defined). */
    public static class Engine {
        public final String name;
        public final String query;
        public final boolean custom;

        public Engine(String name, String query, boolean custom) {
            this.name = name;
            this.query = query;
            this.custom = custom;
        }
    }

    private Utils() {}

    /** All engines: built-ins first, then the user's custom ones. Never null. */
    public static List<Engine> allEngines(Prefs prefs) {
        List<Engine> out = new ArrayList<>();
        for (int i = 0; i < BUILTIN_ENGINES; i++) {
            out.add(new Engine(ENGINE_NAMES[i], ENGINE_QUERIES[i], false));
        }
        for (Engine e : parseCustomEngines(prefs == null ? "" : prefs.customEngines())) {
            out.add(e);
        }
        return out;
    }

    /** Engine for an index, clamped into range. Never null. */
    public static Engine engineAt(Prefs prefs, int index) {
        List<Engine> all = allEngines(prefs);
        return all.get(Math.max(0, Math.min(index, all.size() - 1)));
    }

    /** Query template for the given engine index, clamped into range. */
    public static String searchTemplate(Prefs prefs, int index) {
        return engineAt(prefs, index).query;
    }

    /** Parses the stored custom-engine JSON; invalid entries are skipped silently. */
    public static List<Engine> parseCustomEngines(String raw) {
        List<Engine> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                String url = o.optString("url", "").trim();
                if (validCustomEngine(name, url)) out.add(new Engine(name, url, true));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean validCustomEngine(String name, String url) {
        return name != null && !name.trim().isEmpty() && name.length() <= 40
                && url != null && url.startsWith("http") && url.contains("%s");
    }

    /** Serializes custom engines back to the stored JSON form. */
    public static String serializeCustomEngines(List<Engine> engines) {
        JSONArray arr = new JSONArray();
        for (Engine e : engines) {
            if (!e.custom) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("name", e.name);
                o.put("url", e.query);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /**
     * Parses the stored home-tile JSON ({name,url} objects). Invalid entries
     * are skipped; the list is capped at limit. Never null.
     */
    public static List<Engine> parseHomeTiles(String raw, int limit) {
        List<Engine> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length() && out.size() < limit; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                String url = o.optString("url", "").trim();
                if (name.isEmpty() || !url.startsWith("http")) continue;
                out.add(new Engine(name, url, true));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static String hostOf(String url) {
        if (url == null) return null;
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns a loadable URL for the omnibox text: URL if it looks like one, else a search query. */
    public static String smartUrl(String input, Prefs prefs) {
        if (input == null) return null;
        input = input.trim();
        if (input.isEmpty()) return null;
        String lower = input.toLowerCase();
        boolean hasScheme = lower.startsWith("http://") || lower.startsWith("https://");
        boolean looksLikeHost = !input.contains(" ")
                && (input.contains(".") || lower.equals("localhost"))
                && !input.endsWith(".");
        if (hasScheme || looksLikeHost) {
            return hasScheme ? input : "https://" + input;
        }
        String q = Uri.encode(input);
        int engine = prefs == null ? 0 : prefs.searchEngine();
        String template = searchTemplate(prefs, engine);
        return template.replace("%s", q);
    }

    public static String fileNameFromUrl(String url) {
        try {
            String path = Uri.parse(url).getLastPathSegment();
            if (path == null || path.isEmpty()) return "zerium-download";
            return path;
        } catch (Exception e) {
            return "zerium-download";
        }
    }

    public static String readAsset(Context c, String path) {
        try (InputStream is = c.getAssets().open(path)) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Reads a line-based asset file into a list, skipping blanks and comments. */
    public static List<String> readAssetLines(Context c, String path) {
        List<String> out = new ArrayList<>();
        try (InputStream is = c.getAssets().open(path)) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
