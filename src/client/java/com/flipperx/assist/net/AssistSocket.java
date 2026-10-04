package com.flipperx.assist.net;

import com.flipperx.assist.AssistState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.zip.Deflater;

public final class AssistSocket {
    private static final Gson GSON = new Gson();
    private final URI endpoint;
    private final Consumer<JsonObject> onMessage;
    private final Executor executor;
    private final AssistState state;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private WebSocket ws;
    private boolean connecting;
    private long generation;
    private long retryAt;
    private long backoffMs = 1000;
    private CompletableFuture<?> sends = CompletableFuture.completedFuture(null);
    private Deflater deflater;

    public AssistSocket(URI endpoint, AssistState state, Executor executor,
                        Consumer<JsonObject> onMessage) {
        this.endpoint = endpoint;
        this.state = state;
        this.executor = executor;
        this.onMessage = onMessage;
    }

    public synchronized boolean connected() {
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    public synchronized long generation() { return generation; }

    public synchronized void connect() {
        if (connecting || connected() || System.currentTimeMillis() < retryAt) return;
        connecting = true;
        long attempt = ++generation;
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(endpoint, new Listener(attempt))
                .whenComplete((socket, error) -> {
                    if (error != null) dropped(attempt);
                });
    }

    public synchronized boolean send(JsonObject payload) {
        if (!connected()) return false;
        WebSocket target = ws;
        long attempt = generation;
        String json = GSON.toJson(payload);
        if (deflater != null) {
            ByteBuffer frame = compress(json);
            sends = sends.thenCompose(ignored -> target.sendBinary(frame, true));
        } else {
            sends = sends.thenCompose(ignored -> target.sendText(json, true));
        }
        sends.whenComplete((ignored, error) -> {
            if (error != null) dropped(attempt);
        });
        return true;
    }

    public synchronized void deflate() {
        if (deflater == null && connected()) deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    }

    private ByteBuffer compress(String json) {
        deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, json.length() / 4));
        byte[] buf = new byte[8192];
        int n;
        do {
            n = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
            out.write(buf, 0, n);
        } while (n == buf.length);
        return ByteBuffer.wrap(out.toByteArray());
    }

    private void endDeflater() {
        if (deflater != null) deflater.end();
        deflater = null;
    }

    public synchronized void close() {
        ++generation;
        connecting = false;
        WebSocket old = ws;
        ws = null;
        retryAt = 0;
        backoffMs = 1000;
        state.disconnected();
        sends = CompletableFuture.completedFuture(null);
        endDeflater();
        if (old != null) old.abort();
    }

    private synchronized void dropped(long attempt) {
        if (attempt != generation) return;
        ++generation;
        connecting = false;
        WebSocket old = ws;
        ws = null;
        state.disconnected();
        retryAt = System.currentTimeMillis() + backoffMs;
        backoffMs = Math.min(backoffMs * 2, 30_000);
        sends = CompletableFuture.completedFuture(null);
        endDeflater();
        if (old != null) old.abort();
    }

    private final class Listener implements WebSocket.Listener {
        private final long attempt;
        private final StringBuilder partial = new StringBuilder();
        Listener(long attempt) { this.attempt = attempt; }

        @Override
        public void onOpen(WebSocket socket) {
            synchronized (AssistSocket.this) {
                if (attempt != generation) { socket.abort(); return; }
                ws = socket;
                connecting = false;
                backoffMs = 1000;
                sends = CompletableFuture.completedFuture(null);
                endDeflater();
            }
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String whole = partial.toString();
                partial.setLength(0);
                try {
                    JsonObject message = GSON.fromJson(whole, JsonObject.class);
                    if (message != null) executor.execute(() -> {
                        synchronized (AssistSocket.this) {
                            if (attempt != generation || socket != ws) return;
                            onMessage.accept(message);
                        }
                    });
                } catch (RuntimeException ignored) { }
            }
            socket.request(1);
            return null;
        }

        @Override public void onError(WebSocket socket, Throwable error) { dropped(attempt); }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            dropped(attempt);
            return null;
        }
    }
}
