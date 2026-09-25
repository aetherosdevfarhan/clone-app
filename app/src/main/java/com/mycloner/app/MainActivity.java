package com.mycloner.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private final List<AppItem> items = new ArrayList<>();
    private AppAdapter adapter;
    private ListView lv;
    private View empty;
    private TextView subtitle;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(32), dp(24), dp(12));
        header.addView(Ui.text(this, "Clone App", 30, R.color.text, true));
        subtitle = Ui.text(this, "", 14, R.color.sub, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        header.addView(subtitle);

        lv = new ListView(this);
        adapter = new AppAdapter(this, items, AppAdapter.Mode.MAIN);
        lv.setAdapter(adapter);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, dp(100));
        lv.setVerticalScrollBarEnabled(false);
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            AppItem it = items.get(pos);
            Intent gi = new Intent(this, GuestInfoActivity.class);
            gi.putExtra("pkg", it.pkg);
            gi.putExtra("cloneId", it.cloneId);
            startActivity(gi);
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            AppItem it = items.get(pos);
            new AlertDialog.Builder(this)
                    .setTitle("Remove " + it.label + "?")
                    .setPositiveButton("Remove", (d, w) -> {
                        CloneStore.remove(this, it.cloneId);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        empty = buildEmpty();

        col.addView(header);
        col.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        col.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView fab = Ui.text(this, "+", 30, R.color.on_accent, false);
        fab.setGravity(Gravity.CENTER);
        fab.setIncludeFontPadding(false);
        fab.setBackground(Ui.shape(this, R.color.accent, 30));
        fab.setElevation(dp(6));
        fab.setOnClickListener(v -> startActivity(new Intent(this, PickerActivity.class)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, dp(22), dp(26));
        root.addView(fab, fp);

        setContentView(root);
    }

    private View buildEmpty() {
        LinearLayout e = new LinearLayout(this);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(40), 0, dp(40), dp(80));

        TextView badge = Ui.text(this, "+", 40, R.color.accent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(Ui.shape(this, R.color.accent_soft, 48));
        e.addView(badge, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView t1 = Ui.text(this, "Nothing cloned yet", 20, R.color.text, true);
        t1.setGravity(Gravity.CENTER);
        t1.setPadding(0, dp(20), 0, dp(6));
        e.addView(t1);

        TextView t2 = Ui.text(this,
                "Tap the + button to add an app. You can add the same app more than once to create multiple clones.",
                14, R.color.sub, false);
        t2.setGravity(Gravity.CENTER);
        e.addView(t2);
        return e;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items.clear();
        for (CloneEntry ce : CloneStore.all(this)) {
            AppItem base = AppLoader.byPackage(this, ce.pkg);
            if (base == null) {
                CloneStore.remove(this, ce.id); // underlying app was uninstalled
                continue;
            }
            AppItem row = new AppItem(ce.pkg, ce.label, base.icon, base.apkPath);
            row.cloneId = ce.id;
            items.add(row);
        }
        Collections.sort(items, (a, c) -> a.label.compareToIgnoreCase(c.label));
        adapter.notifyDataSetChanged();

        int n = items.size();
        subtitle.setText(n == 0 ? "Your private clone space"
                : n + (n == 1 ? " clone" : " clones") + " \u00B7 long-press to remove");
        lv.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
    }
}
