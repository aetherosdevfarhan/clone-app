package com.mycloner.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class GuestInfoActivity extends Activity {
    private LinearLayout steps;
    private TextView verdict;
    private TextView launch;
    private String report = "";
    private GuestLoader.Result lastResult;
    private CloneEntry entry;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.bars(this);
        final String pkg = getIntent().getStringExtra("pkg");
        final String cloneId = getIntent().getStringExtra("cloneId");
        entry = cloneId == null ? null : CloneStore.byId(this, cloneId);
        if (entry == null && pkg != null && cloneId != null) {
            // shouldn't normally happen, but keep the screen usable rather than bailing out
            entry = new CloneEntry(cloneId, pkg, 1, pkg);
        }
        AppItem app = pkg == null ? null : AppLoader.byPackage(this, pkg);
        if (app == null || entry == null) { finish(); return; }

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
        bar.addView(Ui.text(this, "Guest loader", 22, R.color.text, true));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(32));
        sv.addView(body);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(16), dp(16), dp(16));
        head.setBackground(Ui.shape(this, R.color.card, 18));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(app.icon);
        head.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.setPadding(dp(14), 0, 0, 0);
        hc.addView(Ui.text(this, entry.label, 20, R.color.text, true));
        hc.addView(Ui.text(this, app.pkg + " \u00B7 clone #" + entry.slot, 12, R.color.sub, false));
        head.addView(hc);
        body.addView(head);

        verdict = Ui.text(this, "Running checks\u2026", 15, R.color.accent, true);
        verdict.setPadding(dp(6), dp(16), dp(6), dp(8));
        body.addView(verdict);

        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        body.addView(steps);

        launch = Ui.text(this, "Launch (experimental)", 15, R.color.on_accent, true);
        launch.setGravity(Gravity.CENTER);
        launch.setPadding(dp(16), dp(14), dp(16), dp(14));
        launch.setBackground(Ui.shape(this, R.color.accent, 16));
        launch.setVisibility(android.view.View.GONE);
        launch.setOnClickListener(v -> attemptLaunch());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(16);
        body.addView(launch, lp2);

        TextView copy = Ui.text(this, "Copy report", 15, R.color.on_accent, true);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(16), dp(14), dp(16), dp(14));
        copy.setBackground(Ui.shape(this, R.color.accent, 16));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("guest report", report));
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(16);
        body.addView(copy, cp);

        root.addView(bar);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        final CloneEntry entryForLoad = entry;
        new Thread(() -> {
            GuestLoader.Result res = GuestLoader.load(this, entryForLoad);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) render(res);
            });
        }).start();
    }

    private void render(GuestLoader.Result r) {
        lastResult = r;
        boolean launchable = r.loader != null && r.launcherClass != null
                && r.launcherName != null && r.guestContext != null
                && Activity.class.isAssignableFrom(r.launcherClass);
        launch.setVisibility(launchable ? android.view.View.VISIBLE : android.view.View.GONE);

        StringBuilder sb = new StringBuilder();
        sb.append("Clone App guest report\n")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android API ").append(Build.VERSION.SDK_INT).append("\n\n");
        steps.removeAllViews();

        for (GuestLoader.Step s : r.steps) {
            String tag = s.state == GuestLoader.OK ? "OK"
                    : s.state == GuestLoader.FAIL ? "FAIL" : "INFO";
            int color = s.state == GuestLoader.OK ? 0xFF2EB67D
                    : s.state == GuestLoader.FAIL ? 0xFFE5484D : 0xFF7A7F94;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(Ui.shape(this, R.color.card, 14));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView pill = Ui.text(this, tag, 11, R.color.on_accent, true);
            pill.setTextColor(0xFFFFFFFF);
            GradientDrawable pg = new GradientDrawable();
            pg.setColor(color);
            pg.setCornerRadius(dp(8));
            pill.setBackground(pg);
            pill.setPadding(dp(8), dp(3), dp(8), dp(3));

            TextView nm = Ui.text(this, s.name, 15, R.color.text, true);
            nm.setPadding(dp(10), 0, 0, 0);
            top.addView(pill);
            top.addView(nm);

            TextView dt = Ui.text(this, s.detail, 12, R.color.sub, false);
            dt.setTypeface(Typeface.MONOSPACE);
            dt.setTextIsSelectable(true);
            dt.setPadding(0, dp(8), 0, 0);

            card.addView(top);
            card.addView(dt);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            steps.addView(card, lp);

            sb.append(tag).append(" | ").append(s.name).append(": ")
                    .append(s.detail).append('\n');
        }
        verdict.setText(r.verdict);
        sb.append('\n').append(r.verdict);
        report = sb.toString();
    }

    private void attemptLaunch() {
        if (lastResult == null || entry == null) return;

        HiddenApi.exempt();
        boolean hooked = InstrumentationHook.install();
        if (!hooked) {
            Toast.makeText(this,
                    "Hook blocked on this Android version: " + InstrumentationHook.getFailureReason(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        LaunchRegistry.register(entry.id, lastResult.loader, lastResult.guestContext, lastResult.appClassName);

        android.content.ComponentName stub = StubSlots.componentFor(this, entry.id);
        android.content.Intent i = new android.content.Intent();
        i.setComponent(stub);
        i.putExtra(LaunchRegistry.EXTRA_TARGET_CLASS, lastResult.launcherName);
        i.putExtra(LaunchRegistry.EXTRA_CLONE_ID, entry.id);
        i.putExtra(LaunchRegistry.EXTRA_TARGET_PKG, entry.pkg);
        startActivity(i);
    }
}
