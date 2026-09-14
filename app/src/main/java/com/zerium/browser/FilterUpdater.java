package com.zerium.browser;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads and validates the two filter lists (hosts blocklist and cosmetic
 * element-hiding rules). Used by the manual "Update filter lists" row in
 * Settings and by the weekly automatic refresh.
 *
 * Both files are written to app-private storage; AdBlocker prefers the updated
 * hosts file on load, CosmeticFilter prefers the updated cosmetic file. A
 * download only replaces the previous file when it passes validation, so a
 * failed or hostile response can never degrade blocking.
 */
public final class FilterUpdater {

    public static final String HOSTS_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    public static final String COSMETIC_URL =
            "https://raw.githubusercontent.com/ansaribilal14/zerium-browser/main/"
                    + "app/src/main/assets/blocklists/cosmetic.txt";

    public static final String HOSTS_FILE = "hosts_updated.txt";
    public static final String COSMETIC_FILE = "cosmetic_updated.txt";

    public interface Callback {
        /** Called on the UI thread. updated = 0 (failed), 1 (hosts), 2 (cosmetic), 3 (both). */
        void onDone(int updated);
    }

    private FilterUpdater() {}

    /** True when the automatic weekly refresh should run right now. */
    public static boolean dueForAutoUpdate(Prefs prefs) {
        if (!prefs.autoUpdateLists()) return false;
        long last = prefs.listLastUpdate();
        if (last == 0L) return true; // first run: refresh past the shipped snapshot
        return System.currentTimeMillis() - last > 7L * 24L * 3600L * 1000L;
    }

    public static void updateAll(Context context, Prefs prefs, Callback cb) {
        final Context app = context.getApplicationContext();
        AsyncTaskCompat.run(() -> {
            boolean hosts = download(app, HOSTS_URL, HOSTS_FILE, 100000L, "0.0.0.0");
            boolean cosmetic = download(app, COSMETIC_URL, COSMETIC_FILE, 2000L, "##");
            int updated = (hosts ? 1 : 0) + (cosmetic ? 2 : 0);
            if (updated > 0) prefs.listLastUpdate(System.currentTimeMillis());
            if (cb != null) {
                AsyncTaskCompat.main(() -> cb.onDone(updated));
            }
        });
    }

    /**
     * Downloads to a temp file and only swaps it into place when validation
     * passes (minimum size plus a marker substring), atomically via rename.
     */
    private static boolean download(Context ctx, String url, String target,
                                    long minBytes, String mustContain) {
        File out = new File(ctx.getFilesDir(), target);
        File tmp = new File(ctx.getFilesDir(), target + ".tmp");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) return false;
            long bytes = 0;
            StringBuilder head = new StringBuilder();
            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    bytes += n;
                    if (head.length() < 65536) {
                        head.append(new String(buf, 0, Math.min(n, buf.length),
                                StandardCharsets.UTF_8));
                    }
                }
            }
            if (bytes < minBytes || !head.toString().contains(mustContain)) {
                tmp.delete();
                return false;
            }
            if (out.exists()) out.delete();
            return tmp.renameTo(out);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Tiny indirection so this class has no dependency on Activity classes. */
    private static final class AsyncTaskCompat {
        static void run(Runnable r) {
            new Thread(r, "zerium-filter-update").start();
        }

        static void main(Runnable r) {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(r);
        }
    }
}
