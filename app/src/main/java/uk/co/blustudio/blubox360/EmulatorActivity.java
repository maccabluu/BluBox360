package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.view.Gravity;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

import xendroid.compose.Application;
import xendroid.compose.Emulator;
import xendroid.compose.Utils;

public final class EmulatorActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_GAME_PATH = "game_path";
    static final String EXTRA_GAME_NAME = "game_name";
    static final String EXTRA_TITLE_ID = "title_id";
    static final String EXTRA_PROFILE_ID = "profile_id";

    private static final int KC_DPAD_LEFT = 0;
    private static final int KC_DPAD_UP = 1;
    private static final int KC_DPAD_RIGHT = 2;
    private static final int KC_DPAD_DOWN = 3;
    private static final int KC_A = 4;
    private static final int KC_B = 5;
    private static final int KC_X = 6;
    private static final int KC_Y = 7;
    private static final int KC_BACK = 8;
    private static final int KC_START = 9;
    private static final int KC_LB = 10;
    private static final int KC_RB = 11;
    private static final int KC_L3 = 12;
    private static final int KC_R3 = 13;
    private static final int KC_LT = 14;
    private static final int KC_RT = 15;
    private static final int KC_L_LEFT = 16;
    private static final int KC_L_UP = 17;
    private static final int KC_L_RIGHT = 18;
    private static final int KC_L_DOWN = 19;
    private static final int KC_R_LEFT = 20;
    private static final int KC_R_UP = 21;
    private static final int KC_R_RIGHT = 22;
    private static final int KC_R_DOWN = 23;
    private static final int VALUE_UNUSED = -1;
    private static final float MIN_STICK_DEADZONE = 0.06f;
    private static final float MAX_STICK_DEADZONE = 0.18f;
    private static final int DIGITAL_OUTPUT_COUNT = 16;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean[] axisPressed = new boolean[24];
    private final int[] axisValue = new int[24];
    private final boolean[] leftMappedOutputs = new boolean[DIGITAL_OUTPUT_COUNT];
    private final boolean[] rightMappedOutputs = new boolean[DIGITAL_OUTPUT_COUNT];
    private final boolean[] macroActive = new boolean[4];
    private Emulator core;
    private SurfaceView surfaceView;
    private Surface surface;
    private TextView loadingText;
    private TextView fpsText;
    private boolean prepared;
    private boolean booted;
    private boolean pauseMenuOpen;
    private boolean lTriggerDown;
    private boolean rTriggerDown;
    private boolean hatLeft;
    private boolean hatUp;
    private boolean hatRight;
    private boolean hatDown;
    private boolean smoothControls;
    private boolean harryPotterAimProfile;
    private ControllerSettings.Config controllerConfig;
    private float filteredLeftX;
    private float filteredLeftY;
    private float filteredRightX;
    private float filteredRightY;
    private float lastLeftX;
    private float lastLeftY;
    private boolean dpadLeft;
    private boolean dpadUp;
    private boolean dpadRight;
    private boolean dpadDown;
    private boolean physicalL3Down;
    private boolean sprintL3Down;
    private boolean emittedL3Down;
    private AlertDialog guestDialog;
    private long guestRequestId = -1;
    private String gameName;
    private String titleId;
    private String profileId;
    private ProfileStore.Profile achievementProfile;
    private DisplayManager displayManager;
    private AchievementPresentation achievementPresentation;
    private AchievementTracker achievementTracker;
    private AchievementData.Snapshot latestAchievementSnapshot;
    private AchievementData.ProfileSummary latestProfileSummary =
            AchievementData.ProfileSummary.empty();
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private boolean thermalShutdown;

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int displayId) {
                    refreshAchievementDisplay();
                }

                @Override public void onDisplayRemoved(int displayId) {
                    refreshAchievementDisplay();
                }

                @Override public void onDisplayChanged(int displayId) {
                    refreshAchievementDisplay();
                }
            };

    private final Runnable statsPoll = new Runnable() {
        @Override public void run() {
            if (!booted || isFinishing()) return;
            if (CoreConfig.showFps(EmulatorActivity.this)) {
                double fps = core.average_fps();
                double ms = core.last_frame_time_ms();
                fpsText.setText(String.format(Locale.US, "%.1f FPS  •  %.1f ms", fps, ms));
                fpsText.setVisibility(View.VISIBLE);
            } else {
                fpsText.setVisibility(View.GONE);
            }
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable promptPoll = new Runnable() {
        @Override public void run() {
            if (!booted || isFinishing()) return;
            if (guestDialog == null || !guestDialog.isShowing()) pollGuestPrompt();
            handler.postDelayed(this, 180);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        for (int i = 0; i < axisValue.length; i++) axisValue[i] = Integer.MIN_VALUE;
        enterImmersive();
        String path = getIntent().getStringExtra(EXTRA_GAME_PATH);
        gameName = getIntent().getStringExtra(EXTRA_GAME_NAME);
        titleId = AchievementData.normalizeTitleId(
                getIntent().getStringExtra(EXTRA_TITLE_ID));
        ProfileStore profiles = new ProfileStore(this);
        ProfileStore.Profile requested = profiles.getById(
                getIntent().getStringExtra(EXTRA_PROFILE_ID));
        achievementProfile = requested == null ? profiles.getActive() : requested;
        profileId = achievementProfile.id;
        if (path == null || !new File(path).isFile()) {
            Toast.makeText(this, "BluBox cannot read the selected game.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (gameName == null || gameName.trim().isEmpty()) gameName = new File(path).getName();
        smoothControls = AppPreferences.smoothControls(this);
        controllerConfig = ControllerSettings.load(this, 1);
        harryPotterAimProfile = AppPreferences.harryPotterPrecisionAim(this)
                && isHarryPotterGame(titleId, gameName);
        buildEmulatorView(gameName);
        if (!setupThermalGuard()) return;
        setupAchievementDisplay();
        new Thread(() -> prepareCore(path), "BluBox-core-load").start();
    }

    private boolean setupThermalGuard() {
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) return true;
        thermalListener = status -> {
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) stopForHeat();
        };
        try {
            powerManager.addThermalStatusListener(getMainExecutor(), thermalListener);
            if (powerManager.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) {
                stopForHeat();
                return false;
            }
        } catch (Throwable ignored) {
            thermalListener = null;
        }
        return true;
    }

    private void stopForHeat() {
        if (thermalShutdown || isFinishing()) return;
        thermalShutdown = true;
        if (core != null) {
            try { core.pause(); } catch (Throwable ignored) { }
        }
        Toast.makeText(this,
                "Game closed because Android reported severe device heat.",
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void setupAchievementDisplay() {
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager == null) return;
        displayManager.registerDisplayListener(displayListener, handler);
        refreshAchievementDisplay();
    }

    private void refreshAchievementDisplay() {
        if (displayManager == null || isFinishing()
                || !AppPreferences.secondScreenAchievements(this)) {
            dismissAchievementDisplay();
            return;
        }
        Display second = findSecondDisplay();
        if (second == null) {
            dismissAchievementDisplay();
            return;
        }
        if (achievementPresentation != null
                && achievementPresentation.getDisplay().getDisplayId() == second.getDisplayId()) {
            return;
        }
        dismissAchievementDisplay();
        try {
            achievementPresentation = new AchievementPresentation(this, second,
                    achievementProfile, gameName, titleId,
                    AppPreferences.showLockedAchievements(this));
            achievementPresentation.show();
            if (latestAchievementSnapshot != null) {
                achievementPresentation.update(latestAchievementSnapshot,
                        latestProfileSummary, null);
            }
            startAchievementTracker();
        } catch (Throwable ignored) {
            achievementPresentation = null;
        }
    }

    @SuppressWarnings("deprecation")
    private Display findSecondDisplay() {
        int currentId = getWindowManager().getDefaultDisplay().getDisplayId();
        Display[] presentations = displayManager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Display result = bestDisplay(presentations, currentId);
        if (result != null) return result;
        return bestDisplay(displayManager.getDisplays(), currentId);
    }

    private static Display bestDisplay(Display[] displays, int currentId) {
        Display result = null;
        long smallestArea = Long.MAX_VALUE;
        if (displays == null) return null;
        for (Display display : displays) {
            if (display == null || display.getDisplayId() == currentId
                    || !display.isValid()) continue;
            Display.Mode mode = display.getMode();
            long area = (long) mode.getPhysicalWidth() * mode.getPhysicalHeight();
            if (area < smallestArea) {
                smallestArea = area;
                result = display;
            }
        }
        return result;
    }

    private void startAchievementTracker() {
        if (achievementTracker != null) return;
        achievementTracker = new AchievementTracker(this, profileId, titleId, gameName,
                (snapshot, profile, newlyUnlocked) -> {
                    latestAchievementSnapshot = snapshot;
                    latestProfileSummary = profile;
                    if (achievementPresentation != null
                            && achievementPresentation.isShowing()) {
                        achievementPresentation.update(snapshot, profile, newlyUnlocked);
                    }
                });
        achievementTracker.start();
    }

    private void dismissAchievementDisplay() {
        if (achievementTracker != null) {
            achievementTracker.stop();
            achievementTracker = null;
        }
        if (achievementPresentation != null) {
            try { achievementPresentation.dismiss(); } catch (Throwable ignored) { }
            achievementPresentation = null;
        }
    }

    private void buildEmulatorView(String name) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surfaceView = new SurfaceView(this);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.HORIZONTAL);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        loading.setPadding(dp(12), dp(8), dp(15), dp(8));
        loading.setBackgroundResource(R.drawable.panel_card);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blubox_logo);
        loading.addView(logo, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(8), 0, 0, 0);
        TextView title = text(name, 14, Color.WHITE, true);
        loadingText = text("Loading BluBox 360 core…", 10, getColor(R.color.cyan), false);
        words.addView(title);
        words.addView(loadingText);
        loading.addView(words);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(360), dp(70));
        loadingParams.gravity = Gravity.START | Gravity.TOP;
        loadingParams.setMargins(dp(14), dp(14), 0, 0);
        root.addView(loading, loadingParams);
        loading.setTag("loading");

        fpsText = text("", 11, Color.WHITE, true);
        fpsText.setGravity(Gravity.CENTER);
        fpsText.setPadding(dp(12), dp(7), dp(12), dp(7));
        fpsText.setBackgroundResource(R.drawable.panel_card);
        fpsText.setVisibility(View.GONE);
        FrameLayout.LayoutParams fpsParams = new FrameLayout.LayoutParams(dp(180), dp(42));
        fpsParams.gravity = Gravity.END | Gravity.TOP;
        fpsParams.setMargins(0, dp(14), dp(14), 0);
        root.addView(fpsText, fpsParams);

        setContentView(root);
        surfaceView.requestFocus();
    }

    private void prepareCore(String path) {
        try {
            core = CoreConfig.ensureCoreLoaded();
            CoreConfig.ensureFiles();
            boolean fablePreset = CoreConfig.applyLaunchSettings(this, titleId, gameName);
            runOnUiThread(() -> {
                try {
                    core.setup_context(this);
                    core.setup_game_path(path);
                    core.setup_launch_args(new String[]{
                            "--storage_root=" + Utils.get_storage_root_path(),
                            "--config=" + Application.get_global_config_file().getAbsolutePath(),
                            "--log_file=" + Utils.get_log_file_path(),
                            "--log_append=true"
                    });
                    core.setup_uri_info_list_file(Application.get_uri_info_list_file().getAbsolutePath());
                    prepared = true;
                    loadingText.setText(fablePreset
                            ? "Fable II performance preset ready…"
                            : harryPotterAimProfile
                            ? "Harry Potter precision aiming ready…"
                            : "Starting game…");
                    bootIfReady();
                } catch (Throwable t) {
                    fail("Core setup failed", t);
                }
            });
        } catch (Throwable t) {
            runOnUiThread(() -> fail("Core load failed", t));
        }
    }

    private void bootIfReady() {
        if (!prepared || surface == null || booted || core == null) return;
        try {
            core.setup_surface(surface);
            core.boot();
            booted = true;
            loadingText.setText("Compiling shaders and starting…");
            handler.postDelayed(() -> {
                View badge = getWindow().getDecorView().findViewWithTag("loading");
                if (badge != null) badge.animate().alpha(0f).setDuration(500)
                        .withEndAction(() -> badge.setVisibility(View.GONE)).start();
            }, 5500);
            handler.post(statsPoll);
            handler.post(promptPoll);
        } catch (Throwable t) {
            fail("Game boot failed", t);
        }
    }

    private void fail(String heading, Throwable t) {
        String detail = t.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = t.getClass().getSimpleName();
        new AlertDialog.Builder(this)
                .setTitle(heading)
                .setMessage(detail + "\n\nTry Performance mode or check the game compatibility and xe.log.")
                .setCancelable(false)
                .setPositiveButton("Back to library", (dialog, which) -> finish())
                .show();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        boolean wasBooted = booted;
        bootIfReady();
        if (wasBooted && core != null) {
            core.setup_surface(surface);
            if (core.is_paused() && !pauseMenuOpen) core.resume();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (booted && width > 0 && height > 0) core.change_surface(width, height);
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (booted && core != null) {
            try { core.pause(); } catch (Throwable ignored) {}
            try { core.setup_surface(null); } catch (Throwable ignored) {}
        }
        surface = null;
    }

    @Override protected void onPause() {
        super.onPause();
        if (booted && core != null) {
            try { core.flush_gpu_caches(); } catch (Throwable ignored) {}
            try { core.pause(); } catch (Throwable ignored) {}
        }
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
        refreshAchievementDisplay();
        if (booted && core != null && surface != null && !pauseMenuOpen) {
            try { if (core.is_paused()) core.resume(); } catch (Throwable ignored) {}
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (powerManager != null && thermalListener != null) {
            try { powerManager.removeThermalStatusListener(thermalListener); }
            catch (Throwable ignored) { }
        }
        dismissAchievementDisplay();
        if (displayManager != null) {
            try { displayManager.unregisterDisplayListener(displayListener); }
            catch (Throwable ignored) { }
        }
        if (guestDialog != null) guestDialog.dismiss();
        if (core != null) {
            try { core.keyboard_cancel_all(); } catch (Throwable ignored) {}
            try { core.msgbox_cancel_all(); } catch (Throwable ignored) {}
            try { core.disc_cancel_all(); } catch (Throwable ignored) {}
            try { core.quit(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
        Process.killProcess(Process.myPid());
    }

    @Override
    public void onBackPressed() {
        if (!booted) {
            finish();
            return;
        }
        if (pauseMenuOpen) return;
        pauseMenuOpen = true;
        try { core.pause(); } catch (Throwable ignored) {}
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("BluBox paused")
                .setMessage("Game progress is saved by the game to the active Xbox 360 profile.")
                .setNegativeButton("Quit to library", (d, which) -> finish())
                .setPositiveButton("Resume", (d, which) -> resumeFromMenu())
                .setOnCancelListener(d -> resumeFromMenu())
                .create();
        dialog.show();
    }

    private void resumeFromMenu() {
        pauseMenuOpen = false;
        try { if (core.is_paused()) core.resume(); } catch (Throwable ignored) {}
        enterImmersive();
    }

    private void pollGuestPrompt() {
        try {
            Emulator.KeyboardRequest keyboard = core.keyboard_request();
            if (keyboard != null && keyboard.id != guestRequestId) {
                showKeyboard(keyboard);
                return;
            }
            Emulator.MessageBoxRequest message = core.msgbox_request();
            if (message != null && message.id != guestRequestId) {
                showMessage(message);
                return;
            }
            Emulator.DiscSwapRequest disc = core.disc_request();
            if (disc != null && disc.id != guestRequestId) showDiscPicker(disc);
        } catch (Throwable ignored) {
        }
    }

    private void showKeyboard(Emulator.KeyboardRequest request) {
        guestRequestId = request.id;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(request.defaultText == null ? "" : request.defaultText);
        input.setSelection(input.length());
        guestDialog = new AlertDialog.Builder(this)
                .setTitle(request.title == null ? "Xbox 360 keyboard" : request.title)
                .setMessage(request.description)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    core.keyboard_submit(request.id, false, "");
                    guestRequestId = -1;
                })
                .setPositiveButton("OK", (dialog, which) -> {
                    String text = input.getText().toString();
                    if (request.maxLength > 0 && text.length() > request.maxLength) {
                        text = text.substring(0, request.maxLength);
                    }
                    core.keyboard_submit(request.id, true, text);
                    guestRequestId = -1;
                }).create();
        guestDialog.show();
    }

    private void showMessage(Emulator.MessageBoxRequest request) {
        guestRequestId = request.id;
        String[] buttons = request.buttons == null || request.buttons.length == 0
                ? new String[]{"OK"} : request.buttons;
        guestDialog = new AlertDialog.Builder(this)
                .setTitle(request.title == null ? "Xbox 360" : request.title)
                .setMessage(request.text)
                .setItems(buttons, (dialog, which) -> {
                    core.msgbox_submit(request.id, which);
                    guestRequestId = -1;
                })
                .setOnCancelListener(dialog -> {
                    int selected = Math.max(0, Math.min(request.activeButton, buttons.length - 1));
                    core.msgbox_submit(request.id, selected);
                    guestRequestId = -1;
                }).create();
        guestDialog.show();
    }

    private void showDiscPicker(Emulator.DiscSwapRequest request) {
        guestRequestId = request.id;
        String[] labels = request.discLabels == null ? new String[0] : request.discLabels;
        String[] paths = request.discPaths == null ? new String[0] : request.discPaths;
        if (labels.length == 0 || paths.length == 0) {
            core.disc_submit(request.id, false, "");
            guestRequestId = -1;
            return;
        }
        guestDialog = new AlertDialog.Builder(this)
                .setTitle("Swap Xbox 360 disc")
                .setMessage(request.message)
                .setItems(labels, (dialog, which) -> {
                    if (which < paths.length) core.disc_submit(request.id, true, paths[which]);
                    else core.disc_submit(request.id, false, "");
                    guestRequestId = -1;
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    core.disc_submit(request.id, false, "");
                    guestRequestId = -1;
                }).create();
        guestDialog.show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) onBackPressed();
            return true;
        }
        int gameKey = mapKey(keyCode);
        if (gameKey >= 0) {
            if (booted && event.getRepeatCount() == 0) {
                sendButtonHaptic(event);
                if (handleMacroKey(keyCode, true)) return true;
                if (controllerConfig.dpadAsLeft && updateDpadKey(keyCode, true)) {
                    applyLeftControl(lastLeftX, lastLeftY);
                    return true;
                }
                if (gameKey == KC_L3) {
                    physicalL3Down = true;
                    emitCombinedL3();
                } else {
                    core.key_event(gameKey, true, VALUE_UNUSED);
                }
            }
            return true;
        }
        return isController(event.getSource()) || super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        int gameKey = mapKey(keyCode);
        if (gameKey >= 0) {
            if (booted) {
                if (handleMacroKey(keyCode, false)) return true;
                if (controllerConfig.dpadAsLeft && updateDpadKey(keyCode, false)) {
                    applyLeftControl(lastLeftX, lastLeftY);
                    return true;
                }
                if (gameKey == KC_L3) {
                    physicalL3Down = false;
                    emitCombinedL3();
                } else {
                    core.key_event(gameKey, false, VALUE_UNUSED);
                }
            }
            return true;
        }
        return isController(event.getSource()) || super.onKeyUp(keyCode, event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return super.onGenericMotionEvent(event);
        }
        if (!booted || pauseMenuOpen) return true;
        for (int i = 0; i < event.getHistorySize(); i++) processStickSample(event, i);
        processStickSample(event, -1);
        if (!macroUsesPhysicalKey(KeyEvent.KEYCODE_BUTTON_L2)
                && !syntheticOutputActive(KC_LT)) {
            lTriggerDown = emitTrigger(Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE)), KC_LT, lTriggerDown);
        }
        if (!macroUsesPhysicalKey(KeyEvent.KEYCODE_BUTTON_R2)
                && !syntheticOutputActive(KC_RT)) {
            rTriggerDown = emitTrigger(Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS)), KC_RT, rTriggerDown);
        }
        emitHat(event);
        return true;
    }

    private int mapKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return KC_DPAD_LEFT;
            case KeyEvent.KEYCODE_DPAD_UP: return KC_DPAD_UP;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return KC_DPAD_RIGHT;
            case KeyEvent.KEYCODE_DPAD_DOWN: return KC_DPAD_DOWN;
            case KeyEvent.KEYCODE_BUTTON_A: return KC_A;
            case KeyEvent.KEYCODE_BUTTON_B: return KC_B;
            case KeyEvent.KEYCODE_BUTTON_X: return KC_X;
            case KeyEvent.KEYCODE_BUTTON_Y: return KC_Y;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return KC_BACK;
            case KeyEvent.KEYCODE_BUTTON_START: return KC_START;
            case KeyEvent.KEYCODE_BUTTON_L1: return KC_LB;
            case KeyEvent.KEYCODE_BUTTON_R1: return KC_RB;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return KC_L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return KC_R3;
            case KeyEvent.KEYCODE_BUTTON_L2: return KC_LT;
            case KeyEvent.KEYCODE_BUTTON_R2: return KC_RT;
            default: return -1;
        }
    }

    private boolean emitTrigger(float value, int code, boolean wasDown) {
        float deadzone = controllerConfig.triggerDeadzone / 100f;
        float normalized = value <= deadzone ? 0f
                : Math.min(1f, (value - deadzone) / (1f - deadzone));
        normalized = Math.min(1f,
                normalized * controllerConfig.triggerSensitivity / 100f);
        int analog = Math.round(normalized * 32767f);
        boolean down = analog > 0;
        if (down != wasDown || axisValue[code] != analog) {
            axisValue[code] = analog;
            core.key_event(code, down, analog);
        }
        return down;
    }

    private void processStickSample(MotionEvent event, int historyPosition) {
        float leftX = axisValue(event, MotionEvent.AXIS_X, historyPosition);
        float leftY = axisValue(event, MotionEvent.AXIS_Y, historyPosition);
        boolean hasZRZ = hasAxis(event, MotionEvent.AXIS_Z)
                && hasAxis(event, MotionEvent.AXIS_RZ);
        boolean hasRXRY = hasAxis(event, MotionEvent.AXIS_RX)
                && hasAxis(event, MotionEvent.AXIS_RY);
        int rightXAxis = hasZRZ ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX;
        int rightYAxis = hasZRZ ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY;
        float rightX = axisValue(event, rightXAxis, historyPosition);
        float rightY = axisValue(event, rightYAxis, historyPosition);
        if (hasZRZ && hasRXRY) {
            float alternateX = axisValue(event, MotionEvent.AXIS_RX, historyPosition);
            float alternateY = axisValue(event, MotionEvent.AXIS_RY, historyPosition);
            if (alternateX * alternateX + alternateY * alternateY
                    > rightX * rightX + rightY * rightY + 0.0001f) {
                rightXAxis = MotionEvent.AXIS_RX;
                rightYAxis = MotionEvent.AXIS_RY;
                rightX = alternateX;
                rightY = alternateY;
            }
        }

        if (controllerConfig.left.swap) {
            float swapped = leftX;
            leftX = leftY;
            leftY = swapped;
        }
        if (controllerConfig.left.invertX) leftX = -leftX;
        if (controllerConfig.left.invertY) leftY = -leftY;
        if (controllerConfig.right.swap) {
            float swapped = rightX;
            rightX = rightY;
            rightY = swapped;
        }
        if (controllerConfig.right.invertX) rightX = -rightX;
        if (controllerConfig.right.invertY) rightY = -rightY;

        float leftDeadzone = stickDeadzone(event, MotionEvent.AXIS_X, MotionEvent.AXIS_Y);
        float rightDeadzone = stickDeadzone(event, rightXAxis, rightYAxis);
        float[] left = shapeStick(leftX, leftY, leftDeadzone,
                controllerConfig.left, 1f);
        float[] right = shapeStick(rightX, rightY, rightDeadzone,
                controllerConfig.right, harryPotterAimProfile ? 1.62f : 1f);

        if (smoothControls) {
            float leftAlpha = 0.72f;
            float rightAlpha = harryPotterAimProfile ? 0.56f : 0.68f;
            filteredLeftX = smoothAxis(filteredLeftX, left[0], leftAlpha);
            filteredLeftY = smoothAxis(filteredLeftY, left[1], leftAlpha);
            filteredRightX = smoothAxis(filteredRightX, right[0], rightAlpha);
            filteredRightY = smoothAxis(filteredRightY, right[1], rightAlpha);
        } else {
            filteredLeftX = left[0];
            filteredLeftY = left[1];
            filteredRightX = right[0];
            filteredRightY = right[1];
        }

        lastLeftX = filteredLeftX;
        lastLeftY = filteredLeftY;
        applyLeftControl(filteredLeftX, filteredLeftY);
        applyRightControl(filteredRightX, filteredRightY);
    }

    private void emitAxisPair(float axis, int negative, int positive, boolean invert) {
        float value = invert ? -axis : axis;
        if (value < 0f) {
            emitAxis(positive, false, 0);
            emitAxis(negative, true, (int) (value * 32768f));
        } else if (value > 0f) {
            emitAxis(negative, false, 0);
            emitAxis(positive, true, (int) (value * 32767f));
        } else {
            emitAxis(negative, false, 0);
            emitAxis(positive, false, 0);
        }
    }

    private float axisValue(MotionEvent event, int axis, int historyPosition) {
        return historyPosition < 0 ? event.getAxisValue(axis)
                : event.getHistoricalAxisValue(axis, historyPosition);
    }

    private boolean hasAxis(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        return device != null
                && device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null;
    }

    private float stickDeadzone(MotionEvent event, int xAxis, int yAxis) {
        InputDevice device = event.getDevice();
        float flat = MIN_STICK_DEADZONE;
        if (device != null) {
            InputDevice.MotionRange x = device.getMotionRange(
                    xAxis, InputDevice.SOURCE_JOYSTICK);
            InputDevice.MotionRange y = device.getMotionRange(
                    yAxis, InputDevice.SOURCE_JOYSTICK);
            if (x != null) flat = Math.max(flat, x.getFlat());
            if (y != null) flat = Math.max(flat, y.getFlat());
        }
        return Math.min(MAX_STICK_DEADZONE, flat);
    }

    private static float[] shapeStick(float x, float y, float hardwareDeadzone,
                                      ControllerSettings.Stick settings,
                                      float minimumCurve) {
        float rawMagnitude = (float) Math.sqrt(x * x + y * y);
        float deadzone = Math.max(hardwareDeadzone, settings.deadzone / 100f);
        if (rawMagnitude <= deadzone) return new float[]{0f, 0f};
        float outerEdge = Math.max(deadzone + 0.05f,
                1f - settings.outerDeadzone / 100f);
        float magnitude = Math.min(outerEdge, rawMagnitude);
        float normalized = (magnitude - deadzone) / (outerEdge - deadzone);
        float curve = Math.max(minimumCurve,
                ControllerSettings.curveExponent(settings.curve));
        curve += settings.acceleration / 100f * 1.15f;
        float shaped = (float) Math.pow(Math.max(0f, normalized), curve);
        float anti = settings.antiDeadzone / 100f;
        if (shaped > 0f && anti > 0f) shaped = anti + (1f - anti) * shaped;
        shaped = Math.min(1f, shaped * settings.sensitivity / 100f);
        float scale = shaped / rawMagnitude;
        return new float[]{clampAxis(x * scale), clampAxis(y * scale)};
    }

    private void applyLeftControl(float x, float y) {
        if (controllerConfig.dpadAsLeft && hasDpadDirection()) {
            releaseMappedOutputs(leftMappedOutputs);
            float dpadX = dpadRight == dpadLeft ? 0f : (dpadRight ? 1f : -1f);
            float dpadY = dpadDown == dpadUp ? 0f : (dpadDown ? 1f : -1f);
            emitAxisPair(dpadX, KC_L_LEFT, KC_L_RIGHT, false);
            emitAxisPair(dpadY, KC_L_UP, KC_L_DOWN, true);
            updateSprintAssist(dpadY < -0.85f);
            return;
        }
        if (controllerConfig.left.mode == ControllerSettings.MODE_ANALOG) {
            releaseMappedOutputs(leftMappedOutputs);
            emitAxisPair(x, KC_L_LEFT, KC_L_RIGHT, false);
            emitAxisPair(y, KC_L_UP, KC_L_DOWN, true);
            updateSprintAssist(y < -0.85f);
        } else {
            releaseAnalogStick(true);
            emitMappedStick(controllerConfig.left, x, y, leftMappedOutputs);
            updateSprintAssist(false);
        }
    }

    private void applyRightControl(float x, float y) {
        if (controllerConfig.right.mode == ControllerSettings.MODE_ANALOG) {
            releaseMappedOutputs(rightMappedOutputs);
            emitAxisPair(x, KC_R_LEFT, KC_R_RIGHT, false);
            emitAxisPair(y, KC_R_UP, KC_R_DOWN, true);
        } else {
            releaseAnalogStick(false);
            emitMappedStick(controllerConfig.right, x, y, rightMappedOutputs);
        }
    }

    private void emitMappedStick(ControllerSettings.Stick settings, float x, float y,
                                 boolean[] currentOutputs) {
        boolean[] desired = new boolean[DIGITAL_OUTPUT_COUNT];
        final float threshold = 0.48f;
        if (x < -threshold) desired[stickDirectionOutput(
                settings, ControllerSettings.DIR_LEFT)] = true;
        if (y < -threshold) desired[stickDirectionOutput(
                settings, ControllerSettings.DIR_UP)] = true;
        if (x > threshold) desired[stickDirectionOutput(
                settings, ControllerSettings.DIR_RIGHT)] = true;
        if (y > threshold) desired[stickDirectionOutput(
                settings, ControllerSettings.DIR_DOWN)] = true;
        for (int code = 0; code < desired.length; code++) {
            if (desired[code] == currentOutputs[code]) continue;
            currentOutputs[code] = desired[code];
            core.key_event(code, desired[code], VALUE_UNUSED);
        }
    }

    private static int stickDirectionOutput(ControllerSettings.Stick settings,
                                            int direction) {
        if (settings.mode == ControllerSettings.MODE_FACE) {
            switch (direction) {
                case ControllerSettings.DIR_LEFT: return KC_X;
                case ControllerSettings.DIR_UP: return KC_Y;
                case ControllerSettings.DIR_RIGHT: return KC_B;
                default: return KC_A;
            }
        }
        if (settings.mode == ControllerSettings.MODE_DPAD) {
            switch (direction) {
                case ControllerSettings.DIR_LEFT: return KC_DPAD_LEFT;
                case ControllerSettings.DIR_UP: return KC_DPAD_UP;
                case ControllerSettings.DIR_RIGHT: return KC_DPAD_RIGHT;
                default: return KC_DPAD_DOWN;
            }
        }
        int output = settings.customDirections[direction];
        return Math.max(0, Math.min(DIGITAL_OUTPUT_COUNT - 1, output));
    }

    private void releaseMappedOutputs(boolean[] outputs) {
        for (int code = 0; code < outputs.length; code++) {
            if (!outputs[code]) continue;
            outputs[code] = false;
            core.key_event(code, false, VALUE_UNUSED);
        }
    }

    private void releaseAnalogStick(boolean left) {
        if (left) {
            emitAxis(KC_L_LEFT, false, 0);
            emitAxis(KC_L_UP, false, 0);
            emitAxis(KC_L_RIGHT, false, 0);
            emitAxis(KC_L_DOWN, false, 0);
        } else {
            emitAxis(KC_R_LEFT, false, 0);
            emitAxis(KC_R_UP, false, 0);
            emitAxis(KC_R_RIGHT, false, 0);
            emitAxis(KC_R_DOWN, false, 0);
        }
    }

    private void updateSprintAssist(boolean forward) {
        boolean active = controllerConfig.sprintAssist && forward;
        if (active == sprintL3Down) return;
        sprintL3Down = active;
        emitCombinedL3();
    }

    private void emitCombinedL3() {
        boolean pressed = physicalL3Down || sprintL3Down;
        if (pressed == emittedL3Down) return;
        emittedL3Down = pressed;
        core.key_event(KC_L3, pressed, VALUE_UNUSED);
    }

    private boolean handleMacroKey(int keyCode, boolean pressed) {
        boolean matched = false;
        for (int index = 0; index < controllerConfig.macros.length; index++) {
            ControllerSettings.Macro macro = controllerConfig.macros[index];
            if (!macro.isReady() || macro.triggerKeyCode != keyCode) continue;
            matched = true;
            if (macroActive[index] == pressed) continue;
            macroActive[index] = pressed;
            for (int output : macro.outputs) {
                core.key_event(output, pressed, VALUE_UNUSED);
            }
        }
        return matched;
    }

    private boolean macroUsesPhysicalKey(int keyCode) {
        for (ControllerSettings.Macro macro : controllerConfig.macros) {
            if (macro.isReady() && macro.triggerKeyCode == keyCode) return true;
        }
        return false;
    }

    private boolean syntheticOutputActive(int outputCode) {
        if (outputCode >= 0 && outputCode < leftMappedOutputs.length
                && (leftMappedOutputs[outputCode] || rightMappedOutputs[outputCode])) {
            return true;
        }
        for (int index = 0; index < controllerConfig.macros.length; index++) {
            if (!macroActive[index]) continue;
            for (int output : controllerConfig.macros[index].outputs) {
                if (output == outputCode) return true;
            }
        }
        return false;
    }

    private void sendButtonHaptic(KeyEvent event) {
        if (!controllerConfig.rumbleEnabled) return;
        ControllerHaptics.click(this, event.getDeviceId(),
                controllerConfig.rumbleStrength);
    }

    private boolean updateDpadKey(int keyCode, boolean pressed) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dpadLeft = pressed; return true;
            case KeyEvent.KEYCODE_DPAD_UP: dpadUp = pressed; return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dpadRight = pressed; return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: dpadDown = pressed; return true;
            default: return false;
        }
    }

    private boolean hasDpadDirection() {
        return dpadLeft || dpadUp || dpadRight || dpadDown;
    }

    private static float smoothAxis(float previous, float target, float alpha) {
        if (Math.abs(target) < 0.0001f) return 0f;
        return clampAxis(previous + (target - previous) * alpha);
    }

    private static float clampAxis(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private static boolean isHarryPotterGame(String titleId, String gameName) {
        String id = titleId == null ? "" : titleId.trim().toUpperCase(Locale.ROOT);
        if ("45410819".equals(id) || "454107FA".equals(id)
                || "454108F9".equals(id) || "45410955".equals(id)) {
            return true;
        }
        String name = gameName == null ? "" : gameName.toLowerCase(Locale.ROOT);
        return name.contains("harry potter");
    }

    private void emitAxis(int code, boolean pressed, int value) {
        if (axisPressed[code] == pressed && axisValue[code] == value) return;
        axisPressed[code] = pressed;
        axisValue[code] = value;
        core.key_event(code, pressed, value);
    }

    private void emitHat(MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        boolean left = x < -0.5f;
        boolean right = x > 0.5f;
        boolean up = y < -0.5f;
        boolean down = y > 0.5f;
        if (controllerConfig.dpadAsLeft) {
            dpadLeft = left;
            dpadRight = right;
            dpadUp = up;
            dpadDown = down;
            applyLeftControl(lastLeftX, lastLeftY);
            return;
        }
        if (left != hatLeft) { core.key_event(KC_DPAD_LEFT, left, VALUE_UNUSED); hatLeft = left; }
        if (right != hatRight) { core.key_event(KC_DPAD_RIGHT, right, VALUE_UNUSED); hatRight = right; }
        if (up != hatUp) { core.key_event(KC_DPAD_UP, up, VALUE_UNUSED); hatUp = up; }
        if (down != hatDown) { core.key_event(KC_DPAD_DOWN, down, VALUE_UNUSED); hatDown = down; }
    }

    private static boolean isController(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
