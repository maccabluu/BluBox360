package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPreferences {
    static final String PREFS = "blubox360_app_settings";
    private static final String KEY_BOOT_ANIMATION = "boot_animation";
    private static final String KEY_STARTUP_SOUND = "startup_sound";
    private static final String KEY_SECOND_SCREEN_ACHIEVEMENTS = "second_screen_achievements";
    private static final String KEY_SHOW_LOCKED_ACHIEVEMENTS = "show_locked_achievements";
    private static final String KEY_SMOOTH_CONTROLS = "smooth_controls";
    private static final String KEY_HARRY_POTTER_AIM = "harry_potter_precision_aim";

    private AppPreferences() {}

    static boolean bootAnimation(Context context) {
        return preferences(context).getBoolean(KEY_BOOT_ANIMATION, true);
    }

    static void setBootAnimation(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_BOOT_ANIMATION, enabled).apply();
    }

    static boolean startupSound(Context context) {
        return preferences(context).getBoolean(KEY_STARTUP_SOUND, true);
    }

    static void setStartupSound(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_STARTUP_SOUND, enabled).apply();
    }

    static boolean secondScreenAchievements(Context context) {
        return preferences(context).getBoolean(KEY_SECOND_SCREEN_ACHIEVEMENTS, true);
    }

    static void setSecondScreenAchievements(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_SECOND_SCREEN_ACHIEVEMENTS, enabled).apply();
    }

    static boolean showLockedAchievements(Context context) {
        return preferences(context).getBoolean(KEY_SHOW_LOCKED_ACHIEVEMENTS, true);
    }

    static void setShowLockedAchievements(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_SHOW_LOCKED_ACHIEVEMENTS, enabled).apply();
    }

    static boolean smoothControls(Context context) {
        return preferences(context).getBoolean(KEY_SMOOTH_CONTROLS, true);
    }

    static void setSmoothControls(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_SMOOTH_CONTROLS, enabled).apply();
    }

    static boolean harryPotterPrecisionAim(Context context) {
        return preferences(context).getBoolean(KEY_HARRY_POTTER_AIM, true);
    }

    static void setHarryPotterPrecisionAim(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_HARRY_POTTER_AIM, enabled).apply();
    }

    static void reset(Context context) {
        preferences(context).edit().clear().apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
