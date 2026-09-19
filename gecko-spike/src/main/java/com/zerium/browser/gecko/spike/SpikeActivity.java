package com.zerium.browser.gecko.spike;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * GeckoView Phase-0 spike (docs/GECKOVIEW_SPIKE.md).
 *
 * Minimal single-activity harness that proves the four Phase-0 questions:
 *  1. the GeckoView dependency resolves from maven.mozilla.org and compiles
 *     against the project's JDK 17 / AGP toolchain,
 *  2. GeckoRuntime boots in a plain Activity with no support libraries,
 *  3. a GeckoSession loads a page and reports progress/title through the
 *     delegate interfaces (overridden single-method, defaults cover the rest),
 *  4. session lifecycle (setActive on start/stop) works the way the
 *     migration plan's Phase-1 would wire it.
 *
 * Everything is built programmatically on purpose: no resources, no
 * appcompat, no Zerium code — the spike must show the bare engine cost.
 * It is NOT part of the shipped browser; :app stays on System WebView.
 */
public class SpikeActivity extends Activity {

    private static final String START_URL = "https://example.com";

    private GeckoRuntime runtime;
    private GeckoSession session;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setPadding(48, 48, 48, 24);
        status.setTextSize(13f);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        GeckoView geckoView = new GeckoView(this);
        root.addView(geckoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        setContentView(root);

        status.setText("Booting GeckoRuntime…");

        runtime = GeckoRuntime.create(this, new GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(false)
                .build());

        session = new GeckoSession();
        // Progress + content delegates: modern GeckoView delegates carry
        // default (no-op) implementations, so overriding just the two
        // methods the spike needs is enough and keeps this file small.
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession s, String url) {
                setStatus("Loading " + url);
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                setStatus(success ? "Loaded." : "Load failed.");
            }
        });
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession s, String title) {
                setStatus("Title: " + title);
            }
        });

        geckoView.setSession(session);
        session.open(runtime);
        session.loadUri(START_URL);
    }

    private void setStatus(final String text) {
        runOnUiThread(() -> {
            if (status != null) status.setText(text);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (session != null) session.setActive(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (session != null) session.setActive(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
