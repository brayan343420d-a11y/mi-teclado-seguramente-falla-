package com.winkeyboard.app;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Teclado estilo Windows 11 con modo flotante (mover y cambiar de tamaño libremente),
 * emojis de Google, fondo personalizado y modo transparente.
 */
public class KeyboardService extends InputMethodService implements KeyboardView.Listener {

    private Prefs prefs;
    private FrameLayout root;
    private FrameLayout panel;
    private LinearLayout toolbar;
    private PanelBg bg;
    private KeyboardView kv;
    private EmojiPanel emoji;
    private View grip;
    private View resizeHandle;
    private TextView btnEmoji, btnTransp, btnFloat, btnSettings, btnHide;

    private boolean floating;
    private boolean shift, caps, ctrl, altGr;

    // estado de arrastre
    private float dragX, dragY;
    private int dragStartX, dragStartY, dragStartW, dragStartH;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) hi = lo;
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------
    @Override
    public View onCreateInputView() {
        prefs = new Prefs(this);
        buildUi();
        applyAll();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (prefs == null || root == null) return;
        shift = false;
        ctrl = false;
        altGr = false;
        if (!restarting) caps = false;
        kv.setModifiers(shift, caps, ctrl, altGr);
        if (!restarting) showEmoji(false);
        applyAll();
        applyWindowStyle();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        if (emoji != null) showEmoji(false);
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    /**
     * Le dice a Android qué parte de la pantalla ocupa el teclado, para que
     * las apps no se reduzcan en modo flotante y los toques fuera del teclado pasen a la app.
     */
    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (root == null || panel == null || panel.getHeight() == 0) return;
        int[] loc = new int[2];
        root.getLocationInWindow(loc);
        int left = loc[0] + panel.getLeft();
        int top = loc[1] + panel.getTop();
        int right = loc[0] + panel.getRight();
        int bottom = loc[1] + panel.getBottom();
        int inset = floating ? loc[1] + root.getHeight() : top;
        outInsets.contentTopInsets = inset;
        outInsets.visibleTopInsets = inset;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(left, top, right, bottom);
    }

    /** Barra de navegación transparente y, si se quiere, el teclado puede llegar hasta abajo del todo. */
    private void applyWindowStyle() {
        try {
            if (getWindow() == null) return;
            Window w = getWindow().getWindow();
            if (w == null) return;
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
            if (prefs.hideNav()) {
                w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Construcción de la interfaz
    // ------------------------------------------------------------------
    private TextView makeBtn(String label, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        tv.setOnClickListener(l);
        toolbar.addView(tv, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT));
        return tv;
    }

    private void buildUi() {
        root = new FrameLayout(this);

        panel = new FrameLayout(this);
        bg = new PanelBg(prefs);
        panel.setBackground(bg);
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, prefs.dockH(), Gravity.BOTTOM));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        panel.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- barra superior ---
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        grip = new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                p.setColor((prefs.textColor() & 0x00FFFFFF) | (0x77 << 24));
                float w = getWidth(), h = getHeight();
                c.drawRoundRect(w / 2f - dp(24), h / 2f - dp(2), w / 2f + dp(24), h / 2f + dp(2),
                        dp(2), dp(2), p);
            }
        };
        toolbar.addView(grip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        grip.setOnTouchListener((v, e) -> onGripTouch(e));

        btnEmoji = makeBtn("😀", v -> showEmoji(emoji.getVisibility() != View.VISIBLE));
        btnTransp = makeBtn("💧", v -> {
            prefs.put("transparent", !prefs.transparent());
            applyAll();
        });
        btnFloat = makeBtn("🎈", v -> {
            prefs.put("floating", !prefs.floating());
            applyAll();
        });
        btnSettings = makeBtn("⚙️", v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        btnHide = makeBtn("▼", v -> requestHideSelf(0));

        // --- contenido: teclas o emojis ---
        FrameLayout content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        kv = new KeyboardView(this);
        kv.setListener(this);
        content.addView(kv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emoji = new EmojiPanel(this, prefs);
        emoji.setVisibility(View.GONE);
        emoji.setListener(new EmojiPanel.Listener() {
            @Override
            public void onEmoji(String e) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(e, 1);
            }

            @Override
            public void onClose() {
                showEmoji(false);
            }

            @Override
            public void onBackspace() {
                sendKey(KeyEvent.KEYCODE_DEL, 0);
            }
        });
        content.addView(emoji, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- franja inferior para cambiar el tamaño (solo en modo flotante) ---
        resizeHandle = new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                p.setColor((prefs.textColor() & 0x00FFFFFF) | (0x88 << 24));
                p.setStrokeWidth(dp(1.5f));
                float w = getWidth(), h = getHeight();
                for (int i = 0; i < 3; i++) {
                    float o = dp(5) * i;
                    c.drawLine(w - dp(4) - o, h - dp(3), w - dp(4), h - dp(3) - o - dp(1), p);
                }
            }
        };
        col.addView(resizeHandle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        resizeHandle.setOnTouchListener((v, e) -> onResizeTouch(e));
    }

    // ------------------------------------------------------------------
    // Aplicar opciones
    // ------------------------------------------------------------------
    private void applyAll() {
        floating = prefs.floating();
        root.setMinimumHeight(prefs.screenH());
        kv.applyPrefs(prefs);
        emoji.applyPrefs();
        bg.apply(prefs, floating ? dp(8) : 0);

        int tc = prefs.textColor();
        btnEmoji.setTextColor(tc);
        btnTransp.setTextColor(tc);
        btnFloat.setTextColor(tc);
        btnSettings.setTextColor(tc);
        btnHide.setTextColor(tc);
        btnFloat.setText(floating ? "📌" : "🎈");
        btnTransp.setAlpha(prefs.transparent() ? 1f : 0.55f);
        resizeHandle.setVisibility(floating ? View.VISIBLE : View.GONE);
        grip.invalidate();
        resizeHandle.invalidate();

        FrameLayout.LayoutParams lp;
        if (floating) {
            lp = new FrameLayout.LayoutParams(prefs.floatW(), prefs.floatH(), Gravity.TOP | Gravity.START);
            lp.leftMargin = prefs.floatX();
            lp.topMargin = prefs.floatY();
        } else {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, prefs.dockH(), Gravity.BOTTOM);
        }
        panel.setLayoutParams(lp);
        root.requestLayout();
    }

    private void showEmoji(boolean show) {
        if (show) emoji.refresh();
        emoji.setVisibility(show ? View.VISIBLE : View.GONE);
        kv.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private int rootW() {
        return root.getWidth() > 0 ? root.getWidth() : prefs.screenW();
    }

    private int rootH() {
        return root.getHeight() > 0 ? root.getHeight() : prefs.screenH();
    }

    // ------------------------------------------------------------------
    // Mover (modo flotante) / cambiar altura (modo acoplado)
    // ------------------------------------------------------------------
    private boolean onGripTouch(MotionEvent e) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragX = e.getRawX();
                dragY = e.getRawY();
                dragStartX = lp.leftMargin;
                dragStartY = lp.topMargin;
                dragStartH = lp.height;
                return true;
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (e.getRawX() - dragX);
                int dy = (int) (e.getRawY() - dragY);
                if (floating) {
                    lp.leftMargin = clamp(dragStartX + dx, 0, rootW() - lp.width);
                    lp.topMargin = clamp(dragStartY + dy, 0, rootH() - lp.height);
                } else {
                    lp.height = clamp(dragStartH - dy, dp(150), (int) (rootH() * 0.75f));
                }
                panel.setLayoutParams(lp);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (floating) {
                    prefs.put("floatX", lp.leftMargin);
                    prefs.put("floatY", lp.topMargin);
                } else {
                    prefs.put("dockH", lp.height);
                }
                return true;
            default:
                return true;
        }
    }

    private boolean onResizeTouch(MotionEvent e) {
        if (!floating) return true;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragX = e.getRawX();
                dragY = e.getRawY();
                dragStartW = lp.width;
                dragStartH = lp.height;
                return true;
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (e.getRawX() - dragX);
                int dy = (int) (e.getRawY() - dragY);
                lp.width = clamp(dragStartW + dx, dp(220), rootW() - lp.leftMargin);
                lp.height = clamp(dragStartH + dy, dp(130), rootH() - lp.topMargin);
                panel.setLayoutParams(lp);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                prefs.put("floatW", lp.width);
                prefs.put("floatH", lp.height);
                return true;
            default:
                return true;
        }
    }

    // ------------------------------------------------------------------
    // Teclas
    // ------------------------------------------------------------------
    private void sendKey(int code, int meta) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long t = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta));
    }

    private int metaState() {
        int m = 0;
        if (shift || caps) m |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (ctrl) m |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        return m;
    }

    private void doCtrl(char c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        char l = Character.toLowerCase(c);
        switch (l) {
            case 'a':
                ic.performContextMenuAction(android.R.id.selectAll);
                break;
            case 'c':
                ic.performContextMenuAction(android.R.id.copy);
                break;
            case 'x':
                ic.performContextMenuAction(android.R.id.cut);
                break;
            case 'v':
                ic.performContextMenuAction(android.R.id.paste);
                break;
            default:
                if (l >= 'a' && l <= 'z') {
                    sendKey(KeyEvent.KEYCODE_A + (l - 'a'),
                            KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
                }
        }
    }

    private void doEnter() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic != null && ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendKey(KeyEvent.KEYCODE_ENTER, 0);
    }

    @Override
    public void onKey(Keys.Key k, boolean longPress) {
        InputConnection ic = getCurrentInputConnection();
        boolean oneShot = true; // si se limpian Mayús / Ctrl / AltGr después de esta tecla

        switch (k.type) {
            case Keys.CHAR: {
                String out = longPress ? k.longText(shift, caps) : k.text(shift, caps, altGr);
                if (out != null) {
                    if (ctrl && out.length() == 1 && Character.isLetter(out.charAt(0))) {
                        doCtrl(out.charAt(0));
                    } else if (ic != null) {
                        ic.commitText(out, 1);
                    }
                }
                break;
            }
            case Keys.SPACE:
                if (ic != null) ic.commitText(" ", 1);
                break;
            case Keys.BACKSPACE:
                sendKey(KeyEvent.KEYCODE_DEL, metaState());
                break;
            case Keys.DEL:
                sendKey(KeyEvent.KEYCODE_FORWARD_DEL, metaState());
                break;
            case Keys.ENTER:
                doEnter();
                break;
            case Keys.TAB:
                sendKey(KeyEvent.KEYCODE_TAB, metaState());
                break;
            case Keys.ESC:
                sendKey(KeyEvent.KEYCODE_ESCAPE, 0);
                break;
            case Keys.LEFT:
                sendKey(KeyEvent.KEYCODE_DPAD_LEFT, metaState());
                break;
            case Keys.RIGHT:
                sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, metaState());
                break;
            case Keys.UP:
                sendKey(KeyEvent.KEYCODE_DPAD_UP, metaState());
                break;
            case Keys.DOWN:
                sendKey(KeyEvent.KEYCODE_DPAD_DOWN, metaState());
                break;
            case Keys.SHIFT:
                shift = !shift;
                oneShot = false;
                break;
            case Keys.CAPS:
                caps = !caps;
                oneShot = false;
                break;
            case Keys.CTRL:
                ctrl = !ctrl;
                oneShot = false;
                break;
            case Keys.ALTGR:
                altGr = !altGr;
                oneShot = false;
                break;
            case Keys.EMOJI:
                showEmoji(true);
                oneShot = false;
                break;
            default:
                break;
        }

        if (oneShot) {
            shift = false;
            ctrl = false;
            altGr = false;
        }
        kv.setModifiers(shift, caps, ctrl, altGr);
    }
}
