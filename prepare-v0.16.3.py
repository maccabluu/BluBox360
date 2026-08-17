from pathlib import Path

ROOT = Path(__file__).resolve().parent / "app/src/main/java/uk/co/blustudio/blubox360"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"v0.16.3 source patch failed: {label}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Manual update checks keep the normal 15-minute automatic cooldown, while the
# Settings button can explicitly bypass it and report either up-to-date or errors.
update = ROOT / "UpdateActivity.java"
replace_once(
    update,
    '''public final class UpdateActivity extends Activity {\n    private static final String RELEASES_URL =''',
    '''public final class UpdateActivity extends Activity {\n    static final String EXTRA_FORCE_CHECK = "force_check";\n    private static final String RELEASES_URL =''',
    "manual update extra",
)
replace_once(
    update,
    '''    private File downloadedApk;\n    private boolean waitingForInstallPermission;''',
    '''    private File downloadedApk;\n    private boolean waitingForInstallPermission;\n    private boolean manualCheck;''',
    "manual update state",
)
replace_once(
    update,
    '''        setFinishOnTouchOutside(false);\n\n        if (state != null) {''',
    '''        setFinishOnTouchOutside(false);\n        manualCheck = getIntent().getBooleanExtra(EXTRA_FORCE_CHECK, false);\n\n        if (state != null) {''',
    "manual update intent",
)
replace_once(
    update,
    '''            if (System.currentTimeMillis() - lastCheck < MIN_CHECK_INTERVAL_MS) {''',
    '''            if (!manualCheck\n                    && System.currentTimeMillis() - lastCheck < MIN_CHECK_INTERVAL_MS) {''',
    "manual cooldown bypass",
)
replace_once(
    update,
    '''    private void checkForUpdate() {\n        new Thread(() -> {\n            try {\n                UpdateInfo info = fetchLatestUpdate();\n                getSharedPreferences(PREFS, MODE_PRIVATE).edit()\n                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();\n                if (info == null) {\n                    runOnUiThread(this::finish);\n                    return;\n                }\n                runOnUiThread(() -> showUpdateDialog(info));\n            } catch (Exception ignored) {\n                runOnUiThread(this::finish);\n            }\n        }, "BluBox-update-check").start();\n    }''',
    '''    private void checkForUpdate() {\n        new Thread(() -> {\n            try {\n                UpdateInfo info = fetchLatestUpdate();\n                getSharedPreferences(PREFS, MODE_PRIVATE).edit()\n                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();\n                if (info == null) {\n                    runOnUiThread(() -> {\n                        if (!manualCheck) {\n                            finish();\n                            return;\n                        }\n                        new AlertDialog.Builder(this)\n                                .setTitle("BluBox is up to date")\n                                .setMessage("You already have the newest BluBox 360 public alpha installed.")\n                                .setPositiveButton("OK", (dialog, which) -> finish())\n                                .setOnCancelListener(dialog -> finish())\n                                .show();\n                    });\n                    return;\n                }\n                runOnUiThread(() -> showUpdateDialog(info));\n            } catch (Exception error) {\n                runOnUiThread(() -> {\n                    if (manualCheck) {\n                        showError("Update check failed", error.getMessage());\n                    } else {\n                        finish();\n                    }\n                });\n            }\n        }, "BluBox-update-check").start();\n    }''',
    "manual update result",
)

# Settings > App gets a dedicated manual check button. It intentionally starts
# UpdateActivity with the force flag so the user's explicit action ignores the
# normal background cooldown without changing automatic check behaviour.
main = ROOT / "MainActivity.java"
replace_once(
    main,
    '''                LinearLayout boot = infoCard("BOOT ANIMATION", "Show the BluBox intro",\n                        "Play the clean animated BluBox logo when the app starts.");''',
    '''                LinearLayout updates = infoCard("UPDATES", "Automatic and manual checks",\n                        "Automatic checks keep the 15-minute cooldown. Manual checks run immediately.");\n                Button checkUpdates = button("Check for updates", true);\n                checkUpdates.setOnClickListener(v -> {\n                    Intent updateIntent = new Intent(this, UpdateActivity.class);\n                    updateIntent.putExtra(UpdateActivity.EXTRA_FORCE_CHECK, true);\n                    startActivity(updateIntent);\n                });\n                LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(\n                        dp(230), dp(50));\n                updateParams.topMargin = dp(10);\n                updates.addView(checkUpdates, updateParams);\n                addSettingsCard(content, updates);\n\n                LinearLayout boot = infoCard("BOOT ANIMATION", "Show the BluBox intro",\n                        "Play the clean animated BluBox logo when the app starts.");''',
    "settings update button",
)
replace_once(
    main,
    '''                "Reduces sustained CPU and GPU load with native rendering, two background shader workers, and no maximum-clock request. The selected FPS target still applies. Fable II uses a 24 FPS ceiling.");''',
    '''                "Uses native rendering, reduced shader work and no maximum-clock request. Smart Heat Guard also checks Android thermal headroom at game launch and can step a 60 FPS target down to 30 FPS when the device is already nearing thermal throttling. Fable II keeps a 24 FPS ceiling.");''',
    "thermal settings explanation",
)

# Smart Heat Guard uses Android's thermal status/headroom APIs. Low Heat Mode still
# keeps the user's selected target while the device is cool. If Android reports
# light-or-higher throttling, or forecasts headroom close to severe throttling,
# BluBox starts the game with a 30 FPS ceiling and one shader worker. The existing
# in-game severe-heat shutdown remains the final safety net.
core = ROOT / "CoreConfig.java"
replace_once(
    core,
    '''import android.os.Handler;\nimport android.os.Looper;''',
    '''import android.os.Build;\nimport android.os.Handler;\nimport android.os.Looper;\nimport android.os.PowerManager;''',
    "thermal imports",
)
replace_once(
    core,
    '''    /** Applies normal settings, then the optional low-heat preset and title fixes. */\n    static synchronized boolean applyLaunchSettings(Context context, String titleId,''',
    '''    private static boolean thermalPressure(Context context) {\n        try {\n            PowerManager powerManager =\n                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);\n            if (powerManager == null) return false;\n            if (powerManager.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_LIGHT) {\n                return true;\n            }\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n                float headroom = powerManager.getThermalHeadroom(20);\n                if (!Float.isNaN(headroom) && headroom >= 0.80f) return true;\n            }\n        } catch (Throwable ignored) {\n        }\n        return false;\n    }\n\n    /** Applies normal settings, then the optional low-heat preset and title fixes. */\n    static synchronized boolean applyLaunchSettings(Context context, String titleId,''',
    "thermal helper",
)
replace_once(
    core,
    '''        boolean fableII = isFableII(titleId, gameName);\n        if (!coolMode(context) && !fableII) return false;''',
    '''        boolean fableII = isFableII(titleId, gameName);\n        boolean thermalPressure = thermalPressure(context);\n        if (!coolMode(context) && !fableII && !thermalPressure) return false;''',
    "thermal launch decision",
)
replace_once(
    core,
    '''            config.save_config_entry("GPU|framerate_limit",\n                    fableII ? "24" : Integer.toString(frameLimit(context)));''',
    '''            int launchFrameLimit = fableII ? 24\n                    : thermalPressure ? Math.min(30, frameLimit(context))\n                    : frameLimit(context);\n            config.save_config_entry("GPU|framerate_limit",\n                    Integer.toString(launchFrameLimit));''',
    "thermal frame cap",
)
replace_once(
    core,
    '''            config.save_config_entry("Vulkan|vulkan_pipeline_creation_threads", "2");''',
    '''            config.save_config_entry("Vulkan|vulkan_pipeline_creation_threads",\n                    thermalPressure ? "1" : "2");''',
    "thermal shader worker limit",
)

print("BluBox 360 v0.16.3 source preparation complete")
