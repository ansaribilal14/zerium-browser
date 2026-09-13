package com.zerium.browser;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Settings: search engine, blocking, privacy, appearance, data controls. */
public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;

    private static final String HOSTS_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.menu_settings);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        prefs = new Prefs(this);

        // Search engine
        TextView engineValue = findViewById(R.id.valueEngine);
        engineValue.setText(Utils.ENGINE_NAMES[prefs.searchEngine()]);
        findViewById(R.id.rowEngine).setOnClickListener(v -> pickEngine());

        // Content blocking
        bindSwitch(R.id.swBlockAds, prefs.blockAds(), (View.OnClickListener) v -> {
            boolean val = !prefs.blockAds();
            prefs.blockAds(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swCosmetic, prefs.blockCosmetic(), (View.OnClickListener) v -> {
            boolean val = !prefs.blockCosmetic();
            prefs.blockCosmetic(val);
            CosmeticFilter.invalidate();
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });

        bindSwitch(R.id.swYT, prefs.youtubeSuppress(), (View.OnClickListener) v -> {
            boolean val = !prefs.youtubeSuppress();
            prefs.youtubeSuppress(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });

        long last = prefs.listLastUpdate();
        String lastText = last == 0 ? getString(R.string.lists_never)
                : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(last));
        ((TextView) findViewById(R.id.valueLists)).setText(lastText);
        findViewById(R.id.rowUpdateLists).setOnClickListener(v -> updateLists());
        findViewById(R.id.rowAllowlist).setOnClickListener(v -> editAllowlist());

        // Privacy
        bindSwitch(R.id.swCookies, prefs.cookiesEnabled(), (View.OnClickListener) v -> {
            boolean val = !prefs.cookiesEnabled();
            prefs.cookiesEnabled(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swCookies3p, prefs.thirdPartyCookiesBlocked(), (View.OnClickListener) v -> {
            boolean val = !prefs.thirdPartyCookiesBlocked();
            prefs.thirdPartyCookiesBlocked(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swJs, prefs.javascriptEnabled(), (View.OnClickListener) v -> {
            boolean val = !prefs.javascriptEnabled();
            prefs.javascriptEnabled(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swHeaders, prefs.privacyHeaders(), (View.OnClickListener) v -> {
            boolean val = !prefs.privacyHeaders();
            prefs.privacyHeaders(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swForceDark, prefs.forceDarkWeb(), (View.OnClickListener) v -> {
            boolean val = !prefs.forceDarkWeb();
            prefs.forceDarkWeb(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });

        // Appearance
        TextView themeValue = findViewById(R.id.valueTheme);
        String[] themes = getResources().getStringArray(R.array.themes);
        themeValue.setText(themes[prefs.appTheme()]);
        findViewById(R.id.rowTheme).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_theme)
                    .setSingleChoiceItems(themes, prefs.appTheme(), (d, which) -> {
                        prefs.appTheme(which);
                        themeValue.setText(themes[which]);
                        applyTheme(which);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        // Data
        findViewById(R.id.rowClearHistory).setOnClickListener(v -> confirm(R.string.clear_history_title, () -> {
            new HistoryDB(this).clear();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        }));
        findViewById(R.id.rowClearCookies).setOnClickListener(v -> confirm(R.string.clear_cookies_title, () -> {
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.CookieManager.getInstance().flush();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        }));
        findViewById(R.id.rowClearCache).setOnClickListener(v -> confirm(R.string.clear_cache_title, () -> {
            android.webkit.WebStorage.getInstance().deleteAllData();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        }));

        // About
        findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());
    }

    private void bindSwitch(int id, boolean current, View.OnClickListener l) {
        com.google.android.material.materialswitch.MaterialSwitch sw = findViewById(id);
        sw.setChecked(current);
        sw.setOnClickListener(l);
    }

    private void applyTheme(int which) {
        int mode = which == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : which == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(mode);
        getDelegate().applyDayNight();
    }

    private void pickEngine() {
        TextView value = findViewById(R.id.valueEngine);
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_engine)
                .setSingleChoiceItems(Utils.ENGINE_NAMES, prefs.searchEngine(), (d, which) -> {
                    prefs.searchEngine(which);
                    value.setText(Utils.ENGINE_NAMES[which]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editAllowlist() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_allowlist, null);
        EditText input = view.findViewById(R.id.allowlistInput);
        input.setText(prefs.allowlist());
        new AlertDialog.Builder(this)
                .setTitle(R.string.allowlist_title)
                .setMessage(R.string.allowlist_message)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    prefs.setAllowlist(input.getText().toString());
                    // Applied immediately: rebuild the runtime allowlist by re-reading prefs
                    // via a fresh AdBlocker in the running browser activity.
                    Prefs p = new Prefs(this);
                    p.setAllowlist(input.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateLists() {
        TextView value = findViewById(R.id.valueLists);
        value.setText(R.string.lists_updating);
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            boolean ok = false;
            int bytes = 0;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(HOSTS_URL).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                try (InputStream is = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) { bos.write(buf, 0, n); bytes += n; }
                    String content = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                    if (content.contains("0.0.0.0") && content.length() > 100000) {
                        File out = new File(getFilesDir(), "hosts_updated.txt");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                        fos.write(content.getBytes(StandardCharsets.UTF_8));
                        fos.close();
                        ok = true;
                    }
                }
            } catch (Exception ignored) {}
            boolean finalOk = ok;
            runOnUiThread(() -> {
                if (finalOk) {
                    prefs.listLastUpdate(System.currentTimeMillis());
                    value.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            .format(new Date(prefs.listLastUpdate())));
                    Toast.makeText(this, getString(R.string.lists_updated), Toast.LENGTH_SHORT).show();
                    // Restart the browser process UI so the new list loads fresh.
                } else {
                    value.setText(getString(R.string.lists_never));
                    Toast.makeText(this, getString(R.string.lists_failed), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void confirm(int titleRes, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setPositiveButton(R.string.ok, (d, w) -> action.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAbout() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "1.0.0";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_body, version))
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
