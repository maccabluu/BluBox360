package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AchievementsActivity extends Activity {
    private ProfileStore profileStore;
    private GameStore gameStore;
    private ProfileStore.Profile activeProfile;
    private TextView totalScore;
    private TextView totalProgress;
    private ProgressBar progressBar;
    private LinearLayout content;
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        profileStore = new ProfileStore(this);
        gameStore = new GameStore(this);
        activeProfile = profileStore.getActive();
        enterImmersive();
        setContentView(buildView());
        loadAchievements();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        if (profileStore != null) {
            profileStore.reload();
            gameStore.reload();
            activeProfile = profileStore.getActive();
            loadAchievements();
        }
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundResource(R.drawable.library_background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(7), dp(10), dp(7));
        header.setBackgroundResource(R.drawable.panel_card);
        Button back = button("← Back", false);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(100), dp(50)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blubox_logo);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        logoParams.setMargins(dp(10), 0, dp(8), 0);
        header.addView(logo, logoParams);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("Achievements", 24, Color.WHITE, true));
        heading.addView(text("Local Xbox 360 profile progress", 10,
                getColor(R.color.cyan), true));
        header.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        AvatarView avatar = new AvatarView(this);
        avatar.setProfile(activeProfile);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView name = text(activeProfile.name, 13, Color.WHITE, true);
        name.setPadding(dp(8), 0, dp(8), 0);
        header.addView(name);
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(16), dp(10), dp(16), dp(10));
        summary.setBackgroundResource(R.drawable.tile_blue);
        LinearLayout summaryTop = new LinearLayout(this);
        summaryTop.setOrientation(LinearLayout.HORIZONTAL);
        totalProgress = text("Reading achievement history…", 14, Color.WHITE, true);
        summaryTop.addView(totalProgress, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        totalScore = text("0 G", 19, Color.WHITE, true);
        summaryTop.addView(totalScore);
        summary.addView(summaryTop);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12));
        progressParams.topMargin = dp(8);
        summary.addView(progressBar, progressParams);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(0, dp(9), 0, dp(8));
        root.addView(summary, summaryParams);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), 0, dp(2), dp(18));
        content.addView(text("Reading local Xbox 360 profile…", 13,
                getColor(R.color.muted), false));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void loadAchievements() {
        int generation = ++loadGeneration;
        ProfileStore.Profile profile = activeProfile;
        List<GameStore.Game> games = gameStore.games();
        new Thread(() -> {
            AchievementData.ProfileSummary result = AchievementData.readProfile(
                    this, profile.id, games);
            runOnUiThread(() -> {
                if (generation == loadGeneration && !isFinishing()) render(result);
            });
        }, "BluBox-achievement-history").start();
    }

    private void render(AchievementData.ProfileSummary summary) {
        int percent = summary.totalScore > 0
                ? Math.round(summary.earnedScore * 100f / summary.totalScore)
                : summary.totalCount > 0
                ? Math.round(summary.unlockedCount * 100f / summary.totalCount) : 0;
        totalProgress.setText(summary.unlockedCount + " / " + summary.totalCount
                + " unlocked  •  " + percent + "%");
        totalScore.setText(summary.earnedScore + " / " + summary.totalScore + " G");
        progressBar.setProgress(percent, true);
        content.removeAllViews();
        if (summary.titles.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No achievement history yet", 20, Color.WHITE, true));
            empty.addView(text("Play an Xbox 360 game once. BluBox reads achievement progress from your active local profile.",
                    12, getColor(R.color.muted), false));
            content.addView(empty, cardParams());
            return;
        }

        if (!summary.recent.isEmpty()) {
            content.addView(text("RECENT UNLOCKS", 11, getColor(R.color.cyan), true));
            int count = Math.min(3, summary.recent.size());
            for (int i = 0; i < count; i++) content.addView(recentRow(summary.recent.get(i)), cardParams());
            TextView gamesHeading = text("GAMES", 11, getColor(R.color.cyan), true);
            gamesHeading.setPadding(0, dp(12), 0, 0);
            content.addView(gamesHeading);
        }
        for (AchievementData.Snapshot title : summary.titles) {
            content.addView(titleRow(title, summary), cardParams());
        }
        TextView local = text("Progress is read from BluBox local profiles. Xbox Live is not contacted.",
                10, getColor(R.color.muted), false);
        local.setPadding(dp(4), dp(10), dp(4), 0);
        content.addView(local);
    }

    private View recentRow(AchievementData.Achievement achievement) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(12), dp(7));
        row.setBackgroundResource(R.drawable.panel_card);
        row.addView(icon(achievement.icon, true), new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        copy.addView(text(achievement.name, 14, Color.WHITE, true));
        copy.addView(text(achievement.gameName + "  •  " + formatDate(achievement.unlockTimeMs),
                9, getColor(R.color.muted), false));
        row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("+" + achievement.score + " G", 14,
                getColor(R.color.cyan), true));
        return row;
    }

    private View titleRow(AchievementData.Snapshot title,
                          AchievementData.ProfileSummary profileSummary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackgroundResource(R.drawable.panel_card);
        TextView badge = text(title.percent() + "%", 17, getColor(R.color.cyan), true);
        badge.setGravity(Gravity.CENTER);
        row.addView(badge, new LinearLayout.LayoutParams(dp(72), dp(58)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        TextView titleName = text(title.gameName, 15, Color.WHITE, true);
        titleName.setMaxLines(1);
        copy.addView(titleName);
        copy.addView(text(title.titleId + "  •  " + title.unlockedCount + " / "
                        + title.totalCount + " achievements",
                9, getColor(R.color.muted), false));
        ProgressBar gameProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        gameProgress.setMax(100);
        gameProgress.setProgress(title.percent());
        copy.addView(gameProgress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
        row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView score = text(title.earnedScore + " / " + title.totalScore + " G",
                12, getColor(R.color.cyan), true);
        score.setGravity(Gravity.CENTER);
        row.addView(score, new LinearLayout.LayoutParams(dp(112), dp(52)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("Open achievements for " + title.gameName);
        row.setOnClickListener(view -> showTitle(title, profileSummary));
        return row;
    }

    private void showTitle(AchievementData.Snapshot title,
                           AchievementData.ProfileSummary summary) {
        AchievementPanelView panel = new AchievementPanelView(this, activeProfile,
                title.gameName, title.titleId, true);
        panel.update(title, summary, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnDismissListener(ignored -> enterImmersive());
        dialog.show();
    }

    private ImageView icon(byte[] bytes, boolean fallbackLogo) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = null;
        if (bytes != null && bytes.length > 0) {
            try { bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
            catch (Throwable ignored) { }
        }
        if (bitmap != null) view.setImageBitmap(bitmap);
        else if (fallbackLogo) view.setImageResource(R.drawable.blubox_logo);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.panel_card);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        return params;
    }

    private String formatDate(long time) {
        if (time <= 0) return "Unlocked locally";
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(time));
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setBackgroundResource(primary ? R.drawable.button_primary : R.drawable.button_secondary);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    @SuppressWarnings("deprecation")
    private void enterImmersive() {
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
