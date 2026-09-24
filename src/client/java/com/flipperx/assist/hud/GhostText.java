package com.flipperx.assist.hud;

public final class GhostText {
    private GhostText() {}

    public record Render(String ghost, int redFrom) {
        public static final Render NONE = new Render("", -1);

        public boolean hasGhost() { return !ghost.isEmpty(); }
        public boolean hasRed() { return redFrom >= 0; }
    }

    public static Render of(String typed, String target) {
        if (target == null || target.isEmpty() || typed == null) return Render.NONE;
        String want = "/" + target;

        int offset = typed.startsWith("/") ? 1 : 0;
        int shared = offset + commonPrefix(typed.substring(offset), target);

        if (shared <= offset && !typed.isEmpty() && typed.length() > offset) return Render.NONE;
        if (typed.isEmpty()) return Render.NONE;

        if (shared == typed.length()) {
            return new Render(want.substring(shared), -1);
        }
        return new Render("", shared);
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    public static Render ofSign(String typed, String amount) {
        if (amount == null || amount.isEmpty() || typed == null) return Render.NONE;
        int shared = commonPrefix(typed, amount);
        if (typed.isEmpty()) return new Render(amount, -1);
        if (shared == 0) return new Render("", 0);
        if (shared == typed.length()) return new Render(amount.substring(shared), -1);
        return new Render("", shared);
    }
}
