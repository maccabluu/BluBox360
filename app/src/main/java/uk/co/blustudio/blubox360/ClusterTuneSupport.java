package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

final class ClusterTuneSupport {
    static final String PACKAGE_NAME = "com.aure.clustertune";
    private static final String RELEASES_URL =
            "https://github.com/AurelioB/ClusterTune/releases";

    private ClusterTuneSupport() { }

    static boolean isInstalled(Context context) {
        return packageInfo(context) != null;
    }

    static String versionName(Context context) {
        PackageInfo info = packageInfo(context);
        return info == null || info.versionName == null ? "" : info.versionName;
    }

    static boolean open(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
            if (intent == null) return false;
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean openDownloadPage(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo packageInfo(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return manager.getPackageInfo(PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(0));
            }
            return manager.getPackageInfo(PACKAGE_NAME, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
