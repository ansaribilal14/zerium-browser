package com.zerium.browser;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * SwipeRefreshLayout that understands WebView scroll state.
 *
 * <p>Root cause this class fixes: the stock {@code canChildScrollUp()} only asks its
 * <em>direct</em> child — here {@code webContainer}, a plain {@code FrameLayout} — whether
 * it can scroll upward. A FrameLayout never scrolls, so the answer was always "no" and
 * SwipeRefreshLayout believed every page sat at the top. Consequence: dragging down to
 * scroll a page back up was hijacked into a pull-to-refresh anywhere on the page.</p>
 *
 * <p>Fix: forward the question to the WebView that is actually visible. Zerium keeps one
 * WebView per tab inside {@code webContainer} and toggles {@code VISIBLE}/{@code GONE} on
 * switch, so the visible child is by definition the active page. Its scroll offset decides
 * whether the refresh gesture may arm — the same contract stock pull-to-refresh uses for
 * scrollable children such as RecyclerView.</p>
 *
 * <p>This is evaluated per gesture (not cached), so scroll restoration, back/forward
 * navigation and programmatic scrolls are always reflected correctly, with no wiring
 * needed when tabs are created, switched or closed.</p>
 */
public class BrowserSwipeLayout extends SwipeRefreshLayout {

    public BrowserSwipeLayout(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean canChildScrollUp() {
        View content = getChildAt(0);
        if (content instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) content;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child != null && child.getVisibility() == View.VISIBLE
                        && child instanceof WebView) {
                    // True when the page is scrolled away from the top: the gesture
                    // must scroll the page, never trigger a refresh.
                    return child.canScrollVertically(-1);
                }
            }
        }
        // No live WebView (empty transient state): defer to the default check.
        return super.canChildScrollUp();
    }
}
