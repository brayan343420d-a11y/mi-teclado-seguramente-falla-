package com.winkeyboard.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** Vista que dibuja las teclas con el estilo del teclado en pantalla de Windows 11. */
public class KeyboardView extends View {

    public interface Listener {
        void onKey(Keys.Key key, boolean longPress);
    }

    private static final int ACCENT = 0xFF0078D4;

    private static class Press {
        final Keys.Key key;
        boolean consumed;
        Runnable lp;
        Runnable rep;

        Press(Keys.Key k) {
            key = k;
        }
    }

    private final List<List<Keys.Key>> rows = Keys.build();
    private Listener listener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    private boolean shift, caps, ctrl, altGr;
    private int keyColor = 0xFF2D2D2D;
    private int textColor = Color.WHITE;
    private int keyAlpha = 255;
    private float mult = 1f;
    private boolean haptic = true;

    private final float gap;
    private final float radius;

    private final SparseArray<Press> presses = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public KeyboardView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        gap = 2.5f * d;
        radius = 4f * d;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, d * 0.8f));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void applyPrefs(Prefs p) {
        keyColor = p.keyColor();
        keyAlpha = p.keyAlpha();
        textColor = p.textColor();
        mult = p.mult();
        haptic = p.haptic();
        invalidate();
    }

    public void setModifiers(boolean shift, boolean caps, boolean ctrl, boolean altGr) {
        this.shift = shift;
        this.caps = caps;
        this.ctrl = ctrl;
        this.altGr = altGr;
        invalidate();
    }

    // ---------------- distribución ----------------
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int n = rows.size();
        float rowH = (h - gap * (n + 1)) / n;
        for (int i = 0; i < n; i++) {
            List<Keys.Key> row = rows.get(i);
            float total = 0;
            for (Keys.Key k : row) total += k.w;
            float avail = w - gap * (row.size() + 1);
            float x = gap;
            float y = gap + i * (rowH + gap);
            for (Keys.Key k : row) {
                float kw = avail * k.w / total;
                k.r.set(x, y, x + kw, y + rowH);
                x += kw + gap;
            }
        }
    }

    // ---------------- dibujo ----------------
    private boolean isActive(Keys.Key k) {
        switch (k.type) {
            case Keys.SHIFT:
                return shift;
            case Keys.CAPS:
                return caps;
            case Keys.CTRL:
                return ctrl;
            case Keys.ALTGR:
                return altGr;
            default:
                return false;
        }
    }

    private boolean isPressedKey(Keys.Key k) {
        for (int i = 0; i < presses.size(); i++) {
            if (presses.valueAt(i).key == k) return true;
        }
        return false;
    }

    private static int lighten(int c, float f) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return Color.argb(Color.alpha(c), r, g, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() > 0 && rows.get(0).get(0).r.isEmpty()) layoutKeys(getWidth(), getHeight());

        boolean faded = mult < 0.9f || keyAlpha < 200;
        for (List<Keys.Key> row : rows) {
            for (Keys.Key k : row) {
                RectF r = k.r;
                boolean active = isActive(k);
                boolean pressed = isPressedKey(k);

                int base;
                if (active) {
                    base = ACCENT;
                } else if (k.type == Keys.CHAR || k.type == Keys.SPACE) {
                    base = keyColor;
                } else {
                    base = lighten(keyColor, 0.07f);
                }
                if (pressed) base = lighten(base, 0.25f);

                int a = Math.round(255 * (keyAlpha / 255f) * mult);
                if (active) a = Math.max(a, Math.round(200 * mult));
                if (pressed) a = Math.max(a, Math.round(150 * mult));
                fill.setColor((base & 0x00FFFFFF) | (Math.min(255, a) << 24));
                canvas.drawRoundRect(r, radius, radius, fill);

                if (faded) {
                    stroke.setColor(0x33FFFFFF);
                    canvas.drawRoundRect(r, radius, radius, stroke);
                }
                drawLabel(canvas, k);
            }
        }
    }

    private void drawLabel(Canvas canvas, Keys.Key k) {
        RectF r = k.r;
        if (k.type == Keys.SPACE) return;

        String main = k.type == Keys.CHAR ? k.text(shift, caps, altGr) : k.label;
        String topLeft = null;
        String topRight = null;
        if (k.type == Keys.CHAR && !k.letter && !shift && !altGr) {
            if (k.shift != null && !k.shift.equals(k.label)) topLeft = k.shift;
            if (k.alt != null) topRight = k.alt;
        }

        float h = r.height();
        float w = r.width();
        boolean single = main.codePointCount(0, main.length()) <= 1;
        float ts = single ? Math.min(h * 0.40f, w * 0.55f) : h * 0.30f;
        text.setTextSize(ts);
        float tw = text.measureText(main);
        float maxW = w * 0.88f;
        if (tw > maxW && tw > 0) text.setTextSize(ts * maxW / tw);

        int tc = textColor;
        text.setColor(tc);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline;
        boolean hasHint = topLeft != null || topRight != null;
        if (hasHint) {
            baseline = r.bottom - h * 0.16f - fm.descent;
        } else {
            baseline = r.centerY() - (fm.ascent + fm.descent) / 2f;
        }
        canvas.drawText(main, r.centerX(), baseline, text);

        if (hasHint) {
            float hs = Math.min(h * 0.24f, w * 0.34f);
            text.setTextSize(hs);
            text.setColor((tc & 0x00FFFFFF) | (150 << 24));
            Paint.FontMetrics f2 = text.getFontMetrics();
            float by = r.top + h * 0.10f - f2.ascent;
            if (topLeft != null) {
                canvas.drawText(topLeft, r.left + w * 0.26f, by, text);
            }
            if (topRight != null) {
                text.setColor((tc & 0x00FFFFFF) | (110 << 24));
                canvas.drawText(topRight, r.right - w * 0.26f, by, text);
            }
        }
    }

    // ---------------- toque ----------------
    private Keys.Key findKey(float x, float y) {
        Keys.Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Keys.Key> row : rows) {
            for (Keys.Key k : row) {
                tmp.set(k.r);
                tmp.inset(-gap, -gap);
                if (tmp.contains(x, y)) {
                    float dx = x - k.r.centerX();
                    float dy = y - k.r.centerY();
                    float d = dx * dx + dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        best = k;
                    }
                }
            }
        }
        return best;
    }

    private void fire(Keys.Key k, boolean longPress) {
        if (listener != null) listener.onKey(k, longPress);
    }

    private void startPress(final int id, final Keys.Key k) {
        final Press p = new Press(k);
        presses.put(id, p);
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (k.repeatable()) {
            p.consumed = true;
            fire(k, false);
            p.rep = new Runnable() {
                @Override
                public void run() {
                    fire(k, false);
                    handler.postDelayed(this, 45);
                }
            };
            handler.postDelayed(p.rep, 380);
        } else if (k.type == Keys.CHAR && k.alt != null) {
            p.lp = new Runnable() {
                @Override
                public void run() {
                    p.consumed = true;
                    if (haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    fire(k, true);
                    invalidate();
                }
            };
            handler.postDelayed(p.lp, 380);
        }
        invalidate();
    }

    private void endPress(int id, boolean doFire) {
        Press p = presses.get(id);
        if (p == null) return;
        if (p.lp != null) handler.removeCallbacks(p.lp);
        if (p.rep != null) handler.removeCallbacks(p.rep);
        presses.remove(id);
        if (doFire && !p.consumed) fire(p.key, false);
        invalidate();
    }

    private void cancelAll() {
        while (presses.size() > 0) {
            endPress(presses.keyAt(0), false);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                Keys.Key k = findKey(e.getX(i), e.getY(i));
                if (k != null) startPress(e.getPointerId(i), k);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    Press p = presses.get(id);
                    if (p != null) {
                        tmp.set(p.key.r);
                        tmp.inset(-gap, -gap);
                        if (!tmp.contains(e.getX(i), e.getY(i))) endPress(id, false);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                endPress(e.getPointerId(e.getActionIndex()), true);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAll();
        super.onDetachedFromWindow();
    }
}
