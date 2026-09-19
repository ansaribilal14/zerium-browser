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
                int totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int reasonCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON);
                while (c.moveToNext()) {
                    Row r = new Row();
                    r.id = c.getLong(idCol);
                    r.title = c.getString(titleCol);
                    long total = c.getLong(totalCol);
                    long done = c.getLong(doneCol);
                    int st = c.getInt(statusCol);
                    switch (st) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            r.status = getString(R.string.download_done)
                                    + (total > 0 ? " \u00b7 " + humanSize(total) : "");
                            break;
                        case DownloadManager.STATUS_FAILED:
                            r.status = getString(R.string.download_status_failed)
                                    + failureReason(c.getInt(reasonCol));
                            break;
                        case DownloadManager.STATUS_PAUSED:
                            r.status = getString(R.string.download_paused)
                                    + (total > 0 ? " \u00b7 " + percent(done, total) : "");
                            break;
                        default: // STATUS_RUNNING / STATUS_PENDING
                            r.status = getString(R.string.download_running)
                                    + (total > 0 ? " \u00b7 " + percent(done, total) : "");
                            break;
                    }
                    rows.add(r);
                    labels.add(r.title);
                }
            }
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
        findViewById(R.id.empty).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String percent(long done, long total) {
        if (total <= 0) return "";
        long pct = Math.min(100, done * 100 / total);
        return " \u00b7 " + pct + "%";
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576f);
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824f);
    }

    /** Maps the common DownloadManager failure reasons to honest plain text. */
    private String failureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return " \u00b7 " + getString(R.string.download_reason_space);
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return " \u00b7 " + getString(R.string.download_reason_http);
            case DownloadManager.ERROR_FILE_ERROR:
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
            case DownloadManager.ERROR_CANNOT_RESUME:
                return " \u00b7 " + getString(R.string.download_reason_file);
            default:
                return "";
        }
    }
}
