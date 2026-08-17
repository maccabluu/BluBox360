package uk.co.blustudio.blups2;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 12, 28));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        header.setBackgroundColor(Color.rgb(8, 24, 48));
        TextView title = text("BluPS2", 26, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1f));
        Button add = button("+ Add Games");
        header.addView(add, new LinearLayout.LayoutParams(dp(150), dp(50)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(82)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(18), dp(22), dp(30));

        page.addView(text("PS2 Library", 24, Color.WHITE, true));
        page.addView(text("0 games • ARM64 Preview", 12, Color.rgb(125, 195, 255), false));

        LinearLayout quick = card();
        quick.addView(text("BluPS2 0.1 Preview", 19, Color.WHITE, true));
        quick.addView(text("Frontend groundwork for a real PS2 core integration.", 12, Color.LTGRAY, false));
        quick.addView(button("Choose Game Folder"));
        page.addView(quick);

        LinearLayout features = card();
        features.addView(text("Coming across from BluBox", 17, Color.WHITE, true));
        features.addView(text("Profiles and saves\nGame covers\nController support\nFPS counter\nThermal monitoring\nSmart Heat Guard\nPer-game settings\nDiagnostics\nUpdate checker", 13, Color.LTGRAY, false));
        page.addView(features);

        LinearLayout status = card();
        status.addView(text("Core status", 17, Color.WHITE, true));
        status.addView(text("PS2 execution: not connected yet\nTarget core: Play! Android\nTarget device: ARM64 Snapdragon handheld", 13, Color.LTGRAY, false));
        page.addView(status);

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundColor(Color.rgb(13, 36, 68));
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
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(20, 105, 195));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
