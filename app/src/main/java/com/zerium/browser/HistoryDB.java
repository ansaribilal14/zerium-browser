package com.zerium.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryDB extends SQLiteOpenHelper {
    private static final String DB = "zerium_history.db";
    private static final int VERSION = 1;

    public static class Entry {
        public final long id;
        public final String url;
        public final String title;
        public final String visited;

        Entry(long id, String url, String title, String visited) {
            this.id = id; this.url = url; this.title = title; this.visited = visited;
        }
    }

    public HistoryDB(Context c) { super(c, DB, null, VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, visited TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) { }

    public void add(String url, String title) {
        // Only real navigated pages are history. Internal pages
        // (about:blank, about:home, data:, javascript:) never get recorded,
        // so neither the history screen nor the omnibox suggestions can
        // ever resurface a blank/error page.
        if (!Urls.isWebUrl(url)) return;
        ContentValues cv = new ContentValues();
        cv.put("url", url);
        cv.put("title", title == null || title.isEmpty() ? url : title);
        cv.put("visited", timestamp());
        getWritableDatabase().insert("history", null, cv);
    }

    public boolean remove(long id) {
        return getWritableDatabase().delete("history", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,url,title,visited FROM history ORDER BY id DESC LIMIT 500", null);
        while (c.moveToNext()) {
            out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)));
        }
        c.close();
        return out;
    }

    /**
     * Omnibox history suggestions: newest entries whose URL or title
     * contains the query, deduplicated by URL (newest title wins). Fully
     * parameterized — the query string is never concatenated into SQL.
     * Returns at most {@code limit} entries, newest first.
     */
    public List<Entry> suggest(String query, int limit) {
        List<Entry> out = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || limit <= 0) return out;
        String like = "%" + q.toLowerCase() + "%";
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(id), url, MAX(title), MAX(visited) FROM history "
                        + "WHERE LOWER(url) LIKE ? OR LOWER(title) LIKE ? "
                        + "GROUP BY url ORDER BY MAX(id) DESC LIMIT ?",
                new String[]{like, like, String.valueOf(limit)});
        while (c.moveToNext()) {
            out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)));
        }
        c.close();
        return out;
    }

    /**
     * Most-visited distinct URLs for the start-page shortcuts, ordered by
     * visit count. Titles keep their newest version; ties break arbitrarily.
     */
    public List<Entry> topSites(int limit) {
        List<Entry> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(id), url, MAX(title), MAX(visited) FROM history "
                        + "GROUP BY url ORDER BY COUNT(*) DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))});
        while (c.moveToNext()) {
            out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)));
        }
        c.close();
        return out;
    }

    public void clear() {
        getWritableDatabase().delete("history", null, null);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
    }
}
