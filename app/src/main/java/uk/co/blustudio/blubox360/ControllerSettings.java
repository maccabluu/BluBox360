package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Saved Xbox 360 controller profiles shared by the settings and emulator processes. */
final class ControllerSettings {
    static final int PLAYER_COUNT = 4;

    static final int MODE_ANALOG = 0;
    static final int MODE_FACE = 1;
    static final int MODE_DPAD = 2;
    static final int MODE_CUSTOM = 3;

    static final int DIR_LEFT = 0;
    static final int DIR_UP = 1;
    static final int DIR_RIGHT = 2;
    static final int DIR_DOWN = 3;

    static final String LEFT_MODE = "left_mode";
    static final String LEFT_SWAP = "left_swap";
    static final String LEFT_INVERT_X = "left_invert_x";
    static final String LEFT_INVERT_Y = "left_invert_y";
    static final String RIGHT_MODE = "right_mode";
    static final String RIGHT_SWAP = "right_swap";
    static final String RIGHT_INVERT_X = "right_invert_x";
    static final String RIGHT_INVERT_Y = "right_invert_y";
    static final String DPAD_AS_LEFT = "dpad_as_left";
    static final String SPRINT_ASSIST = "sprint_assist";
    static final String RUMBLE_ENABLED = "rumble_enabled";
    static final String RUMBLE_STRENGTH = "rumble_strength";
    static final String TRIGGER_DEADZONE = "trigger_deadzone";
    static final String TRIGGER_SENSITIVITY = "trigger_sensitivity";

    static final String LEFT_CURVE = "left_curve";
    static final String LEFT_DEADZONE = "left_deadzone";
    static final String LEFT_OUTER = "left_outer";
    static final String LEFT_ANTI = "left_anti";
    static final String LEFT_SENSITIVITY = "left_sensitivity";
    static final String LEFT_ACCELERATION = "left_acceleration";
    static final String RIGHT_CURVE = "right_curve";
    static final String RIGHT_DEADZONE = "right_deadzone";
    static final String RIGHT_OUTER = "right_outer";
    static final String RIGHT_ANTI = "right_anti";
    static final String RIGHT_SENSITIVITY = "right_sensitivity";
    static final String RIGHT_ACCELERATION = "right_acceleration";

    static final String[] MODE_NAMES = {"Analog", "Face", "D-pad", "Custom"};
    static final String[] CURVE_NAMES = {"Linear", "Light", "Medium", "Strong"};
    static final String[] XBOX_BUTTON_NAMES = {
            "D-pad Left", "D-pad Up", "D-pad Right", "D-pad Down",
            "A", "B", "X", "Y", "Back", "Start", "LB", "RB", "L3", "R3",
            "LT", "RT"
    };
    static final int[] XBOX_BUTTON_CODES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    private static final String EDIT_PLAYER = "controller_edit_player";
    private static final int[] FACE_DIRECTIONS = {6, 7, 5, 4};
    private static final int[] DPAD_DIRECTIONS = {0, 1, 2, 3};

    private ControllerSettings() { }

    static Config load(Context context, int player) {
        int checkedPlayer = checkedPlayer(player);
        Stick left = new Stick(
                integer(context, checkedPlayer, LEFT_MODE, MODE_ANALOG),
                flag(context, checkedPlayer, LEFT_SWAP, false),
                flag(context, checkedPlayer, LEFT_INVERT_X, false),
                flag(context, checkedPlayer, LEFT_INVERT_Y, false),
                integer(context, checkedPlayer, LEFT_CURVE, 1),
                integer(context, checkedPlayer, LEFT_DEADZONE, 6),
                integer(context, checkedPlayer, LEFT_OUTER, 0),
                integer(context, checkedPlayer, LEFT_ANTI, 0),
                integer(context, checkedPlayer, LEFT_SENSITIVITY, 100),
                integer(context, checkedPlayer, LEFT_ACCELERATION, 0),
                customDirections(context, checkedPlayer, true));
        Stick right = new Stick(
                integer(context, checkedPlayer, RIGHT_MODE, MODE_ANALOG),
                flag(context, checkedPlayer, RIGHT_SWAP, false),
                flag(context, checkedPlayer, RIGHT_INVERT_X, false),
                flag(context, checkedPlayer, RIGHT_INVERT_Y, false),
                integer(context, checkedPlayer, RIGHT_CURVE, 1),
                integer(context, checkedPlayer, RIGHT_DEADZONE, 6),
                integer(context, checkedPlayer, RIGHT_OUTER, 0),
                integer(context, checkedPlayer, RIGHT_ANTI, 0),
                integer(context, checkedPlayer, RIGHT_SENSITIVITY, 100),
                integer(context, checkedPlayer, RIGHT_ACCELERATION, 0),
                customDirections(context, checkedPlayer, false));
        Macro[] macros = new Macro[4];
        for (int index = 0; index < macros.length; index++) {
            macros[index] = new Macro(macroTrigger(context, checkedPlayer, index),
                    macroOutputs(context, checkedPlayer, index));
        }
        return new Config(checkedPlayer,
                flag(context, checkedPlayer, RUMBLE_ENABLED, true),
                integer(context, checkedPlayer, RUMBLE_STRENGTH, 100),
                integer(context, checkedPlayer, TRIGGER_DEADZONE, 4),
                integer(context, checkedPlayer, TRIGGER_SENSITIVITY, 100),
                flag(context, checkedPlayer, DPAD_AS_LEFT, false),
                flag(context, checkedPlayer, SPRINT_ASSIST, false),
                left, right, macros);
    }

    static int editingPlayer(Context context) {
        return checkedPlayer(preferences(context).getInt(EDIT_PLAYER, 1));
    }

    static void setEditingPlayer(Context context, int player) {
        preferences(context).edit().putInt(EDIT_PLAYER, checkedPlayer(player)).apply();
    }

    static void resetPlayer(Context context, int player) {
        String prefix = "controller_p" + checkedPlayer(player) + "_";
        SharedPreferences preferences = preferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    static boolean flag(Context context, int player, String name, boolean fallback) {
        return preferences(context).getBoolean(key(player, name), fallback);
    }

    static void setFlag(Context context, int player, String name, boolean value) {
        preferences(context).edit().putBoolean(key(player, name), value).apply();
    }

    static int integer(Context context, int player, String name, int fallback) {
        return preferences(context).getInt(key(player, name), fallback);
    }

    static void setInteger(Context context, int player, String name, int value) {
        preferences(context).edit().putInt(key(player, name), value).apply();
    }

    static int customDirection(Context context, int player, boolean left, int direction) {
        int[] fallback = left ? DPAD_DIRECTIONS : FACE_DIRECTIONS;
        return integer(context, player, customKey(left, direction), fallback[direction]);
    }

    static void setCustomDirection(Context context, int player, boolean left,
                                   int direction, int xboxCode) {
        setInteger(context, player, customKey(left, direction), xboxCode);
    }

    static int macroTrigger(Context context, int player, int index) {
        return integer(context, player, "macro_" + index + "_trigger", -1);
    }

    static void setMacroTrigger(Context context, int player, int index, int keyCode) {
        setInteger(context, player, "macro_" + index + "_trigger", keyCode);
    }

    static int[] macroOutputs(Context context, int player, int index) {
        String value = preferences(context).getString(
                key(player, "macro_" + index + "_outputs"), "");
        if (value == null || value.trim().isEmpty()) return new int[0];
        List<Integer> result = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                int code = Integer.parseInt(part.trim());
                if (code >= 0 && code <= 15 && !result.contains(code)) result.add(code);
            } catch (NumberFormatException ignored) { }
        }
        int[] output = new int[result.size()];
        for (int i = 0; i < result.size(); i++) output[i] = result.get(i);
        return output;
    }

    static void setMacroOutputs(Context context, int player, int index, int[] outputs) {
        StringBuilder value = new StringBuilder();
        if (outputs != null) {
            for (int output : outputs) {
                if (output < 0 || output > 15) continue;
                if (value.length() > 0) value.append(',');
                value.append(output);
            }
        }
        preferences(context).edit().putString(
                key(player, "macro_" + index + "_outputs"), value.toString()).apply();
    }

    static String macroOutputLabel(int[] outputs) {
        if (outputs == null || outputs.length == 0) return "Not set";
        StringBuilder label = new StringBuilder();
        for (int output : outputs) {
            if (output < 0 || output >= XBOX_BUTTON_NAMES.length) continue;
            if (label.length() > 0) label.append(" + ");
            label.append(XBOX_BUTTON_NAMES[output]);
        }
        return label.length() == 0 ? "Not set" : label.toString();
    }

    static String physicalButtonName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
            case KeyEvent.KEYCODE_BUTTON_L2: return "LT";
            case KeyEvent.KEYCODE_BUTTON_R2: return "RT";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Back";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-pad Left";
            case KeyEvent.KEYCODE_DPAD_UP: return "D-pad Up";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-pad Right";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-pad Down";
            default:
                String label = KeyEvent.keyCodeToString(keyCode);
                return label.startsWith("KEYCODE_") ? label.substring(8) : label;
        }
    }

    static boolean isBindableKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_BUTTON_B
                || keyCode == KeyEvent.KEYCODE_BUTTON_X
                || keyCode == KeyEvent.KEYCODE_BUTTON_Y
                || keyCode == KeyEvent.KEYCODE_BUTTON_L1
                || keyCode == KeyEvent.KEYCODE_BUTTON_R1
                || keyCode == KeyEvent.KEYCODE_BUTTON_L2
                || keyCode == KeyEvent.KEYCODE_BUTTON_R2
                || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL
                || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR
                || keyCode == KeyEvent.KEYCODE_BUTTON_START
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || (keyCode >= KeyEvent.KEYCODE_BUTTON_1
                && keyCode <= KeyEvent.KEYCODE_BUTTON_16);
    }

    static float curveExponent(int curve) {
        switch (curve) {
            case 0: return 1.0f;
            case 2: return 1.55f;
            case 3: return 2.05f;
            default: return 1.22f;
        }
    }

    private static int[] customDirections(Context context, int player, boolean left) {
        int[] result = new int[4];
        for (int direction = 0; direction < result.length; direction++) {
            result[direction] = customDirection(context, player, left, direction);
        }
        return result;
    }

    private static String customKey(boolean left, int direction) {
        return (left ? "left_custom_" : "right_custom_") + direction;
    }

    private static String key(int player, String name) {
        return "controller_p" + checkedPlayer(player) + "_" + name;
    }

    private static int checkedPlayer(int player) {
        return Math.max(1, Math.min(PLAYER_COUNT, player));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                AppPreferences.PREFS, Context.MODE_PRIVATE);
    }

    static final class Config {
        final int player;
        final boolean rumbleEnabled;
        final int rumbleStrength;
        final int triggerDeadzone;
        final int triggerSensitivity;
        final boolean dpadAsLeft;
        final boolean sprintAssist;
        final Stick left;
        final Stick right;
        final Macro[] macros;

        Config(int player, boolean rumbleEnabled, int rumbleStrength,
               int triggerDeadzone, int triggerSensitivity, boolean dpadAsLeft,
               boolean sprintAssist, Stick left, Stick right, Macro[] macros) {
            this.player = player;
            this.rumbleEnabled = rumbleEnabled;
            this.rumbleStrength = Math.max(0, Math.min(200, rumbleStrength));
            this.triggerDeadzone = Math.max(0, Math.min(30, triggerDeadzone));
            this.triggerSensitivity = Math.max(50, Math.min(150, triggerSensitivity));
            this.dpadAsLeft = dpadAsLeft;
            this.sprintAssist = sprintAssist;
            this.left = left;
            this.right = right;
            this.macros = macros;
        }
    }

    static final class Stick {
        final int mode;
        final boolean swap;
        final boolean invertX;
        final boolean invertY;
        final int curve;
        final int deadzone;
        final int outerDeadzone;
        final int antiDeadzone;
        final int sensitivity;
        final int acceleration;
        final int[] customDirections;

        Stick(int mode, boolean swap, boolean invertX, boolean invertY, int curve,
              int deadzone, int outerDeadzone, int antiDeadzone, int sensitivity,
              int acceleration, int[] customDirections) {
            this.mode = Math.max(MODE_ANALOG, Math.min(MODE_CUSTOM, mode));
            this.swap = swap;
            this.invertX = invertX;
            this.invertY = invertY;
            this.curve = Math.max(0, Math.min(3, curve));
            this.deadzone = Math.max(0, Math.min(30, deadzone));
            this.outerDeadzone = Math.max(0, Math.min(30, outerDeadzone));
            this.antiDeadzone = Math.max(0, Math.min(50, antiDeadzone));
            this.sensitivity = Math.max(50, Math.min(200, sensitivity));
            this.acceleration = Math.max(0, Math.min(100, acceleration));
            this.customDirections = customDirections == null
                    ? new int[]{0, 1, 2, 3} : customDirections.clone();
        }
    }

    static final class Macro {
        final int triggerKeyCode;
        final int[] outputs;

        Macro(int triggerKeyCode, int[] outputs) {
            this.triggerKeyCode = triggerKeyCode;
            this.outputs = outputs == null ? new int[0] : outputs.clone();
        }

        boolean isReady() {
            return triggerKeyCode >= 0 && outputs.length > 0;
        }
    }
}
