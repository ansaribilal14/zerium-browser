package com.zerium.browser;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings: search engines, blocking, privacy, behaviour, appearance, data. */
public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;

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
        engineValue.setText(Utils.engineAt(prefs, prefs.searchEngine()).name);
        findViewById(R.id.rowEngine).setOnClickListener(v -> pickEngine());

        // Custom engines
        TextView customValue = findViewById(R.id.valueEngines);
        customValue.setText(String.valueOf(Utils.parseCustomEngines(prefs.customEngines()).size()));
        findViewById(R.id.rowManageEngines).setOnClickListener(v -> manageCustomEngines());

        // Home shortcuts
        TextView shortcutsValue = findViewById(R.id.valueShortcuts);
        shortcutsValue.setText(prefs.homeTiles().isEmpty()
                ? R.string.shortcuts_auto : R.string.shortcuts_custom);
        findViewById(R.id.rowHomeShortcuts).setOnClickListener(v -> pickShortcutsMode());

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

        bindSwitch(R.id.swAutoLists, prefs.autoUpdateLists(), (View.OnClickListener) v -> {
            boolean val = !prefs.autoUpdateLists();
            prefs.autoUpdateLists(val);
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
        bindSwitch(R.id.swHttpsUpgrade, prefs.httpsUpgrade(), (View.OnClickListener) v -> {
            boolean val = !prefs.httpsUpgrade();
            prefs.httpsUpgrade(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swForceDark, prefs.forceDarkWeb(), (View.OnClickListener) v -> {
            boolean val = !prefs.forceDarkWeb();
            prefs.forceDarkWeb(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });

        // Behaviour
        bindSwitch(R.id.swPullRefresh, prefs.pullToRefresh(), (View.OnClickListener) v -> {
            boolean val = !prefs.pullToRefresh();
            prefs.pullToRefresh(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swForceZoom, prefs.forceZoom(), (View.OnClickListener) v -> {
            boolean val = !prefs.forceZoom();
            prefs.forceZoom(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });
        bindSwitch(R.id.swAutoplay, prefs.mediaAutoplay(), (View.OnClickListener) v -> {
            boolean val = !prefs.mediaAutoplay();
            prefs.mediaAutoplay(val);
            ((com.google.android.material.materialswitch.MaterialSwitch) v).setChecked(val);
        });

        // Text size
        TextView textSizeValue = findViewById(R.id.valueTextSize);
        textSizeValue.setText(getString(R.string.text_size_percent, prefs.textZoom()));
        findViewById(R.id.rowTextSize).setOnClickListener(v -> pickTextSize());

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

    private static final int[] TEXT_SIZES = {50, 75, 100, 125, 150, 175, 200};

    private void pickTextSize() {
        TextView value = findViewById(R.id.valueTextSize);
        String[] labels = new String[TEXT_SIZES.length];
        for (int i = 0; i < TEXT_SIZES.length; i++) {
            labels[i] = getString(R.string.text_size_percent, TEXT_SIZES[i]);
        }
        int current = 0;
        for (int i = 0; i < TEXT_SIZES.length; i++) {
            if (TEXT_SIZES[i] == prefs.textZoom()) current = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.text_size)
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    prefs.textZoom(TEXT_SIZES[which]);
                    value.setText(labels[which]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickEngine() {
        TextView value = findViewById(R.id.valueEngine);
        List<Utils.Engine> all = Utils.allEngines(prefs);
        String[] names = new String[all.size()];
        int current = Math.max(0, Math.min(prefs.searchEngine(), all.size() - 1));
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).custom ? all.get(i).name + " \u2605" : all.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_engine)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    prefs.searchEngine(which);
                    value.setText(all.get(which).name);
                    d.dismiss();
                })
                .setPositiveButton(R.string.add_engine, (d, w) -> addCustomEngine())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void manageCustomEngines() {
        List<Utils.Engine> customs = Utils.parseCustomEngines(prefs.customEngines());
        if (customs.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.manage_engines)
                    .setMessage(R.string.no_custom_engines)
                    .setPositiveButton(R.string.add_engine, (d, w) -> addCustomEngine())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        String[] names = new String[customs.size()];
        for (int i = 0; i < customs.size(); i++) names[i] = customs.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.manage_engines)
                .setItems(names, (d, which) -> {
                    Utils.Engine target = customs.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(target.name)
                            .setPositiveButton(R.string.set_default, (d2, w2) -> {
                                List<Utils.Engine> all = Utils.allEngines(prefs);
                                for (int i = 0; i < all.size(); i++) {
                                    if (all.get(i).custom && all.get(i).name.equals(target.name)
                                            && all.get(i).query.equals(target.query)) {
                                        prefs.searchEngine(i);
                                        ((TextView) findViewById(R.id.valueEngine)).setText(target.name);
                                        break;
                                    }
                                }
                                d2.dismiss();
                            })
                            .setNegativeButton(R.string.delete, (d2, w2) -> {
                                deleteCustomEngine(target);
                                d2.dismiss();
                            })
                            .setNeutralButton(R.string.cancel, null)
                            .show();
                })
                .setPositiveButton(R.string.add_engine, (d, w) -> addCustomEngine())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteCustomEngine(Utils.Engine target) {
        List<Utils.Engine> customs = Utils.parseCustomEngines(prefs.customEngines());
        List<Utils.Engine> remaining = new ArrayList<>();
        for (Utils.Engine e : customs) {
            if (!e.name.equals(target.name) || !e.query.equals(target.query)) remaining.add(e);
        }
        prefs.customEngines(Utils.serializeCustomEngines(remaining));
        ((TextView) findViewById(R.id.valueEngines)).setText(String.valueOf(remaining.size()));
        // If the deleted engine was the default, fall back to DuckDuckGo.
        List<Utils.Engine> all = Utils.allEngines(prefs);
        if (prefs.searchEngine() >= all.size()) prefs.searchEngine(0);
        ((TextView) findViewById(R.id.valueEngine)).setText(
                Utils.engineAt(prefs, prefs.searchEngine()).name);
        Toast.makeText(this, R.string.engine_deleted, Toast.LENGTH_SHORT).show();
    }

    private void addCustomEngine() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        final EditText name = new EditText(this);
        name.setHint(R.string.engine_name_hint);
        name.setSingleLine(true);
        box.addView(name);
        final EditText url = new EditText(this);
        url.setHint(R.string.engine_url_hint);
        url.setSingleLine(true);
        box.addView(url);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_engine)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (!Utils.validCustomEngine(n, u)) {
                        Toast.makeText(this, R.string.engine_url_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<Utils.Engine> customs = Utils.parseCustomEngines(prefs.customEngines());
                    for (Utils.Engine e : customs) {
                        if (e.name.equals(n)) {
                            Toast.makeText(this, R.string.engine_url_invalid, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    customs.add(new Utils.Engine(n, u, true));
                    prefs.customEngines(Utils.serializeCustomEngines(customs));
                    ((TextView) findViewById(R.id.valueEngines)).setText(String.valueOf(customs.size()));
                    Toast.makeText(this, R.string.engine_added, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickShortcutsMode() {
        TextView value = findViewById(R.id.valueShortcuts);
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_shortcuts)
                .setItems(new CharSequence[]{
                                getString(R.string.shortcuts_auto), getString(R.string.shortcuts_custom)},
                        (d, which) -> {
                            if (which == 0) {
                                prefs.homeTiles("");
                                value.setText(R.string.shortcuts_auto);
                                Toast.makeText(this, R.string.shortcuts_saved, Toast.LENGTH_SHORT).show();
                            } else {
                                editShortcuts();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editShortcuts() {
        TextView value = findViewById(R.id.valueShortcuts);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        TextView hint = new TextView(this);
        hint.setText(R.string.shortcuts_hint);
        hint.setTextSize(12f);
        box.addView(hint);
        final EditText input = new EditText(this);
        input.setMinLines(4);
        input.setGravity(android.view.Gravity.TOP);
        List<Utils.Engine> current = Utils.parseHomeTiles(prefs.homeTiles(), 100);
        StringBuilder sb = new StringBuilder();
        for (Utils.Engine e : current) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.name.replace("|", "").trim()).append(" | ").append(e.query);
        }
        input.setText(sb.toString());
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.home_shortcuts)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    List<Utils.Engine> tiles = new ArrayList<>();
                    boolean hadInvalid = false;
                    for (String line : input.getText().toString().split("\n")) {
                        int sep = line.indexOf('|');
                        if (sep <= 0) {
                            if (line.trim().isEmpty()) continue;
                            hadInvalid = true;
                            continue;
                        }
                        String n = line.substring(0, sep).trim();
                        String u = line.substring(sep + 1).trim();
                        if (n.isEmpty() || !u.startsWith("http")) {
                            hadInvalid = true;
                            continue;
                        }
                        tiles.add(new Utils.Engine(n, u, true));
                        if (tiles.size() >= 8) break;
                    }
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < tiles.size(); i++) {
                        if (i > 0) json.append(',');
                        json.append("{\"name\":").append(jsonQuote(tiles.get(i).name))
                                .append(",\"url\":").append(jsonQuote(tiles.get(i).query)).append('}');
                    }
                    json.append(']');
                    prefs.homeTiles(json.toString());
                    value.setText(tiles.isEmpty() ? R.string.shortcuts_auto : R.string.shortcuts_custom);
                    Toast.makeText(this, hadInvalid ? R.string.shortcuts_invalid
                            : R.string.shortcuts_saved, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Minimal JSON string quoting for the tile/URL pair we just validated. */
    private static String jsonQuote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\');
            out.append(ch);
        }
        return out.append('"').toString();
    }

    private void editAllowlist() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_allowlist, null);
        EditText input = view.findViewById(R.id.allowlistInput);
        input.setText(prefs.allowlist());
        new AlertDialog.Builder(this)
                .setTitle(R.string.allowlist_title)
                .setMessage(R.string.allowlist_message)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) ->
                        prefs.setAllowlist(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateLists() {
        TextView value = findViewById(R.id.valueLists);
        value.setText(R.string.lists_updating);
        FilterUpdater.updateAll(this, prefs, updated -> {
            if (updated == 3) {
                value.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(new Date(prefs.listLastUpdate())));
                Toast.makeText(this, getString(R.string.lists_updated), Toast.LENGTH_SHORT).show();
            } else if (updated > 0) {
                value.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(new Date(prefs.listLastUpdate())));
                Toast.makeText(this, getString(R.string.lists_partial), Toast.LENGTH_LONG).show();
            } else {
                value.setText(getString(R.string.lists_failed));
                Toast.makeText(this, getString(R.string.lists_failed), Toast.LENGTH_LONG).show();
            }
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
