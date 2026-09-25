package com.mycloner.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;

/**
 * Base for the pooled stub activities (StubActivity1..StubActivity5,
 * declared in the manifest). GuestInstrumentation substitutes the real
 * guest Activity in place of whichever concrete subclass Android
 * instantiates; if that substitution didn't happen (hook blocked,
 * class not found, etc.) this fallback UI renders instead so you can
 * see why.
 */
public class StubActivityBase extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(32), dp(32), dp(32));
        root.setBackgroundColor(getColor(R.color.bg));

        String pkg = getIntent() != null ? getIntent().getStringExtra(LaunchRegistry.EXTRA_TARGET_PKG) : null;
        String reason = InstrumentationHook.getFailureReason();
        String msg = "The proxy launch fell back to the stub - the guest activity for "
                + (pkg != null ? pkg : "this clone") + " was not substituted.\n\n"
                + (reason != null ? "Hook error: " + reason
                : "Instrumentation hook not installed.");

        android.widget.TextView tv = Ui.text(this, msg, 14, R.color.text, false);
        tv.setTextIsSelectable(true);
        root.addView(tv);
        setContentView(root);
    }

    private int dp(float v) { return Ui.dp(this, v); }
}
