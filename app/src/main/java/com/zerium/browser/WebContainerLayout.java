package com.zerium.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * The tab container. Besides holding every tab's WebView it implements the
 * edge-swipe tab-switch gesture (UC-Browser style): a clearly horizontal
 * drag that starts within {@link #EDGE_INSET_DP} of the left screen edge and
 * moves right switches to the previous tab; starting at the right edge and
 * moving left switches to the next one. The gesture steals touches only when
 * every one of these holds, so normal page interaction is never affected:
 *
 * <ul>
 *   <li>the user enabled it (Settings → Gestures) and a listener is wired,</li>
 *   <li>the touch started inside the edge inset (never mid-screen),</li>
 *   <li>the drag is dominantly horizontal (|dx| ≥ 56dp and |dx| > 2·|dy|),</li>
 *   <li>the visible WebView cannot scroll horizontally in the drag direction,
 *       so carousels, sliders and map pans keep their own swipes.</li>
 * </ul>
 *
 * A drag that turns vertical (|dy| > 48dp) abandons tracking entirely and
 * leaves the SwipeRefreshLayout above this container free to run its
 * pull-to-refresh logic, exactly as in the v1.3.1 gesture fix.
 */
public class WebContainerLayout extends FrameLayout {

    /** Edge zone width in dp; sits inside the system's ~24dp gesture-nav zone. */
    static final int EDGE_INSET_DP = 24;
    /** Horizontal distance the finger must travel before the tab switch fires. */
    static final int TRIGGER_DP = 56;
    /** Vertical travel that cancels tracking (page scroll wins). */
    static final int ABANDON_DP = 48;

    /** Notified when a confirmed edge swipe should switch tabs. */
    public interface Listener {
        void onSwitchPrevious();
        void onSwitchNext();
    }

    private Listener listener;
    private boolean gesturesEnabled = true;
    private float startX, startY;
    private int side; // -1 left edge, 1 right edge, 0 not tracking
    private boolean armed;

    public WebContainerLayout(Context context) { super(context); }

    public WebContainerLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setGestureListener(Listener l) { listener = l; }

    public void setGesturesEnabled(boolean enabled) { gesturesEnabled = enabled; }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (gesturesEnabled && listener != null) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    armed = false;
                    side = 0;
                    int inset = dp(EDGE_INSET_DP);
                    if (startX <= inset && canTrack(-1)) side = -1;
                    else if (startX >= getWidth() - inset && canTrack(1)) side = 1;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (side != 0 && !armed) {
                        float dx = ev.getX() - startX;
                        float dy = ev.getY() - startY;
                        if (Math.abs(dy) > dp(ABANDON_DP)) {
                            // Vertical intent: hand the gesture to the page /
                            // pull-to-refresh and stop watching this stream.
                            side = 0;
                        } else if (Math.abs(dx) >= dp(TRIGGER_DP)
                                && Math.abs(dx) > 2 * Math.abs(dy)) {
                            boolean toPrevious = side < 0 && dx > 0;
                            boolean toNext = side > 0 && dx < 0;
                            if (toPrevious || toNext) {
                                int dir = toPrevious ? -1 : 1;
                                WebView w = visibleWebView();
                                if (w == null || !w.canScrollHorizontally(dir)) {
                                    armed = true;
                                    if (toPrevious) listener.onSwitchPrevious();
                                    else listener.onSwitchNext();
                                    return true; // take over the rest of the stream
                                }
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    side = 0;
                    armed = false;
                    break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            side = 0;
            armed = false;
        }
        return armed || super.onTouchEvent(ev);
    }

    /**
     * @param dir -1 to scroll left, 1 to scroll right (the direction the page
     *            content would move if the gesture were left to the WebView).
     */
    private boolean canTrack(int dir) {
        WebView w = visibleWebView();
        return w == null || !w.canScrollHorizontally(dir);
    }

    /** The visible WebView for this container, if any (mirrors BrowserSwipeLayout). */
    private WebView visibleWebView() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE && child instanceof WebView) {
                return (WebView) child;
            }
        }
        return null;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
