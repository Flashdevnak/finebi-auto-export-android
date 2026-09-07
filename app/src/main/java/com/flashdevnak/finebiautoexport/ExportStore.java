package com.flashdevnak.finebiautoexport;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class ExportStore {
    public static final class Saved {
        public final Uri uri;
        public final String displayName;

        Saved(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }
    }

    public static final class Item {
        public final Uri uri;
        public final String name;
        public final long modified;

        Item(Uri uri, String name, long modified) {
            this.uri = uri;
            this.name = name;
            this.modified = modified;
        }
    }

    private ExportStore() {}

    public static boolean exists(Context context, String displayName) {
        ContentResolver r = context.getContentResolver();
        String[] projection = { MediaStore.Downloads._ID };
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
        String[] args = { displayName };

        try (Cursor c = r.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args, null
        )) {
            return c != null && c.moveToFirst();
        }
    }

    public static Saved saveValidated(
            Context context,
            byte[] bytes,
            String updateTime
    ) throws Exception {
        String date = updateTime.substring(0, 10);
        String safe = updateTime.replace(":", "-").replace(" ", "_");
        String name = "HUB_departure_monitor_" + safe + ".xlsx";

        File temp = new File(context.getCacheDir(), "finebi_" + safe + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            fos.write(bytes);
        }

        XlsxValidator.Result validation = XlsxValidator.validate(temp, updateTime);
        if (!validation.valid) {
            temp.delete();
            throw new IllegalStateException("XLSX validation failed: " + validation.message);
        }

        if (exists(context, name)) {
            temp.delete();
            return new Saved(findUri(context, name), name);
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(
                MediaStore.Downloads.MIME_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/FineBI_Auto_Export/" + date
        );
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            temp.delete();
            throw new IllegalStateException("MediaStore insert failed");
        }

        try {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Cannot open Downloads output");
                out.write(bytes);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        } finally {
            temp.delete();
        }

        return new Saved(uri, name);
    }

    public static List<Item> list(Context context, int limit) {
        ArrayList<Item> out = new ArrayList<>();
        ContentResolver r = context.getContentResolver();
        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED
        };

        String selection = MediaStore.Downloads.RELATIVE_PATH + " LIKE ?";
        String[] args = { Environment.DIRECTORY_DOWNLOADS + "/FineBI_Auto_Export/%" };
        String sort = MediaStore.Downloads.DATE_MODIFIED + " DESC";

        try (Cursor c = r.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args, sort
        )) {
            if (c != null) {
                int idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                int nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
                int modifiedCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED);

                while (c.moveToNext() && out.size() < limit) {
                    long id = c.getLong(idCol);
                    String name = c.getString(nameCol);
                    long modified = c.getLong(modifiedCol) * 1000L;
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                    );
                    out.add(new Item(uri, name, modified));
                }
            }
        } catch (Exception ignored) {}

        return out;
    }

    private static Uri findUri(Context context, String displayName) {
        ContentResolver r = context.getContentResolver();
        String[] projection = { MediaStore.Downloads._ID };
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
        String[] args = { displayName };

        try (Cursor c = r.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args, null
        )) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                );
            }
        }
        return null;
    }
}
