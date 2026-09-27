package com.flipperx.assist;

public final class CoflPause {
    private CoflPause() {}

    // We don't want cofl spying on us.
    public static boolean active() {
        AssistState state = AssistClient.state();
        return state.running() || state.resumePending();
    }
}
