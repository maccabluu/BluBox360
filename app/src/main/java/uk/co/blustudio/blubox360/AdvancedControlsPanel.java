package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Programmatic controls page modelled after a console emulator settings screen. */
final class AdvancedControlsPanel extends LinearLayout {
    private final Activity activity;
    private final Runnable refresh;
    private final int player;

    AdvancedControlsPanel(Activity activity, Runnable refresh) {
        super(activity);
        this.activity = activity;
        this.refresh = refresh;
        this.player = ControllerSettings.editingPlayer(activity);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(18));
        build();
    }

    private void build() {
        addSection("Player & Rumble");
        addView(playerCard());
        addView(toggleCard("Rumble / vibration",
                "Adds short haptic feedback to controller button presses. The gamepad motor is used first, with the AYN Thor motor as fallback.",
                ControllerSettings.RUMBLE_ENABLED, true));
        addView(seekCard("Vibration strength",
                "Changes controller and device haptic strength.",
                ControllerSettings.RUMBLE_STRENGTH, 0, 200, 100, "%"));
        addView(testRumbleCard());

        addSection("Analog Triggers");
        addView(seekCard("Trigger pressure",
                "Scales LT and RT pressure. Lower values soften the trigger. Higher values reach full pressure sooner.",
                ControllerSettings.TRIGGER_SENSITIVITY, 50, 150, 100, "%"));
        addView(seekCard("Trigger deadzone",
                "Ignores a small amount of LT and RT travel near the resting position.",
                ControllerSettings.TRIGGER_DEADZONE, 0, 30, 4, "%"));

        addSection("Analog Sticks");
        addView(toggleCard("Left-stick sprint assist",
                "Presses L3 while the left stick is held near full forward. This helps games with click-to-sprint controls.",
                ControllerSettings.SPRINT_ASSIST, false));
        addView(stickModeCard(true));
        addView(toggleCard("Left Stick - Swap X/Y",
                "Swaps the left stick horizontal and vertical axes.",
                ControllerSettings.LEFT_SWAP, false));
        addView(toggleCard("Left Stick - Invert X",
                "Mirrors the left stick horizontally.",
                ControllerSettings.LEFT_INVERT_X, false));
        addView(toggleCard("Left Stick - Invert Y",
                "Mirrors the left stick vertically.",
                ControllerSettings.LEFT_INVERT_Y, false));
        addView(stickModeCard(false));
        addView(toggleCard("Right Stick - Swap X/Y",
                "Swaps the right stick horizontal and vertical axes.",
                ControllerSettings.RIGHT_SWAP, false));
        addView(toggleCard("Right Stick - Invert X",
                "Mirrors the right stick horizontally.",
                ControllerSettings.RIGHT_INVERT_X, false));
        addView(toggleCard("Right Stick - Invert Y",
                "Mirrors the right stick vertically.",
                ControllerSettings.RIGHT_INVERT_Y, false));
        addView(toggleCard("D-pad acts as Left Stick",
                "Sends full left-stick movement from the physical D-pad. Digital D-pad presses stop while this option is on.",
                ControllerSettings.DPAD_AS_LEFT, false));

        addStickFeel(true);
        addStickFeel(false);

        addSection("Macros");
        TextView macroHelp = text(
                "Set four Xbox 360 button combinations. Bind each macro to one physical controller button, then choose its output buttons.",
                11, activity.getColor(R.color.muted), false);
        macroHelp.setPadding(dp(4), 0, dp(4), dp(8));
        addView(macroHelp);
        for (int index = 0; index < 4; index++) addView(macroCard(index));

        Button reset = button("Reset Player " + player + " controls", false);
        reset.setOnClickListener(v -> confirmReset());
        LayoutParams resetParams = new LayoutParams(dp(250), dp(50));
        resetParams.gravity = Gravity.CENTER_HORIZONTAL;
        resetParams.topMargin = dp(12);
        addView(reset, resetParams);
    }

    private LinearLayout playerCard() {
        int connected = controllerIds().size();
        LinearLayout card = card("Editing Player " + player,
                "Xbox 360 supports four controller slots. Player 1 is active in this alpha. Player 2 to 4 profiles are saved for a later native multiplayer update.");
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, dp(9), 0, 0);
        for (int index = 1; index <= ControllerSettings.PLAYER_COUNT; index++) {
            final int selected = index;
            Button choice = button("Player " + index, index == player);
            choice.setOnClickListener(v -> {
                ControllerSettings.setEditingPlayer(activity, selected);
                refresh.run();
            });
            LayoutParams params = new LayoutParams(0, dp(48), 1f);
            if (index > 1) params.leftMargin = dp(6);
            row.addView(choice, params);
        }
        card.addView(row);
        TextView devices = text(connected == 0 ? "No external controller detected"
                        : connected + " controller" + (connected == 1 ? "" : "s") + " detected",
                10, activity.getColor(R.color.cyan), true);
        devices.setPadding(0, dp(8), 0, 0);
        card.addView(devices);
        return card;
    }

    private LinearLayout testRumbleCard() {
        LinearLayout card = card("Test rumble - Player " + player,
                "Sends a short vibration to the assigned gamepad or the AYN Thor motor.");
        Button test = button("Test rumble", true);
        test.setOnClickListener(v -> {
            int strength = ControllerSettings.integer(activity, player,
                    ControllerSettings.RUMBLE_STRENGTH, 100);
            boolean worked = ControllerHaptics.test(activity, controllerIdForPlayer(), strength);
            Toast.makeText(activity, worked ? "Rumble test sent."
                            : "No vibration motor was found.", Toast.LENGTH_SHORT).show();
        });
        LayoutParams params = new LayoutParams(dp(190), dp(48));
        params.topMargin = dp(8);
        card.addView(test, params);
        return card;
    }

    private LinearLayout stickModeCard(boolean left) {
        String side = left ? "Left Stick" : "Right Stick";
        String key = left ? ControllerSettings.LEFT_MODE : ControllerSettings.RIGHT_MODE;
        int current = ControllerSettings.integer(activity, player, key,
                ControllerSettings.MODE_ANALOG);
        LinearLayout card = card(side,
                "Choose what this stick sends to the Xbox 360 game.");
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        for (int index = 0; index < ControllerSettings.MODE_NAMES.length; index++) {
            final int selected = index;
            Button option = button(ControllerSettings.MODE_NAMES[index], current == index);
            option.setOnClickListener(v -> {
                ControllerSettings.setInteger(activity, player, key, selected);
                refresh.run();
            });
            LayoutParams params = new LayoutParams(0, dp(46), 1f);
            if (index > 0) params.leftMargin = dp(5);
            row.addView(option, params);
        }
        card.addView(row);
        if (current == ControllerSettings.MODE_CUSTOM) {
            Button configure = button("Edit custom directions", false);
            configure.setOnClickListener(v -> showCustomDirections(left));
            LayoutParams params = new LayoutParams(dp(220), dp(46));
            params.topMargin = dp(7);
            card.addView(configure, params);
        }
        return card;
    }

    private void addStickFeel(boolean left) {
        String side = left ? "Left Stick Feel" : "Right Stick Feel";
        addSection(side);
        String curveKey = left ? ControllerSettings.LEFT_CURVE : ControllerSettings.RIGHT_CURVE;
        int currentCurve = ControllerSettings.integer(activity, player, curveKey, 1);
        LinearLayout curve = card("Response curve",
                "Higher settings make small movements gentler while full stick travel still reaches maximum output.");
        LinearLayout curveRow = new LinearLayout(activity);
        curveRow.setOrientation(HORIZONTAL);
        curveRow.setPadding(0, dp(8), 0, 0);
        for (int index = 0; index < ControllerSettings.CURVE_NAMES.length; index++) {
            final int selected = index;
            Button option = button(ControllerSettings.CURVE_NAMES[index], currentCurve == index);
            option.setOnClickListener(v -> {
                ControllerSettings.setInteger(activity, player, curveKey, selected);
                refresh.run();
            });
            LayoutParams params = new LayoutParams(0, dp(46), 1f);
            if (index > 0) params.leftMargin = dp(5);
            curveRow.addView(option, params);
        }
        curve.addView(curveRow);
        addView(curve);

        addView(seekCard("Deadzone",
                "Ignores stick travel near the centre and removes drift.",
                left ? ControllerSettings.LEFT_DEADZONE : ControllerSettings.RIGHT_DEADZONE,
                0, 30, 6, "%"));
        addView(seekCard("Outer deadzone",
                "Maps the outer edge to full movement for short-travel handheld sticks.",
                left ? ControllerSettings.LEFT_OUTER : ControllerSettings.RIGHT_OUTER,
                0, 30, 0, "%"));
        addView(seekCard("Anti-deadzone",
                "Sends a minimum output after movement starts. Raise this only when a game ignores small stick movement.",
                left ? ControllerSettings.LEFT_ANTI : ControllerSettings.RIGHT_ANTI,
                0, 50, 0, "%"));
        addView(seekCard("Sensitivity",
                "Under 100% is slower and finer. Over 100% reaches full movement faster.",
                left ? ControllerSettings.LEFT_SENSITIVITY : ControllerSettings.RIGHT_SENSITIVITY,
                50, 200, 100, "%"));
        addView(seekCard("Acceleration",
                "Keeps small movements precise and increases speed near the outer edge.",
                left ? ControllerSettings.LEFT_ACCELERATION : ControllerSettings.RIGHT_ACCELERATION,
                0, 100, 0, "%"));
    }

    private LinearLayout macroCard(int index) {
        int trigger = ControllerSettings.macroTrigger(activity, player, index);
        int[] outputs = ControllerSettings.macroOutputs(activity, player, index);
        String title = ControllerSettings.macroOutputLabel(outputs);
        String binding = trigger < 0 ? "Controller: not bound"
                : "Controller: " + ControllerSettings.physicalButtonName(trigger);
        LinearLayout card = card("M" + (index + 1) + "   " + title, binding);
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        Button bind = button("Bind", true);
        bind.setOnClickListener(v -> bindMacro(index));
        actions.addView(bind, new LayoutParams(dp(130), dp(46)));
        Button edit = button("Edit", false);
        edit.setOnClickListener(v -> editMacro(index));
        LayoutParams editParams = new LayoutParams(dp(130), dp(46));
        editParams.leftMargin = dp(7);
        actions.addView(edit, editParams);
        card.addView(actions);
        return card;
    }

    private LinearLayout toggleCard(String title, String description, String key,
                                    boolean fallback) {
        LinearLayout card = card(title, description);
        CheckBox toggle = new CheckBox(activity);
        toggle.setText("Enabled");
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(12);
        toggle.setChecked(ControllerSettings.flag(activity, player, key, fallback));
        toggle.setPadding(0, dp(7), 0, 0);
        toggle.setOnCheckedChangeListener((button, checked) ->
                ControllerSettings.setFlag(activity, player, key, checked));
        card.addView(toggle);
        return card;
    }

    private LinearLayout seekCard(String title, String description, String key,
                                  int minimum, int maximum, int fallback, String suffix) {
        int value = Math.max(minimum, Math.min(maximum,
                ControllerSettings.integer(activity, player, key, fallback)));
        LinearLayout card = card(title + "   " + value + suffix, description);
        TextView valueLabel = (TextView) card.getChildAt(0);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(maximum - minimum);
        seek.setProgress(value - minimum);
        seek.setPadding(0, dp(8), 0, 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress,
                                                     boolean fromUser) {
                int selected = minimum + progress;
                valueLabel.setText(title + "   " + selected + suffix);
                if (fromUser) ControllerSettings.setInteger(
                        activity, player, key, selected);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        card.addView(seek, new LayoutParams(LayoutParams.MATCH_PARENT, dp(52)));
        return card;
    }

    private void bindMacro(int index) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Bind M" + (index + 1))
                .setMessage("Press one physical controller button. BluBox will use it to start this macro.")
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Clear binding", (box, which) -> {
                    ControllerSettings.setMacroTrigger(activity, player, index, -1);
                    refresh.run();
                })
                .create();
        dialog.setOnKeyListener((box, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0
                    || !ControllerSettings.isBindableKey(keyCode)) return false;
            ControllerSettings.setMacroTrigger(activity, player, index, keyCode);
            box.dismiss();
            Toast.makeText(activity, "M" + (index + 1) + " bound to "
                            + ControllerSettings.physicalButtonName(keyCode) + ".",
                    Toast.LENGTH_SHORT).show();
            refresh.run();
            return true;
        });
        dialog.show();
    }

    private void editMacro(int index) {
        int[] current = ControllerSettings.macroOutputs(activity, player, index);
        boolean[] selected = new boolean[ControllerSettings.XBOX_BUTTON_NAMES.length];
        for (int output : current) if (output >= 0 && output < selected.length) {
            selected[output] = true;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Edit M" + (index + 1) + " combo")
                .setMultiChoiceItems(ControllerSettings.XBOX_BUTTON_NAMES, selected,
                        (dialog, which, checked) -> selected[which] = checked)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Clear combo", (dialog, which) -> {
                    ControllerSettings.setMacroOutputs(activity, player, index, new int[0]);
                    refresh.run();
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    List<Integer> outputs = new ArrayList<>();
                    for (int code = 0; code < selected.length; code++) {
                        if (selected[code]) outputs.add(code);
                    }
                    int[] values = new int[outputs.size()];
                    for (int i = 0; i < values.length; i++) values[i] = outputs.get(i);
                    ControllerSettings.setMacroOutputs(activity, player, index, values);
                    refresh.run();
                })
                .show();
    }

    private void showCustomDirections(boolean left) {
        int[] directionCodes = {
                ControllerSettings.DIR_UP, ControllerSettings.DIR_DOWN,
                ControllerSettings.DIR_LEFT, ControllerSettings.DIR_RIGHT
        };
        String[] directionNames = {"Up", "Down", "Left", "Right"};
        String[] rows = new String[directionCodes.length];
        for (int index = 0; index < rows.length; index++) {
            int output = ControllerSettings.customDirection(activity, player, left,
                    directionCodes[index]);
            rows[index] = directionNames[index] + ": " + xboxName(output);
        }
        new AlertDialog.Builder(activity)
                .setTitle((left ? "Left" : "Right") + " Stick custom directions")
                .setItems(rows, (dialog, which) -> chooseCustomOutput(
                        left, directionCodes[which], directionNames[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseCustomOutput(boolean left, int direction, String directionName) {
        int current = ControllerSettings.customDirection(activity, player, left, direction);
        int checked = Math.max(0, Math.min(ControllerSettings.XBOX_BUTTON_NAMES.length - 1,
                current));
        new AlertDialog.Builder(activity)
                .setTitle(directionName + " sends")
                .setSingleChoiceItems(ControllerSettings.XBOX_BUTTON_NAMES, checked,
                        (dialog, which) -> {
                            ControllerSettings.setCustomDirection(
                                    activity, player, left, direction, which);
                            dialog.dismiss();
                            refresh.run();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(activity)
                .setTitle("Reset Player " + player + " controls?")
                .setMessage("This resets stick tuning, trigger tuning, vibration, mappings, and macros for this player profile.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    ControllerSettings.resetPlayer(activity, player);
                    refresh.run();
                })
                .show();
    }

    private int controllerIdForPlayer() {
        List<Integer> ids = controllerIds();
        int index = player - 1;
        return index >= 0 && index < ids.size() ? ids.get(index) : -1;
    }

    private static List<Integer> controllerIds() {
        List<Integer> result = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK)
                    == InputDevice.SOURCE_JOYSTICK) {
                result.add(id);
            }
        }
        return result;
    }

    private String xboxName(int code) {
        return code >= 0 && code < ControllerSettings.XBOX_BUTTON_NAMES.length
                ? ControllerSettings.XBOX_BUTTON_NAMES[code] : "Not set";
    }

    private void addSection(String title) {
        TextView heading = text(title, 17, activity.getColor(R.color.cyan), true);
        heading.setPadding(dp(4), dp(16), 0, dp(7));
        addView(heading);
    }

    private LinearLayout card(String title, String description) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackgroundResource(R.drawable.panel_card);
        TextView heading = text(title, 14, Color.WHITE, true);
        card.addView(heading);
        TextView detail = text(description, 10, activity.getColor(R.color.muted), false);
        detail.setPadding(0, dp(4), 0, 0);
        card.addView(detail);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setBackgroundResource(primary
                ? R.drawable.button_primary : R.drawable.button_secondary);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
