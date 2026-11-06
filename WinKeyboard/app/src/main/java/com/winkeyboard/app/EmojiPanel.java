package com.winkeyboard.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Panel de emojis. Se dibujan con el texto del sistema, así que en Android se ven
 * con la fuente de Google (Noto Color Emoji).
 */
public class EmojiPanel extends LinearLayout {

    public interface Listener {
        void onEmoji(String emoji);

        void onClose();

        void onBackspace();
    }

    private static final int MAX_RECENT = 32;

    private final Prefs prefs;
    private Listener listener;
    private final List<String> icons = new ArrayList<>();
    private final List<List<String>> cats = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final List<TextView> tinted = new ArrayList<>();
    private List<String> current = new ArrayList<>();
    private int selected = 1;
    private final GridView grid;
    private final BaseAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable repeatBack = new Runnable() {
        @Override
        public void run() {
            if (listener != null) listener.onBackspace();
            handler.postDelayed(this, 50);
        }
    };

    public EmojiPanel(Context c, Prefs p) {
        super(c);
        prefs = p;
        setOrientation(VERTICAL);

        icons.add("🕘");
        cats.add(new ArrayList<String>());
        load();

        grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(prefs.dp(46));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(new ColorDrawable(0x22FFFFFF));
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return current.size();
            }

            @Override
            public Object getItem(int i) {
                return current.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                TextView tv;
                if (convert instanceof TextView) {
                    tv = (TextView) convert;
                } else {
                    tv = new TextView(getContext());
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextSize(26);
                    tv.setLayoutParams(new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, prefs.dp(44)));
                }
                tv.setText(current.get(i));
                return tv;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            String e = current.get(pos);
            if (prefs.haptic()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onEmoji(e);
            addRecent(e);
        });
        addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // barra inferior: ABC | categorías | borrar
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, prefs.dp(38)));

        TextView abc = makeText("ABC", 14);
        abc.setOnClickListener(v -> {
            if (listener != null) listener.onClose();
        });
        bar.addView(abc, new LinearLayout.LayoutParams(prefs.dp(52), ViewGroup.LayoutParams.MATCH_PARENT));
        tinted.add(abc);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(c);
        tabs.setOrientation(HORIZONTAL);
        hs.addView(tabs, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < icons.size(); i++) {
            final int idx = i;
            TextView tv = makeText(icons.get(i), 18);
            tv.setOnClickListener(v -> select(idx));
            tabs.addView(tv, new LinearLayout.LayoutParams(prefs.dp(42), ViewGroup.LayoutParams.MATCH_PARENT));
            tabViews.add(tv);
        }
        bar.addView(hs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView back = makeText("⌫", 18);
        back.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (listener != null) listener.onBackspace();
                    handler.postDelayed(repeatBack, 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(repeatBack);
                    return true;
                default:
                    return true;
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(prefs.dp(52), ViewGroup.LayoutParams.MATCH_PARENT));
        tinted.add(back);

        applyPrefs();
        // si no hay recientes, empezar en la primera categoría real
        selected = recents().isEmpty() ? 1 : 0;
        select(selected);
    }

    private TextView makeText(String s, float size) {
        TextView tv = new TextView(getContext());
        tv.setText(s);
        tv.setTextSize(size);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void applyPrefs() {
        int tc = prefs.textColor();
        for (TextView t : tinted) t.setTextColor(tc);
        for (TextView t : tabViews) t.setTextColor(tc);
        highlightTab();
    }

    /** Se llama cada vez que se abre el panel. */
    public void refresh() {
        select(selected == 0 || !recents().isEmpty() ? selected : 1);
    }

    private void load() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                getContext().getAssets().open("emojis.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length < 3) continue;
                icons.add(parts[0].trim());
                cats.add(new ArrayList<>(Arrays.asList(parts[2].trim().split("\\s+"))));
            }
        } catch (Exception ex) {
            icons.add("😀");
            cats.add(new ArrayList<>(Arrays.asList("😀", "😂", "😍", "👍", "❤️", "🙏", "🔥", "🎉")));
        }
    }

    private List<String> recents() {
        String s = prefs.sp.getString("recent", "");
        List<String> out = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return out;
        out.addAll(Arrays.asList(s.trim().split("\\s+")));
        return out;
    }

    private void addRecent(String e) {
        List<String> r = recents();
        r.remove(e);
        r.add(0, e);
        while (r.size() > MAX_RECENT) r.remove(r.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String x : r) sb.append(x).append(' ');
        prefs.sp.edit().putString("recent", sb.toString().trim()).apply();
    }

    private void select(int i) {
        if (i < 0 || i >= cats.size()) i = 1;
        selected = i;
        current = (i == 0) ? recents() : cats.get(i);
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
        highlightTab();
    }

    private void highlightTab() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            t.setBackgroundColor(i == selected ? 0x44FFFFFF : Color.TRANSPARENT);
        }
    }
}
