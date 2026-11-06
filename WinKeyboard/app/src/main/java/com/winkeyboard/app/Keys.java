package com.winkeyboard.app;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Definición de las teclas (distribución estilo teclado en pantalla de Windows 11, español). */
public final class Keys {
    private Keys() {
    }

    public static final int CHAR = 0, BACKSPACE = 1, ENTER = 2, SHIFT = 3, CAPS = 4, TAB = 5, CTRL = 6,
            ALTGR = 7, SPACE = 8, EMOJI = 9, LEFT = 10, RIGHT = 11, UP = 12, DOWN = 13, ESC = 14, DEL = 15;

    public static final class Key {
        public final int type;
        public final String label;
        public final String shift;
        public final String alt;
        public final float w;
        public final boolean letter;
        public final RectF r = new RectF();

        Key(int type, String label, String shift, String alt, float w) {
            this.type = type;
            this.label = label;
            this.shift = shift;
            this.alt = alt;
            this.w = w;
            this.letter = type == CHAR && label.length() == 1 && Character.isLetter(label.charAt(0));
        }

        public boolean repeatable() {
            return type == BACKSPACE || type == DEL || type == LEFT || type == RIGHT
                    || type == UP || type == DOWN;
        }

        /** Texto que se muestra y se escribe al tocar. */
        public String text(boolean sh, boolean caps, boolean altGr) {
            if (altGr && alt != null) {
                return (letter && (sh || caps)) ? alt.toUpperCase() : alt;
            }
            if (letter) {
                return (sh || caps) ? label.toUpperCase() : label;
            }
            if (sh && shift != null) {
                return shift;
            }
            return label;
        }

        /** Texto que se escribe con pulsación larga. */
        public String longText(boolean sh, boolean caps) {
            if (alt == null) return null;
            return (letter && (sh || caps)) ? alt.toUpperCase() : alt;
        }
    }

    private static Key c(String label, String shift, String alt) {
        return new Key(CHAR, label, shift, alt, 1f);
    }

    private static Key t(int type, String label, float w) {
        return new Key(type, label, null, null, w);
    }

    public static List<List<Key>> build() {
        List<List<Key>> rows = new ArrayList<>();

        // Fila 1: Esc, números, borrar
        List<Key> r1 = new ArrayList<>();
        r1.add(t(ESC, "Esc", 1f));
        String[][] nums = {
                {"1", "!", "|"}, {"2", "\"", "@"}, {"3", "#", "~"}, {"4", "$", "€"}, {"5", "%", "^"},
                {"6", "&", "`"}, {"7", "/", "{"}, {"8", "(", "["}, {"9", ")", "]"}, {"0", "=", "}"}
        };
        for (String[] n : nums) r1.add(c(n[0], n[1], n[2]));
        r1.add(t(BACKSPACE, "⌫", 1.5f));
        rows.add(r1);

        // Fila 2: Tab, QWERTY, Supr
        List<Key> r2 = new ArrayList<>();
        r2.add(t(TAB, "Tab", 1.5f));
        String[] q = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
        String[] qAlt = {"@", null, "é", null, null, null, "ú", "í", "ó", null};
        for (int i = 0; i < q.length; i++) r2.add(c(q[i], null, qAlt[i]));
        r2.add(t(DEL, "Supr", 1f));
        rows.add(r2);

        // Fila 3: Bloq Mayús, ASDF, Entrar
        List<Key> r3 = new ArrayList<>();
        r3.add(t(CAPS, "Bloq May", 1.25f));
        String[] a = {"a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"};
        String[] aAlt = {"á", null, null, null, null, null, null, null, null, "+"};
        for (int i = 0; i < a.length; i++) r3.add(c(a[i], null, aAlt[i]));
        r3.add(t(ENTER, "Entrar", 1.25f));
        rows.add(r3);

        // Fila 4: Mayús, ZXCV, signos, flecha arriba
        List<Key> r4 = new ArrayList<>();
        r4.add(t(SHIFT, "Mayús", 1.5f));
        String[] z = {"z", "x", "c", "v", "b", "n", "m"};
        for (String s : z) r4.add(c(s, null, null));
        r4.add(c(",", ";", "<"));
        r4.add(c(".", ":", ">"));
        r4.add(c("-", "_", "\\"));
        r4.add(t(UP, "↑", 1f));
        rows.add(r4);

        // Fila 5: Ctrl, emojis, AltGr, espacio, ? !, flechas
        List<Key> r5 = new ArrayList<>();
        r5.add(t(CTRL, "Ctrl", 1.25f));
        r5.add(t(EMOJI, "😀", 1.25f));
        r5.add(t(ALTGR, "AltGr", 1.25f));
        r5.add(t(SPACE, "", 3.75f));
        r5.add(c("?", "¿", "'"));
        r5.add(c("!", "¡", "*"));
        r5.add(t(LEFT, "←", 1f));
        r5.add(t(DOWN, "↓", 1f));
        r5.add(t(RIGHT, "→", 1f));
        rows.add(r5);

        return rows;
    }
}
