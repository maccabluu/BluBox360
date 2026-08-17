package uk.co.blustudio.blups2;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * First real-core integration path for BluPS2.
 *
 * BluPS2 uses the upstream Play! Android app as the initial execution backend.
 * This keeps BluPS2's frontend separate from upstream emulator code while we
 * validate game launching on ARM64 hardware. A native embedded-core bridge can
 * replace this adapter later after upstream source is built and tested in CI.
 */
public final class PlayCoreBridge {
    private static final String[] PLAY_PACKAGES = {
            "com.virtualapplications.play",
            "com.virtualapplications.play.debug"
    };

    private PlayCoreBridge() {}

    public static boolean isInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : PLAY_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return false;
    }

    public static boolean launchGame(Context context, Uri gameUri) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : PLAY_PACKAGES) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setPackage(packageName);
            intent.setDataAndType(gameUri, "application/octet-stream");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent);
                return true;
            }
        }
        return false;
    }

    public static Intent upstreamInstallPage() {
        return new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.virtualapplications.play"));
    }
}
