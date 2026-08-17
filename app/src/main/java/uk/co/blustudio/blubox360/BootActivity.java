package uk.co.blustudio.blubox360;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BootActivity extends Activity {
    private static final long BOOT_DURATION_MS = 1900L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StartupSound startupSound;
    private boolean opened;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        enterImmersive();

        if (state == null && AppPreferences.startupSound(this)) {
            startupSound = new StartupSound(this);
            startupSound.play();
        }

        if (!AppPreferences.bootAnimation(this)) {
            openLibrary();
            return;
        }
        setContentView(buildBootView());
    }

    private View buildBootView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.library_background);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(20), dp(24), dp(20));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blubox_logo);
        logo.setContentDescription("BluBox 360 logo");
        logo.setAlpha(0f);
        logo.setScaleX(0.72f);
        logo.setScaleY(0.72f);
        content.addView(logo, new LinearLayout.LayoutParams(dp(196), dp(196)));

        TextView title = text("BluBox 360", 34, Color.WHITE, true);
        title.setAlpha(0f);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView subtitle = text("XBOX 360 • ARM64", 12, getColor(R.color.cyan), true);
        subtitle.setAlpha(0f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(content, contentParams);

        TextView starting = text("STARTING EMULATION CORE", 10,
                getColor(R.color.muted), true);
        starting.setGravity(Gravity.CENTER);
        starting.setLetterSpacing(0.16f);
        starting.setAlpha(0f);
        FrameLayout.LayoutParams startingParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(40),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        startingParams.bottomMargin = dp(30);
        root.addView(starting, startingParams);

        root.post(() -> {
            AnimatorSet entrance = new AnimatorSet();
            entrance.playTogether(
                    ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.72f, 1f),
                    ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.72f, 1f),
                    ObjectAnimator.ofFloat(logo, View.ROTATION, -5f, 0f),
                    ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, dp(12), 0f));
            entrance.setDuration(650L);
            entrance.setInterpolator(new DecelerateInterpolator());
            entrance.start();
            subtitle.animate().alpha(1f).setStartDelay(360L).setDuration(430L).start();
            starting.animate().alpha(1f).setStartDelay(720L).setDuration(420L).start();
            logo.animate().scaleX(1.04f).scaleY(1.04f)
                    .setStartDelay(880L).setDuration(520L).withEndAction(() ->
                            logo.animate().scaleX(1f).scaleY(1f).setDuration(230L).start()).start();
        });
        handler.postDelayed(this::openLibrary, BOOT_DURATION_MS);
        return root;
    }

    private void openLibrary() {
        if (opened || isFinishing()) return;
        opened = true;
        startActivity(new Intent(this, MainActivity.class));
        startActivity(new Intent(this, UpdateActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        // The startup player releases itself when the chime finishes. Do not stop it here,
        // because BootActivity closes immediately when the boot animation is disabled.
        startupSound = null;
        super.onDestroy();
    }
}
