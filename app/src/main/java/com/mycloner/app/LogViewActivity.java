package com.mycloner.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Process;
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

        TextView copy = Ui.text(this, "Copy log", 15, R.color.on_accent, true);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(16), dp(14), dp(16), dp(14));
        copy.setBackground(Ui.shape(this, R.color.accent, 16));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("log", log));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(16);
        cp.rightMargin = dp(16);
        cp.bottomMargin = dp(16);
        cp.topMargin = dp(8);
        root.addView(copy, cp);

        setContentView(root);

        new Thread(() -> {
            String captured = capture();
            runOnUiThread(() -> {
                log = captured;
                tv.setText(captured.isEmpty() ? "(no log lines captured)" : captured);
            });
        }).start();
    }

    private String capture() {
        StringBuilder sb = new StringBuilder();
        try {
            int pid = Process.myPid();
            Process proc = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-v", "time", "--pid=" + pid});
            BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
            int from = Math.max(0, lines.size() - 600);
            for (int i = from; i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
            if (lines.isEmpty()) {
                sb.append("No lines came back. On some ROMs an app can't read even its ")
                        .append("own logcat. If this stays empty, connect the phone to a PC ")
                        .append("and run: adb logcat --pid=").append(pid);
            }
        } catch (Throwable t) {
            sb.append("Could not run logcat: ").append(t).append('\n');
        }
        return sb.toString();
    }
}
