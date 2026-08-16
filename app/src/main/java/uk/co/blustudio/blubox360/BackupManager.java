package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class BackupManager {
    private static final int FORMAT_VERSION = 1;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ENTRY_COUNT = 50_000;
    private static final long MAX_RESTORE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_MANIFEST_BYTES = 64L * 1024L * 1024L;
    private static final String MANIFEST = "backup_manifest.json";
    private static final String[] PREFERENCE_FILES = {
            "blubox360_library",
            "blubox_profiles_v1",
            "blubox360_core",
            "blubox360_ui",
            AppPreferences.PREFS
    };

    private BackupManager() {}

    static Result writeBackup(Context context, Uri destination) throws Exception {
        Context app = context.getApplicationContext();
        Counter counter = new Counter();
        try (OutputStream raw = app.getContentResolver().openOutputStream(destination, "w")) {
            if (raw == null) throw new IllegalStateException("Android could not open the backup file");
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
                byte[] manifest = buildManifest(app).toString(2).getBytes(StandardCharsets.UTF_8);
                writeBytes(zip, MANIFEST, manifest);
                counter.files++;
                counter.bytes += manifest.length;

                File content = CoreConfig.contentRoot();
                if (content.isDirectory()) {
                    addTree(zip, content, content, "storage/content/", counter);
                }
                File global = CoreConfig.globalConfig();
                if (global.isFile()) {
                    addFile(zip, global, "storage/global_config.toml", counter);
                }
                File profiles = new File(app.getFilesDir(), "profiles");
                if (profiles.isDirectory()) {
                    addTree(zip, profiles, profiles, "internal/profiles/", counter);
                }
                File covers = CoverArtStore.folder(app);
                if (covers.isDirectory()) {
                    addTree(zip, covers, covers, "internal/covers/", counter);
                }
                File mods = ModManager.folder();
                if (mods.isDirectory()) {
                    addTree(zip, mods, mods, "storage/patches/", counter);
                }
                zip.finish();
            }
        }
        return new Result(counter.files, counter.bytes);
    }

    static Result restoreBackup(Context context, Uri source) throws Exception {
        Context app = context.getApplicationContext();
        File stage = new File(app.getCacheDir(), "blubox-restore-" + UUID.randomUUID());
        if (!stage.mkdirs()) throw new IllegalStateException("Could not create restore workspace");
        Counter counter = new Counter();
        try {
            extractValidated(app, source, stage, counter);
            File manifestFile = new File(stage, MANIFEST);
            if (!manifestFile.isFile()) throw new IllegalArgumentException("This is not a BluBox backup");
            JSONObject manifest = new JSONObject(readText(manifestFile, MAX_MANIFEST_BYTES));
            if (manifest.optInt("format", -1) != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported BluBox backup version");
            }
            JSONObject preferences = manifest.optJSONObject("preferences");
            validatePreferences(preferences);

            File stagedContent = new File(stage, "storage/content");
            if (stagedContent.isDirectory()) {
                copyTree(stagedContent, stagedContent, CoreConfig.contentRoot());
            }
            File stagedGlobal = new File(stage, "storage/global_config.toml");
            if (stagedGlobal.isFile()) copyFile(stagedGlobal, CoreConfig.globalConfig());
            File stagedProfiles = new File(stage, "internal/profiles");
            if (stagedProfiles.isDirectory()) {
                File destination = new File(app.getFilesDir(), "profiles");
                copyTree(stagedProfiles, stagedProfiles, destination);
            }
            File stagedCovers = new File(stage, "internal/covers");
            if (stagedCovers.isDirectory()) {
                copyTree(stagedCovers, stagedCovers, CoverArtStore.folder(app));
            }
            File stagedMods = new File(stage, "storage/patches");
            if (stagedMods.isDirectory()) {
                copyTree(stagedMods, stagedMods, ModManager.folder());
            }
            restorePreferences(app, preferences);
            return new Result(counter.files, counter.bytes);
        } finally {
            deleteStage(stage, app.getCacheDir());
        }
    }

    private static JSONObject buildManifest(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_VERSION);
        root.put("app", "BluBox 360");
        root.put("app_version", appVersion(context));
        root.put("created_utc", utcNow());
        root.put("games_included", false);
        root.put("firmware_included", false);
        JSONObject preferences = new JSONObject();
        for (String name : PREFERENCE_FILES) {
            preferences.put(name, encodePreferences(
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll()));
        }
        root.put("preferences", preferences);
        return root;
    }

    private static JSONObject encodePreferences(Map<String, ?> values) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> item : values.entrySet()) {
            Object value = item.getValue();
            if (value == null) continue;
            JSONObject encoded = new JSONObject();
            if (value instanceof String) {
                encoded.put("type", "string");
                encoded.put("value", value);
            } else if (value instanceof Boolean) {
                encoded.put("type", "boolean");
                encoded.put("value", value);
            } else if (value instanceof Integer) {
                encoded.put("type", "int");
                encoded.put("value", value);
            } else if (value instanceof Long) {
                encoded.put("type", "long");
                encoded.put("value", value);
            } else if (value instanceof Float) {
                encoded.put("type", "float");
                encoded.put("value", ((Float) value).doubleValue());
            } else if (value instanceof Set) {
                encoded.put("type", "string_set");
                JSONArray array = new JSONArray();
                for (Object member : (Set<?>) value) {
                    if (member instanceof String) array.put(member);
                }
                encoded.put("value", array);
            } else {
                continue;
            }
            result.put(item.getKey(), encoded);
        }
        return result;
    }

    private static void restorePreferences(Context context, JSONObject all) throws Exception {
        for (String name : PREFERENCE_FILES) {
            JSONObject encoded = all.optJSONObject(name);
            if (encoded == null) continue;
            SharedPreferences.Editor editor = context
                    .getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            Iterator<String> keys = encoded.keys();
            int keyCount = 0;
            while (keys.hasNext()) {
                if (++keyCount > 20_000) throw new IllegalArgumentException("Backup has too many settings");
                String key = keys.next();
                if (key.length() > 512) throw new IllegalArgumentException("Backup setting name is invalid");
                JSONObject item = encoded.optJSONObject(key);
                if (item == null) continue;
                String type = item.optString("type", "");
                switch (type) {
                    case "string":
                        editor.putString(key, item.optString("value", ""));
                        break;
                    case "boolean":
                        editor.putBoolean(key, item.optBoolean("value", false));
                        break;
                    case "int":
                        editor.putInt(key, item.optInt("value", 0));
                        break;
                    case "long":
                        editor.putLong(key, item.optLong("value", 0L));
                        break;
                    case "float":
                        editor.putFloat(key, (float) item.optDouble("value", 0.0));
                        break;
                    case "string_set":
                        JSONArray array = item.optJSONArray("value");
                        Set<String> members = new LinkedHashSet<>();
                        if (array != null) {
                            for (int i = 0; i < array.length() && i < 20_000; i++) {
                                members.add(array.optString(i, ""));
                            }
                        }
                        editor.putStringSet(key, members);
                        break;
                    default:
                        throw new IllegalArgumentException("Backup contains an unknown setting type");
                }
            }
            if (!editor.commit()) throw new IllegalStateException("Could not restore app settings");
        }
    }

    private static void validatePreferences(JSONObject all) throws Exception {
        if (all == null) throw new IllegalArgumentException("Backup settings are missing");
        for (String name : PREFERENCE_FILES) {
            JSONObject encoded = all.optJSONObject(name);
            if (encoded == null) continue;
            Iterator<String> keys = encoded.keys();
            int keyCount = 0;
            while (keys.hasNext()) {
                if (++keyCount > 20_000) throw new IllegalArgumentException("Backup has too many settings");
                String key = keys.next();
                if (key.length() > 512) throw new IllegalArgumentException("Backup setting name is invalid");
                JSONObject item = encoded.optJSONObject(key);
                if (item == null) throw new IllegalArgumentException("Backup setting is invalid");
                String type = item.optString("type", "");
                if (!"string".equals(type) && !"boolean".equals(type)
                        && !"int".equals(type) && !"long".equals(type)
                        && !"float".equals(type) && !"string_set".equals(type)) {
                    throw new IllegalArgumentException("Backup contains an unknown setting type");
                }
                if ("string_set".equals(type)) {
                    JSONArray array = item.optJSONArray("value");
                    if (array == null || array.length() > 20_000) {
                        throw new IllegalArgumentException("Backup setting set is invalid");
                    }
                }
            }
        }
    }

    private static void extractValidated(Context context, Uri source, File stage,
                                         Counter counter) throws Exception {
        Set<String> names = new HashSet<>();
        try (InputStream raw = context.getContentResolver().openInputStream(source)) {
            if (raw == null) throw new IllegalStateException("Android could not open the backup file");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    validateEntryName(name);
                    if (!names.add(name)) throw new IllegalArgumentException("Backup contains duplicate files");
                    if (names.size() > MAX_ENTRY_COUNT) {
                        throw new IllegalArgumentException("Backup contains too many files");
                    }
                    if (entry.isDirectory()) {
                        zip.closeEntry();
                        continue;
                    }
                    if (!allowedEntry(name)) {
                        throw new IllegalArgumentException("Backup contains unsupported data");
                    }
                    if (entry.getSize() > MAX_RESTORE_BYTES) {
                        throw new IllegalArgumentException("Backup entry is too large");
                    }
                    File destination = checkedChild(stage, name);
                    File parent = destination.getParentFile();
                    if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                        throw new IllegalStateException("Could not prepare restored files");
                    }
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            if (count == 0) continue;
                            counter.bytes += count;
                            if (counter.bytes > MAX_RESTORE_BYTES) {
                                throw new IllegalArgumentException("Backup expands beyond the safe limit");
                            }
                            out.write(buffer, 0, count);
                        }
                    }
                    counter.files++;
                    zip.closeEntry();
                }
            }
        }
    }

    private static boolean allowedEntry(String name) {
        return MANIFEST.equals(name)
                || "storage/global_config.toml".equals(name)
                || name.startsWith("storage/content/")
                || name.startsWith("storage/patches/")
                || name.startsWith("internal/profiles/")
                || name.startsWith("internal/covers/");
    }

    private static void validateEntryName(String name) {
        if (name == null || name.isEmpty() || name.length() > 1024
                || name.startsWith("/") || name.startsWith("\\")
                || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Backup contains an unsafe file name");
        }
        String[] components = name.split("/", -1);
        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            boolean trailingDirectoryMarker = component.isEmpty()
                    && i == components.length - 1 && name.endsWith("/");
            if ((!trailingDirectoryMarker && component.isEmpty())
                    || ".".equals(component) || "..".equals(component)) {
                throw new IllegalArgumentException("Backup contains an unsafe file path");
            }
        }
    }

    private static void addTree(ZipOutputStream zip, File root, File current,
                                String prefix, Counter counter) throws Exception {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            ensureInside(root, child);
            if (child.isDirectory()) {
                addTree(zip, root, child, prefix, counter);
            } else if (child.isFile()) {
                String relative = relativePath(root, child);
                addFile(zip, child, prefix + relative, counter);
            }
        }
    }

    private static void addFile(ZipOutputStream zip, File source, String name,
                                Counter counter) throws Exception {
        validateEntryName(name);
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count == 0) continue;
                zip.write(buffer, 0, count);
                counter.bytes += count;
            }
        }
        zip.closeEntry();
        counter.files++;
    }

    private static void writeBytes(ZipOutputStream zip, String name, byte[] value)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value);
        zip.closeEntry();
    }

    private static void copyTree(File root, File current, File destinationRoot)
            throws Exception {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            ensureInside(root, child);
            String relative = relativePath(root, child);
            File destination = checkedChild(destinationRoot, relative);
            if (child.isDirectory()) {
                if (!destination.isDirectory() && !destination.mkdirs()) {
                    throw new IllegalStateException("Could not restore a folder");
                }
                copyTree(root, child, destinationRoot);
            } else if (child.isFile()) {
                copyFile(child, destination);
            }
        }
    }

    private static String relativePath(File root, File child) throws Exception {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(rootPath + File.separator)) {
            throw new IllegalArgumentException("Backup path leaves its allowed folder");
        }
        return childPath.substring(rootPath.length() + 1)
                .replace(File.separatorChar, '/');
    }

    private static void copyFile(File source, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IllegalStateException("Could not create a restore folder");
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count > 0) out.write(buffer, 0, count);
            }
        }
    }

    private static File checkedChild(File root, String relative) throws Exception {
        File destination = new File(root, relative);
        ensureInside(root, destination);
        return destination;
    }

    private static void ensureInside(File root, File candidate) throws Exception {
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        if (!candidatePath.equals(rootPath)
                && !candidatePath.startsWith(rootPath + File.separator)) {
            throw new IllegalArgumentException("Backup path leaves its allowed folder");
        }
    }

    private static String readText(File file, long maxBytes) throws Exception {
        if (file.length() > maxBytes) throw new IllegalArgumentException("Backup manifest is too large");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            long total = 0;
            while ((count = in.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > maxBytes) throw new IllegalArgumentException("Backup manifest is too large");
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void deleteStage(File stage, File cacheRoot) {
        try {
            ensureInside(cacheRoot, stage);
            deleteTree(stage);
        } catch (Throwable ignored) {
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static String appVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    static final class Result {
        final int files;
        final long bytes;

        Result(int files, long bytes) {
            this.files = files;
            this.bytes = bytes;
        }

        String summary(String action) {
            return action + " " + files + " file" + (files == 1 ? "" : "s")
                    + " (" + humanBytes(bytes) + ").";
        }
    }

    private static final class Counter {
        int files;
        long bytes;
    }

    private static String humanBytes(long value) {
        if (value < 1024) return value + " B";
        double kib = value / 1024.0;
        if (kib < 1024) return String.format(Locale.US, "%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024) return String.format(Locale.US, "%.1f MiB", mib);
        return String.format(Locale.US, "%.2f GiB", mib / 1024.0);
    }
}
