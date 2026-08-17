package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class ProfileActivity extends Activity {
    private static final int REQ_PROFILE_PHOTO = 610;

    private ProfileStore store;
    private GridLayout profileGrid;
    private TextView activeProfileLabel;
    private TextView editorTitle;
    private EditText gamertagInput;
    private AvatarView editorAvatar;
    private Button deleteButton;
    private Button removePhotoButton;
    private ProfileStore.Profile draft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);
        enterImmersiveMode();

        store = new ProfileStore(this);
        profileGrid = findViewById(R.id.profileGrid);
        activeProfileLabel = findViewById(R.id.activeProfileLabel);
        editorTitle = findViewById(R.id.profileEditorTitle);
        gamertagInput = findViewById(R.id.gamertagInput);
        editorAvatar = findViewById(R.id.profileEditorAvatar);
        deleteButton = findViewById(R.id.deleteProfileButton);
        removePhotoButton = findViewById(R.id.removePhotoButton);

        findViewById(R.id.profileBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.newProfileButton).setOnClickListener(v -> beginNewProfile());
        findViewById(R.id.choosePhotoButton).setOnClickListener(v -> chooseProfilePhoto());
        removePhotoButton.setOnClickListener(v -> removeProfilePhoto());
        findViewById(R.id.saveProfileButton).setOnClickListener(v -> saveDraft());
        deleteButton.setOnClickListener(v -> confirmDelete());

        renderProfiles();
        showEditor(store.getActive());
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @SuppressWarnings("deprecation")
    private void enterImmersiveMode() {
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
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void renderProfiles() {
        profileGrid.removeAllViews();
        List<ProfileStore.Profile> profiles = store.getProfiles();
        ProfileStore.Profile active = store.getActive();
        activeProfileLabel.setText(active.name + " • " + profiles.size() + "/" +
                ProfileStore.MAX_PROFILES + " profiles");

        int availableWidth = Math.max(dp(360), getResources().getDisplayMetrics().widthPixels - dp(390));
        int columns = Math.max(2, Math.min(3, availableWidth / dp(190)));
        int cardWidth = (availableWidth - dp(12) * columns) / columns;
        profileGrid.setColumnCount(columns);
        for (ProfileStore.Profile profile : profiles) {
            profileGrid.addView(createProfileCard(profile, cardWidth,
                    profile.id.equals(active.id)));
        }
    }

    private View createProfileCard(ProfileStore.Profile profile, int cardWidth, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.cover_card);
        card.setSelected(active);
        card.setClickable(true);
        card.setFocusable(true);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth;
        params.height = dp(246);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(params);

        AvatarView avatar = new AvatarView(this);
        avatar.setProfile(profile);
        card.addView(avatar, new LinearLayout.LayoutParams(dp(118), dp(118)));

        TextView name = new TextView(this);
        name.setText(profile.name);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(15);
        name.setGravity(android.view.Gravity.CENTER);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(5);
        card.addView(name, nameParams);

        TextView state = new TextView(this);
        String saveStatus = CoreConfig.hasNativeProfile(this, profile.id)
                ? "Xbox 360 saves ready" : "Profile will be created";
        state.setText((active ? "ACTIVE • " : "") + saveStatus);
        state.setTextColor(getColor(active ? R.color.cyan : R.color.muted));
        state.setTextSize(9);
        state.setGravity(android.view.Gravity.CENTER);
        card.addView(state, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button use = new Button(this);
        use.setText(active ? "Active" : "Use profile");
        use.setEnabled(!active);
        use.setTextSize(11);
        use.setOnClickListener(v -> {
            store.setActive(profile.id);
            CoreConfig.syncActiveProfileAsync(this, profile, null);
            showEditor(profile);
            renderProfiles();
        });
        card.addView(use, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        card.setOnClickListener(v -> showEditor(profile));
        return card;
    }

    private void showEditor(ProfileStore.Profile profile) {
        if (profile == null) return;
        draft = profile.copy();
        boolean exists = store.getById(draft.id) != null;
        editorTitle.setText(exists ? "Edit profile" : "Create profile");
        gamertagInput.setText(draft.name);
        gamertagInput.setSelection(gamertagInput.getText().length());
        deleteButton.setVisibility(exists ? View.VISIBLE : View.GONE);
        updatePreview();
    }

    private void beginNewProfile() {
        if (store.getProfiles().size() >= ProfileStore.MAX_PROFILES) {
            Toast.makeText(this, "Maximum of " + ProfileStore.MAX_PROFILES + " profiles reached.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        showEditor(store.newDraft());
        gamertagInput.requestFocus();
    }

    private void chooseProfilePhoto() {
        if (draft == null) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PROFILE_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PROFILE_PHOTO || resultCode != RESULT_OK ||
                data == null || data.getData() == null || draft == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
        if (AvatarView.importProfilePhoto(this, draft.id, uri)) {
            updatePreview();
            renderProfiles();
            Toast.makeText(this, "Profile photo added.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "That photo could not be loaded.", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeProfilePhoto() {
        if (draft == null) return;
        if (AvatarView.removeProfilePhoto(this, draft.id)) {
            updatePreview();
            renderProfiles();
            Toast.makeText(this, "Profile photo removed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePreview() {
        if (draft != null) {
            editorAvatar.setProfile(draft);
            removePhotoButton.setEnabled(AvatarView.hasProfilePhoto(this, draft.id));
        }
    }

    private void saveDraft() {
        if (draft == null) return;
        boolean isNew = store.getById(draft.id) == null;
        draft.name = gamertagInput.getText().toString();
        ProfileStore.SaveResult result = store.save(draft);
        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
        if (!result.success) return;
        if (isNew) store.setActive(result.profile.id);
        CoreConfig.syncActiveProfileAsync(this, result.profile,
                message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
        renderProfiles();
        showEditor(result.profile);
    }

    private void confirmDelete() {
        if (draft == null || store.getById(draft.id) == null) return;
        if (store.getProfiles().size() <= 1) {
            Toast.makeText(this, "Keep at least one BluBox profile.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete " + draft.name + "?")
                .setMessage("The profile entry and profile photo will be removed. Its HDD save file is kept on the device for safety.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete profile", (dialog, which) -> {
                    String id = draft.id;
                    if (store.delete(id)) {
                        AvatarView.removeProfilePhoto(this, id);
                        Toast.makeText(this, "Profile removed. Save data was not erased.",
                                Toast.LENGTH_SHORT).show();
                        renderProfiles();
                        showEditor(store.getActive());
                    }
                })
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isGameControllerSource(event.getSource()) && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
                finish();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
                View focused = getCurrentFocus();
                if (focused != null && focused.performClick()) return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isGameControllerSource(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
