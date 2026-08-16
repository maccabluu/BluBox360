package uk.co.blustudio.blubox360;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import xendroid.compose.Emulator;

public final class MainActivity extends Activity {
    private static final int REQ_GAMES = 400;
    private static final int REQ_STORAGE = 401;
    private static final int REQ_BACKUP = 402;
    private static final int REQ_RESTORE = 403;
    private static final int REQ_DRIVER = 404;
    private static final int REQ_GAME_FOLDER = 405;
    private static final int REQ_MOD = 406;
    private static final int REQ_COVER = 407;

    private static final int STORAGE_ACTION_NONE = 0;
    private static final int STORAGE_ACTION_FILES = 1;
    private static final int STORAGE_ACTION_FOLDER = 2;
    private static final int STORAGE_ACTION_REFRESH = 3;

    private static final int PAGE_HOME = 0;
    private static final int PAGE_GAMES = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final int PAGE_DIAGNOSTICS = 3;

    private static final String UI_PREFS = "blubox360_ui";
    private static final String PREF_VIEW_MODE = "library_view_mode";
    private static final String VIEW_SHELF = "shelf";
    private static final String VIEW_GRID = "grid";
    private static final String VIEW_LIST = "list";

    private GameStore gameStore;
    private ProfileStore profileStore;
    private FrameLayout pageHost;
    private FrameLayout drawerLayer;
    private View drawerPanel;
    private AvatarView profileAvatar;
    private AvatarView drawerProfileAvatar;
    private TextView profileName;
    private TextView drawerProfileName;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private Button viewModeButton;
    private Button refreshLibraryButton;
    private Button addGamesButton;
    private int currentPage = PAGE_HOME;
    private int settingsCategory;
    private int pendingStorageAction;
    private boolean libraryScanRunning;
    private boolean corePreparing;
    private boolean drawerOpen;
    private String searchQuery = "";
    private String libraryViewMode = VIEW_GRID;
    private String pendingCoverGameId;
    private final LruCache<String, Bitmap> coverCache =
            new LruCache<String, Bitmap>(16 * 1024) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount() / 1024);
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        gameStore = new GameStore(this);
        profileStore = new ProfileStore(this);
        libraryViewMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(PREF_VIEW_MODE, VIEW_GRID);
        if (!VIEW_SHELF.equals(libraryViewMode) && !VIEW_GRID.equals(libraryViewMode)
                && !VIEW_LIST.equals(libraryViewMode)) {
            libraryViewMode = VIEW_GRID;
        }
        enterImmersive();
        setContentView(buildRoot());
        updateProfileBadge();
        showPage(PAGE_HOME);
        prepareCoreAndProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        profileStore.reload();
        gameStore.reload();
        updateProfileBadge();
        showPage(currentPage);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && pendingStorageAction != STORAGE_ACTION_NONE
                && Environment.isExternalStorageManager()) {
            continuePendingStorageAction();
        }
    }

    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.library_background);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.addView(buildHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78)));
        pageHost = new FrameLayout(this);
        main.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(main, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        drawerLayer = buildDrawer();
        root.addView(drawerLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.rgb(7, 18, 36));

        TextView menu = label("☰", 28, Color.WHITE, false);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Open options menu");
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setBackgroundResource(R.drawable.button_secondary);
        menu.setOnClickListener(v -> openDrawer());
        header.addView(menu, new LinearLayout.LayoutParams(dp(58), dp(58)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blubox_logo);
        logo.setContentDescription("BluBox logo");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        logoParams.setMargins(dp(8), 0, dp(8), 0);
        header.addView(logo, logoParams);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        headerTitle = label("Library", 20, Color.WHITE, true);
        headerSubtitle = label("Total games: " + gameStore.games().size(), 11,
                getColor(R.color.muted), false);
        brand.addView(headerTitle);
        brand.addView(headerSubtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        boolean compact = getResources().getConfiguration().screenWidthDp < 900;
        viewModeButton = button(compact ? viewModeIcon() : viewModeIcon() + " View", false);
        viewModeButton.setContentDescription("Choose game display style");
        viewModeButton.setOnClickListener(v -> showViewModeChooser());
        int viewWidth = compact ? dp(58) : dp(104);
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(viewWidth, dp(54));
        viewParams.setMargins(dp(8), 0, 0, 0);
        header.addView(viewModeButton, viewParams);

        refreshLibraryButton = button(compact ? "↻" : "↻ Refresh", false);
        refreshLibraryButton.setContentDescription("Refresh the selected game folder");
        refreshLibraryButton.setOnClickListener(v -> refreshGameFolder());
        int refreshWidth = compact ? dp(58) : dp(116);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                refreshWidth, dp(54));
        refreshParams.setMargins(dp(8), 0, 0, 0);
        header.addView(refreshLibraryButton, refreshParams);

        addGamesButton = button(compact ? "+" : "+ Add games", true);
        addGamesButton.setContentDescription("Add a game file or choose a game folder");
        addGamesButton.setOnClickListener(v -> addGames());
        int addWidth = compact ? dp(58) : dp(154);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(addWidth, dp(54));
        addParams.setMargins(dp(8), 0, dp(8), 0);
        header.addView(addGamesButton, addParams);

        LinearLayout account = new LinearLayout(this);
        account.setOrientation(LinearLayout.HORIZONTAL);
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.setPadding(dp(8), 0, 0, 0);
        account.setClickable(true);
        account.setFocusable(true);
        account.setBackgroundResource(R.drawable.button_secondary);
        account.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        profileAvatar = new AvatarView(this);
        account.addView(profileAvatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        if (!compact) {
            profileName = label("Player 1", 12, Color.WHITE, true);
            profileName.setGravity(Gravity.CENTER_VERTICAL);
            profileName.setPadding(dp(7), 0, dp(8), 0);
            account.addView(profileName, new LinearLayout.LayoutParams(dp(92), dp(48)));
        }
        header.addView(account, new LinearLayout.LayoutParams(
                compact ? dp(58) : dp(150), dp(56)));
        return header;
    }

    private FrameLayout buildDrawer() {
        FrameLayout layer = new FrameLayout(this);
        layer.setVisibility(View.GONE);
        layer.setAlpha(1f);
        layer.setBackgroundColor(Color.argb(175, 0, 5, 18));
        layer.setClickable(true);
        layer.setFocusable(true);
        layer.setOnClickListener(v -> closeDrawer());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.drawer_panel);
        scroll.setClickable(true);
        scroll.setFocusable(true);
        scroll.setOnClickListener(v -> { });
        drawerPanel = scroll;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(20));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blubox_logo);
        identity.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(10), 0, 0, 0);
        names.addView(label("BluBox 360", 21, Color.WHITE, true));
        names.addView(label("ARM64 ALPHA", 10, getColor(R.color.cyan), true));
        identity.addView(names, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = label("×", 30, Color.WHITE, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close options menu");
        close.setClickable(true);
        close.setFocusable(true);
        close.setBackgroundResource(R.drawable.button_secondary);
        close.setOnClickListener(v -> closeDrawer());
        identity.addView(close, new LinearLayout.LayoutParams(dp(50), dp(50)));
        panel.addView(identity);

        LinearLayout account = new LinearLayout(this);
        account.setOrientation(LinearLayout.HORIZONTAL);
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.setPadding(dp(10), dp(8), dp(10), dp(8));
        account.setBackgroundResource(R.drawable.tile_blue);
        account.setClickable(true);
        account.setFocusable(true);
        account.setOnClickListener(v -> {
            closeDrawer();
            startActivity(new Intent(this, ProfileActivity.class));
        });
        drawerProfileAvatar = new AvatarView(this);
        account.addView(drawerProfileAvatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
        drawerProfileName = label("Player 1", 15, Color.WHITE, true);
        drawerProfileName.setPadding(dp(10), 0, 0, 0);
        account.addView(drawerProfileName, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68));
        accountParams.setMargins(0, dp(13), 0, dp(12));
        panel.addView(account, accountParams);

        TextView options = label("OPTIONS", 11, getColor(R.color.muted), true);
        options.setPadding(dp(4), 0, 0, dp(7));
        panel.addView(options);

        panel.addView(drawerItem("▦", "Library", "Browse and search games", () -> {
            closeDrawer();
            showPage(PAGE_GAMES);
        }));
        panel.addView(drawerItem("＋", "Add / Change Games", "Internal storage or microSD", () -> {
            closeDrawer();
            addGames();
        }));
        panel.addView(drawerItem("☺", "Profiles & Avatars", "Switch player or make an avatar", () -> {
            closeDrawer();
            startActivity(new Intent(this, ProfileActivity.class));
        }));
        panel.addView(drawerItem("★", "Achievements", "Progress, gamerscore and recent unlocks", () -> {
            closeDrawer();
            startActivity(new Intent(this, AchievementsActivity.class));
        }));
        panel.addView(drawerItem("▣", "Save Game Progress", "Automatic saves for each profile", () -> {
            closeDrawer();
            showSaveInfo();
        }));
        panel.addView(drawerItem("◉", "Controls", "AYN and Bluetooth controllers", () -> {
            closeDrawer();
            showControllerInfo();
        }));
        panel.addView(drawerItem("✦", "Settings", "App, performance, renderer and more", () -> {
            closeDrawer();
            showPage(PAGE_SETTINGS);
        }));
        panel.addView(drawerItem("◆", "Mods", "Import and manage Xenia patch mods", () -> {
            closeDrawer();
            settingsCategory = 7;
            showPage(PAGE_SETTINGS);
        }));
        panel.addView(drawerItem("⚙", "Patches & Compatibility", "Per-game fixes are enabled", () -> {
            closeDrawer();
            showPatchesInfo();
        }));
        panel.addView(drawerItem("ⓘ", "Diagnostics & About", "Storage, Vulkan and licences", () -> {
            closeDrawer();
            showPage(PAGE_DIAGNOSTICS);
        }));

        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        int maxWidth = Math.min(dp(370),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.72f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                maxWidth, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START);
        layer.addView(scroll, params);
        return layer;
    }

    private View drawerItem(String icon, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(7), dp(12), dp(7));
        row.setBackgroundResource(R.drawable.drawer_item);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + ". " + subtitle);
        row.setOnClickListener(v -> action.run());

        TextView symbol = label(icon, 23, getColor(R.color.cyan), true);
        symbol.setGravity(Gravity.CENTER);
        row.addView(symbol, new LinearLayout.LayoutParams(dp(42), dp(46)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(label(title, 14, Color.WHITE, true));
        copy.addView(label(subtitle, 9, getColor(R.color.muted), false));
        row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62));
        params.bottomMargin = dp(5);
        row.setLayoutParams(params);
        return row;
    }

    private void openDrawer() {
        if (drawerLayer == null || drawerOpen) return;
        drawerOpen = true;
        updateProfileBadge();
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.setAlpha(0f);
        int width = drawerPanel.getLayoutParams() == null
                ? dp(370) : drawerPanel.getLayoutParams().width;
        drawerPanel.setTranslationX(-width);
        drawerLayer.animate().alpha(1f).setDuration(170).start();
        drawerPanel.animate().translationX(0f).setDuration(210).start();
        drawerPanel.requestFocus();
    }

    private void closeDrawer() {
        if (drawerLayer == null || !drawerOpen) return;
        drawerOpen = false;
        int width = drawerPanel.getWidth() > 0 ? drawerPanel.getWidth() : dp(370);
        drawerPanel.animate().translationX(-width).setDuration(180).start();
        drawerLayer.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            drawerLayer.setVisibility(View.GONE);
            drawerLayer.setAlpha(1f);
            drawerPanel.setTranslationX(0f);
        }).start();
    }

    private void updateHeader() {
        if (headerTitle == null || headerSubtitle == null) return;
        boolean libraryPage = currentPage == PAGE_HOME || currentPage == PAGE_GAMES;
        if (currentPage == PAGE_SETTINGS) {
            headerTitle.setText("Settings");
            headerSubtitle.setText("App, performance, renderer, audio and controls");
        } else if (currentPage == PAGE_DIAGNOSTICS) {
            headerTitle.setText("Diagnostics");
            headerSubtitle.setText("Core, storage and compatibility");
        } else {
            headerTitle.setText("Library");
            int total = gameStore.games().size();
            headerSubtitle.setText("Total games: " + total);
        }
        if (viewModeButton != null) {
            boolean compact = getResources().getConfiguration().screenWidthDp < 900;
            viewModeButton.setText(compact ? viewModeIcon() : viewModeIcon() + " View");
            viewModeButton.setVisibility(libraryPage ? View.VISIBLE : View.GONE);
        }
        if (refreshLibraryButton != null) {
            refreshLibraryButton.setVisibility(libraryPage ? View.VISIBLE : View.GONE);
            refreshLibraryButton.setEnabled(!libraryScanRunning);
        }
        if (addGamesButton != null) {
            addGamesButton.setVisibility(libraryPage ? View.VISIBLE : View.GONE);
        }
    }

    private void showViewModeChooser() {
        String[] choices = {
                "3D Shelf — large horizontal cases",
                "3D Grid — cover wall",
                "Compact 3D List — details and smaller cases"
        };
        int selected = VIEW_SHELF.equals(libraryViewMode) ? 0
                : VIEW_LIST.equals(libraryViewMode) ? 2 : 1;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose game display")
                .setSingleChoiceItems(choices, selected, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int checked = dialog.getListView().getCheckedItemPosition();
                    setLibraryViewMode(checked == 0 ? VIEW_SHELF
                            : checked == 2 ? VIEW_LIST : VIEW_GRID);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void setLibraryViewMode(String mode) {
        libraryViewMode = mode;
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putString(PREF_VIEW_MODE, mode).apply();
        if (currentPage == PAGE_HOME || currentPage == PAGE_GAMES) {
            showPage(currentPage);
        } else if (currentPage == PAGE_SETTINGS) {
            showPage(PAGE_SETTINGS);
        }
        Toast.makeText(this, viewModeName() + " selected", Toast.LENGTH_SHORT).show();
    }

    private String viewModeIcon() {
        if (VIEW_SHELF.equals(libraryViewMode)) return "▱";
        if (VIEW_LIST.equals(libraryViewMode)) return "☷";
        return "▦";
    }

    private String viewModeName() {
        if (VIEW_SHELF.equals(libraryViewMode)) return "3D Shelf";
        if (VIEW_LIST.equals(libraryViewMode)) return "Compact 3D List";
        return "3D Grid";
    }

    private void showSaveInfo() {
        ProfileStore.Profile active = profileStore.getActive();
        new AlertDialog.Builder(this)
                .setTitle("Save Game Progress")
                .setMessage("Saving is automatic for " + active.name
                        + ". Each BluBox profile has separate Xbox 360 save data, so changing profiles does not overwrite another player's progress.")
                .setNegativeButton(android.R.string.ok, null)
                .setPositiveButton("Manage profiles", (dialog, which) ->
                        startActivity(new Intent(this, ProfileActivity.class)))
                .show();
    }

    private void showControllerInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Controls")
                .setMessage("BluBox automatically maps the AYN Thor controls and standard Android gamepads: both sticks, D-pad, A/B/X/Y, triggers, shoulder buttons, Start and Back.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showPatchesInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Patches & Compatibility")
                .setMessage("Bundled compatibility files and imported Xenia patch mods load at game launch. Support and performance still vary by title.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Open Mods", (dialog, which) -> {
                    settingsCategory = 7;
                    showPage(PAGE_SETTINGS);
                })
                .show();
    }

    private void showPage(int page) {
        if (pageHost == null) return;
        currentPage = page;
        pageHost.removeAllViews();
        switch (page) {
            case PAGE_GAMES: pageHost.addView(buildLibraryPage(false)); break;
            case PAGE_SETTINGS: pageHost.addView(buildSettingsPage()); break;
            case PAGE_DIAGNOSTICS: pageHost.addView(buildDiagnosticsPage()); break;
            default: pageHost.addView(buildLibraryPage(true)); break;
        }
        updateHeader();
    }

    private View buildLibraryPage(boolean home) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(12), dp(20), dp(14));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setText(searchQuery);
        search.setSelection(search.getText().length());
        search.setHint("Search games…");
        search.setHintTextColor(getColor(R.color.muted));
        search.setTextColor(Color.WHITE);
        search.setTextSize(15);
        search.setPadding(dp(17), 0, dp(17), 0);
        search.setBackgroundResource(R.drawable.search_bar);
        search.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(10));
        page.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        populateLibraryResults(results, home, searchQuery);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                searchQuery = value == null ? "" : value.toString();
                populateLibraryResults(results, home, searchQuery);
            }
        });
        return page;
    }

    private void populateLibraryResults(LinearLayout host, boolean home, String query) {
        host.removeAllViews();
        List<GameStore.Game> all = gameStore.games();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<GameStore.Game> shown = new ArrayList<>();
        for (GameStore.Game game : all) {
            String haystack = (game.name + " " + game.titleId + " " + game.path)
                    .toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || haystack.contains(needle)) shown.add(game);
        }
        if (home && needle.isEmpty() && shown.size() > 8) {
            shown = new ArrayList<>(shown.subList(0, 8));
        }

        String heading = needle.isEmpty() ? (home ? "Recently played" : "All games")
                : "Search results";
        TextView section = label(heading, 20, Color.WHITE, true);
        section.setPadding(0, dp(14), 0, dp(2));
        host.addView(section);
        TextView count = label(needle.isEmpty()
                        ? all.size() + " game" + (all.size() == 1 ? "" : "s") + " in your library"
                        : shown.size() + " match" + (shown.size() == 1 ? "" : "es"),
                11, getColor(R.color.muted), false);
        count.setPadding(0, 0, 0, dp(8));
        host.addView(count);

        if (shown.isEmpty()) {
            ScrollView scroller = new ScrollView(this);
            scroller.setFillViewport(true);
            scroller.addView(all.isEmpty() ? buildEmptyLibrary() : buildNoSearchResults());
            host.addView(scroller, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (VIEW_SHELF.equals(libraryViewMode)) {
            host.addView(buildShelfLibrary(shown), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (VIEW_LIST.equals(libraryViewMode)) {
            host.addView(buildCompactLibrary(shown), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ScrollView scroller = new ScrollView(this);
            scroller.setFillViewport(true);
            GridLayout grid = new GridLayout(this);
            int columns = Math.max(3, Math.min(7,
                    getResources().getDisplayMetrics().widthPixels / dp(190)));
            grid.setColumnCount(columns);
            grid.setUseDefaultMargins(false);
            int available = getResources().getDisplayMetrics().widthPixels - dp(44);
            int cardWidth = Math.max(dp(155), available / columns - dp(10));
            for (GameStore.Game game : shown) grid.addView(buildGameCard(game, cardWidth));
            scroller.addView(grid);
            host.addView(scroller, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
    }

    private View buildNoSearchResults() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(25), dp(35), dp(25), dp(35));
        empty.setBackgroundResource(R.drawable.panel_card);
        empty.addView(label("No games found", 22, Color.WHITE, true));
        TextView body = label("Try a different game name or title ID.", 12,
                getColor(R.color.muted), false);
        body.setPadding(0, dp(7), 0, 0);
        empty.addView(body);
        return empty;
    }

    private View buildEmptyLibrary() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(25), dp(35), dp(25), dp(35));
        empty.setBackgroundResource(R.drawable.panel_card);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blubox_logo);
        empty.addView(logo, new LinearLayout.LayoutParams(dp(118), dp(118)));
        TextView title = label("Add your first Xbox 360 game", 22, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        empty.addView(title);
        TextView body = label("BluBox reads your own ISO, default.xex, and ZAR files directly from storage. Games are not included.",
                12, getColor(R.color.muted), false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(50), dp(7), dp(50), dp(12));
        empty.addView(body);
        Button add = button("Choose game files", true);
        add.setOnClickListener(v -> addGames());
        empty.addView(add, new LinearLayout.LayoutParams(dp(220), dp(52)));
        return empty;
    }

    private View buildGameCard(GameStore.Game game, int width) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(9), dp(9), dp(8));
        card.setBackgroundResource(R.drawable.cover_card);
        card.setFocusable(true);
        card.setClickable(true);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = width;
        params.height = dp(274);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(params);

        GameCover3DView cover = new GameCover3DView(this);
        cover.setCover(gameCover(game), game.name);
        card.addView(cover, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView name = label(game.name, 13, Color.WHITE, true);
        name.setMaxLines(1);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(5), 0, 0);
        card.addView(name);
        String meta = game.titleId == null || game.titleId.isEmpty()
                ? formatLabel(game.path) : game.titleId + " • " + formatLabel(game.path);
        TextView details = label(meta, 9, getColor(R.color.cyan), true);
        details.setGravity(Gravity.CENTER);
        details.setMaxLines(1);
        card.addView(details);
        card.setContentDescription("Play " + game.name);
        card.setOnClickListener(v -> launchGame(game));
        card.setOnLongClickListener(v -> {
            showGameOptions(game);
            return true;
        });
        return card;
    }

    private View buildShelfLibrary(List<GameStore.Game> games) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setFillViewport(true);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.HORIZONTAL);
        shelf.setGravity(Gravity.CENTER_VERTICAL);
        shelf.setPadding(dp(7), dp(4), dp(18), dp(14));
        for (GameStore.Game game : games) shelf.addView(buildShelfCard(game));
        scroller.addView(shelf, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));
        return scroller;
    }

    private View buildShelfCard(GameStore.Game game) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackgroundResource(R.drawable.cover_card);
        card.setClickable(true);
        card.setFocusable(true);
        GameCover3DView cover = new GameCover3DView(this);
        cover.setCover(gameCover(game), game.name);
        card.addView(cover, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView name = label(game.name, 13, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        card.addView(name);
        TextView details = label(gameMeta(game), 9, getColor(R.color.cyan), true);
        details.setGravity(Gravity.CENTER);
        card.addView(details);
        card.setContentDescription("Play " + game.name);
        card.setOnClickListener(v -> launchGame(game));
        card.setOnLongClickListener(v -> {
            showGameOptions(game);
            return true;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(188),
                LinearLayout.LayoutParams.MATCH_PARENT);
        params.setMargins(dp(5), dp(4), dp(5), dp(4));
        card.setLayoutParams(params);
        return card;
    }

    private View buildCompactLibrary(List<GameStore.Game> games) {
        ScrollView scroller = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(2), 0, dp(12));
        for (GameStore.Game game : games) list.addView(buildCompactGameRow(game));
        scroller.addView(list);
        return scroller;
    }

    private View buildCompactGameRow(GameStore.Game game) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(12), dp(7));
        row.setBackgroundResource(R.drawable.cover_card);
        row.setClickable(true);
        row.setFocusable(true);
        GameCover3DView cover = new GameCover3DView(this);
        cover.setCover(gameCover(game), game.name);
        row.addView(cover, new LinearLayout.LayoutParams(dp(78), dp(106)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, dp(10), 0);
        copy.addView(label(game.name, 16, Color.WHITE, true));
        copy.addView(label(gameMeta(game), 10, getColor(R.color.cyan), true));
        String location = game.path == null ? "Storage" : new File(game.path).getParent();
        TextView path = label(location == null ? "Storage" : location, 9,
                getColor(R.color.muted), false);
        path.setMaxLines(1);
        path.setPadding(0, dp(4), 0, 0);
        copy.addView(path);
        row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button play = button("Play", true);
        play.setOnClickListener(v -> launchGame(game));
        row.addView(play, new LinearLayout.LayoutParams(dp(92), dp(48)));
        row.setContentDescription("Play " + game.name);
        row.setOnClickListener(v -> launchGame(game));
        row.setOnLongClickListener(v -> {
            showGameOptions(game);
            return true;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(122));
        params.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(params);
        return row;
    }

    private Bitmap gameCover(GameStore.Game game) {
        Bitmap custom = coverCache.get(game.id);
        if (custom == null && CoverArtStore.has(this, game.id)) {
            custom = CoverArtStore.load(this, game.id);
            if (custom != null) coverCache.put(game.id, custom);
        }
        if (custom != null) return custom;
        byte[] iconBytes = game.iconBytes();
        Bitmap bitmap = iconBytes == null ? null
                : BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
        return bitmap == null ? placeholderCover() : bitmap;
    }

    private String gameMeta(GameStore.Game game) {
        return game.titleId == null || game.titleId.isEmpty()
                ? formatLabel(game.path) : game.titleId + " • " + formatLabel(game.path);
    }

    private View buildSettingsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(12), dp(20), dp(14));
        page.addView(label("Settings", 27, Color.WHITE, true));
        page.addView(label("BluBox controls arranged like a console emulator settings hub.",
                12, getColor(R.color.muted), false));

        HorizontalScrollView tabScroller = new HorizontalScrollView(this);
        tabScroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(0, dp(10), dp(8), dp(8));
        String[] categories = {"App", "Performance", "Renderer", "Audio",
                "Controls", "Hotkeys", "Achievements", "Mods", "Storage"};
        List<Button> tabs = new ArrayList<>();
        FrameLayout contentHost = new FrameLayout(this);
        for (int i = 0; i < categories.length; i++) {
            final int index = i;
            Button tab = button(categories[i], false);
            tab.setOnClickListener(v -> {
                settingsCategory = index;
                populateSettingsCategory(contentHost, tabs);
            });
            tabs.add(tab);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(126), dp(48));
            params.setMargins(0, 0, dp(6), 0);
            tabRow.addView(tab, params);
        }
        tabScroller.addView(tabRow);
        page.addView(tabScroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));
        page.addView(contentHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        populateSettingsCategory(contentHost, tabs);
        return page;
    }

    private void populateSettingsCategory(FrameLayout host, List<Button> tabs) {
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setBackgroundResource(i == settingsCategory
                    ? R.drawable.button_primary : R.drawable.button_secondary);
        }
        host.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(18));

        switch (settingsCategory) {
            case 1:
                content.addView(graphicsCard());
                addSettingsCard(content, frameRateCard());
                addSettingsCard(content, coolModeCard());
                addSettingsCard(content, clusterTuneCard());
                addSettingsCard(content, infoCard("THERMAL GUIDANCE", "Start with Performance",
                        "Performance mode is recommended for demanding games and lower heat. Balanced adds FSR. HD renders at 2× and uses more power."));
                break;
            case 2:
                Runnable refreshRenderer = () -> populateSettingsCategory(host, tabs);
                content.addView(rendererDriverCard(refreshRenderer));
                addSettingsCard(content, rendererChoiceCard(
                        "DISPLAY & RESOLUTION", "Internal resolution",
                        "Xenia uses whole-number render scales. Native or 2× is recommended on AYN Thor Pro. Higher settings use much more memory, power and heat.",
                        new String[]{"Native", "2×", "3×", "4×", "5×", "6×", "7×"},
                        new String[]{"1", "2", "3", "4", "5", "6", "7"},
                        Integer.toString(CoreConfig.renderScale(this)), value -> {
                            CoreConfig.setRenderScale(this, Integer.parseInt(value));
                            refreshRenderer.run();
                        }));
                addSettingsCard(content, rendererChoiceCard(
                        "DISPLAY MODE", "Xbox 360 aspect ratio",
                        "Fit 16:9 keeps the intended widescreen shape. 4:3 supports older titles. Stretch fills the panel and can distort the picture.",
                        new String[]{"Fit 16:9", "Original 4:3", "Stretch"},
                        new String[]{CoreConfig.ASPECT_WIDE, CoreConfig.ASPECT_FOUR_THREE,
                                CoreConfig.ASPECT_STRETCH},
                        CoreConfig.aspectMode(this), value -> {
                            CoreConfig.setAspectMode(this, value);
                            refreshRenderer.run();
                        }));
                addSettingsCard(content, videoSignalCard(refreshRenderer));
                addSettingsCard(content, rendererChoiceCard(
                        "POST PROCESSING", "Anti-aliasing",
                        "FXAA smooths jagged edges after the Xbox 360 frame is rendered. Extreme is softer and costs more GPU time.",
                        new String[]{"Off", "FXAA", "FXAA Extreme"},
                        new String[]{"off", "fxaa", "fxaa_extreme"},
                        CoreConfig.antialiasing(this), value -> {
                            CoreConfig.setAntialiasing(this, value);
                            refreshRenderer.run();
                        }));
                addSettingsCard(content, rendererChoiceCard(
                        "SCALING & SHARPENING", "Output filter",
                        "FSR gives the clearest balanced output. CAS adds sharpening without FSR scaling. None uses basic output scaling.",
                        new String[]{"None", "CAS", "FSR"},
                        new String[]{"none", "cas", "fsr"},
                        CoreConfig.upscaler(this), value -> {
                            CoreConfig.setUpscaler(this, value);
                            refreshRenderer.run();
                        }));
                addSettingsCard(content, rendererChoiceCard(
                        "TEXTURES & FILTERING", "Anisotropic filtering",
                        "Sharper textures at steep viewing angles. Game default is safest. Forced modes can cause rare title-specific artifacts.",
                        new String[]{"Game", "Off", "2×", "4×", "8×", "16×"},
                        new String[]{"-1", "0", "2", "3", "4", "5"},
                        Integer.toString(CoreConfig.anisotropic(this)), value -> {
                            CoreConfig.setAnisotropic(this, Integer.parseInt(value));
                            refreshRenderer.run();
                        }));
                addSettingsCard(content, shaderCompilationCard(refreshRenderer));
                addSettingsCard(content, rendererChoiceCard(
                        "GPU READBACK", "Resolve accuracy",
                        "Unified memory is fastest at native resolution on Adreno. Fast is recommended above native. Accurate copies every resolve. Disabled can break effects.",
                        new String[]{"Unified memory", "Fast", "Accurate", "Disabled"},
                        new String[]{"uma", "fast", "all", "none"},
                        CoreConfig.readbackMode(this), value -> {
                            if ("uma".equals(value) && CoreConfig.renderScale(this) > 1) {
                                Toast.makeText(this,
                                        "Unified memory readback needs native resolution. Fast was kept.",
                                        Toast.LENGTH_LONG).show();
                            }
                            CoreConfig.setReadbackMode(this, value);
                            refreshRenderer.run();
                        }));
                addSettingsCard(content, shaderCacheCard());
                Button rendererDiagnostics = button("Open renderer diagnostics", true);
                rendererDiagnostics.setOnClickListener(v -> showPage(PAGE_DIAGNOSTICS));
                addSettingsCard(content, rendererDiagnostics);
                break;
            case 3:
                content.addView(infoCard("AUDIO", "XMA decoding enabled",
                        "Xbox 360 XMA audio is decoded by the emulator core and sent through Android's low-latency audio system."));
                addSettingsCard(content, infoCard("OUTPUT", "AAudio with OpenSL ES fallback",
                        "BluBox chooses the compatible Android audio path automatically for the device."));
                break;
            case 4:
                content.addView(infoCard("CONTROLS", "AYN Thor controls detected automatically",
                        "Xbox 360 controller profiles, macros, stick mapping, trigger pressure, rumble tests, and separate left and right stick tuning."));
                addSettingsCard(content, controllerTuningCard());
                Button controls = button("Show controller mapping", true);
                controls.setOnClickListener(v -> showControllerInfo());
                addSettingsCard(content, controls);
                content.addView(new AdvancedControlsPanel(this,
                        () -> populateSettingsCategory(host, tabs)));
                break;
            case 5:
                content.addView(infoCard("LIBRARY HOTKEYS", "A select • Y options • B back",
                        "Use the D-pad or left stick to move focus. Press A to open, Y for cover and removal options, and B to return."));
                addSettingsCard(content, infoCard("GAME SHORTCUTS", "Tap to play • hold for options",
                        "Hold a game to choose full cover artwork, reset its cover, or remove the library entry."));
                break;
            case 6:
                content.addView(infoCard("THOR LOWER SCREEN", secondDisplayStatus(),
                        "During gameplay, BluBox keeps the game on the main display and shows local achievement progress on the lower display."));
                LinearLayout secondScreen = infoCard("LIVE ACHIEVEMENTS", "Track while playing",
                        "Unlocked achievements, gamerscore and progress refresh from the active BluBox profile." );
                CheckBox secondScreenToggle = new CheckBox(this);
                secondScreenToggle.setText("Use lower screen while playing");
                secondScreenToggle.setTextColor(Color.WHITE);
                secondScreenToggle.setTextSize(12);
                secondScreenToggle.setChecked(AppPreferences.secondScreenAchievements(this));
                secondScreenToggle.setPadding(0, dp(8), 0, 0);
                secondScreenToggle.setOnCheckedChangeListener((button, checked) ->
                        AppPreferences.setSecondScreenAchievements(this, checked));
                secondScreen.addView(secondScreenToggle);
                CheckBox lockedToggle = new CheckBox(this);
                lockedToggle.setText("Show locked achievements");
                lockedToggle.setTextColor(Color.WHITE);
                lockedToggle.setTextSize(12);
                lockedToggle.setChecked(AppPreferences.showLockedAchievements(this));
                lockedToggle.setOnCheckedChangeListener((button, checked) ->
                        AppPreferences.setShowLockedAchievements(this, checked));
                secondScreen.addView(lockedToggle);
                addSettingsCard(content, secondScreen);
                LinearLayout history = infoCard("LOCAL TRACKING", "No Xbox Live sign-in required",
                        "BluBox reads GPD achievement records from the active local profile. Xbox Live is not contacted.");
                Button historyButton = button("Open achievement history", true);
                historyButton.setOnClickListener(v ->
                        startActivity(new Intent(this, AchievementsActivity.class)));
                LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(
                        dp(230), dp(50));
                historyParams.topMargin = dp(10);
                history.addView(historyButton, historyParams);
                addSettingsCard(content, history);
                break;
            case 7:
                populateModsCategory(content, () -> populateSettingsCategory(host, tabs));
                break;
            case 8:
                boolean ready = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                        || Environment.isExternalStorageManager();
                content.addView(infoCard("STORAGE", ready ? "microSD access ready" : "Permission needed",
                        ready ? "BluBox can launch ISO, XEX and ZAR files directly from their real storage paths."
                                : "Grant All files access so large Xbox 360 images can remain on your microSD card."));
                Button addGames = button("Add games from storage", true);
                addGames.setOnClickListener(v -> addGames());
                addSettingsCard(content, addGames);
                String folderPath = gameStore.gameFolderPath();
                LinearLayout folder = infoCard("GAME FOLDER",
                        folderPath == null || folderPath.isEmpty()
                                ? "No folder selected" : new File(folderPath).getName(),
                        folderPath == null || folderPath.isEmpty()
                                ? "Choose a folder containing ISO, ZAR, or extracted default.xex games."
                                : folderPath);
                Button chooseFolder = button("Choose game folder", false);
                chooseFolder.setOnClickListener(v -> chooseGameFolder());
                LinearLayout.LayoutParams chooseFolderParams = new LinearLayout.LayoutParams(
                        dp(230), dp(50));
                chooseFolderParams.topMargin = dp(10);
                folder.addView(chooseFolder, chooseFolderParams);
                Button refreshFolder = button("Refresh library", true);
                refreshFolder.setEnabled(!libraryScanRunning);
                refreshFolder.setOnClickListener(v -> refreshGameFolder());
                LinearLayout.LayoutParams refreshFolderParams = new LinearLayout.LayoutParams(
                        dp(230), dp(50));
                refreshFolderParams.topMargin = dp(8);
                folder.addView(refreshFolder, refreshFolderParams);
                addSettingsCard(content, folder);
                Button storage = button(ready ? "Storage access is enabled" : "Grant storage access", false);
                storage.setEnabled(!ready);
                storage.setOnClickListener(v -> requestStorageAccess(false));
                addSettingsCard(content, storage);
                break;
            default:
                content.addView(infoCard("APP", "BluBox 360 0.12.0 public alpha",
                        "Built by Macca and the BluBox team for public testing on ARM64 Android handhelds."));
                LinearLayout boot = infoCard("BOOT ANIMATION", "Show the BluBox intro",
                        "Play the clean animated BluBox logo when the app starts.");
                CheckBox bootToggle = new CheckBox(this);
                bootToggle.setText("Play boot animation");
                bootToggle.setTextColor(Color.WHITE);
                bootToggle.setTextSize(12);
                bootToggle.setChecked(AppPreferences.bootAnimation(this));
                bootToggle.setPadding(0, dp(8), 0, 0);
                bootToggle.setOnCheckedChangeListener((button, checked) -> {
                    AppPreferences.setBootAnimation(this, checked);
                    Toast.makeText(this, checked ? "Boot animation enabled"
                            : "Boot animation disabled", Toast.LENGTH_SHORT).show();
                });
                boot.addView(bootToggle);
                addSettingsCard(content, boot);

                LinearLayout backup = infoCard("BACK UP APP DATA", "Save a BluBox backup .zip",
                        "Profiles, Xbox 360 saves, artwork and all app settings are included. Games and firmware are not included.");
                Button backupButton = button("Back up app data", true);
                backupButton.setOnClickListener(v -> chooseBackupDestination());
                LinearLayout.LayoutParams backupParams = new LinearLayout.LayoutParams(dp(230), dp(50));
                backupParams.topMargin = dp(10);
                backup.addView(backupButton, backupParams);
                addSettingsCard(content, backup);

                LinearLayout restore = infoCard("RESTORE APP DATA", "Load a BluBox backup .zip",
                        "Files with the same name are replaced. BluBox restarts after the restore finishes.");
                Button restoreButton = button("Restore app data", false);
                restoreButton.setOnClickListener(v -> chooseBackupToRestore());
                LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(dp(230), dp(50));
                restoreParams.topMargin = dp(10);
                restore.addView(restoreButton, restoreParams);
                addSettingsCard(content, restore);

                LinearLayout reset = infoCard("RESET APP", "Restore settings to their defaults",
                        "Your games, profiles and Xbox 360 save progress are kept.");
                Button resetButton = button("Reset app settings", false);
                resetButton.setOnClickListener(v -> confirmResetApp());
                LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(230), dp(50));
                resetParams.topMargin = dp(10);
                reset.addView(resetButton, resetParams);
                addSettingsCard(content, reset);

                LinearLayout display = infoCard("LIBRARY DISPLAY", viewModeName(),
                        "Every mode uses automatic 3D Xbox 360 cases. Choose a shelf, cover grid, or compact list.");
                Button chooseDisplay = button("Choose display style", true);
                chooseDisplay.setOnClickListener(v -> showViewModeChooser());
                LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(dp(230), dp(50));
                chooseParams.topMargin = dp(10);
                display.addView(chooseDisplay, chooseParams);
                addSettingsCard(content, display);
                LinearLayout saves = infoCard("SAVE GAME PROGRESS", "Automatic per profile",
                        "Profiles keep their own Xbox 360 account and save data. Switching profiles does not overwrite another player's progress.");
                Button profiles = button("Manage profiles & avatars", false);
                profiles.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
                LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(dp(230), dp(50));
                profileParams.topMargin = dp(10);
                saves.addView(profiles, profileParams);
                addSettingsCard(content, saves);
                break;
        }
        scroll.addView(content);
        host.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void addSettingsCard(LinearLayout content, View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        content.addView(card, params);
    }

    private View graphicsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(15), dp(18), dp(16));
        card.setBackgroundResource(R.drawable.tile_blue);
        card.addView(label("VIDEO", 21, Color.WHITE, true));
        card.addView(label("Choose smooth performance or higher-resolution rendering. Changes apply on the next launch.",
                11, Color.rgb(220, 241, 255), false));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        String mode = CoreConfig.graphicsMode(this);
        RadioButton performance = radio("Performance • native resolution, fastest", CoreConfig.GRAPHICS_PERFORMANCE);
        RadioButton balanced = radio("Balanced • native rendering + FSR sharpening", CoreConfig.GRAPHICS_BALANCED);
        RadioButton hd = radio("HD • 2x internal resolution + FSR", CoreConfig.GRAPHICS_HD);
        RadioButton custom = radio("Custom • Renderer tab settings", CoreConfig.GRAPHICS_CUSTOM);
        group.addView(performance);
        group.addView(balanced);
        group.addView(hd);
        group.addView(custom);
        if (CoreConfig.GRAPHICS_HD.equals(mode)) hd.setChecked(true);
        else if (CoreConfig.GRAPHICS_PERFORMANCE.equals(mode)) performance.setChecked(true);
        else if (CoreConfig.GRAPHICS_CUSTOM.equals(mode)) custom.setChecked(true);
        else balanced.setChecked(true);
        group.setOnCheckedChangeListener((g, id) -> {
            View selected = g.findViewById(id);
            if (selected != null && selected.getTag() instanceof String) {
                CoreConfig.setGraphicsMode(this, (String) selected.getTag());
            }
        });
        card.addView(group);

        CheckBox fps = new CheckBox(this);
        fps.setText("Show FPS and frame-time overlay");
        fps.setTextColor(Color.WHITE);
        fps.setTextSize(12);
        fps.setChecked(CoreConfig.showFps(this));
        fps.setOnCheckedChangeListener((button, checked) -> CoreConfig.setShowFps(this, checked));
        card.addView(fps);
        return card;
    }

    private LinearLayout controllerTuningCard() {
        LinearLayout card = infoCard("STICK RESPONSE", "Smooth controller mode",
                "Removes the jump at the edge of the deadzone and filters tiny stick changes without blocking full movement. Changes start with the next game.");
        CheckBox smooth = new CheckBox(this);
        smooth.setText("Use smooth analogue controls");
        smooth.setTextColor(Color.WHITE);
        smooth.setTextSize(12);
        smooth.setChecked(AppPreferences.smoothControls(this));
        smooth.setPadding(0, dp(8), 0, 0);
        smooth.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.setSmoothControls(this, checked));
        card.addView(smooth);

        CheckBox harryPotter = new CheckBox(this);
        harryPotter.setText("Harry Potter precision aiming");
        harryPotter.setTextColor(Color.WHITE);
        harryPotter.setTextSize(12);
        harryPotter.setChecked(AppPreferences.harryPotterPrecisionAim(this));
        harryPotter.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.setHarryPotterPrecisionAim(this, checked));
        card.addView(harryPotter);
        TextView detail = label(
                "The Harry Potter profile slows the right stick near the centre for steadier spell aiming while keeping full turn speed at the edge.",
                10, getColor(R.color.muted), false);
        detail.setPadding(0, dp(4), 0, 0);
        card.addView(detail);
        return card;
    }

    private LinearLayout frameRateCard() {
        int current = CoreConfig.frameLimit(this);
        LinearLayout card = infoCard("FRAME RATE", current + " FPS target",
                "Compatible games output up to 60 FPS. Games with an internal 30 FPS lock keep their original rate. Fable II keeps its safer 24 FPS recovery limit.");
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton thirty = radio("30 FPS", "30");
        RadioButton sixty = radio("60 FPS", "60");
        group.addView(thirty, new RadioGroup.LayoutParams(0, dp(48), 1f));
        group.addView(sixty, new RadioGroup.LayoutParams(0, dp(48), 1f));
        if (current == 30) thirty.setChecked(true);
        else sixty.setChecked(true);
        group.setOnCheckedChangeListener((buttons, id) -> {
            View selected = buttons.findViewById(id);
            if (selected != null && selected.getTag() instanceof String) {
                CoreConfig.setFrameLimit(this,
                        Integer.parseInt((String) selected.getTag()));
                Toast.makeText(this, "Frame-rate target saved for the next game.",
                        Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(group);
        return card;
    }

    private void populateModsCategory(LinearLayout content, Runnable refresh) {
        List<ModManager.PatchMod> mods;
        try {
            mods = ModManager.list();
        } catch (Throwable t) {
            content.addView(infoCard("MODS", "Mods folder unavailable", safeMessage(t)));
            return;
        }
        int enabled = 0;
        for (ModManager.PatchMod mod : mods) if (mod.enabled) enabled++;
        LinearLayout overview = infoCard("XENIA PATCH MODS",
                enabled + " active • " + mods.size() + " installed",
                "Import .patch.toml files for games you own. A file-level switch controls whether Xenia reads each mod at the next game launch.");
        Button install = button("Import patch mod", true);
        install.setOnClickListener(v -> openModPicker());
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(dp(230), dp(50));
        installParams.topMargin = dp(10);
        overview.addView(install, installParams);
        content.addView(overview);

        if (mods.isEmpty()) {
            addSettingsCard(content, infoCard("NO MODS INSTALLED", "Your mods list is empty",
                    "Choose Import patch mod and select a valid Xenia .patch.toml file."));
            return;
        }
        for (ModManager.PatchMod mod : mods) {
            String target = mod.titleId.isEmpty() ? mod.file.getName()
                    : "Title ID " + mod.titleId + " • " + mod.file.getName();
            LinearLayout card = infoCard(mod.builtIn ? "BUILT-IN COMPATIBILITY" : "PATCH MOD",
                    mod.title, target);
            CheckBox state = new CheckBox(this);
            state.setText(mod.builtIn ? "Managed by BluBox" : "Enabled for game launch");
            state.setTextColor(Color.WHITE);
            state.setTextSize(12);
            state.setChecked(mod.enabled);
            state.setEnabled(!mod.builtIn);
            state.setPadding(0, dp(7), 0, 0);
            state.setOnCheckedChangeListener((button, checked) -> {
                try {
                    ModManager.setEnabled(mod, checked);
                    Toast.makeText(this, checked ? "Patch mod enabled."
                                    : "Patch mod disabled.", Toast.LENGTH_SHORT).show();
                    refresh.run();
                } catch (Throwable t) {
                    Toast.makeText(this, "Mod change failed: " + safeMessage(t),
                            Toast.LENGTH_LONG).show();
                    button.setOnCheckedChangeListener(null);
                    button.setChecked(mod.enabled);
                }
            });
            card.addView(state);
            if (!mod.builtIn) {
                Button remove = button("Remove mod", false);
                remove.setOnClickListener(v -> confirmRemoveMod(mod, refresh));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        dp(180), dp(46));
                removeParams.topMargin = dp(7);
                card.addView(remove, removeParams);
            }
            addSettingsCard(content, card);
        }
    }

    private void confirmRemoveMod(ModManager.PatchMod mod, Runnable refresh) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + mod.title + "?")
                .setMessage("The patch file will be removed. Your game and save progress stay untouched.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove mod", (dialog, which) -> {
                    try {
                        ModManager.delete(mod);
                        refresh.run();
                        Toast.makeText(this, "Patch mod removed.", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(this, "Mod removal failed: " + safeMessage(t),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private LinearLayout coolModeCard() {
        boolean enabled = CoreConfig.coolMode(this);
        LinearLayout card = infoCard("LOW HEAT MODE",
                enabled ? "On for every game" : "Off",
                "Reduces sustained CPU and GPU load with native rendering, two background shader workers, and no maximum-clock request. The selected FPS target still applies. Fable II uses a 24 FPS ceiling.");
        CheckBox toggle = new CheckBox(this);
        toggle.setText("Use the low-heat launch preset");
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(12);
        toggle.setChecked(enabled);
        toggle.setPadding(0, dp(8), 0, 0);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            CoreConfig.setCoolMode(this, checked);
            Toast.makeText(this, checked
                            ? "Low Heat Mode will start with the next game."
                            : "Low Heat Mode is off for the next game.",
                    Toast.LENGTH_LONG).show();
        });
        card.addView(toggle);
        return card;
    }

    private LinearLayout clusterTuneCard() {
        boolean installed = ClusterTuneSupport.isInstalled(this);
        String version = ClusterTuneSupport.versionName(this);
        String status = installed
                ? "Installed" + (version.isEmpty() ? "" : " • " + version)
                : "Not installed";
        LinearLayout card = infoCard("CLUSTERTUNE", status,
                installed
                        ? "Open ClusterTune once, make a cooler CPU and GPU profile, then assign BluBox 360 under App Profiles. ClusterTune applies the profile while BluBox is in front."
                        : "ClusterTune supports AYN handheld frequency limits and per-app profiles. Install it, then assign BluBox 360 to a cooler profile.");
        Button action = button(installed ? "Open ClusterTune" : "Get ClusterTune", true);
        action.setOnClickListener(v -> {
            boolean opened = installed
                    ? ClusterTuneSupport.open(this)
                    : ClusterTuneSupport.openDownloadPage(this);
            if (!opened) {
                Toast.makeText(this, installed
                                ? "ClusterTune could not be opened."
                                : "The ClusterTune download page could not be opened.",
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(230), dp(50));
        params.topMargin = dp(10);
        card.addView(action, params);
        return card;
    }

    private LinearLayout rendererDriverCard(Runnable refresh) {
        boolean customSupported = CoreConfig.supportsCustomDriver();
        String path = CoreConfig.vulkanDriverPath(this);
        String active = CoreConfig.vulkanDriverName(this);
        LinearLayout card = infoCard("GRAPHICS API • VULKAN", CoreConfig.gpuName(),
                customSupported
                        ? "Xbox 360 rendering uses Vulkan. Active driver: " + active
                        + ". Import AdrenoTools-compatible Turnip packages as a .zip."
                        : "Xbox 360 rendering uses Vulkan. Custom packages need an Adreno GPU, so the system Vulkan driver is active.");

        GridLayout actions = new GridLayout(this);
        actions.setColumnCount(3);
        actions.setPadding(0, dp(10), 0, 0);
        Button system = button(path.isEmpty() ? "System driver • Active" : "System driver", path.isEmpty());
        system.setEnabled(!path.isEmpty());
        system.setOnClickListener(v -> {
            CoreConfig.setVulkanDriver(this, "");
            refresh.run();
        });
        int actionIndex = 0;
        addGridButton(actions, system, actionIndex / 3, actionIndex++ % 3, 3);

        for (DriverPackageManager.InstalledDriver driver : DriverPackageManager.installed()) {
            boolean selected = path.equals(driver.path);
            Button driverButton = button(driver.name + (selected ? " • Active" : ""), selected);
            driverButton.setEnabled(!selected);
            driverButton.setOnClickListener(v -> {
                CoreConfig.setVulkanDriver(this, driver.path);
                refresh.run();
            });
            addGridButton(actions, driverButton, actionIndex / 3, actionIndex++ % 3, 3);
        }

        Button install = button("Import driver .zip", false);
        install.setEnabled(customSupported);
        install.setOnClickListener(v -> openDriverPicker());
        addGridButton(actions, install, actionIndex / 3, actionIndex++ % 3, 3);

        if (!path.isEmpty()) {
            Button remove = button("Delete custom driver", false);
            remove.setOnClickListener(v -> confirmRemoveDriver());
            addGridButton(actions, remove, actionIndex / 3, actionIndex % 3, 3);
        }
        card.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private LinearLayout rendererChoiceCard(String eyebrow, String title, String body,
                                            String[] labels, String[] values,
                                            String selected, Consumer<String> onSelected) {
        LinearLayout card = infoCard(eyebrow, title, body);
        GridLayout grid = new GridLayout(this);
        int columns = Math.min(4, labels.length);
        grid.setColumnCount(columns);
        grid.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < labels.length; i++) {
            Button choice = button(labels[i], values[i].equals(selected));
            String value = values[i];
            choice.setOnClickListener(v -> onSelected.accept(value));
            addGridButton(grid, choice, i / columns, i % columns, columns);
        }
        card.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void addGridButton(GridLayout grid, Button button, int row, int column,
                               int columns) {
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row), GridLayout.spec(column, 1f));
        params.width = 0;
        params.height = dp(48);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(button, params);
    }

    private LinearLayout videoSignalCard(Runnable refresh) {
        LinearLayout card = infoCard("XBOX 360 VIDEO", "Progressive output",
                "Progressive is recommended for the AYN Thor display. Interlaced mode only changes the signal reported to games that check the console video mode.");
        CheckBox interlaced = new CheckBox(this);
        interlaced.setText("Report interlaced output for game compatibility");
        interlaced.setTextColor(Color.WHITE);
        interlaced.setTextSize(12);
        interlaced.setPadding(0, dp(8), 0, 0);
        interlaced.setChecked(CoreConfig.interlaced(this));
        interlaced.setOnCheckedChangeListener((button, checked) -> {
            CoreConfig.setInterlaced(this, checked);
            refresh.run();
        });
        card.addView(interlaced);
        return card;
    }

    private LinearLayout shaderCompilationCard(Runnable refresh) {
        LinearLayout card = infoCard("SHADER PIPELINES", "Multithreaded compilation",
                "Background pipeline workers reduce shader stutter. Smooth mode can show brief object pop-in while a new pipeline finishes.");

        CheckBox async = rendererCheckBox("Compile shaders in the background",
                CoreConfig.asyncShaders(this));
        async.setOnCheckedChangeListener((button, checked) -> {
            CoreConfig.setAsyncShaders(this, checked);
            refresh.run();
        });
        card.addView(async);

        CheckBox smooth = rendererCheckBox("Prefer smooth frame pacing with brief pop-in",
                CoreConfig.skipShaderDraws(this));
        smooth.setEnabled(CoreConfig.asyncShaders(this));
        smooth.setOnCheckedChangeListener((button, checked) -> {
            CoreConfig.setSkipShaderDraws(this, checked);
            refresh.run();
        });
        card.addView(smooth);

        CheckBox preload = rendererCheckBox("Preload saved pipelines when a game starts",
                CoreConfig.pipelinePreload(this));
        preload.setOnCheckedChangeListener((button, checked) -> {
            CoreConfig.setPipelinePreload(this, checked);
            refresh.run();
        });
        card.addView(preload);

        TextView workers = label("Pipeline workers", 11, getColor(R.color.cyan), true);
        workers.setPadding(0, dp(9), 0, 0);
        card.addView(workers);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        String[] labels = {"Auto", "2", "4", "6"};
        String[] values = {"-1", "2", "4", "6"};
        String selected = Integer.toString(CoreConfig.pipelineThreads(this));
        for (int i = 0; i < labels.length; i++) {
            Button choice = button(labels[i], values[i].equals(selected));
            String value = values[i];
            choice.setEnabled(CoreConfig.asyncShaders(this));
            choice.setOnClickListener(v -> {
                CoreConfig.setPipelineThreads(this, Integer.parseInt(value));
                refresh.run();
            });
            addGridButton(grid, choice, 0, i, 4);
        }
        card.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private CheckBox rendererCheckBox(String text, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextColor(Color.WHITE);
        checkBox.setTextSize(12);
        checkBox.setChecked(checked);
        checkBox.setPadding(0, dp(5), 0, 0);
        return checkBox;
    }

    private LinearLayout shaderCacheCard() {
        LinearLayout card = infoCard("SHADER CACHE", "Clear compiled shaders",
                "Removes per-game Xenia shader data and Vulkan pipeline caches. The next launch rebuilds them and can stutter at first. Saves and games are untouched.");
        Button clear = button("Clear shader cache", false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear shader cache?")
                .setMessage("The next game launch will rebuild shaders. Save progress and game files stay untouched.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Clear cache", (dialog, which) ->
                        CoreConfig.clearShaderCacheAsync(this, message ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()))
                .show());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(230), dp(50));
        params.topMargin = dp(10);
        card.addView(clear, params);
        return card;
    }

    private RadioButton radio(String text, String value) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setTag(value);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setPadding(0, dp(4), 0, dp(4));
        return button;
    }

    private LinearLayout infoCard(String eyebrow, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.panel_card);
        card.addView(label(eyebrow, 10, getColor(R.color.cyan), true));
        card.addView(label(title, 18, Color.WHITE, true));
        TextView bodyView = label(body, 11, getColor(R.color.muted), false);
        bodyView.setPadding(0, dp(5), 0, 0);
        card.addView(bodyView);
        return card;
    }

    private View buildDiagnosticsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(16), dp(22), dp(20));
        page.addView(label("Diagnostics", 27, Color.WHITE, true));
        page.addView(label("Useful checks before starting a game.", 12,
                getColor(R.color.muted), false));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(0, dp(14), 0, 0);
        grid.addView(infoCard("EMULATION", "ARM64 core installed",
                "Xenia-derived PowerPC JIT, Vulkan renderer, XMA audio, game patches, and persistent saves."),
                new LinearLayout.LayoutParams(0, dp(190), 1f));
        LinearLayout.LayoutParams gpuParams = new LinearLayout.LayoutParams(0, dp(190), 1f);
        gpuParams.setMargins(dp(12), 0, 0, 0);
        grid.addView(infoCard("VIDEO", CoreConfig.gpuName(),
                "Vulkan is required. Snapdragon Gen 2 or newer with Adreno 740 or newer is the intended target."), gpuParams);
        LinearLayout.LayoutParams storageParams = new LinearLayout.LayoutParams(0, dp(190), 1f);
        storageParams.setMargins(dp(12), 0, 0, 0);
        boolean storage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager();
        LinearLayout storageCard = infoCard("STORAGE", storage ? "SD access ready" : "Permission needed",
                storage ? "BluBox can launch supported game files by their real path."
                        : "Grant All files access so large ISO files can stay on your microSD card.");
        grid.addView(storageCard, storageParams);
        page.addView(grid);

        Button storageButton = button(storage ? "Storage access is enabled" : "Grant SD card access", true);
        storageButton.setEnabled(!storage);
        storageButton.setOnClickListener(v -> requestStorageAccess(false));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(250), dp(52));
        buttonParams.topMargin = dp(14);
        page.addView(storageButton, buttonParams);

        Button notices = button("Open-source notices", false);
        notices.setOnClickListener(v -> showOpenSourceNotices());
        LinearLayout.LayoutParams noticeButtonParams = new LinearLayout.LayoutParams(dp(250), dp(52));
        noticeButtonParams.topMargin = dp(9);
        page.addView(notices, noticeButtonParams);

        TextView note = label("Compatibility varies by game. BluBox does not include Xbox firmware, games, or Microsoft code. Use legal backups you own.",
                11, getColor(R.color.warn), false);
        note.setPadding(0, dp(14), 0, 0);
        page.addView(note);
        scroll.addView(page);
        return scroll;
    }

    private void showOpenSourceNotices() {
        String notices;
        try {
            byte[] data = xendroid.compose.Application.load_assets_file(
                    this, "open_source_notices.txt");
            notices = data == null ? "Notices could not be loaded."
                    : new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            notices = "Notices could not be loaded: " + t.getMessage();
        }
        new AlertDialog.Builder(this)
                .setTitle("About BluBox 360")
                .setMessage(notices)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void prepareCoreAndProfile() {
        if (corePreparing) return;
        corePreparing = true;
        ProfileStore.Profile active = profileStore.getActive();
        CoreConfig.applySettingsAsync(this, ignored ->
                CoreConfig.syncActiveProfileAsync(this, active, message -> {
                    corePreparing = false;
                    updateProfileBadge();
                }));
    }

    private void addGames() {
        boolean hasFolder = !gameStore.gameFolderPath().isEmpty();
        String[] choices = hasFolder
                ? new String[]{"Add game file", "Choose game folder", "Refresh library"}
                : new String[]{"Add game file", "Choose game folder"};
        new AlertDialog.Builder(this)
                .setTitle("Add games")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) addGameFiles();
                    else if (which == 1) chooseGameFolder();
                    else refreshGameFolder();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addGameFiles() {
        if (needsStorageAccess()) {
            showStorageAccessExplanation(STORAGE_ACTION_FILES);
            return;
        }
        openGamePicker();
    }

    private void chooseGameFolder() {
        if (needsStorageAccess()) {
            showStorageAccessExplanation(STORAGE_ACTION_FOLDER);
            return;
        }
        openGameFolderPicker();
    }

    private void refreshGameFolder() {
        if (libraryScanRunning) return;
        String folderPath = gameStore.gameFolderPath();
        if (folderPath == null || folderPath.isEmpty()) {
            chooseGameFolder();
            return;
        }
        if (needsStorageAccess()) {
            showStorageAccessExplanation(STORAGE_ACTION_REFRESH);
            return;
        }
        File folder = new File(folderPath);
        if (!folder.isDirectory() || !folder.canRead()) {
            Toast.makeText(this,
                    "The selected game folder is unavailable. Choose it again.",
                    Toast.LENGTH_LONG).show();
            chooseGameFolder();
            return;
        }

        libraryScanRunning = true;
        updateHeader();
        Toast.makeText(this, "Scanning the game folder…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<File> files = StorageResolver.scanGameFiles(folderPath, 512);
                List<GameStore.Game> newGames = new ArrayList<>();
                for (File file : files) {
                    String path = file.getAbsolutePath();
                    boolean exists = gameStore.containsPath(path);
                    GameStore.Game game = gameStore.addOrUpdate(path,
                            Uri.fromFile(file).toString(), file.getName(), file.length());
                    if (!exists) newGames.add(game);
                }

                runOnUiThread(() -> {
                    searchQuery = "";
                    showPage(PAGE_GAMES);
                    Toast.makeText(this,
                            files.size() + " game" + (files.size() == 1 ? "" : "s")
                                    + " found. " + newGames.size() + " new.",
                            Toast.LENGTH_LONG).show();
                });

                for (GameStore.Game game : newGames) extractMetadataSync(game);
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Library refresh failed: " + safeMessage(t),
                        Toast.LENGTH_LONG).show());
            } finally {
                runOnUiThread(() -> {
                    libraryScanRunning = false;
                    showPage(PAGE_GAMES);
                });
            }
        }, "BluBox-library-scan").start();
    }

    private boolean needsStorageAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager();
    }

    private void showStorageAccessExplanation(int action) {
        new AlertDialog.Builder(this)
                .setTitle("Allow microSD game access")
                .setMessage("Xbox 360 disc images are too large to copy into BluBox. Android's All files access lets the emulator read selected ISO, XEX, or ZAR files directly from internal storage or microSD.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Continue", (dialog, which) -> requestStorageAccess(action))
                .show();
    }

    private void requestStorageAccess(boolean thenPick) {
        requestStorageAccess(thenPick ? STORAGE_ACTION_FILES : STORAGE_ACTION_NONE);
    }

    private void requestStorageAccess(int action) {
        pendingStorageAction = action;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            continuePendingStorageAction();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_STORAGE);
        } catch (Throwable t) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    REQ_STORAGE);
        }
    }

    private void continuePendingStorageAction() {
        int action = pendingStorageAction;
        pendingStorageAction = STORAGE_ACTION_NONE;
        if (action == STORAGE_ACTION_FILES) openGamePicker();
        else if (action == STORAGE_ACTION_FOLDER) openGameFolderPicker();
        else if (action == STORAGE_ACTION_REFRESH) refreshGameFolder();
    }

    private void openGamePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_GAMES);
        } catch (Throwable t) {
            Toast.makeText(this, "Android file picker is unavailable.", Toast.LENGTH_LONG).show();
        }
    }

    private void openGameFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_GAME_FOLDER);
        } catch (Throwable t) {
            Toast.makeText(this, "Android folder picker is unavailable.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openModPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "application/toml", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_MOD);
        } catch (Throwable t) {
            Toast.makeText(this, "Android could not open the mod file picker.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void installMod(Uri source) {
        Toast.makeText(this, "Checking patch mod…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ModManager.PatchMod mod = ModManager.importPatch(this, source);
                runOnUiThread(() -> {
                    settingsCategory = 7;
                    showPage(PAGE_SETTINGS);
                    Toast.makeText(this, mod.title + " added to Mods.",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Mod import failed: " + safeMessage(t), Toast.LENGTH_LONG).show());
            }
        }, "BluBox-mod-import").start();
    }

    private void openCoverPicker(GameStore.Game game) {
        pendingCoverGameId = game.id;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_COVER);
        } catch (Throwable t) {
            pendingCoverGameId = null;
            Toast.makeText(this, "Android could not open the cover image picker.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void installCover(String gameId, Uri source) {
        Toast.makeText(this, "Preparing full cover artwork…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                CoverArtStore.importCover(this, gameId, source);
                runOnUiThread(() -> {
                    coverCache.remove(gameId);
                    showPage(PAGE_GAMES);
                    Toast.makeText(this, "Full game cover saved.", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Cover import failed: " + safeMessage(t), Toast.LENGTH_LONG).show());
            }
        }, "BluBox-cover-import").start();
    }

    private void openDriverPicker() {
        if (!CoreConfig.supportsCustomDriver()) {
            Toast.makeText(this, "Custom Vulkan drivers need an Adreno GPU.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed",
                "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_DRIVER);
        } catch (Throwable t) {
            Toast.makeText(this, "Android could not open the driver file picker.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void installDriver(Uri source) {
        Toast.makeText(this, "Checking Vulkan driver package…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                DriverPackageManager.InstalledDriver driver =
                        DriverPackageManager.install(this, source);
                CoreConfig.setVulkanDriver(this, driver.path);
                runOnUiThread(() -> {
                    settingsCategory = 2;
                    showPage(PAGE_SETTINGS);
                    Toast.makeText(this,
                            driver.name + " installed. The next game uses this driver.",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Driver install failed: " + safeMessage(t), Toast.LENGTH_LONG).show());
            }
        }, "BluBox-driver-install").start();
    }

    private void confirmRemoveDriver() {
        if (CoreConfig.vulkanDriverPath(this).isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete custom Vulkan driver?")
                .setMessage("BluBox will return to the Android system driver. Games, saves and shader settings stay untouched.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete driver", (dialog, which) ->
                        CoreConfig.removeActiveDriverAsync(this, message -> {
                            settingsCategory = 2;
                            showPage(PAGE_SETTINGS);
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        }))
                .show();
    }

    private void chooseBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "BluBox-360-backup-" + date + ".zip");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_BACKUP);
        } catch (Throwable t) {
            Toast.makeText(this, "Android could not open the backup file picker.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void chooseBackupToRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_RESTORE);
        } catch (Throwable t) {
            Toast.makeText(this, "Android could not open the restore file picker.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void createBackup(Uri destination) {
        Toast.makeText(this, "Creating BluBox backup…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                BackupManager.Result result = BackupManager.writeBackup(this, destination);
                runOnUiThread(() -> Toast.makeText(this,
                        result.summary("Backed up"), Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Backup failed: " + safeMessage(t), Toast.LENGTH_LONG).show());
            }
        }, "BluBox-backup").start();
    }

    private void confirmRestore(Uri source) {
        new AlertDialog.Builder(this)
                .setTitle("Restore BluBox app data?")
                .setMessage("Profiles, saves, artwork and settings in the backup will replace files with the same name. Your game images are not changed.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Restore", (dialog, which) -> restoreBackup(source))
                .show();
    }

    private void restoreBackup(Uri source) {
        Toast.makeText(this, "Checking and restoring backup…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                BackupManager.Result result = BackupManager.restoreBackup(this, source);
                runOnUiThread(() -> {
                    Toast.makeText(this, result.summary("Restored") + " Restarting BluBox…",
                            Toast.LENGTH_LONG).show();
                    View root = getWindow().getDecorView();
                    root.postDelayed(this::restartApp, 850L);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Restore failed: " + safeMessage(t), Toast.LENGTH_LONG).show());
            }
        }, "BluBox-restore").start();
    }

    private void restartApp() {
        Intent restart = new Intent(this, BootActivity.class);
        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(restart);
        finishAffinity();
    }

    private void confirmResetApp() {
        new AlertDialog.Builder(this)
                .setTitle("Reset app settings?")
                .setMessage("Boot, display, graphics and diagnostic settings return to their defaults. Games, profiles and save progress are kept.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Reset settings", (dialog, which) -> {
                    AppPreferences.reset(this);
                    getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().clear().apply();
                    CoreConfig.resetSettings(this);
                    libraryViewMode = VIEW_GRID;
                    searchQuery = "";
                    settingsCategory = 0;
                    showPage(PAGE_SETTINGS);
                    Toast.makeText(this, "App settings reset. Games and saves were kept.",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private static String safeMessage(Throwable t) {
        String value = t.getMessage();
        return value == null || value.trim().isEmpty()
                ? t.getClass().getSimpleName() : value;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_STORAGE) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
                continuePendingStorageAction();
            } else {
                pendingStorageAction = STORAGE_ACTION_NONE;
                Toast.makeText(this, "Storage access was not granted.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQ_BACKUP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                createBackup(data.getData());
            }
            return;
        }
        if (requestCode == REQ_RESTORE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                confirmRestore(data.getData());
            }
            return;
        }
        if (requestCode == REQ_MOD) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                installMod(data.getData());
            }
            return;
        }
        if (requestCode == REQ_COVER) {
            String gameId = pendingCoverGameId;
            pendingCoverGameId = null;
            if (gameId != null && resultCode == RESULT_OK
                    && data != null && data.getData() != null) {
                installCover(gameId, data.getData());
            }
            return;
        }
        if (requestCode == REQ_DRIVER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                installDriver(data.getData());
            }
            return;
        }
        if (requestCode == REQ_GAME_FOLDER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Throwable ignored) { }
                String path = StorageResolver.absoluteDirectoryPath(this, uri);
                if (path == null) {
                    Toast.makeText(this,
                            "BluBox needs a real internal-storage or microSD folder.",
                            Toast.LENGTH_LONG).show();
                } else {
                    gameStore.setGameFolder(path, uri.toString());
                    refreshGameFolder();
                }
            }
            return;
        }
        if (requestCode != REQ_GAMES || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int added = 0;
        int skipped = 0;
        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            String path = StorageResolver.absolutePath(this, uri);
            if (path == null || !StorageResolver.supported(path)) {
                skipped++;
                continue;
            }
            boolean exists = gameStore.containsPath(path);
            GameStore.Game game = gameStore.addOrUpdate(path, uri.toString(),
                    StorageResolver.displayName(this, uri), StorageResolver.size(this, uri));
            if (!exists) {
                added++;
                extractMetadata(game);
            }
        }
        searchQuery = "";
        showPage(PAGE_GAMES);
        String message = added + " game" + (added == 1 ? "" : "s") + " added";
        if (skipped > 0) message += ". " + skipped + " unsupported or inaccessible";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void extractMetadata(GameStore.Game game) {
        new Thread(() -> {
            extractMetadataSync(game);
            runOnUiThread(() -> showPage(currentPage));
        }, "BluBox-metadata").start();
    }

    private void extractMetadataSync(GameStore.Game game) {
        try {
            Emulator core = CoreConfig.ensureCoreLoaded();
            Emulator.GameInfo info = core.meta_from_path(game.path,
                    StorageResolver.gameFormat(game.path));
            if (info != null) {
                gameStore.applyMetadata(game.id, info.name, info.titleId, info.icon);
            }
        } catch (Throwable ignored) { }
    }

    private void launchGame(GameStore.Game game) {
        File file = new File(game.path);
        if (!file.isFile() || !file.canRead()) {
            Toast.makeText(this, "Game file is no longer readable. Check the SD card.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            requestStorageAccess(false);
            return;
        }
        Toast.makeText(this, "Preparing " + game.name + "…", Toast.LENGTH_SHORT).show();
        ProfileStore.Profile active = profileStore.getActive();
        new Thread(() -> {
            try {
                CoreConfig.syncProfile(this, active);
                String resolvedTitleId = AchievementData.normalizeTitleId(game.titleId);
                if (resolvedTitleId.isEmpty()) {
                    try {
                        Emulator emulator = CoreConfig.ensureCoreLoaded();
                        Emulator.GameInfo info = emulator.meta_from_path(game.path,
                                StorageResolver.gameFormat(game.path));
                        if (info != null) {
                            resolvedTitleId = AchievementData.normalizeTitleId(info.titleId);
                            gameStore.applyMetadata(game.id, info.name, info.titleId, info.icon);
                        }
                    } catch (Throwable ignored) { }
                }
                String launchTitleId = resolvedTitleId;
                runOnUiThread(() -> {
                    gameStore.markPlayed(game.id);
                    Intent intent = new Intent(this, EmulatorActivity.class);
                    intent.putExtra(EmulatorActivity.EXTRA_GAME_PATH, game.path);
                    intent.putExtra(EmulatorActivity.EXTRA_GAME_NAME, game.name);
                    intent.putExtra(EmulatorActivity.EXTRA_TITLE_ID, launchTitleId);
                    intent.putExtra(EmulatorActivity.EXTRA_PROFILE_ID, active.id);
                    startActivity(intent);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not prepare the emulator: " + safeMessage(t),
                        Toast.LENGTH_LONG).show());
            }
        }, "BluBox-launch").start();
    }

    private String secondDisplayStatus() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager == null) return "Display service unavailable";
        return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).length > 0
                ? "AYN Thor lower screen detected"
                : "Ready when the lower screen is available";
    }

    private void showGameOptions(GameStore.Game game) {
        boolean customCover = CoverArtStore.has(this, game.id);
        String[] choices = customCover
                ? new String[]{"Choose full cover image", "Reset automatic cover",
                "Remove from library"}
                : new String[]{"Choose full cover image", "Remove from library"};
        new AlertDialog.Builder(this)
                .setTitle(game.name)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        openCoverPicker(game);
                    } else if (customCover && which == 1) {
                        CoverArtStore.remove(this, game.id);
                        coverCache.remove(game.id);
                        showPage(currentPage);
                        Toast.makeText(this, "Automatic cover restored.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        confirmRemove(game);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmRemove(GameStore.Game game) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + game.name + "?")
                .setMessage("Only the BluBox library entry is removed. Your game file and saves stay untouched.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    CoverArtStore.remove(this, game.id);
                    coverCache.remove(game.id);
                    gameStore.remove(game.id);
                    showPage(currentPage);
                })
                .show();
    }

    private void updateProfileBadge() {
        ProfileStore.Profile active = profileStore.getActive();
        if (profileAvatar != null) profileAvatar.setProfile(active);
        if (profileName != null) profileName.setText(active.name);
        if (drawerProfileAvatar != null) drawerProfileAvatar.setProfile(active);
        if (drawerProfileName != null) drawerProfileName.setText(active.name);
    }

    private Bitmap placeholderCover() {
        int size = 300;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, size, size,
                Color.rgb(5, 67, 180), Color.rgb(23, 174, 255), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0, 0, size, size, 28, 28, paint);
        paint.setShader(null);
        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.blubox_logo);
        if (logo != null) {
            canvas.drawBitmap(logo, null,
                    new android.graphics.RectF(62, 42, 238, 218), paint);
        }
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(34);
        canvas.drawText("XBOX 360", size / 2f, 270, paint);
        return bitmap;
    }

    private String formatLabel(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".iso")) return "ISO";
        if (lower.endsWith(".xex")) return "XEX";
        if (lower.endsWith(".zar")) return "ZAR";
        return "GAME";
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackgroundResource(primary ? R.drawable.button_primary : R.drawable.button_secondary);
        return button;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isController(event.getSource()) && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
                View focus = getCurrentFocus();
                if (focus != null && focus.performClick()) return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_Y
                    && event.getRepeatCount() == 0) {
                View focus = getCurrentFocus();
                if (focus != null && focus.performLongClick()) return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
                if (drawerOpen) {
                    closeDrawer();
                    return true;
                }
                if (currentPage != PAGE_HOME) {
                    showPage(PAGE_HOME);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
        } else if (currentPage != PAGE_HOME) {
            showPage(PAGE_HOME);
        } else {
            super.onBackPressed();
        }
    }

    private static boolean isController(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
