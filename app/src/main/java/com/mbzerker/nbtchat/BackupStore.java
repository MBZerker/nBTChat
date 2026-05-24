package com.mbzerker.nbtchat;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupStore {
    private static final String ENTRY_NAME = "nbtchat-backup.json";
    private static final String[] PREF_NAMES = {
            "local_profile",
            "known_contacts",
            "contact_fingerprints",
            "contact_identities",
            "contact_flags",
            "chat_messages",
            "chat_meta",
            "official_gadgets",
            "relay_queue",
            "relay_seen",
            "app_settings",
            "appearance",
            "local_identity"
    };

    public File createZipBackup(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("type", "nbtchat-backup");
        root.put("version", 1);
        root.put("createdAt", System.currentTimeMillis());

        JSONObject prefs = new JSONObject();
        for (String name : PREF_NAMES) {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : new EncryptedPrefs(context, name).getAll().entrySet()) {
                values.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
            prefs.put(name, values);
        }
        root.put("prefs", prefs);

        File dir = new File(context.getCacheDir(), "backups");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Nao foi possivel preparar o backup.");
        }
        File file = new File(dir, "nBTChat-backup-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(ENTRY_NAME));
            zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    public RestoreResult restoreZipBackup(Context context, Uri uri) throws Exception {
        JSONObject root = readBackupJson(context, uri);
        if (!"nbtchat-backup".equals(root.optString("type", ""))) {
            throw new IllegalArgumentException("Arquivo de backup invalido.");
        }
        JSONObject prefs = root.optJSONObject("prefs");
        if (prefs == null) {
            throw new IllegalArgumentException("Backup sem dados para restaurar.");
        }

        int groups = 0;
        int values = 0;
        for (String name : PREF_NAMES) {
            JSONObject group = prefs.optJSONObject(name);
            if (group == null) {
                continue;
            }
            EncryptedPrefs encryptedPrefs = new EncryptedPrefs(context, name);
            encryptedPrefs.clear();
            EncryptedPrefs.Editor editor = encryptedPrefs.edit();
            Iterator<String> keys = group.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                editor.putString(key, group.optString(key, ""));
                values++;
            }
            editor.apply();
            groups++;
        }
        return new RestoreResult(groups, values);
    }

    private JSONObject readBackupJson(Context context, Uri uri) throws Exception {
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalArgumentException("Nao foi possivel abrir o backup.");
        }
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!ENTRY_NAME.equals(entry.getName()) && !entry.getName().endsWith(".json")) {
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        throw new IllegalArgumentException("O ZIP nao contem um backup do nBTChat.");
    }

    public static final class RestoreResult {
        public final int groups;
        public final int values;

        RestoreResult(int groups, int values) {
            this.groups = groups;
            this.values = values;
        }
    }
}
