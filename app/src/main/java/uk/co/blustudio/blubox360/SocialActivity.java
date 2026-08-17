package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class SocialActivity extends Activity {
    private ProfileStore profileStore;
    private SocialStore socialStore;
    private ProfileStore.Profile activeProfile;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        profileStore = new ProfileStore(this);
        socialStore = new SocialStore(this);
        activeProfile = profileStore.getActive();
        enterImmersive();
        setContentView(buildRoot());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        profileStore.reload();
        activeProfile = profileStore.getActive();
        render();
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.library_background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackgroundColor(Color.rgb(7, 18, 36));

        Button back = button("‹ Back", false);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(110), dp(52)));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(14), 0, 0, 0);
        title.addView(label("Friends & Online", 22, Color.WHITE, true));
        title.addView(label("BluBox Social Beta", 11, getColor(R.color.cyan), true));
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void render() {
        if (content == null || activeProfile == null) return;
        content.removeAllViews();

        String tag = socialStore.bluTag(activeProfile);
        boolean onlineEnabled = socialStore.onlineEnabled(activeProfile.id);
        String presence = socialStore.presence(activeProfile.id);

        LinearLayout profileCard = card();
        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);

        AvatarView avatar = new AvatarView(this);
        avatar.setProfile(activeProfile);
        profileRow.addView(avatar, new LinearLayout.LayoutParams(dp(76), dp(76)));

        LinearLayout profileCopy = new LinearLayout(this);
        profileCopy.setOrientation(LinearLayout.VERTICAL);
        profileCopy.setPadding(dp(14), 0, 0, 0);
        profileCopy.addView(label(activeProfile.name, 20, Color.WHITE, true));
        profileCopy.addView(label("Online name: " + activeProfile.name, 11, Color.WHITE, false));
        profileCopy.addView(label(tag, 13, getColor(R.color.cyan), true));
        profileCopy.addView(label(onlineEnabled ? presence : "Online services off",
                11, onlineEnabled ? Color.rgb(96, 224, 142) : getColor(R.color.muted), false));
        profileRow.addView(profileCopy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout profileActions = new LinearLayout(this);
        profileActions.setOrientation(LinearLayout.VERTICAL);
        Button editName = button("Edit Name", true);
        editName.setOnClickListener(v -> editOnlineName());
        profileActions.addView(editName, new LinearLayout.LayoutParams(dp(142), dp(48)));
        Button copy = button("Copy BluTag", false);
        copy.setOnClickListener(v -> copyBluTag(tag));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(142), dp(48));
        copyParams.topMargin = dp(6);
        profileActions.addView(copy, copyParams);
        profileRow.addView(profileActions, new LinearLayout.LayoutParams(dp(142),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        profileCard.addView(profileRow);

        TextView explain = label("Your BluBox profile name is now your online name. Editing it here also updates your profile name and your BluTag name automatically. The numbers at the end of your BluTag stay linked to this profile.",
                11, getColor(R.color.muted), false);
        explain.setPadding(0, dp(10), 0, 0);
        profileCard.addView(explain);
        content.addView(profileCard, matchWrap(dp(0), dp(12)));

        LinearLayout onlineCard = card();
        onlineCard.addView(label("BLUBOX ONLINE BETA", 12, getColor(R.color.cyan), true));
        TextView serviceState = label(onlineEnabled ? "Online presence enabled" : "Online presence disabled",
                17, Color.WHITE, true);
        serviceState.setPadding(0, dp(8), 0, dp(4));
        onlineCard.addView(serviceState);
        onlineCard.addView(label("This beta adds friends, BluTags, matching profile/online names and presence controls. Internet friend syncing and game invites still need the BluBox online server before they go live.",
                11, getColor(R.color.muted), false));

        LinearLayout onlineButtons = new LinearLayout(this);
        onlineButtons.setOrientation(LinearLayout.HORIZONTAL);
        onlineButtons.setGravity(Gravity.CENTER_VERTICAL);
        onlineButtons.setPadding(0, dp(12), 0, 0);

        Button onlineToggle = button(onlineEnabled ? "Turn Online Off" : "Turn Online On", true);
        onlineToggle.setOnClickListener(v -> {
            socialStore.setOnlineEnabled(activeProfile.id, !onlineEnabled);
            render();
        });
        onlineButtons.addView(onlineToggle, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button status = button(presence, false);
        status.setEnabled(onlineEnabled);
        status.setOnClickListener(v -> choosePresence());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(dp(150), dp(52));
        statusParams.setMargins(dp(10), 0, 0, 0);
        onlineButtons.addView(status, statusParams);
        onlineCard.addView(onlineButtons);
        content.addView(onlineCard, matchWrap(dp(0), dp(12)));

        LinearLayout addCard = card();
        addCard.addView(label("ADD FRIEND", 12, getColor(R.color.cyan), true));
        addCard.addView(label("Add somebody using their BluTag", 17, Color.WHITE, true));
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(0, dp(10), 0, 0);
        EditText input = new EditText(this);
        input.setHint("Example: Macca#3600");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(getColor(R.color.muted));
        input.setBackgroundResource(R.drawable.button_secondary);
        input.setPadding(dp(14), 0, dp(14), 0);
        addRow.addView(input, new LinearLayout.LayoutParams(0, dp(54), 1f));
        Button add = button("Add Friend", true);
        add.setOnClickListener(v -> {
            SocialStore.AddResult result = socialStore.addFriend(activeProfile.id, input.getText().toString());
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
            if (result.success) render();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(140), dp(54));
        addParams.setMargins(dp(10), 0, 0, 0);
        addRow.addView(add, addParams);
        addCard.addView(addRow);
        content.addView(addCard, matchWrap(dp(0), dp(12)));

        List<SocialStore.Friend> friends = socialStore.friends(activeProfile.id);
        TextView heading = label("FRIENDS  " + friends.size(), 13, Color.WHITE, true);
        heading.setPadding(dp(4), dp(6), 0, dp(8));
        content.addView(heading);

        if (friends.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER);
            TextView icon = label("☺", 42, getColor(R.color.cyan), false);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon);
            TextView emptyTitle = label("No friends added yet", 17, Color.WHITE, true);
            emptyTitle.setGravity(Gravity.CENTER);
            empty.addView(emptyTitle);
            TextView emptyCopy = label("Add a BluTag above to test the new friends list.", 11,
                    getColor(R.color.muted), false);
            emptyCopy.setGravity(Gravity.CENTER);
            empty.addView(emptyCopy);
            content.addView(empty, matchWrap(dp(0), dp(8)));
        } else {
            for (SocialStore.Friend friend : friends) {
                content.addView(friendRow(friend), matchWrap(dp(0), dp(8)));
            }
        }
    }

    private void editOnlineName() {
        if (activeProfile == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(activeProfile.name);
        input.setSelection(input.getText().length());
        input.setHint("BluBox name");
        input.setSelectAllOnFocus(false);

        new AlertDialog.Builder(this)
                .setTitle("Edit BluBox name")
                .setMessage("This name is used for your local profile and your BluBox online name.")
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", (dialog, which) -> {
                    ProfileStore.Profile updated = activeProfile.copy();
                    updated.name = input.getText().toString();
                    ProfileStore.SaveResult result = profileStore.save(updated);
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                    if (!result.success || result.profile == null) return;
                    activeProfile = result.profile;
                    CoreConfig.syncActiveProfileAsync(this, result.profile, null);
                    render();
                })
                .show();
    }

    private View friendRow(SocialStore.Friend friend) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = label(friend.displayName.isEmpty() ? "?"
                : friend.displayName.substring(0, 1).toUpperCase(), 24, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.tile_blue);
        line.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(label(friend.displayName, 16, Color.WHITE, true));
        copy.addView(label(friend.bluTag, 11, getColor(R.color.cyan), false));
        copy.addView(label(friend.status + " • " + friend.playing, 10, getColor(R.color.muted), false));
        line.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button remove = button("Remove", false);
        remove.setOnClickListener(v -> confirmRemove(friend));
        line.addView(remove, new LinearLayout.LayoutParams(dp(108), dp(48)));
        row.addView(line);
        return row;
    }

    private void choosePresence() {
        String[] choices = {SocialStore.STATUS_ONLINE, SocialStore.STATUS_AWAY, SocialStore.STATUS_OFFLINE};
        new AlertDialog.Builder(this)
                .setTitle("Online status")
                .setItems(choices, (dialog, which) -> {
                    socialStore.setPresence(activeProfile.id, choices[which]);
                    render();
                })
                .show();
    }

    private void confirmRemove(SocialStore.Friend friend) {
        new AlertDialog.Builder(this)
                .setTitle("Remove friend?")
                .setMessage("Remove " + friend.displayName + " from your BluBox friends?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    socialStore.removeFriend(activeProfile.id, friend.id);
                    render();
                })
                .show();
    }

    private void copyBluTag(String tag) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("BluBox BluTag", tag));
        Toast.makeText(this, "BluTag copied", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.tile_blue);
        return card;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setBackgroundResource(primary ? R.drawable.button_primary : R.drawable.button_secondary);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
