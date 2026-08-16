package uk.co.blustudio.blubox360;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class AchievementTracker {
    interface Listener {
        void onAchievementUpdate(AchievementData.Snapshot snapshot,
                                 AchievementData.ProfileSummary profile,
                                 AchievementData.Achievement newlyUnlocked);
    }

    private final Context context;
    private final String profileId;
    private final String titleId;
    private final String gameName;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor;
    private AchievementData.Snapshot previous;
    private AchievementData.ProfileSummary profileSummary = AchievementData.ProfileSummary.empty();

    AchievementTracker(Context context, String profileId, String titleId,
                       String gameName, Listener listener) {
        this.context = context.getApplicationContext();
        this.profileId = profileId;
        this.titleId = titleId;
        this.gameName = gameName;
        this.listener = listener;
    }

    synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BluBox-achievements");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::poll, 0L, 900L, TimeUnit.MILLISECONDS);
    }

    synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        main.removeCallbacksAndMessages(null);
    }

    private void poll() {
        try {
            AchievementData.Snapshot current = AchievementData.normalizeTitleId(titleId).isEmpty()
                    ? AchievementData.readNewestTitle(context, profileId, gameName)
                    : AchievementData.readTitle(context, profileId, titleId, gameName);
            if (!AchievementData.changed(previous, current)) return;
            AchievementData.Achievement unlocked = AchievementData.newlyUnlocked(previous, current);
            previous = current;
            profileSummary = AchievementData.readProfile(context, profileId,
                    Collections.emptyList());
            main.post(() -> {
                if (listener != null) listener.onAchievementUpdate(
                        current, profileSummary, unlocked);
            });
        } catch (Throwable ignored) {
        }
    }
}
