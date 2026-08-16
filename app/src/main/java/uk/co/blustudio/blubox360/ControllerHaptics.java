package uk.co.blustudio.blubox360;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;

/** Routes short controller feedback to a gamepad motor, with the Thor motor as fallback. */
final class ControllerHaptics {
    private ControllerHaptics() { }

    static boolean test(Context context, int deviceId, int strength) {
        return vibrate(context, deviceId, strength, 180);
    }

    static boolean click(Context context, int deviceId, int strength) {
        return vibrate(context, deviceId, Math.max(1, strength / 3), 18);
    }

    @SuppressWarnings("deprecation")
    private static boolean vibrate(Context context, int deviceId, int strength,
                                   long durationMs) {
        int checkedStrength = Math.max(0, Math.min(200, strength));
        if (checkedStrength == 0) return false;
        Vibrator vibrator = null;
        InputDevice device = InputDevice.getDevice(deviceId);
        try {
            if (device != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibrator = device.getVibratorManager().getDefaultVibrator();
                } else {
                    vibrator = device.getVibrator();
                }
                if (vibrator != null && !vibrator.hasVibrator()) vibrator = null;
            }
        } catch (Throwable ignored) {
            vibrator = null;
        }
        if (vibrator == null) {
            vibrator = (Vibrator) context.getApplicationContext()
                    .getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return false;
        int amplitude = Math.max(1, Math.min(255,
                Math.round(255f * Math.min(1f, checkedStrength / 100f))));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
            } else {
                vibrator.vibrate(durationMs);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
