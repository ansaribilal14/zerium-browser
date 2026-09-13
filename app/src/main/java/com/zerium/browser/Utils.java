package com.zerium.browser;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    private Utils() {}

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
    public static String smartUrl(String input, int engine) {
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
        String template = ENGINE_QUERIES[Math.max(0, Math.min(engine, ENGINE_QUERIES.length - 1))];
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
