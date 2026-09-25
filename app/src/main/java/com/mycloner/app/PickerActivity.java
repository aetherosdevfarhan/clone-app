package com.mycloner.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PickerActivity extends Activity {
    private final List<AppItem> all = new ArrayList<>();
    private final List<AppItem> shown = new ArrayList<>();
    private AppAdapter adapter;
    private EditText search;
    private TextView status;
    private boolean loaded = false;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(24), dp(20), dp(8));
        TextView back = Ui.text(this, "\u2190", 26, R.color.text, false);
        back.setPadding(dp(12), dp(4), dp(16), dp(4));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, "Add an app", 22, R.color.text, true));

        search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextSize(15);
        search.setTextColor(getColor(R.color.text));
        search.setHintTextColor(getColor(R.color.sub));
        search.setBackground(Ui.shape(this, R.color.card, 16));
        search.setPadding(dp(18), dp(14), dp(18), dp(14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(dp(16), dp(4), dp(16), dp(8));

        TextView hint = Ui.text(this, "Tap an app to add a new clone \u2014 tap it again for a second, third, etc.",
                12, R.color.sub, false);
        hint.setPadding(dp(20), 0, dp(20), dp(8));

        status = Ui.text(this, "Loading apps\u2026", 14, R.color.sub, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), dp(40), dp(24), dp(24));

        ListView lv = new ListView(this);
        adapter = new AppAdapter(this, shown, AppAdapter.Mode.PICKER);
        lv.setAdapter(adapter);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setVerticalScrollBarEnabled(false);
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            AppItem it = shown.get(pos);
            CloneEntry added = CloneStore.add(this, it.pkg, it.label);
            it.cloneCount = CloneStore.countFor(this, it.pkg);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Added \"" + added.label + "\"", Toast.LENGTH_SHORT).show();
        });

        root.addView(bar);
        root.addView(search, sp);
        root.addView(hint);
        root.addView(status);
        root.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        new Thread(() -> {
            List<AppItem> apps = AppLoader.launchable(this);
            for (AppItem a : apps) a.cloneCount = CloneStore.countFor(this, a.pkg);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                all.addAll(apps);
                loaded = true;
                filter(search.getText().toString());
            });
        }).start();
    }

    private void filter(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppItem it : all) {
            if (s.isEmpty()
                    || it.label.toLowerCase(Locale.ROOT).contains(s)
                    || it.pkg.toLowerCase(Locale.ROOT).contains(s)) {
                shown.add(it);
            }
        }
        adapter.notifyDataSetChanged();
        if (!loaded) {
            status.setText("Loading apps\u2026");
            status.setVisibility(View.VISIBLE);
        } else if (shown.isEmpty()) {
            status.setText("No apps match");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }
}
