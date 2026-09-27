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
    private List<Item> items = new ArrayList<>();

    public OverlayView(Context c) {
        super(c);
        bg.setColor(Color.argb(205, 20, 20, 20));
        fg.setColor(Color.WHITE);
        fg.setTypeface(android.graphics.Typeface.create(
                "sans", android.graphics.Typeface.BOLD));
        setWillNotDraw(false);
    }

    public void setItems(List<Item> next) {
        items = next == null ? new ArrayList<>() : next;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        for (Item it : items) {
            if (it.box == null || it.text == null || it.text.trim().isEmpty()) continue;
            Rect b = new Rect(it.box);
            int pad = Math.round(4 * density);
            b.inset(-pad, -pad);
            RectF rf = new RectF(b);
            canvas.drawRoundRect(rf, 7 * density, 7 * density, bg);

            float maxSize = Math.max(
                    12 * density,
                    Math.min(24 * density, b.height() * 0.72f));
            fg.setTextSize(maxSize);
            float maxWidth = Math.max(40, b.width() - pad * 2f);
            List<String> lines = wrap(it.text, fg, maxWidth);
            float lineH = fg.getFontMetrics().bottom - fg.getFontMetrics().top + 2 * density;
            float total = lineH * lines.size();
            float y = b.centerY() - total / 2f - fg.getFontMetrics().top;
            for (String line : lines) {
                canvas.drawText(line, b.left + pad, y, fg);
                y += lineH;
            }
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
