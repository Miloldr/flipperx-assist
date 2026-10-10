import com.flipperx.assist.Chat;
import com.flipperx.assist.net.VersionCheck;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Optional;

public class ChatLinkCheck {
    static int fails = 0;

    static void eq(String what, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        if (!ok) fails++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + "  got=" + got + " want=" + want);
    }

    static String clickableText(Component message, int width) {
        StringSplitter splitter = new StringSplitter((cp, style) -> 6f);
        List<FormattedText> lines = splitter.splitLines(message, width, Style.EMPTY);
        StringBuilder out = new StringBuilder();
        for (FormattedText line : lines) {
            line.visit((style, text) -> {
                if (style.getClickEvent() instanceof ClickEvent.OpenUrl) out.append(text);
                return Optional.empty();
            }, Style.EMPTY);
        }
        return out.toString();
    }

    static void serverChat() {
        System.out.println("a server chat line prints as it was sent:");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Component c = Chat.fromServer(JsonParser.parseString(
                "{\"type\":\"chat\",\"component\":{\"text\":\"\",\"extra\":["
                        + "{\"text\":\"[FlipperX]\",\"color\":\"gold\"},"
                        + "{\"text\":\" Two players on one coop.\",\"color\":\"yellow\"}]}}").getAsJsonObject());
        eq("text", c == null ? null : c.getString(), "[FlipperX] Two players on one coop.");
        eq("first part gold", c == null ? null : c.getSiblings().get(0).getStyle().getColor(), "gold");
        Component plain = Chat.fromServer(JsonParser.parseString("{\"type\":\"chat\",\"text\":\"Hello\"}").getAsJsonObject());
        eq("plain text", plain == null ? null : plain.getString(), "Hello");
        Component broken = Chat.fromServer(JsonParser.parseString("{\"type\":\"chat\",\"component\":{\"nope\":1},\"text\":\"fallback\"}").getAsJsonObject());
        eq("unreadable component falls back to text", broken == null ? null : broken.getString(), "fallback");
    }

    public static void main(String[] a) {
        serverChat();
        String url = "https://flipperx.digital/assist/link?token=abcdefghijklmnopqrstuv";
        Component message = Chat.line(Component.literal("Click to finish logging in: ").append(Chat.link(url, url)));

        System.out.println("the login link stays clickable wherever chat wraps it:");
        for (int width : new int[]{120, 200, 320, 1000}) {
            eq("width " + width, clickableText(message, width), url);
        }

        System.out.println("the prefix:");
        eq("names the product", message.getString().startsWith("[FlipperX Assist] "), true);

        System.out.println("buttons run the client command:");
        Component button = Chat.button("[stop these reminders]", "/flipperx reminders off");
        ClickEvent click = button.getStyle().getClickEvent();
        eq("run command", click instanceof ClickEvent.RunCommand r ? r.command() : click, "/flipperx reminders off");

        System.out.println("short url:");
        eq("scheme dropped", Chat.shortUrl("https://flipperx.digital/assistmod"), "flipperx.digital/assistmod");

        System.out.println("version compare:");
        eq("newer patch", VersionCheck.compare("0.1.11", "0.1.10") > 0, true);
        eq("same", VersionCheck.compare("0.1.11", "0.1.11"), 0);
        eq("older", VersionCheck.compare("0.1.9", "0.1.10") < 0, true);
        eq("numeric not text", VersionCheck.compare("0.1.10", "0.1.9") > 0, true);
        eq("missing part is zero", VersionCheck.compare("0.2", "0.1.11") > 0, true);
        eq("suffix ignored", VersionCheck.compare("0.1.11+build", "0.1.11"), 0);
        eq("unknown current", VersionCheck.compare("0.1.11", "") > 0, true);

        System.out.println(fails == 0 ? "\nall passed" : "\n" + fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
