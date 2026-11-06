package com.flipperx.assist.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DiscordPresence {
    private static final Gson GSON = new Gson();
    private static final long RETRY_MS = 30_000;
    private static final int MAX_FRAME = 1 << 20;

    private final Object lock = new Object();
    private JsonObject wanted;
    private boolean dirty;
    private Thread thread;

    private Pipe pipe;
    private String pipeClient;
    private boolean shown;
    private volatile String result;

    public String result() {
        return result;
    }

    public void update(JsonObject presence) {
        synchronized (lock) {
            if (presence == null && wanted == null && !dirty) return;
            wanted = presence;
            dirty = true;
            lock.notifyAll();
            if (thread == null) {
                thread = new Thread(this::run, "bzassist-discord");
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    public void clear() {
        update(null);
    }

    private void run() {
        long retryAt = 0;
        while (true) {
            JsonObject want;
            synchronized (lock) {
                long now = System.currentTimeMillis();
                while (!dirty && !(wanted != null && !shown && retryAt > 0 && now >= retryAt)) {
                    try {
                        long wait = wanted != null && !shown && retryAt > 0 ? Math.max(1, retryAt - now) : 0;
                        lock.wait(wait);
                    } catch (InterruptedException e) {
                        return;
                    }
                    now = System.currentTimeMillis();
                }
                dirty = false;
                want = wanted;
            }
            try {
                apply(want);
                retryAt = 0;
                result = shown ? "ok" : "cleared";
            } catch (IOException | RuntimeException e) {
                close();
                shown = false;
                retryAt = want == null ? 0 : System.currentTimeMillis() + RETRY_MS;
                String text = e.getClass().getSimpleName() + ": " + e.getMessage();
                result = text.length() > 300 ? text.substring(0, 300) : text;
            }
        }
    }

    private void apply(JsonObject want) throws IOException {
        if (want == null) {
            if (pipe != null && shown) send(JsonNull.INSTANCE);
            shown = false;
            return;
        }
        String client = want.has("client_id") ? want.get("client_id").getAsString() : "";
        if (client.isEmpty()) {
            if (pipe != null && shown) send(JsonNull.INSTANCE);
            shown = false;
            return;
        }
        if (pipe == null || !client.equals(pipeClient)) {
            close();
            open(client);
        }
        JsonElement activity = want.has("activity") ? want.get("activity") : JsonNull.INSTANCE;
        send(activity);
        shown = !activity.isJsonNull();
    }

    private void open(String client) throws IOException {
        IOException last = null;
        for (Pipe candidate : candidates()) {
            try {
                candidate.connect();
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", client);
                write(candidate, 0, hello);
                Frame ready = read(candidate);
                if (ready.op != 1) throw new IOException("discord refused the handshake");
                pipe = candidate;
                pipeClient = client;
                return;
            } catch (IOException e) {
                last = e;
                try {
                    candidate.close();
                } catch (IOException ignored) {
                }
            }
        }
        throw last != null ? last : new IOException("no discord-ipc socket found");
    }

    private void send(JsonElement activity) throws IOException {
        if (pipe == null) return;
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", UUID.randomUUID().toString());
        write(pipe, 1, cmd);
        Frame answer = read(pipe);
        if (answer.op == 2) throw new IOException("discord closed the connection: " + answer.body);
        JsonElement parsed = JsonParser.parseString(answer.body);
        if (parsed.isJsonObject() && "ERROR".equals(text(parsed.getAsJsonObject(), "evt"))) {
            JsonElement data = parsed.getAsJsonObject().get("data");
            String why = data != null && data.isJsonObject() ? text(data.getAsJsonObject(), "message") : null;
            throw new IOException("discord refused the status: " + (why != null ? why : answer.body));
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private void close() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
        }
        pipe = null;
        pipeClient = null;
    }

    private static void write(Pipe p, int op, JsonElement body) throws IOException {
        byte[] json = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(op).putInt(json.length).put(json);
        p.write(frame.array());
    }

    private static Frame read(Pipe p) throws IOException {
        byte[] head = new byte[8];
        p.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = h.getInt();
        int len = h.getInt();
        if (len < 0 || len > MAX_FRAME) throw new IOException("bad frame from discord");
        byte[] body = new byte[len];
        p.readFully(body);
        return new Frame(op, new String(body, StandardCharsets.UTF_8));
    }

    private static List<Pipe> candidates() {
        List<Pipe> out = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            if (windows) {
                out.add(new WindowsPipe("\\\\.\\pipe\\discord-ipc-" + i));
                continue;
            }
            for (Path dir : unixDirs()) {
                Path socket = dir.resolve("discord-ipc-" + i);
                if (Files.exists(socket)) out.add(new UnixPipe(socket));
            }
        }
        return out;
    }

    private static List<Path> unixDirs() {
        List<Path> roots = new ArrayList<>();
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(env);
            if (v != null && !v.isEmpty()) roots.add(Path.of(v));
        }
        roots.add(Path.of("/tmp"));
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            out.add(root);
            out.add(root.resolve("app/com.discordapp.Discord"));
            out.add(root.resolve(".flatpak/com.discordapp.Discord/xdg-run"));
            out.add(root.resolve("snap.discord"));
        }
        return out;
    }

    private record Frame(int op, String body) {}

    private interface Pipe extends Closeable {
        void connect() throws IOException;

        void write(byte[] bytes) throws IOException;

        void readFully(byte[] into) throws IOException;
    }

    private static final class WindowsPipe implements Pipe {
        private final String name;
        private RandomAccessFile file;

        WindowsPipe(String name) {
            this.name = name;
        }

        public void connect() throws IOException {
            file = new RandomAccessFile(name, "rw");
        }

        public void write(byte[] bytes) throws IOException {
            file.write(bytes);
        }

        public void readFully(byte[] into) throws IOException {
            file.readFully(into);
        }

        public void close() throws IOException {
            if (file != null) file.close();
        }
    }

    private static final class UnixPipe implements Pipe {
        private final Path path;
        private SocketChannel channel;

        UnixPipe(Path path) {
            this.path = path;
        }

        public void connect() throws IOException {
            channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(path));
        }

        public void write(byte[] bytes) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(bytes);
            while (b.hasRemaining()) channel.write(b);
        }

        public void readFully(byte[] into) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(into);
            while (b.hasRemaining()) {
                if (channel.read(b) < 0) throw new IOException("discord closed the connection");
            }
        }

        public void close() throws IOException {
            if (channel != null) channel.close();
        }
    }
}
