package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visual language for the production UI. */
public final class UiKit {
    public static final int NAVY = Color.rgb(15, 23, 42);
    public static final int NAVY_SOFT = Color.rgb(30, 41, 59);
    public static final int BLUE = Color.rgb(37, 99, 235);
    public static final int BLUE_DARK = Color.rgb(29, 78, 216);
    public static final int BLUE_SOFT = Color.rgb(239, 246, 255);
    public static final int GREEN = Color.rgb(22, 163, 74);
    public static final int GREEN_SOFT = Color.rgb(240, 253, 244);
    public static final int AMBER = Color.rgb(217, 119, 6);
    public static final int AMBER_SOFT = Color.rgb(255, 251, 235);
    public static final int RED = Color.rgb(220, 38, 38);
    public static final int RED_SOFT = Color.rgb(254, 242, 242);
    public static final int TEXT = Color.rgb(15, 23, 42);
    public static final int MUTED = Color.rgb(100, 116, 139);
    public static final int BORDER = Color.rgb(226, 232, 240);
    public static final int SURFACE = Color.WHITE;
    public static final int BG = Color.rgb(248, 250, 252);

    private UiKit() {}

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    public static GradientDrawable outlined(int fill, int stroke, float radiusDp, Context context) {
        GradientDrawable d = rounded(fill, radiusDp, context);
        d.setStroke(dp(context, 1), stroke);
        return d;
    }

    public static GradientDrawable gradient(int start, int end, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end}
        );
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    public static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLineSpacing(0f, 1.08f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static TextView button(Context c, String label, boolean primary) {
        TextView b = text(c, label, 14, primary ? Color.WHITE : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        b.setMinHeight(dp(c, 50));
        b.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        b.setBackground(primary
                ? rounded(BLUE, 14, c)
                : outlined(Color.WHITE, BORDER, 14, c));
        return b;
    }

    public static TextView sectionTitle(Context c, String title) {
        return text(c, title, 13, TEXT, true);
    }

    public static TextView sectionHint(Context c, String hint) {
        return text(c, hint, 11, MUTED, false);
    }

    public static LinearLayout.LayoutParams full(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        p.topMargin = dp(c, topDp);
        return p;
    }

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static void elevation(View v, float dp) {
        v.setElevation(dp);
    }
}
