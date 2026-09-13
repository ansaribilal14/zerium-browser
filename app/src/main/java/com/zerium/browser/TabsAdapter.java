package com.zerium.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Grid adapter for the tab switcher. */
public class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.VH> {

    public interface Listener {
        void onOpen(Tab tab);
        void onClose(Tab tab);
    }

    private final List<Tab> tabs;
    private final TabManager manager;
    private final Listener listener;

    public TabsAdapter(List<Tab> tabs, TabManager manager, Listener listener) {
        this.tabs = tabs;
        this.manager = manager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Tab t = tabs.get(position);
        h.title.setText(t.title == null || t.title.isEmpty() ? t.url : t.title);
        h.url.setText(t.url == null || t.url.isEmpty() ? h.itemView.getContext().getString(R.string.start_page) : t.url);
        h.incognito.setVisibility(t.incognito ? View.VISIBLE : View.GONE);
        boolean current = tabs.indexOf(t) == manager.current();
        int stroke = current
                ? ContextCompat.getColor(h.itemView.getContext(), R.color.accent)
                : ContextCompat.getColor(h.itemView.getContext(), R.color.border);
        h.card.setStrokeWidth(current ? 4 : 1);
        h.card.setStrokeColor(stroke);
        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.close.setOnClickListener(v -> listener.onClose(t));
    }

    @Override
    public int getItemCount() { return tabs.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final com.google.android.material.card.MaterialCardView card;
        final TextView title;
        final TextView url;
        final TextView incognito;
        final ImageButton close;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.tabCard);
            title = v.findViewById(R.id.tabTitle);
            url = v.findViewById(R.id.tabUrl);
            incognito = v.findViewById(R.id.tabIncognito);
            close = v.findViewById(R.id.tabClose);
        }
    }
}
