package com.winkeyboard.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.io.File;

/** Fondo del teclado: color + imagen personalizada opcional, con transparencia. */
public class PanelBg extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private int color = 0xFF1B1B1B;
    private int imgAlpha = 255;
    private float radius = 0f;
    private Bitmap bmp;
    private long stamp = -1;

    public PanelBg(Prefs p) {
        apply(p, 0f);
    }

    public void apply(Prefs p, float radiusPx) {
        radius = radiusPx;
        float m = p.mult();
        int a = Math.round(p.panelAlpha() * m);
        color = (p.panelColor() & 0x00FFFFFF) | (a << 24);
        imgAlpha = Math.round(p.imgAlpha() * m);
        File f = p.bgFile();
        if (f.exists()) {
            long lm = f.lastModified();
            if (bmp == null || lm != stamp) {
                stamp = lm;
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            }
        } else {
            bmp = null;
            stamp = -1;
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        rect.set(b);
        c.save();
        if (radius > 0) {
            clip.reset();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            c.clipPath(clip);
        }
        if (Color.alpha(color) > 0) {
            paint.setAlpha(255);
            paint.setColor(color);
            c.drawRect(rect, paint);
        }
        if (bmp != null && imgAlpha > 0) {
            // recorte "centrar y llenar"
            float bw = bmp.getWidth(), bh = bmp.getHeight();
            float targetRatio = rect.width() / rect.height();
            float srcW, srcH;
            if (bw / bh > targetRatio) {
                srcH = bh;
                srcW = bh * targetRatio;
            } else {
                srcW = bw;
                srcH = bw / targetRatio;
            }
            int l = (int) ((bw - srcW) / 2f);
            int t = (int) ((bh - srcH) / 2f);
            src.set(l, t, (int) (l + srcW), (int) (t + srcH));
            paint.setColor(Color.WHITE);
            paint.setAlpha(Math.min(255, imgAlpha));
            c.drawBitmap(bmp, src, rect, paint);
        }
        c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
