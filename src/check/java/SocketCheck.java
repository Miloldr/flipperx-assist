import com.flipperx.assist.AssistState;
import com.flipperx.assist.net.AssistSocket;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class SocketCheck {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out");
            Thread.sleep(5);
        }
    }
    public static void main(String[] args) throws Exception {
        try (Peer peer = new Peer()) {
            AssistState state = new AssistState();
            Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
            AtomicInteger replies = new AtomicInteger();
            AssistSocket socket = new AssistSocket(URI.create("ws://127.0.0.1:" + peer.server.getLocalPort()),
                    state, callbacks::add, message -> replies.incrementAndGet());
            for (int i = 0; i < 80; i++) { socket.connect(); Thread.sleep(2); }
            until(socket::connected);
            check(peer.connections.get() == 1, "Concurrent handshakes escaped the single-connection guard");
            check(!state.linked(), "Opening transport must not authenticate the account");
            for (int i = 0; i < 200; i++) {
                JsonObject message = new JsonObject();
                message.addProperty("n", i);
                check(socket.send(message), "Connected send rejected");
            }
            until(() -> peer.frames.size() == 200 && callbacks.size() == 200);
            for (int i = 0; i < 200; i++) check(peer.frames.get(i).equals("{\"n\":" + i + "}"), "Lost or reordered message " + i);
            state.linked(true);
            state.running(true);
            socket.close();
            check(!state.linked() && !state.running(), "Close left stale active state");
            Runnable callback;
            while ((callback = callbacks.poll()) != null) callback.run();
            check(replies.get() == 0, "Old socket delivered callbacks after logout");
            socket.connect();
            until(socket::connected);
            until(() -> peer.connections.get() == 2);
            JsonObject message = new JsonObject(); message.addProperty("new", true);
            socket.send(message);
            until(() -> callbacks.size() == 1);
            callbacks.remove().run();
            check(replies.get() == 1, "Replacement socket cannot deliver messages");
            state.linked(true); state.running(true);
            peer.latest.close();
            until(() -> !socket.connected());
            until(() -> !state.linked() && !state.running());
            int connections = peer.connections.get();
            for (int i = 0; i < 50; i++) socket.connect();
            Thread.sleep(100);
            check(peer.connections.get() == connections, "Tick polling bypassed reconnect backoff");
            until(() -> { socket.connect(); return socket.connected(); });
            socket.close();
        }
        AssistState state = new AssistState();
        state.running(true);
        JsonObject hud = new JsonObject();
        hud.addProperty("running", true);
        hud.addProperty("stopped_reason", "idle");
        state.hud(hud);
        check(!state.running() && state.status().contains("idle"), "Server stop reason was ignored");
        state.reset();
        check(state.step() == AssistState.Step.NONE && state.profit() == 0, "Account reset leaked state");
        AssistState.Step click = new AssistState.Step("click", "", 11, "left", "Click Coal", "", "COAL", "Coal", null);
        state.step(click);
        state.current(false);
        check(state.step() == click && !state.current(), "A menu transition blanked the instruction");
        state.receiveStep(new AssistState.Step("wait", "", -1, "left", "Loading", "", null, null, null), true);
        check(state.step() == click && !state.current(), "A loading reply replaced the readable instruction");
        long since = state.stepSince();
        state.receiveStep(click, false);
        check(state.stepSince() == since, "A repeat of the same step restarted its fade");
        AssistState.Step next = new AssistState.Step("click", "", 11, "left", "Click Create Buy Order", "", "COAL", "Coal", null);
        state.receiveStep(next, false);
        check(state.step() == next && state.current(), "A new action at the same slot did not advance");
        check(state.previous() == click, "The replaced step was not kept for the cross-fade");
        state.disconnected();
        check(state.step() == AssistState.Step.NONE && !state.current(), "Disconnect retained actionable guidance");
        System.out.println("Socket/state checks passed: single connection, ordered sends, stale callbacks, reconnect backoff, stop/reset.");
    }

    static final class Peer implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        final AtomicInteger connections = new AtomicInteger();
        final List<String> frames = new CopyOnWriteArrayList<>();
        volatile Socket latest;
        Peer() throws IOException {
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept(); latest = socket; connections.incrementAndGet();
                        Thread.ofVirtual().start(() -> serve(socket));
                    } catch (IOException ignored) { return; }
                }
            });
        }
        void serve(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                StringBuilder headers = new StringBuilder();
                while (!headers.toString().endsWith("\r\n\r\n")) {
                    int b = in.read(); if (b < 0) return; headers.append((char)b);
                }
                String key = Arrays.stream(headers.toString().split("\r\n"))
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:"))
                        .findFirst().orElseThrow().split(":", 2)[1].trim();
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                Thread.sleep(250);
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                while (true) {
                    int op = in.read(); if (op < 0) return;
                    int len = in.read() & 127;
                    if (len == 126) len = (in.read() << 8) | in.read();
                    if (len == 127) throw new AssertionError("Unexpected large test frame");
                    byte[] mask = in.readNBytes(4), body = in.readNBytes(len);
                    for (int i = 0; i < body.length; i++) body[i] ^= mask[i % 4];
                    if ((op & 15) == 8) return;
                    frames.add(new String(body, StandardCharsets.UTF_8));
                    byte[] reply = "{\"type\":\"test\"}".getBytes(StandardCharsets.UTF_8);
                    out.write(0x81); out.write(reply.length); out.write(reply); out.flush();
                }
            } catch (Exception ignored) { }
        }
        public void close() throws IOException {
            server.close(); if (latest != null) latest.close();
        }
    }
}
