import com.flipperx.assist.hud.GhostText;

public class GhostTextCheck {
    static int fails = 0;
    static void eq(String what, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        if (!ok) fails++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + "  got=" + got + " want=" + want);
    }
    public static void main(String[] a) {
        System.out.println("typing the command correctly:");
        eq("empty box, nothing yet", GhostText.of("", "bz d d").ghost(), "");
        eq("slash only", GhostText.of("/", "bz d d").ghost(), "bz d d");
        eq("halfway", GhostText.of("/bz d", "bz d d").ghost(), " d");
        eq("complete", GhostText.of("/bz d d", "bz d d").ghost(), "");
        eq("no red while correct", GhostText.of("/bz d", "bz d d").hasRed(), false);

        System.out.println("going wrong:");
        eq("wrong char red from there", GhostText.of("/bz x", "bz d d").redFrom(), 4);
        eq("no ghost once wrong", GhostText.of("/bz x", "bz d d").ghost(), "");
        eq("overtyped past the end", GhostText.of("/bz d dd", "bz d d").redFrom(), 7);

        System.out.println("typing something else entirely:");
        eq("a chat message is untouched", GhostText.of("hello there", "bz d d").hasRed(), false);
        eq("...and gets no ghost", GhostText.of("hello there", "bz d d").hasGhost(), false);
        eq("a different command is left alone", GhostText.of("/home", "bz d d").hasRed(), false);
        eq("...and still tracks the right one", GhostText.of("/bz ", "bz d d").ghost(), "d d");

        System.out.println("no instruction:");
        eq("null target", GhostText.of("/bz", null).hasGhost(), false);
        eq("empty target", GhostText.of("/bz", "").hasGhost(), false);

        System.out.println("the sign:");
        eq("empty sign shows the amount", GhostText.ofSign("", "5000").ghost(), "5000");
        eq("partial", GhostText.ofSign("50", "5000").ghost(), "00");
        eq("wrong digit", GhostText.ofSign("51", "5000").redFrom(), 1);
        eq("done", GhostText.ofSign("5000", "5000").ghost(), "");

        System.out.println(fails == 0 ? "\nall passed" : "\n" + fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
