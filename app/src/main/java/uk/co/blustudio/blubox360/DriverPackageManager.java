package uk.co.blustudio.blubox360;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import xendroid.compose.Application;

final class DriverPackageManager {
    static final class InstalledDriver {
        final String path;
        final String name;

        InstalledDriver(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private static final long MAX_ENTRY_BYTES = 192L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_FILES = 48;

    private DriverPackageManager() {}

    static InstalledDriver install(Context context, Uri source) throws Exception {
        File root = Application.get_custom_driver_dir().getCanonicalFile();
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("driver storage is unavailable");
        }

        String archiveName = displayName(context, source);
        String packageName = sanitize(stripExtension(archiveName));
        if (packageName.isEmpty()) packageName = "Vulkan-driver";
        File staging = new File(root, ".install-" + System.nanoTime()).getCanonicalFile();
        checkChild(root, staging);
        if (!staging.mkdirs()) throw new IllegalStateException("driver staging failed");

        try {
            List<String> libraries = new ArrayList<>();
            long total = 0;
            int files = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream raw = context.getContentResolver().openInputStream(source);
                 ZipInputStream zip = raw == null ? null : new ZipInputStream(raw)) {
                if (zip == null) throw new IllegalArgumentException("driver package could not be opened");
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zip.closeEntry();
                        continue;
                    }
                    String name = entry.getName();
                    if (name == null || name.contains("/") || name.contains("\\")
                            || name.equals(".") || name.equals("..")) {
                        throw new IllegalArgumentException("driver files must be at the top of the zip");
                    }
                    boolean library = name.endsWith(".so");
                    boolean metadata = "meta.json".equals(name);
                    if (!library && !metadata) {
                        zip.closeEntry();
                        continue;
                    }
                    if (++files > MAX_FILES) {
                        throw new IllegalArgumentException("driver package contains too many files");
                    }
                    File output = new File(staging, name).getCanonicalFile();
                    checkChild(staging, output);
                    long entryBytes = 0;
                    try (FileOutputStream out = new FileOutputStream(output)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            entryBytes += count;
                            total += count;
                            if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                                throw new IllegalArgumentException("driver package is too large");
                            }
                            out.write(buffer, 0, count);
                        }
                    }
                    if (library) {
                        output.setReadable(true, false);
                        output.setExecutable(true, false);
                        libraries.add(name);
                    }
                    zip.closeEntry();
                }
            }

            String mainLibrary = null;
            File metadata = new File(staging, "meta.json");
            if (metadata.isFile()) {
                JSONObject json = new JSONObject(readSmallFile(metadata));
                mainLibrary = json.optString("libraryName", "").trim();
                if (mainLibrary.contains("/") || mainLibrary.contains("\\")
                        || !mainLibrary.endsWith(".so")) {
                    throw new IllegalArgumentException("meta.json has an invalid libraryName");
                }
            }
            if (mainLibrary == null || mainLibrary.isEmpty()) {
                if (libraries.size() != 1) {
                    throw new IllegalArgumentException(
                            "meta.json is required when a package has several libraries");
                }
                mainLibrary = libraries.get(0);
            }
            if (!new File(staging, mainLibrary).isFile()) {
                throw new IllegalArgumentException("the main Vulkan library is missing");
            }

            File destination = uniqueDestination(root, packageName).getCanonicalFile();
            checkChild(root, destination);
            if (!staging.renameTo(destination)) {
                throw new IllegalStateException("driver package could not be installed");
            }
            return new InstalledDriver(new File(destination, mainLibrary).getAbsolutePath(),
                    destination.getName());
        } catch (Exception t) {
            deleteTree(staging);
            throw t;
        }
    }

    static List<InstalledDriver> installed() {
        List<InstalledDriver> result = new ArrayList<>();
        try {
            File root = Application.get_custom_driver_dir().getCanonicalFile();
            File[] folders = root.listFiles(File::isDirectory);
            if (folders == null) return result;
            for (File folder : folders) {
                if (folder.getName().startsWith(".install-")) continue;
                String mainLibrary = null;
                File metadata = new File(folder, "meta.json");
                if (metadata.isFile()) {
                    JSONObject json = new JSONObject(readSmallFile(metadata));
                    mainLibrary = json.optString("libraryName", "").trim();
                }
                if (mainLibrary == null || mainLibrary.isEmpty()) {
                    File[] libraries = folder.listFiles((dir, name) -> name.endsWith(".so"));
                    if (libraries != null && libraries.length == 1) {
                        mainLibrary = libraries[0].getName();
                    }
                }
                if (mainLibrary == null || mainLibrary.contains("/")
                        || mainLibrary.contains("\\")) continue;
                File library = new File(folder, mainLibrary).getCanonicalFile();
                checkChild(root, library);
                if (library.isFile()) {
                    result.add(new InstalledDriver(library.getAbsolutePath(), folder.getName()));
                }
            }
        } catch (Throwable ignored) {
        }
        result.sort(Comparator.comparing(driver -> driver.name.toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value.trim();
            }
        } catch (Throwable ignored) {
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "driver.zip" : segment;
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String sanitize(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
    }

    private static File uniqueDestination(File root, String base) {
        File candidate = new File(root, base);
        if (!candidate.exists()) return candidate;
        for (int i = 2; i < 1000; i++) {
            candidate = new File(root, base + "-" + i);
            if (!candidate.exists()) return candidate;
        }
        return new File(root, base + "-" + System.currentTimeMillis());
    }

    private static String readSmallFile(File file) throws Exception {
        if (file.length() > 1024L * 1024L) {
            throw new IllegalArgumentException("meta.json is too large");
        }
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        }
    }

    private static void checkChild(File root, File child) throws Exception {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(rootPath + File.separator)) {
            throw new SecurityException("driver package path is unsafe");
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
