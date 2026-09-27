package com.oai.thaiscreentranslate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    public static class Item {
        public final Rect box;
        public final String text;
        public Item(Rect box, String text) {
            this.box = box;
            this.text = text;
        }
    }

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusFg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Item> items = new ArrayList<>();
    private String status = "เริ่มระบบ...";
    private int sourceWidth = 1;
    private int sourceHeight = 1;

    public OverlayView(Context c) {
        super(c);
        setBackgroundColor(Color.TRANSPARENT);
        bg.setColor(Color.argb(210, 18, 18, 18));
        fg.setColor(Color.WHITE);
        fg.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        statusBg.setColor(Color.argb(220, 255, 122, 0));
        statusFg.setColor(Color.WHITE);
        statusFg.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        setWillNotDraw(false);
    }

    public void setSourceSize(int w, int h) {
        sourceWidth = Math.max(1, w);
        sourceHeight = Math.max(1, h);
    }

    public void setItems(List<Item> next) {
        items = next == null ? new ArrayList<>() : next;
        invalidate();
    }

    public void setStatus(String next) {
        status = next == null ? "" : next;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float sx = getWidth() > 0 ? (float) getWidth() / (float) sourceWidth : 1f;
        float sy = getHeight() > 0 ? (float) getHeight() / (float) sourceHeight : 1f;

        for (Item it : items) {
            if (it.box == null || it.text == null || it.text.trim().isEmpty()) continue;

            Rect b = new Rect(
                    Math.round(it.box.left * sx),
                    Math.round(it.box.top * sy),
                    Math.round(it.box.right * sx),
                    Math.round(it.box.bottom * sy)
            );

            int pad = Math.round(5 * density);
            b.inset(-pad, -pad);
            b.left = Math.max(0, b.left);
            b.top = Math.max(0, b.top);
            b.right = Math.min(getWidth(), b.right);
            b.bottom = Math.min(getHeight(), b.bottom);

            RectF rf = new RectF(b);
            canvas.drawRoundRect(rf, 7 * density, 7 * density, bg);

            float maxSize = Math.max(
                    12 * density,
                    Math.min(23 * density, Math.max(20, b.height()) * 0.68f));
            fg.setTextSize(maxSize);

            float maxWidth = Math.max(50, b.width() - pad * 2f);
            List<String> lines = wrap(it.text, fg, maxWidth);
            float lineH = fg.getFontMetrics().bottom - fg.getFontMetrics().top + 2 * density;
            float total = lineH * lines.size();
            float y = b.centerY() - total / 2f - fg.getFontMetrics().top;

            for (String line : lines) {
                canvas.drawText(line, b.left + pad, y, fg);
                y += lineH;
            }
        }

        if (status != null && !status.isEmpty()) {
            statusFg.setTextSize(13 * density);
            float pad = 7 * density;
            float textW = statusFg.measureText(status);
            float top = 34 * density;
            RectF r = new RectF(
                    8 * density,
                    top,
                    Math.min(getWidth() - 8 * density, 8 * density + textW + pad * 2),
                    top + 31 * density
            );
            canvas.drawRoundRect(r, 12 * density, 12 * density, statusBg);
            canvas.drawText(status, r.left + pad, r.centerY() - (statusFg.ascent() + statusFg.descent()) / 2f, statusFg);
        }
    }

    private List<String> wrap(String s, Paint p, float max) {
        List<String> out = new ArrayList<>();
        String text = s.replace('\n', ' ').trim();
        if (text.isEmpty()) return out;

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String test = line.toString() + c;
            if (p.measureText(test) > max && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(c);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
