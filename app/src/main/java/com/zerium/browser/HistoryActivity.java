package com.zerium.browser;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Browsing history. Tap to open, long-press to delete one entry. */
public class HistoryActivity extends AppCompatActivity {

    private HistoryDB db;
    private ArrayAdapter<String> adapter;
    private final List<HistoryDB.Entry> entries = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.menu_history);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        db = new HistoryDB(this);

        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                HistoryDB.Entry e = entries.get(position);
                t1.setText(e.title);
                t2.setText(e.visited + "  -  " + e.url);
                t2.setTextColor(0xFF888888);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            Intent data = new Intent();
            data.setData(android.net.Uri.parse(entries.get(position).url));
            setResult(RESULT_OK, data);
            finish();
        });
        list.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            HistoryDB.Entry e = entries.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(e.url)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        db.remove(e.id);
                        reload();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        reload();
    }

    private void reload() {
        entries.clear();
        labels.clear();
        entries.addAll(db.all());
        for (HistoryDB.Entry e : entries) labels.add(e.title);
        adapter.notifyDataSetChanged();
        findViewById(R.id.empty).setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
