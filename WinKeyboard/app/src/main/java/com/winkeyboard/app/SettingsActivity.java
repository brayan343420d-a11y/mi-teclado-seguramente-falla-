package com.winkeyboard.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;

/** Pantalla de ajustes: instalación, vista previa en vivo, colores, fondo, transparencia y tamaño. */
public class SettingsActivity extends Activity {

    private static final int REQ_IMG = 11;
    private static final int[] PRESETS = {
            0xFF2D2D2D, 0xFF000000, 0xFFFFFFFF, 0xFF0078D4, 0xFFE81123,
            0xFF107C10, 0xFFFFB900, 0xFF8764B8, 0xFFFF4DA6, 0xFF00B7C3
    };

    private interface IntCb {
        void on(int v);
    }

    private Prefs prefs;
    private LinearLayout page;
    private KeyboardView preview;
    private PanelBg previewBg;
    private FrameLayout previewPanel;

    private int dp(float v) {
        return prefs.dp(v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        ScrollView sv = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(24), dp(16), dp(48));
        sv.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        buildPage();
        refresh();
    }

    // ------------------------------------------------------------------
    // Construcción de la pantalla
    // ------------------------------------------------------------------
    private void buildPage() {
        TextView title = new TextView(this);
        title.setText("Teclado Win11");
        title.setTextSize(26);
        page.addView(title);

        section("1. Instalación");
        button("Activar el teclado", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("Elegirlo como teclado actual", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        section("2. Vista previa");
        previewPanel = new FrameLayout(this);
        previewBg = new PanelBg(prefs);
        previewPanel.setBackground(previewBg);
        preview = new KeyboardView(this);
        previewPanel.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(previewPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        EditText test = new EditText(this);
        test.setHint("Escribe aquí para probar el teclado");
        page.addView(test);

        section("3. Modo");
        toggle("Teclado flotante (mover y cambiar tamaño libremente)", prefs.floating(),
                (c, on) -> prefs.put("floating", on));
        toggle("Teclado transparente", prefs.transparent(), (c, on) -> {
            prefs.put("transparent", on);
            refresh();
        });
        seek("Nivel de transparencia", 0, 90, prefs.transparency(), " %", v -> {
            prefs.put("transparency", v);
            refresh();
        });
        toggle("Ocultar el espacio de la barra de navegación", prefs.hideNav(),
                (c, on) -> prefs.put("hideNav", on));
        toggle("Vibración al teclear", prefs.haptic(), (c, on) -> {
            prefs.put("haptic", on);
            refresh();
        });

        section("4. Colores");
        colorBlock("Color de las teclas", "keyColor", 0xFF2D2D2D);
        seek("Opacidad de las teclas", 0, 100, Math.round(prefs.keyAlpha() * 100f / 255f), " %", v -> {
            prefs.put("keyAlpha", Math.round(v * 255f / 100f));
            refresh();
        });
        colorBlock("Color del texto", "textColor", 0xFFFFFFFF);
        colorBlock("Color del fondo", "panelColor", 0xFF1B1B1B);
        seek("Opacidad del fondo", 0, 100, Math.round(prefs.panelAlpha() * 100f / 255f), " %", v -> {
            prefs.put("panelAlpha", Math.round(v * 255f / 100f));
            refresh();
        });

        section("5. Fondo personalizado");
        button("Elegir imagen de fondo", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, REQ_IMG);
        });
        button("Quitar imagen de fondo", v -> {
            prefs.bgFile().delete();
            refresh();
        });
        seek("Opacidad de la imagen", 0, 100, Math.round(prefs.imgAlpha() * 100f / 255f), " %", v -> {
            prefs.put("imgAlpha", Math.round(v * 255f / 100f));
            refresh();
        });

        section("6. Tamaño");
        float density = getResources().getDisplayMetrics().density;
        int screenWdp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int screenHdp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        seek("Altura (teclado acoplado)", 150, Math.max(200, Math.round(screenHdp * 0.75f)),
                Math.round(prefs.dockH() / density), " dp", v -> prefs.put("dockH", dp(v)));
        seek("Ancho (teclado flotante)", 220, Math.max(240, screenWdp),
                Math.round(prefs.floatW() / density), " dp", v -> prefs.put("floatW", dp(v)));
        seek("Altura (teclado flotante)", 130, Math.max(200, Math.round(screenHdp * 0.9f)),
                Math.round(prefs.floatH() / density), " dp", v -> prefs.put("floatH", dp(v)));
        button("Restablecer tamaño y posición", v -> {
            prefs.resetLayout();
            Toast.makeText(this, "Tamaño y posición restablecidos", Toast.LENGTH_SHORT).show();
        });

        TextView tip = new TextView(this);
        tip.setText("Consejo: en el teclado, arrastra la barra de arriba para moverlo (flotante) "
                + "o cambiar su altura (acoplado). En modo flotante, arrastra la esquina de abajo "
                + "a la derecha para cambiar el tamaño. Mantén pulsada una tecla para ver su "
                + "símbolo extra (acentos, @, |, etc.).");
        tip.setPadding(0, dp(20), 0, 0);
        page.addView(tip);
    }

    private void refresh() {
        if (preview == null) return;
        preview.applyPrefs(prefs);
        previewBg.apply(prefs, dp(8));
        preview.invalidate();
        previewPanel.invalidate();
    }

    // ------------------------------------------------------------------
    // Piezas reutilizables
    // ------------------------------------------------------------------
    private void section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, dp(24), 0, dp(6));
        page.addView(t);
    }

    private void button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        page.addView(b);
    }

    private void toggle(String label, boolean cur, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setChecked(cur);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setOnCheckedChangeListener(l);
        page.addView(s);
    }

    private void seek(final String label, final int min, int max, int cur, final String unit, final IntCb cb) {
        final TextView t = new TextView(this);
        int c = Math.max(min, Math.min(max, cur));
        t.setText(label + ": " + c + unit);
        t.setPadding(0, dp(10), 0, 0);
        page.addView(t);
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(c - min);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int v = p + min;
                t.setText(label + ": " + v + unit);
                if (fromUser) cb.on(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        page.addView(s);
    }

    private void colorBlock(String title, final String key, int def) {
        int cur = prefs.i(key, def) | 0xFF000000;

        TextView t = new TextView(this);
        t.setText(title);
        t.setPadding(0, dp(14), 0, dp(4));
        page.addView(t);

        final View swatch = new View(this);
        swatch.setBackgroundColor(cur);
        page.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        final SeekBar[] sbs = new SeekBar[3];
        final Runnable apply = () -> {
            int c = 0xFF000000 | (sbs[0].getProgress() << 16) | (sbs[1].getProgress() << 8) | sbs[2].getProgress();
            prefs.put(key, c);
            swatch.setBackgroundColor(c);
            refresh();
        };

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setPadding(0, dp(6), 0, dp(2));
        page.addView(presets);

        String[] names = {"R", "G", "B"};
        int[] vals = {(cur >> 16) & 0xFF, (cur >> 8) & 0xFF, cur & 0xFF};
        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView n = new TextView(this);
            n.setText(names[i]);
            row.addView(n, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
            SeekBar s = new SeekBar(this);
            s.setMax(255);
            s.setProgress(vals[i]);
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    apply.run();
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                }
            });
            sbs[i] = s;
            row.addView(s, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            page.addView(row);
        }

        for (final int pc : PRESETS) {
            View v = new View(this);
            v.setBackgroundColor(pc);
            v.setOnClickListener(x -> {
                sbs[0].setProgress((pc >> 16) & 0xFF);
                sbs[1].setProgress((pc >> 8) & 0xFF);
                sbs[2].setProgress(pc & 0xFF);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(6);
            presets.addView(v, lp);
        }
    }

    // ------------------------------------------------------------------
    // Imagen de fondo
    // ------------------------------------------------------------------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMG && res == RESULT_OK && data != null && data.getData() != null) {
            importImage(data.getData());
        }
    }

    private void importImage(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in2, null, o2);
            }
            if (bmp == null) throw new IllegalStateException("imagen no válida");
            try (FileOutputStream fos = new FileOutputStream(prefs.bgFile())) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bmp.recycle();
            refresh();
            Toast.makeText(this, "Fondo aplicado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo usar esa imagen", Toast.LENGTH_LONG).show();
        }
    }
}
