package uk.co.blustudio.blubox360;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AchievementPanelView extends LinearLayout {
    private final ProfileStore.Profile profile;
    private final boolean includeLocked;
    private final TextView profileScore;
    private final TextView fpsMetric;
    private final TextView temperatureMetric;
    private final TextView batteryMetric;
    private final TextView gameTitle;
    private final TextView gameId;
    private final TextView progressText;
    private final TextView scoreText;
    private final ProgressBar progressBar;
    private final LinearLayout achievementList;
    private final LinearLayout unlockBanner;
    private final ImageView unlockIcon;
    private final TextView unlockName;
    private final TextView unlockScore;
    private final TextView emptyText;
    private final Button allButton;
    private final Button unlockedButton;
    private final Button lockedButton;
    private AchievementData.Snapshot snapshot;
    private int filter;

    AchievementPanelView(Context context, ProfileStore.Profile profile,
                         String gameName, String titleId, boolean includeLocked) {
        super(context);
        this.profile = profile;
        this.includeLocked = includeLocked;
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(12));
        setBackgroundResource(R.drawable.library_background);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundResource(R.drawable.panel_card);
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.blubox_logo);
        logo.setContentDescription("BluBox 360 logo");
        header.addView(logo, new LayoutParams(dp(58), dp(58)));

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(VERTICAL);
        heading.setPadding(dp(9), 0, 0, 0);
        heading.addView(text("ACHIEVEMENTS", 10, color(R.color.cyan), true));
        heading.addView(text("BluBox 360", 22, Color.WHITE, true));
        header.addView(heading, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        AvatarView avatar = new AvatarView(context);
        avatar.setProfile(profile);
        header.addView(avatar, new LayoutParams(dp(52), dp(52)));
        LinearLayout player = new LinearLayout(context);
        player.setOrientation(VERTICAL);
        player.setPadding(dp(8), 0, 0, 0);
        TextView playerName = text(profile == null ? "Player 1" : profile.name,
                13, Color.WHITE, true);
        profileScore = text("0 G total", 10, color(R.color.cyan), true);
        player.addView(playerName);
        player.addView(profileScore);
        header.addView(player, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(76)));

        LinearLayout telemetry = new LinearLayout(context);
        telemetry.setOrientation(HORIZONTAL);
        telemetry.setGravity(Gravity.CENTER_VERTICAL);
        telemetry.setPadding(dp(8), dp(4), dp(8), dp(4));
        telemetry.setBackgroundResource(R.drawable.tile_blue);
        fpsMetric = metric("FPS", "0.0");
        temperatureMetric = metric("TEMP", "--°C");
        batteryMetric = metric("BATTERY", "--%");
        telemetry.addView(fpsMetric, new LayoutParams(0, dp(42), 1f));
        telemetry.addView(temperatureMetric, new LayoutParams(0, dp(42), 1f));
        telemetry.addView(batteryMetric, new LayoutParams(0, dp(42), 1f));
        LayoutParams telemetryParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(50));
        telemetryParams.topMargin = dp(6);
        addView(telemetry, telemetryParams);

        unlockBanner = new LinearLayout(context);
        unlockBanner.setOrientation(HORIZONTAL);
        unlockBanner.setGravity(Gravity.CENTER_VERTICAL);
        unlockBanner.setPadding(dp(12), dp(9), dp(12), dp(9));
        unlockBanner.setBackgroundResource(R.drawable.tile_blue);
        unlockBanner.setVisibility(GONE);
        unlockIcon = new ImageView(context);
        unlockIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        unlockBanner.addView(unlockIcon, new LayoutParams(dp(58), dp(58)));
        LinearLayout unlockCopy = new LinearLayout(context);
        unlockCopy.setOrientation(VERTICAL);
        unlockCopy.setPadding(dp(10), 0, dp(8), 0);
        unlockCopy.addView(text("ACHIEVEMENT UNLOCKED", 9, Color.rgb(192, 241, 255), true));
        unlockName = text("", 16, Color.WHITE, true);
        unlockCopy.addView(unlockName);
        unlockBanner.addView(unlockCopy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        unlockScore = text("", 18, Color.WHITE, true);
        unlockBanner.addView(unlockScore);
        LayoutParams unlockParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(78));
        unlockParams.topMargin = dp(8);
        addView(unlockBanner, unlockParams);

        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(VERTICAL);
        summary.setPadding(dp(14), dp(10), dp(14), dp(11));
        summary.setBackgroundResource(R.drawable.tile_blue);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout gameCopy = new LinearLayout(context);
        gameCopy.setOrientation(VERTICAL);
        gameTitle = text(gameName, 18, Color.WHITE, true);
        gameTitle.setMaxLines(1);
        gameId = text(titleId == null ? "" : titleId, 9, color(R.color.cyan), true);
        gameCopy.addView(gameTitle);
        gameCopy.addView(gameId);
        titleRow.addView(gameCopy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        progressText = text("Waiting for achievement data", 13, Color.WHITE, true);
        progressText.setGravity(Gravity.END);
        titleRow.addView(progressText);
        summary.addView(titleRow);
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LayoutParams barParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(12));
        barParams.topMargin = dp(8);
        summary.addView(progressBar, barParams);
        scoreText = text("0 / 0 G", 10, Color.rgb(220, 241, 255), true);
        scoreText.setGravity(Gravity.END);
        summary.addView(scoreText);
        LayoutParams summaryParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(8);
        addView(summary, summaryParams);

        LinearLayout filters = new LinearLayout(context);
        filters.setOrientation(HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        allButton = filterButton("All", 0);
        unlockedButton = filterButton("Unlocked", 1);
        lockedButton = filterButton("Locked", 2);
        filters.addView(allButton, new LayoutParams(0, dp(46), 1f));
        LayoutParams unlockedParams = new LayoutParams(0, dp(46), 1f);
        unlockedParams.setMargins(dp(6), 0, includeLocked ? dp(6) : 0, 0);
        filters.addView(unlockedButton, unlockedParams);
        if (includeLocked) filters.addView(lockedButton, new LayoutParams(0, dp(46), 1f));
        LayoutParams filterParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(54));
        filterParams.topMargin = dp(6);
        addView(filters, filterParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        achievementList = new LinearLayout(context);
        achievementList.setOrientation(VERTICAL);
        achievementList.setPadding(0, 0, 0, dp(14));
        emptyText = text("The game will create achievement data after its profile loads.",
                12, color(R.color.muted), false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(24), dp(34), dp(24), dp(34));
        achievementList.addView(emptyText);
        scroll.addView(achievementList);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        selectFilter(0);
    }

    void update(AchievementData.Snapshot snapshot,
                AchievementData.ProfileSummary profileSummary,
                AchievementData.Achievement newlyUnlocked) {
        this.snapshot = snapshot;
        gameTitle.setText(snapshot.gameName);
        gameId.setText(snapshot.titleId.isEmpty() ? "LOCAL XBOX 360 PROFILE" : snapshot.titleId);
        profileScore.setText(profileSummary.earnedScore + " G total");
        if (snapshot.available) {
            progressText.setText(snapshot.unlockedCount + " / " + snapshot.totalCount
                    + " unlocked  •  " + snapshot.percent() + "%");
            scoreText.setText(snapshot.earnedScore + " / " + snapshot.totalScore + " G");
            progressBar.setProgress(snapshot.percent(), true);
        } else {
            progressText.setText("Waiting for achievement data");
            scoreText.setText("Start playing to begin tracking");
            progressBar.setProgress(0, true);
        }
        renderAchievements();
        if (newlyUnlocked != null) showUnlock(newlyUnlocked);
    }

    void updateTelemetry(double fps, float temperatureC, int batteryPercent,
                         boolean charging, int thermalStatus) {
        fpsMetric.setText(String.format(Locale.US, "FPS  %.1f", Math.max(0d, fps)));
        temperatureMetric.setText(Float.isNaN(temperatureC)
                ? "TEMP  --°C"
                : String.format(Locale.US, "TEMP  %.0f°C", temperatureC));
        batteryMetric.setText(batteryPercent < 0
                ? "BATTERY  --%"
                : "BATTERY  " + batteryPercent + "%" + (charging ? " +" : ""));

        int normal = color(R.color.cyan);
        int warm = Color.rgb(255, 190, 84);
        int hot = Color.rgb(255, 104, 118);
        fpsMetric.setTextColor(fps > 0d && fps < 45d ? warm : normal);
        boolean severe = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
                || (!Float.isNaN(temperatureC) && temperatureC >= 70f);
        boolean elevated = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
                || (!Float.isNaN(temperatureC) && temperatureC >= 55f);
        temperatureMetric.setTextColor(severe ? hot : elevated ? warm : normal);
        batteryMetric.setTextColor(batteryPercent >= 0 && batteryPercent <= 15 ? hot : normal);
    }

    private void renderAchievements() {
        achievementList.removeAllViews();
        if (snapshot == null || !snapshot.available || snapshot.achievements.isEmpty()) {
            achievementList.addView(emptyText);
            return;
        }
        int shown = 0;
        for (AchievementData.Achievement achievement : snapshot.achievements) {
            if (!includeLocked && !achievement.unlocked) continue;
            if (filter == 1 && !achievement.unlocked) continue;
            if (filter == 2 && achievement.unlocked) continue;
            achievementList.addView(achievementRow(achievement));
            shown++;
        }
        if (shown == 0) {
            TextView none = text(filter == 1 ? "No achievements unlocked yet."
                            : "No locked achievements.",
                    12, color(R.color.muted), false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(20), dp(30), dp(20), dp(30));
            achievementList.addView(none);
        }
    }

    private View achievementRow(AchievementData.Achievement achievement) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(12), dp(8));
        row.setBackgroundResource(R.drawable.panel_card);
        row.setAlpha(achievement.unlocked ? 1f : 0.72f);

        FrameLayout iconFrame = new FrameLayout(getContext());
        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = decodeIcon(achievement.icon);
        if (bitmap != null) {
            icon.setImageBitmap(bitmap);
            iconFrame.addView(icon, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            TextView symbol = text(achievement.unlocked ? "✓" : "◇", 28,
                    achievement.unlocked ? color(R.color.cyan) : color(R.color.muted), true);
            symbol.setGravity(Gravity.CENTER);
            iconFrame.addView(symbol, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        row.addView(iconFrame, new LayoutParams(dp(64), dp(64)));

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        copy.setPadding(dp(11), 0, dp(8), 0);
        TextView name = text(achievement.name, 14, Color.WHITE, true);
        name.setMaxLines(1);
        copy.addView(name);
        TextView description = text(achievement.description(), 10,
                achievement.unlocked ? Color.rgb(213, 234, 255) : color(R.color.muted), false);
        description.setMaxLines(2);
        copy.addView(description);
        if (achievement.unlocked && achievement.unlockTimeMs > 0) {
            String date = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                    .format(new Date(achievement.unlockTimeMs));
            copy.addView(text("Unlocked " + date, 8, color(R.color.cyan), true));
        }
        row.addView(copy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView score = text(achievement.score + " G", 14,
                achievement.unlocked ? color(R.color.cyan) : color(R.color.muted), true);
        score.setGravity(Gravity.CENTER);
        row.addView(score, new LayoutParams(dp(58), dp(48)));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(84));
        params.bottomMargin = dp(5);
        row.setLayoutParams(params);
        return row;
    }

    private void showUnlock(AchievementData.Achievement achievement) {
        Bitmap bitmap = decodeIcon(achievement.icon);
        if (bitmap != null) unlockIcon.setImageBitmap(bitmap);
        else unlockIcon.setImageResource(R.drawable.blubox_logo);
        unlockName.setText(achievement.name);
        unlockScore.setText("+" + achievement.score + " G");
        unlockBanner.animate().cancel();
        unlockBanner.setAlpha(0f);
        unlockBanner.setVisibility(VISIBLE);
        unlockBanner.animate().alpha(1f).setDuration(220L).start();
        unlockBanner.removeCallbacks(hideUnlock);
        unlockBanner.postDelayed(hideUnlock, 6500L);
    }

    private final Runnable hideUnlock = this::hideUnlockBanner;

    private void hideUnlockBanner() {
        unlockBanner.animate().alpha(0f).setDuration(300L)
                .withEndAction(() -> unlockBanner.setVisibility(GONE)).start();
    }

    private Button filterButton(String label, int value) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(view -> selectFilter(value));
        return button;
    }

    private TextView metric(String label, String value) {
        TextView view = text(label + "  " + value, 12, color(R.color.cyan), true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        return view;
    }

    private void selectFilter(int value) {
        filter = value;
        allButton.setBackgroundResource(value == 0
                ? R.drawable.button_primary : R.drawable.button_secondary);
        unlockedButton.setBackgroundResource(value == 1
                ? R.drawable.button_primary : R.drawable.button_secondary);
        lockedButton.setBackgroundResource(value == 2
                ? R.drawable.button_primary : R.drawable.button_secondary);
        renderAchievements();
    }

    private Bitmap decodeIcon(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int color(int resource) {
        return getContext().getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
