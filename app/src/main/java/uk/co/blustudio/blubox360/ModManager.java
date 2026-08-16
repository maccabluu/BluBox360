package uk.co.blustudio.blubox360;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ModManager {
    private static final long MAX_PATCH_BYTES = 2L * 1024L * 1024L;
    private static final String DISABLED_SUFFIX = ".disabled";
    private static final String BUILT_IN_FABLE =
            "4D5307F1 - Fable II (BluBox Performance).patch.toml";
    private static final Pattern TITLE_NAME = Pattern.compile(
            "(?m)^\\s*title_name\\s*=\\s*\"([^\"]{1,160})\"");
    private static final Pattern TITLE_ID = Pattern.compile(
            "(?mi)^\\s*title_id\\s*=\\s*\"([0-9a-f]{8})\"");

    private ModManager() { }

    static synchronized List<PatchMod> list() {
        List<PatchMod> result = new ArrayList<>();
        File folder = folder();
        File[] files = folder.listFiles();
        if (files == null) return result;
        for (File file : files) {
            String name = file.getName();
            boolean enabled = name.toLowerCase(Locale.ROOT).endsWith(".patch.toml");
            boolean disabled = name.toLowerCase(Locale.ROOT)
                    .endsWith(".patch.toml" + DISABLED_SUFFIX);
            if (!file.isFile() || (!enabled && !disabled)) continue;
            try {
                String text = new String(readFile(file), StandardCharsets.UTF_8);
                result.add(new PatchMod(file, displayTitle(text, name),
                        value(TITLE_ID, text), enabled,
                        BUILT_IN_FABLE.equals(enabledName(name))));
            } catch (Throwable ignored) {
                result.add(new PatchMod(file, cleanDisplayName(name), "",
                        enabled, BUILT_IN_FABLE.equals(enabledName(name))));
            }
        }
        result.sort(Comparator.comparing((PatchMod mod) -> !mod.builtIn)
                .thenComparing(mod -> mod.title.toLowerCase(Locale.ROOT)));
        return result;
    }

    static synchronized PatchMod importPatch(Context context, Uri source) throws Exception {
        if (source == null) throw new IllegalArgumentException("No patch file was selected");
        byte[] data;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IllegalStateException("Android could not open the patch file");
            data = readLimited(in);
        }
        String text = new String(data, StandardCharsets.UTF_8);
        validatePatch(text);

        String sourceName = StorageResolver.displayName(context, source);
        String titleId = value(TITLE_ID, text).toUpperCase(Locale.ROOT);
        String safeName = safePatchName(sourceName, titleId);
        File directory = folder();
        File destination = uniqueFile(directory, safeName);
        File temporary = new File(directory, ".import-" + System.nanoTime() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary, false)) {
            out.write(data);
            out.getFD().sync();
        } catch (Throwable t) {
            temporary.delete();
            throw t;
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("The patch mod could not be installed");
        }
        return new PatchMod(destination, displayTitle(text, destination.getName()),
                titleId, true, false);
    }

    static synchronized void setEnabled(PatchMod mod, boolean enabled) throws Exception {
        if (mod == null || mod.builtIn) return;
        File source = checkedFile(mod.file);
        String name = source.getName();
        File destination = enabled
                ? new File(folder(), enabledName(name))
                : new File(folder(), enabledName(name) + DISABLED_SUFFIX);
        if (source.equals(destination)) return;
        if (destination.exists()) throw new IllegalStateException("A patch with this name already exists");
        if (!source.renameTo(destination)) {
            throw new IllegalStateException("The patch state could not be changed");
        }
    }

    static synchronized void delete(PatchMod mod) throws Exception {
        if (mod == null || mod.builtIn) return;
        File file = checkedFile(mod.file);
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("The patch mod could not be removed");
        }
    }

    static File folder() {
        File directory = new File(CoreConfig.storageRoot(), "patches");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("The mods folder could not be created");
        }
        return directory;
    }

    private static File checkedFile(File file) throws Exception {
        File directory = folder().getCanonicalFile();
        File checked = file.getCanonicalFile();
        if (!checked.getParentFile().equals(directory)) {
            throw new IllegalArgumentException("The patch path is unsafe");
        }
        return checked;
    }

    private static void validatePatch(String text) {
        if (text.indexOf('\0') >= 0 || value(TITLE_NAME, text).isEmpty()
                || value(TITLE_ID, text).isEmpty() || !text.contains("[[patch]]")) {
            throw new IllegalArgumentException("Select a valid Xenia .patch.toml file");
        }
    }

    private static String displayTitle(String text, String fallback) {
        String title = value(TITLE_NAME, text);
        return title.isEmpty() ? cleanDisplayName(fallback) : title;
    }

    private static String value(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String safePatchName(String value, String titleId) {
        String name = value == null ? "Imported mod" : value.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(DISABLED_SUFFIX)) {
            name = name.substring(0, name.length() - DISABLED_SUFFIX.length());
            lower = name.toLowerCase(Locale.ROOT);
        }
        if (lower.endsWith(".patch.toml")) {
            name = name.substring(0, name.length() - ".patch.toml".length());
        } else if (lower.endsWith(".toml")) {
            name = name.substring(0, name.length() - ".toml".length());
        }
        name = name.replaceAll("[^A-Za-z0-9 _().'-]", "_").trim();
        if (name.isEmpty()) name = "Imported mod";
        if (!name.toUpperCase(Locale.ROOT).startsWith(titleId)) {
            name = titleId + " - " + name;
        }
        if (name.length() > 90) name = name.substring(0, 90).trim();
        return name + ".patch.toml";
    }

    private static File uniqueFile(File directory, String name) {
        File candidate = new File(directory, name);
        if (!candidate.exists()) return candidate;
        String stem = name.substring(0, name.length() - ".patch.toml".length());
        for (int index = 2; index <= 99; index++) {
            candidate = new File(directory, stem + " (" + index + ").patch.toml");
            if (!candidate.exists()) return candidate;
        }
        throw new IllegalStateException("Too many patch files use this name");
    }

    private static String enabledName(String name) {
        return name.endsWith(DISABLED_SUFFIX)
                ? name.substring(0, name.length() - DISABLED_SUFFIX.length()) : name;
    }

    private static String cleanDisplayName(String name) {
        String value = enabledName(name);
        if (value.toLowerCase(Locale.ROOT).endsWith(".patch.toml")) {
            value = value.substring(0, value.length() - ".patch.toml".length());
        }
        return value;
    }

    private static byte[] readFile(File file) throws Exception {
        if (file.length() > MAX_PATCH_BYTES) throw new IllegalArgumentException("Patch file is too large");
        try (InputStream in = new FileInputStream(file)) {
            return readLimited(in);
        }
    }

    private static byte[] readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int count;
        long total = 0;
        while ((count = in.read(buffer)) != -1) {
            if (count == 0) continue;
            total += count;
            if (total > MAX_PATCH_BYTES) throw new IllegalArgumentException("Patch file is too large");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    static final class PatchMod {
        final File file;
        final String title;
        final String titleId;
        final boolean enabled;
        final boolean builtIn;

        PatchMod(File file, String title, String titleId, boolean enabled, boolean builtIn) {
            this.file = file;
            this.title = title;
            this.titleId = titleId;
            this.enabled = enabled;
            this.builtIn = builtIn;
        }
    }
}
