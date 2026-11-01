package com.zerium.browser;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Grid adapter for the tab switcher. Shows the active space's tabs plus
 *  every incognito tab (incognito tabs belong to no space). */
public class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.VH> {

    public interface Listener {
        void onOpen(Tab tab);
        void onClose(Tab tab);
        /** Long-press a card: move the tab to another Zerium Space. */
        void onLongPress(Tab tab);
    }

    private final List<Tab> tabs;
    private final TabManager manager;
    private final Listener listener;
    private String activeSpaceId = Spaces.DEFAULT_ID;

    public TabsAdapter(List<Tab> tabs, TabManager manager, Listener listener) {
        this.tabs = tabs;
        this.manager = manager;
        this.listener = listener;
    }

    /** Space whose tabs the switcher currently shows. */
    public void setActiveSpaceId(String spaceId) {
        activeSpaceId = spaceId == null ? Spaces.DEFAULT_ID : spaceId;
    }

    private boolean visible(Tab t) {
        return t.incognito || activeSpaceId.equals(t.spaceId);
    }

    private Tab visibleAt(int position) {
        int seen = 0;
        for (Tab t : tabs) {
            if (!visible(t)) continue;
            if (seen == position) return t;
            seen++;
        }
        return null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Tab t = visibleAt(position);
        if (t == null) return;
        h.title.setText(t.title == null || t.title.isEmpty() ? t.url : t.title);
        h.url.setText(displayHost(t));
        h.incognito.setVisibility(t.incognito ? View.VISIBLE : View.GONE);
        if (t.preview != null && !t.preview.isRecycled()) {
            h.preview.setImageBitmap(t.preview);
        } else {
            h.preview.setImageDrawable(null);
        }
        boolean current = tabs.indexOf(t) == manager.current();
        h.card.setStrokeWidth(current ? 3 : 0);
        h.card.setStrokeColor(ContextCompat.getColor(h.itemView.getContext(), R.color.primary));
        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.close.setOnClickListener(v -> listener.onClose(t));
        h.itemView.setOnLongClickListener(v -> {
            if (!t.incognito) listener.onLongPress(t);
            return true;
        });
    }

    private static String displayHost(Tab t) {
        String u = t.url == null ? "" : t.url;
        if (u.isEmpty() || u.equals(MainActivity.HOME_URL) || u.startsWith("data:")) {
            return t.incognito ? "Incognito" : "Zerium Start";
        }
        String host = Utils.hostOf(u);
        return host == null || host.isEmpty() ? u : host;
    }

    @Override
    public int getItemCount() {
        int n = 0;
        for (Tab t : tabs) if (visible(t)) n++;
        return n;
    }

    static class VH extends RecyclerView.ViewHolder {
        final com.google.android.material.card.MaterialCardView card;
        final TextView title;
        final TextView url;
        final TextView incognito;
        final ImageView preview;
        final ImageButton close;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.tabCard);
            title = v.findViewById(R.id.tabTitle);
            url = v.findViewById(R.id.tabUrl);
            incognito = v.findViewById(R.id.tabIncognito);
            preview = v.findViewById(R.id.tabPreview);
            close = v.findViewById(R.id.tabClose);
        }
    }
}
