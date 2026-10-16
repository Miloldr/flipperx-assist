import com.flipperx.assist.net.Reports;

public class ReportsCheck {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Reports old = new Reports();
        check(!old.holds(true, true), "Nothing in flight holds nothing");
        old.sent(old.next(), 7);
        check(old.waiting(), "A sent report waits for its answer");
        check(old.holds(true, true), "An old server gets one report at a time");
        check(!old.stale(-1), "An answer without a seq is never stale");
        old.answer(-1);
        check(!old.waiting(), "Any answer ends the wait on an old server");

        Reports r = new Reports();
        r.numbered(true);
        r.sent(r.next(), 10);
        check(r.holds(false, false), "An unchanged screen waits for its answer");
        check(r.holds(true, false), "A change to lore or counts only waits for its answer");
        check(!r.holds(true, true), "A slot coming, going or renamed goes out at once");
        r.sent(r.next(), 20);
        check(!r.holds(true, true), "Two in flight is not the most");
        check(r.context(1, -1) == 10 && r.context(2, -1) == 20, "Each report keeps its screen");
        check(r.context(9, -1) == -1 && r.context(-1, 5) == 5, "Unknown seqs fall back");
        check(r.stale(1), "The answer to the screen left behind is stale");
        check(r.waiting(), "A stale answer does not end the wait");
        check(!r.stale(2), "The newest report's answer is not stale");
        r.answer(2);
        check(!r.waiting(), "The newest answer ends the wait");
        check(!r.stale(2), "A push answering the newest report is not stale");
        check(!r.holds(false, false), "Nothing waits once answered");

        r.sent(r.next(), 30);
        r.answer(3);
        r.sent(r.next(), 40);
        r.sent(r.next(), 50);
        r.answer(5);
        check(!r.stale(5) && r.stale(4), "Later pairs order the same way");

        Reports full = new Reports();
        full.numbered(true);
        for (int i = 0; i < 4; i++) full.sent(full.next(), i);
        check(full.holds(true, true), "Four in flight is the most");
        full.answer(4);
        check(!full.holds(true, true), "An answer frees them all");

        r.reset();
        check(!r.numbered() && !r.waiting() && r.next() == 1, "A new socket starts over");
        System.out.println("ReportsCheck passed");
    }
}
