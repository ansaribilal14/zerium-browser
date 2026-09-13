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
        if (url == null || url.isEmpty() || url.startsWith("data:")) return;
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

    public void clear() {
        getWritableDatabase().delete("history", null, null);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
    }
}
