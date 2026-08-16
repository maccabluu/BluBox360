package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/** Reads low-cost device information for the AYN Thor lower-screen dashboard. */
final class DeviceTelemetry {
    static final class BatteryState {
        final int percent;
        final float temperatureC;
        final boolean charging;

        BatteryState(int percent, float temperatureC, boolean charging) {
            this.percent = percent;
            this.temperatureC = temperatureC;
            this.charging = charging;
        }
    }

    private DeviceTelemetry() { }

    static BatteryState readBattery(Context context) {
        int percent = -1;
        float temperatureC = Float.NaN;
        boolean charging = false;
        try {
            Intent battery = context.getApplicationContext().registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    percent = Math.max(0, Math.min(100,
                            Math.round(level * 100f / scale)));
                }
                int rawTemperature = battery.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (rawTemperature != Integer.MIN_VALUE) {
                    float value = rawTemperature / 10f;
                    if (plausible(value)) temperatureC = value;
                }
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Throwable ignored) { }
        return new BatteryState(percent, temperatureC, charging);
    }

    /**
     * Reads a CPU, SoC, GPU, or skin thermal zone when Android exposes one.
     * Falls back to the battery sensor, which is available on standard Android builds.
     */
    static float readTemperatureC(float batteryFallbackC) {
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles(file ->
                file != null && file.getName().startsWith("thermal_zone"));
        float best = Float.NaN;
        int bestPriority = -1;
        if (zones != null) {
            for (File zone : zones) {
                try {
                    String type = readLine(new File(zone, "type"))
                            .toLowerCase(Locale.ROOT);
                    int priority = priority(type);
                    if (priority < 0) continue;
                    float value = normalizeTemperature(readLine(new File(zone, "temp")));
                    if (!plausible(value)) continue;
                    if (priority > bestPriority
                            || (priority == bestPriority
                            && (Float.isNaN(best) || value > best))) {
                        bestPriority = priority;
                        best = value;
                    }
                } catch (Throwable ignored) { }
            }
        }
        return Float.isNaN(best) ? batteryFallbackC : best;
    }

    private static int priority(String type) {
        if (type.contains("cpu") || type.contains("soc") || type.contains("apu")
                || type.contains("cluster") || type.contains("tsens")) return 3;
        if (type.contains("gpu") || type.contains("gpuss")) return 3;
        if (type.contains("skin") || type.contains("shell")
                || type.contains("quiet")) return 2;
        if (type.contains("battery") || type.contains("batt")) return 1;
        return -1;
    }

    private static float normalizeTemperature(String raw) {
        double value = Double.parseDouble(raw.trim());
        double absolute = Math.abs(value);
        if (absolute >= 1000d) value /= 1000d;
        else if (absolute > 200d) value /= 10d;
        return (float) value;
    }

    private static String readLine(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String value = reader.readLine();
            if (value == null) throw new IllegalStateException("empty sensor");
            return value.trim();
        }
    }

    private static boolean plausible(float value) {
        return !Float.isNaN(value) && value >= 0f && value <= 125f;
    }
}
