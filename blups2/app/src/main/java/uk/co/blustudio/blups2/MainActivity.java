package uk.co.blustudio.blups2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int PICK_GAME = 1001;
    private static final int PICK_BIOS = 1002;
    private TextView selectedGame;
    private TextView selectedBios;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(4, 9, 20));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        header.setBackgroundColor(Color.rgb(7, 20, 42));
        header.addView(text("BluPS2", 27, Color.WHITE, true), new LinearLayout.LayoutParams(0, dp(58), 1f));
        TextView version = text("0.1 Preview", 12, Color.rgb(65, 160, 255), true);
        header.addView(version);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(82)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(18), dp(22), dp(30));
        page.addView(text("PS2 Library", 24, Color.WHITE, true));
        page.addView(text("ARM64 Android • Vulkan target • Controller ready", 12, Color.rgb(125,195,255), false));

        LinearLayout game = card();
        game.addView(text("Game", 18, Color.WHITE, true));
        selectedGame = text("No PS2 game selected", 13, Color.LTGRAY, false);
        game.addView(selectedGame);
        Button chooseGame = button("Choose ISO / BIN / CHD");
        chooseGame.setOnClickListener(v -> chooseFile(PICK_GAME, new String[]{"application/octet-stream", "application/x-iso9660-image"}));
        game.addView(chooseGame);
        page.addView(game);

        LinearLayout bios = card();
        bios.addView(text("BIOS", 18, Color.WHITE, true));
        selectedBios = text("No BIOS selected", 13, Color.LTGRAY, false);
        bios.addView(selectedBios);
        Button chooseBios = button("Choose legally dumped PS2 BIOS");
        chooseBios.setOnClickListener(v -> chooseFile(PICK_BIOS, new String[]{"application/octet-stream"}));
        bios.addView(chooseBios);
        page.addView(bios);

        LinearLayout play = card();
        play.addView(text("Emulation", 18, Color.WHITE, true));
        play.addView(text("Renderer: Vulkan preferred\nArchitecture: ARM64-v8a\nSmart Heat Guard: planned\nFPS counter: planned", 13, Color.LTGRAY, false));
        Button launch = button("Launch with PS2 Core");
        launch.setOnClickListener(v -> launchCore());
        play.addView(launch);
        page.addView(play);

        LinearLayout status = card();
        status.addView(text("Core status", 18, Color.WHITE, true));
        status.addView(text("BluPS2 now has game and BIOS selection. Native PS2 execution still requires the Play! core to be compiled and linked into this app. The button will not claim a game boot until that core is present.", 13, Color.LTGRAY, false));
        page.addView(status);

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void chooseFile(int requestCode, String[] types) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        if (requestCode == PICK_GAME) selectedGame.setText("Selected: " + uri.getLastPathSegment());
        if (requestCode == PICK_BIOS) selectedBios.setText("Selected: " + uri.getLastPathSegment());
    }

    private void launchCore() {
        Toast.makeText(this, "Native PS2 core integration is the next build step.", Toast.LENGTH_LONG).show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundColor(Color.rgb(11, 30, 58));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(16, 102, 205));
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
