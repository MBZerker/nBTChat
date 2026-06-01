package com.mbzerker.nbtchat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class BackupFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.mbzerker.nbtchat.backupfiles";

    public static Uri uriFor(Context context, File file) {
        String parent = file == null || file.getParentFile() == null ? "" : file.getParentFile().getName();
        String bucket = "updates".equals(parent) ? "updates" : "backups";
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(bucket)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri == null ? "" : uri.getLastPathSegment();
        String lowerName = name == null ? "" : name.toLowerCase();
        if (lowerName.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        return "application/zip";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = fileFor(uri);
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileFor(uri);
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File fileFor(Uri uri) {
        String name = uri == null ? "" : uri.getLastPathSegment();
        if (name == null) {
            name = "";
        }
        String bucket = "backups";
        if (uri != null && uri.getPathSegments().size() >= 2) {
            String first = uri.getPathSegments().get(0);
            if ("updates".equals(first)) {
                bucket = "updates";
            }
        }
        name = name.replace("/", "").replace("\\", "");
        File dir = new File(getContext().getCacheDir(), bucket);
        return new File(dir, name);
    }
}
