package com.winkeyboard.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Todas las opciones del teclado en un solo lugar. */
public class Prefs {
    public final Context ctx;
    public final SharedPreferences sp;

    public Prefs(Context c) {
        Context app = c.getApplicationContext();
        ctx = app != null ? app : c;
        sp = ctx.getSharedPreferences("kbprefs", Context.MODE_PRIVATE);
    }

    // ---------- utilidades ----------
    public int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    public int screenW() {
        return ctx.getResources().getDisplayMetrics().widthPixels;
    }

    public int screenH() {
        return ctx.getResources().getDisplayMetrics().heightPixels;
    }

    public int i(String k, int d) {
        return sp.getInt(k, d);
    }

    public boolean b(String k, boolean d) {
        return sp.getBoolean(k, d);
    }

    public void put(String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    public void put(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) hi = lo;
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------- modos ----------
    public boolean floating() {
        return b("floating", false);
    }

    public boolean transparent() {
        return b("transparent", false);
    }

    /** 0 a 90: cuánto se desvanece todo el teclado en modo transparente. */
    public int transparency() {
        return i("transparency", 60);
    }

    public boolean hideNav() {
        return b("hideNav", true);
    }

    public boolean haptic() {
        return b("haptic", true);
    }

    /** Multiplicador de opacidad: 1 normal, menor en modo transparente. */
    public float mult() {
        return transparent() ? (100 - transparency()) / 100f : 1f;
    }

    // ---------- colores ----------
    public int keyColor() {
        return i("keyColor", 0xFF2D2D2D);
    }

    public int keyAlpha() {
        return i("keyAlpha", 255);
    }

    public int textColor() {
        return i("textColor", 0xFFFFFFFF);
    }

    public int panelColor() {
        return i("panelColor", 0xFF1B1B1B);
    }

    public int panelAlpha() {
        return i("panelAlpha", 255);
    }

    public int imgAlpha() {
        return i("imgAlpha", 255);
    }

    public File bgFile() {
        return new File(ctx.getFilesDir(), "bg.png");
    }

    // ---------- tamaños y posición ----------
    public int dockH() {
        return clamp(i("dockH", dp(270)), dp(150), (int) (screenH() * 0.75f));
    }

    public int floatW() {
        int def = Math.min((int) (screenW() * 0.9f), dp(520));
        return clamp(i("floatW", def), dp(220), screenW());
    }

    public int floatH() {
        return clamp(i("floatH", dp(250)), dp(130), (int) (screenH() * 0.9f));
    }

    public int floatX() {
        return clamp(i("floatX", (screenW() - floatW()) / 2), 0, screenW() - floatW());
    }

    public int floatY() {
        return clamp(i("floatY", screenH() / 4), 0, screenH() - floatH());
    }

    public void resetLayout() {
        sp.edit().remove("dockH").remove("floatW").remove("floatH")
                .remove("floatX").remove("floatY").apply();
    }
}
