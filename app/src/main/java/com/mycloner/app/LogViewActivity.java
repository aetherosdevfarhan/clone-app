package com.mycloner.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LogViewActivity extends Activity {
    private String log = "";

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
        bar.addView(Ui.text(this, "Device log", 20, R.color.text, true));
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        TextView tv = Ui.text(this, "Capturing\u2026", 11, R.color.text, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(dp(16), dp(8), dp(16), dp(16));
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(16));

        TextView clear = Ui.text(this, "Clear crash log", 14, R.color.text, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(16), dp(14), dp(16), dp(14));
        clear.setBackground(Ui.shape(this, R.color.card, 16));
        clear.setOnClickListener(v -> {
            CrashLog.clear(this);
            Toast.makeText(this, "Crash log cleared - relaunch the clone to test again", Toast.LENGTH_SHORT).show();
            recreate();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.rightMargin = dp(8);
        row.addView(clear, clp);

        TextView copy = Ui.text(this, "Copy all", 14, R.color.on_accent, true);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(16), dp(14), dp(16), dp(14));
        copy.setBackground(Ui.shape(this, R.color.accent, 16));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("log", log));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams cop = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cop.leftMargin = dp(8);
        row.addView(copy, cop);

        root.addView(row);
        setContentView(root);

        new Thread(() -> {
            String captured = capture();
            runOnUiThread(() -> {
                log = captured;
                tv.setText(captured.isEmpty() ? "(nothing captured yet)" : captured);
            });
        }).start();
    }

    private String capture() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Crash log (in-process, reliable) ===\n");
        String crashes = CrashLog.read(this);
        sb.append(crashes.isEmpty()
                ? "(no uncaught exceptions recorded - if Discord is hanging with no crash, that's expected here)\n"
                : crashes);

        sb.append("\n=== logcat --pid=<self> (best effort, may be blocked on this ROM) ===\n");
        try {
            int pid = android.os.Process.myPid();
            Process proc = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-v", "time", "--pid=" + pid});
            BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
            int from = Math.max(0, lines.size() - 400);
            if (lines.isEmpty()) {
                sb.append("(no lines - either nothing logged, or this device blocks logcat exec for apps)\n");
            } else {
                for (int i = from; i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
            }
        } catch (Throwable t) {
            sb.append("Could not run logcat: ").append(t).append('\n');
        }
        return sb.toString();
    }
}
