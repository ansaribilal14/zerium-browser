package com.zerium.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Downloads managed by the system DownloadManager. Tap to open, long-press to delete. */
public class DownloadsActivity extends AppCompatActivity {

    private static class Row {
        long id;
        String title;
        String status;
    }

    private DownloadManager dm;
    private ArrayAdapter<String> adapter;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.menu_downloads);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                Row r = rows.get(position);
                t1.setText(r.title);
                t2.setText(r.status);
                t2.setTextColor(0xFF888888);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            Row r = rows.get(position);
            try {
                android.net.Uri uri = dm.getUriForDownloadedFile(r.id);
                if (uri != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri,
                            dm.getMimeTypeForDownloadedFile(r.id));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.download_not_ready, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.download_open_failed, Toast.LENGTH_SHORT).show();
            }
        });
        list.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            dm.remove(rows.get(position).id);
            reload();
            return true;
        });
        reload();
    }

    private void reload() {
        rows.clear();
        labels.clear();
        try (Cursor c = dm.query(new DownloadManager.Query())) {
            if (c != null) {
                int idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
                int titleCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE);
                int statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
                while (c.moveToNext()) {
                    Row r = new Row();
                    r.id = c.getLong(idCol);
                    r.title = c.getString(titleCol);
                    int st = c.getInt(statusCol);
                    r.status = st == DownloadManager.STATUS_SUCCESSFUL ? getString(R.string.download_done)
                            : st == DownloadManager.STATUS_FAILED ? getString(R.string.download_status_failed)
                            : st == DownloadManager.STATUS_PAUSED ? getString(R.string.download_paused)
                            : getString(R.string.download_running);
                    rows.add(r);
                    labels.add(r.title);
                }
            }
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
        findViewById(R.id.empty).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
