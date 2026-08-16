package uk.co.blustudio.blubox360;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

final class AchievementPresentation extends Presentation {
    private final ProfileStore.Profile profile;
    private final String gameName;
    private final String titleId;
    private final boolean includeLocked;
    private AchievementPanelView panel;
    private AchievementData.Snapshot pendingSnapshot;
    private AchievementData.ProfileSummary pendingProfile;

    AchievementPresentation(Context context, Display display,
                            ProfileStore.Profile profile, String gameName,
                            String titleId, boolean includeLocked) {
        super(context, display, R.style.SecondScreenTheme);
        this.profile = profile;
        this.gameName = gameName;
        this.titleId = titleId;
        this.includeLocked = includeLocked;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        panel = new AchievementPanelView(getContext(), profile, gameName,
                titleId, includeLocked);
        setContentView(panel);
        if (pendingSnapshot != null) {
            panel.update(pendingSnapshot,
                    pendingProfile == null ? AchievementData.ProfileSummary.empty() : pendingProfile,
                    null);
        }
    }

    void update(AchievementData.Snapshot snapshot,
                AchievementData.ProfileSummary profileSummary,
                AchievementData.Achievement newlyUnlocked) {
        pendingSnapshot = snapshot;
        pendingProfile = profileSummary;
        if (panel != null) panel.update(snapshot, profileSummary, newlyUnlocked);
    }
}
