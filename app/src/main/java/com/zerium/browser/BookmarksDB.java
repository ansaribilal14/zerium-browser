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

public class BookmarksDB extends SQLiteOpenHelper {
    private static final String DB = "zerium_bookmarks.db";
    private static final int VERSION = 1;

    public static class Entry {
        public final long id;
        public final String url;
        public final String title;
        public final String added;

        Entry(long id, String url, String title, String added) {
            this.id = id; this.url = url; this.title = title; this.added = added;
        }
    }

    public BookmarksDB(Context c) { super(c, DB, null, VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT UNIQUE, title TEXT, added TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) { }

    public boolean add(String url, String title) {
        if (url == null || url.isEmpty()) return false;
        if (contains(url)) return false;
        ContentValues cv = new ContentValues();
        cv.put("url", url);
        cv.put("title", title == null || title.isEmpty() ? url : title);
        cv.put("added", now());
        getWritableDatabase().insertWithOnConflict("bookmarks", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        return true;
    }

    public boolean remove(long id) {
        return getWritableDatabase().delete("bookmarks", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean contains(String url) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM bookmarks WHERE url=?", new String[]{url});
        boolean has = c.moveToFirst();
        c.close();
        return has;
    }

    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,url,title,added FROM bookmarks ORDER BY id DESC", null);
        while (c.moveToNext()) {
            out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)));
        }
        c.close();
        return out;
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
