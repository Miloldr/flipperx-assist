import com.flipperx.assist.net.Lag;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class LagCheck {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Lag off = new Lag();
        off.add(Lag.REPORT, 1000, 1, 0);
        check(off.take(true, 2000) == null, "Nothing is kept until the server turns it on");

        Lag lag = new Lag();
        lag.on(true);
        lag.add(Lag.REPORT, 1000, 7, 0);
        lag.packet(1010);
        lag.packet(1020);
        lag.packet(1100);
        lag.add(Lag.ANSWER, 1150, 7, 0);
        check(lag.take(false, 2000) == null, "A batch waits for its 30s");
        JsonObject o = lag.take(false, 1000 + Lag.EVERY_MS);
        check(o != null && "lag".equals(o.get("type").getAsString()), "A batch goes out after 30s");
        check(o.get("t0").getAsLong() == 1000, "Times count from the first event");
        JsonArray ev = o.getAsJsonArray("ev");
        check(ev.size() == 4 * 4, "Packets within 50ms of the last one are one batch: " + ev.size());
        check(ev.get(0).getAsString().equals("r") && ev.get(2).getAsLong() == 7, "A report keeps its seq");
        check(ev.get(4).getAsString().equals("m") && ev.get(5).getAsLong() == 10, "The first packet is kept");
        check(ev.get(9).getAsLong() == 100, "A packet after a gap starts a new batch");
        check(ev.get(13).getAsLong() == 150, "The answer keeps its time");
        check(lag.take(true, 99_000) == null, "A batch is sent once");

        for (int i = 0; i < Lag.MAX_EVENTS + 5; i++) lag.add(Lag.CLICK, 5000 + i, 1, i);
        JsonObject full = lag.take(false, 5001);
        check(full != null && full.getAsJsonArray("ev").size() == 4 * Lag.MAX_EVENTS, "A full batch goes at once");

        lag.add(Lag.SCREEN, 9000, 0, 0);
        lag.on(false);
        check(lag.take(true, 9001) == null, "Turning it off drops what was kept");
        System.out.println("LagCheck passed");
    }
}
