package uk.co.blustudio.blubox360;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class StorageResolver {
    private StorageResolver() {}

    static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (Throwable ignored) {
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "Xbox 360 game" : segment;
    }

    static long size(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    static String absolutePath(Context context, Uri uri) {
        if (uri == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return valid(uri.getPath());
        }
        List<String> candidates = new ArrayList<>();
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            try {
                String id = DocumentsContract.getDocumentId(uri);
                if (id.startsWith("raw:")) candidates.add(id.substring(4));
                String[] parts = id.split(":", 2);
                if (parts.length == 2) {
                    if ("primary".equalsIgnoreCase(parts[0])) {
                        candidates.add(new File(Environment.getExternalStorageDirectory(),
                                parts[1]).getAbsolutePath());
                    } else {
                        candidates.add("/storage/" + parts[0] + "/" + parts[1]);
                        candidates.add("/mnt/media_rw/" + parts[0] + "/" + parts[1]);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String path = uri.getPath();
        if (path != null) {
            candidates.add(path);
            int storage = path.indexOf("/storage/");
            if (storage >= 0) candidates.add(path.substring(storage));
            int raw = path.indexOf("raw:");
            if (raw >= 0) candidates.add(path.substring(raw + 4));
        }
        for (String candidate : candidates) {
            String result = valid(candidate);
            if (result != null) return result;
        }
        return null;
    }

    static String absoluteDirectoryPath(Context context, Uri uri) {
        if (uri == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return validDirectory(uri.getPath());
        }
        List<String> candidates = new ArrayList<>();
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            try {
                addDocumentIdCandidates(candidates, DocumentsContract.getTreeDocumentId(uri));
            } catch (Throwable ignored) {
                try {
                    addDocumentIdCandidates(candidates, DocumentsContract.getDocumentId(uri));
                } catch (Throwable ignoredAgain) { }
            }
        }
        String path = uri.getPath();
        if (path != null) {
            int storage = path.indexOf("/storage/");
            if (storage >= 0) candidates.add(path.substring(storage));
            int raw = path.indexOf("raw:");
            if (raw >= 0) candidates.add(path.substring(raw + 4));
        }
        for (String candidate : candidates) {
            String result = validDirectory(candidate);
            if (result != null) return result;
        }
        return null;
    }

    private static void addDocumentIdCandidates(List<String> candidates, String id) {
        if (id == null || id.trim().isEmpty()) return;
        if (id.startsWith("raw:")) candidates.add(id.substring(4));
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return;
        if ("primary".equalsIgnoreCase(parts[0])) {
            candidates.add(new File(Environment.getExternalStorageDirectory(),
                    parts[1]).getAbsolutePath());
        } else {
            candidates.add("/storage/" + parts[0] + "/" + parts[1]);
            candidates.add("/mnt/media_rw/" + parts[0] + "/" + parts[1]);
        }
    }

    static List<File> scanGameFiles(String rootPath, int maximumGames) {
        List<File> result = new ArrayList<>();
        if (rootPath == null || maximumGames <= 0) return result;
        try {
            File root = new File(rootPath).getCanonicalFile();
            if (!root.isDirectory() || !root.canRead()) return result;
            String rootPrefix = root.getPath() + File.separator;
            ArrayDeque<ScanFolder> pending = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            pending.add(new ScanFolder(root, 0));
            int directories = 0;
            while (!pending.isEmpty() && result.size() < maximumGames
                    && directories < 4096) {
                ScanFolder next = pending.removeFirst();
                File directory;
                try {
                    directory = next.file.getCanonicalFile();
                } catch (Throwable ignored) {
                    continue;
                }
                String directoryPath = directory.getPath();
                if (!directory.equals(root) && !directoryPath.startsWith(rootPrefix)) continue;
                if (!visited.add(directoryPath)) continue;
                directories++;
                File[] children = directory.listFiles();
                if (children == null) continue;
                for (File child : children) {
                    if (result.size() >= maximumGames) break;
                    String name = child.getName();
                    if (name.startsWith(".")) continue;
                    if (child.isDirectory()) {
                        if (next.depth < 10 && !skipDirectory(name)) {
                            pending.addLast(new ScanFolder(child, next.depth + 1));
                        }
                    } else if (child.isFile() && child.canRead() && scanSupported(name)) {
                        try {
                            File game = child.getCanonicalFile();
                            if (game.getPath().startsWith(rootPrefix)) result.add(game);
                        } catch (Throwable ignored) { }
                    }
                }
            }
        } catch (Throwable ignored) { }
        result.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        return result;
    }

    private static boolean scanSupported(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".iso") || lower.endsWith(".zar")
                || "default.xex".equals(lower);
    }

    private static boolean skipDirectory(String name) {
        return "Android".equalsIgnoreCase(name)
                || "lost+found".equalsIgnoreCase(name)
                || "$RECYCLE.BIN".equalsIgnoreCase(name)
                || "System Volume Information".equalsIgnoreCase(name);
    }

    private static String validDirectory(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File directory = new File(path);
        return directory.isDirectory() && directory.canRead()
                ? directory.getAbsolutePath() : null;
    }

    private static final class ScanFolder {
        final File file;
        final int depth;

        ScanFolder(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }

    private static String valid(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path);
        return file.isFile() && file.canRead() ? file.getAbsolutePath() : null;
    }

    static int gameFormat(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".iso")) return 0;
        if (lower.endsWith(".xex")) return 1;
        if (lower.endsWith(".zar")) return 2;
        return -1;
    }

    static boolean supported(String path) {
        return gameFormat(path) >= 0;
    }
}
