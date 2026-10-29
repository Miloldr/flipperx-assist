package com.flipperx.assist.game;

import java.util.regex.Pattern;

public final class ChatHide {
    private static final Pattern LOCRAW = Pattern.compile("^\\s*\\{\\s*\"server\"\\s*:.*}\\s*$");
    private static final Pattern SUGGEST = Pattern.compile("^\\s*CLICK THIS TO SUGGEST IT IN CHAT \\[(NO )?DASHES]\\s*$");
    private static final Pattern CLOCK = Pattern.compile("^\\s*Click here for instructions on how to fix your clock\\.?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private ChatHide() {}

    public static boolean hide(String line) {
        String s = GameUtil.strip(line);
        return LOCRAW.matcher(s).matches() || SUGGEST.matcher(s).matches() || CLOCK.matcher(s).matches();
    }
}
