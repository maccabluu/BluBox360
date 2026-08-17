package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class UpdateActivity extends Activity {
    private static final String RELEASES_URL =
            "https://api.github.com/repos/maccabluu/BluBox360/releases?per_page=20";
    private static final long MIN_CHECK_INTERVAL_MS = 15L * 60L * 1000L;
    private static final String PREFS = "blubox360_updates";
    private static final String PREF_LAST_CHECK = "last_check_ms";

    private File downloadedApk;
    private boolean waitingForInstallPermission;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setFinishOnTouchOutside(false);

        if (state != null) {
            String path = state.getString("downloaded_apk");
            if (path != null && !path.isBlank()) downloadedApk = new File(path);
            waitingForInstallPermission = state.getBoolean("waiting_install_permission", false);
        }

        if (!waitingForInstallPermission) {
            long lastCheck = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getLong(PREF_LAST_CHECK, 0L);
            if (System.currentTimeMillis() - lastCheck < MIN_CHECK_INTERVAL_MS) {
                finish();
                return;
            }
            checkForUpdate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!waitingForInstallPermission) return;
        waitingForInstallPermission = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls()) {
            installDownloadedApk();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Allow BluBox updates")
                    .setMessage("Android needs permission for BluBox to install downloaded updates. "
                            + "Choose Allow, then return to BluBox.")
                    .setPositiveButton("Open settings", (dialog, which) ->
                            requestInstallPermission())
                    .setNegativeButton("Later", (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (downloadedApk != null) outState.putString("downloaded_apk", downloadedApk.getAbsolutePath());
        outState.putBoolean("waiting_install_permission", waitingForInstallPermission);
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                UpdateInfo info = fetchLatestUpdate();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                if (info == null) {
                    runOnUiThread(this::finish);
                    return;
                }
                runOnUiThread(() -> showUpdateDialog(info));
            } catch (Exception ignored) {
                runOnUiThread(this::finish);
            }
        }, "BluBox-update-check").start();
    }

    private UpdateInfo fetchLatestUpdate() throws Exception {
        String currentVersion = currentVersionName();
        JSONArray releases = new JSONArray(readText(RELEASES_URL));
        UpdateInfo best = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)) continue;

            String tag = release.optString("tag_name", "");
            if (compareVersions(tag, currentVersion) <= 0) continue;

            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;

            String apkName = null;
            String apkUrl = null;
            String checksumUrl = null;
            long apkSize = 0L;

            for (int a = 0; a < assets.length(); a++) {
                JSONObject asset = assets.optJSONObject(a);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".apk") && lower.contains("arm64")) {
                    apkName = name;
                    apkUrl = asset.optString("browser_download_url", "");
                    apkSize = asset.optLong("size", 0L);
                } else if ("sha256sums.txt".equals(lower)) {
                    checksumUrl = asset.optString("browser_download_url", "");
                }
            }

            if (apkName == null || apkUrl == null || apkUrl.isBlank()) continue;
            UpdateInfo candidate = new UpdateInfo(
                    tag,
                    release.optString("name", tag),
                    release.optString("body", "No release notes were provided."),
                    apkName,
                    apkUrl,
                    checksumUrl,
                    apkSize);
            if (best == null || compareVersions(candidate.tag, best.tag) > 0) best = candidate;
        }
        return best;
    }

    private void showUpdateDialog(UpdateInfo info) {
        if (isFinishing()) return;
        String size = info.apkSize > 0
                ? String.format(Locale.UK, "%.1f MB", info.apkSize / 1024d / 1024d)
                : "APK download";
        new AlertDialog.Builder(this)
                .setTitle("BluBox 360 update available")
                .setMessage(info.title + "\n\n" + size
                        + "\n\nA newer BluBox version is ready. Update now or choose Later.")
                .setPositiveButton("Update now", (dialog, which) -> downloadUpdate(info))
                .setNeutralButton("What's new", (dialog, which) -> showReleaseNotes(info))
                .setNegativeButton("Later", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showReleaseNotes(UpdateInfo info) {
        new AlertDialog.Builder(this)
                .setTitle(info.title)
                .setMessage(info.notes == null || info.notes.isBlank()
                        ? "No release notes were provided." : info.notes)
                .setPositiveButton("Update now", (dialog, which) -> downloadUpdate(info))
                .setNegativeButton("Back", (dialog, which) -> showUpdateDialog(info))
                .setOnCancelListener(dialog -> showUpdateDialog(info))
                .show();
    }

    @SuppressWarnings("deprecation")
    private void downloadUpdate(UpdateInfo info) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Downloading BluBox update");
        progress.setMessage(info.apkName);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(info.apkSize <= 0);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                File root = getExternalCacheDir();
                if (root == null) root = getCacheDir();
                File updates = new File(root, "updates");
                if (!updates.exists() && !updates.mkdirs()) {
                    throw new IllegalStateException("Could not create update folder");
                }
                File target = new File(updates, info.apkName);
                downloadFile(info.apkUrl, target, progress);

                if (info.checksumUrl != null && !info.checksumUrl.isBlank()) {
                    String expected = expectedSha256(info.checksumUrl, info.apkName);
                    if (expected != null && !expected.equalsIgnoreCase(sha256(target))) {
                        target.delete();
                        throw new IllegalStateException("The downloaded update failed verification.");
                    }
                }

                downloadedApk = target;
                runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    installDownloadedApk();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    showError("Update download failed", error.getMessage());
                });
            }
        }, "BluBox-update-download").start();
    }

    @SuppressWarnings("deprecation")
    private void downloadFile(String source, File target, ProgressDialog progress) throws Exception {
        HttpURLConnection connection = openConnection(source);
        long total = connection.getContentLengthLong();
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0L;
            int lastPercent = -1;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int percent = (int) Math.min(100L, (done * 100L) / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        int value = percent;
                        runOnUiThread(() -> {
                            progress.setIndeterminate(false);
                            progress.setProgress(value);
                        });
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private void installDownloadedApk() {
        if (downloadedApk == null || !downloadedApk.isFile()) {
            showError("Update unavailable", "The downloaded APK could not be found.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            requestInstallPermission();
            return;
        }

        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(getPackageName());
            params.setSize(downloadedApk.length());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            }

            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try (InputStream input = new FileInputStream(downloadedApk);
                 OutputStream output = session.openWrite("BluBox-update.apk", 0, downloadedApk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                session.fsync(output);
            }

            Intent statusIntent = new Intent(this, UpdateInstallReceiver.class);
            statusIntent.setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, sessionId, statusIntent, flags);
            session.commit(pendingIntent.getIntentSender());
            session.close();
            Toast.makeText(this, "Android will ask you to confirm the BluBox update.",
                    Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception error) {
            showError("Update installation failed", error.getMessage());
        }
    }

    private void requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            installDownloadedApk();
            return;
        }
        waitingForInstallPermission = true;
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void showError(String title, String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message == null || message.isBlank()
                        ? "Please try again later." : message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private String currentVersionName() throws Exception {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        return info.versionName == null ? "0.0.0" : info.versionName;
    }

    private static int compareVersions(String first, String second) {
        int[] a = versionNumbers(first);
        int[] b = versionNumbers(second);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        return 0;
    }

    private static int[] versionNumbers(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
        int dash = clean.indexOf('-');
        if (dash >= 0) clean = clean.substring(0, dash);
        String[] parts = clean.split("\\.");
        int[] result = new int[] {0, 0, 0};
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static String readText(String source) throws Exception {
        HttpURLConnection connection = openConnection(source);
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "BluBox360-Android-Updater");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("GitHub returned HTTP " + code);
        }
        return connection;
    }

    private static String expectedSha256(String checksumUrl, String apkName) throws Exception {
        String text = readText(checksumUrl);
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.endsWith(apkName)) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length > 0 && parts[0].matches("[0-9a-fA-F]{64}")) {
                    return parts[0].toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b));
        return result.toString();
    }

    private static final class UpdateInfo {
        final String tag;
        final String title;
        final String notes;
        final String apkName;
        final String apkUrl;
        final String checksumUrl;
        final long apkSize;

        UpdateInfo(String tag, String title, String notes, String apkName,
                   String apkUrl, String checksumUrl, long apkSize) {
            this.tag = tag;
            this.title = title;
            this.notes = notes;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
            this.apkSize = apkSize;
        }
    }
}
