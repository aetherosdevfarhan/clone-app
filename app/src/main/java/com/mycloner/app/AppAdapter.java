package com.mycloner.app;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class AppAdapter extends BaseAdapter {
    /** MAIN: existing clones (tap opens diagnostics, pill = "Open"). PICKER: installable apps (tap always adds a new clone slot, pill = "Add"). */
    public enum Mode { MAIN, PICKER }

    private final Context ctx;
    private final List<AppItem> items;
    private final Mode mode;

    public AppAdapter(Context ctx, List<AppItem> items, Mode mode) {
        this.ctx = ctx;
        this.items = items;
        this.mode = mode;
    }

    private int dp(float v) { return Ui.dp(ctx, v); }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int p) { return items.get(p); }
    @Override public long getItemId(int p) { return p; }

    private static class Holder {
        LinearLayout card;
        ImageView icon;
        TextView name, pkg, pill;
    }

    @Override
    public View getView(int p, View convert, ViewGroup parent) {
        AppItem it = items.get(p);
        Holder h;
        View wrap;

        if (convert == null) {
            wrap = buildRow();
            h = new Holder();
            h.card = (LinearLayout) ((FrameLayout) wrap).getChildAt(0);
            h.icon = (ImageView) h.card.getChildAt(0);
            LinearLayout col = (LinearLayout) h.card.getChildAt(1);
            h.name = (TextView) col.getChildAt(0);
            h.pkg = (TextView) col.getChildAt(1);
            h.pill = (TextView) h.card.getChildAt(2);
            wrap.setTag(h);
        } else {
            wrap = convert;
            h = (Holder) wrap.getTag();
        }

        h.icon.setImageDrawable(it.icon);
        h.name.setText(it.label);

        String sub = it.pkg;
        if (mode == Mode.PICKER && it.cloneCount > 0) {
            sub += " \u00B7 " + it.cloneCount + " clone" + (it.cloneCount > 1 ? "s" : "") + " added";
        }
        h.pkg.setText(sub);

        boolean picker = mode == Mode.PICKER;
        h.pill.setText(picker ? "Add" : "Open");
        h.pill.setTextColor(ctx.getColor(R.color.accent));
        h.pill.setBackground(Ui.shape(ctx, R.color.accent_soft, 20));

        return wrap;
    }

    private View buildRow() {
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(dp(16), dp(5), dp(16), dp(5));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.pressable(ctx, R.color.card, 18));
        card.setDuplicateParentStateEnabled(true);

        ImageView iv = new ImageView(ctx);
        card.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(ctx, "", 16, R.color.text, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView pkg = Ui.text(ctx, "", 12, R.color.sub, false);
        pkg.setSingleLine(true);
        pkg.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        col.addView(pkg);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(14), 0, dp(10), 0);
        card.addView(col, cp);

        TextView pill = Ui.text(ctx, "", 12, R.color.accent, true);
        pill.setPadding(dp(14), dp(7), dp(14), dp(7));
        card.addView(pill);

        wrap.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }
}
