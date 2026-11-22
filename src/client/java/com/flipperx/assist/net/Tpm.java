package com.flipperx.assist.net;

import com.google.gson.JsonObject;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public final class Tpm {
    private interface NCrypt extends StdCallLibrary {
        int NCryptOpenStorageProvider(PointerByReference provider, WString name, int flags);

        int NCryptCreatePersistedKey(Pointer provider, PointerByReference key, WString alg, WString name,
                                     int legacySpec, int flags);

        int NCryptSetProperty(Pointer object, WString property, Pointer input, int size, int flags);

        int NCryptGetProperty(Pointer object, WString property, Pointer output, int size, IntByReference result,
                              int flags);

        int NCryptFinalizeKey(Pointer key, int flags);

        int NCryptFreeObject(Pointer object);
    }

    private static final String PROVIDER = "Microsoft Platform Crypto Provider";
    private static final int IDENTITY_KEY = 0x8;
    private static final String[] EK_PROPS = {"PCP_RSA_EKPUB", "PCP_EKPUB"};
    private static final String[] CERT_PROPS = {"PCP_RSA_EKNVCERT", "PCP_RSA_EKCERT", "PCP_EKNVCERT",
            "PCP_EKCERT", "PCP_INTERMEDIATE_CA_EKCERT"};

    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bzassist-tpm");
        t.setDaemon(true);
        return t;
    });

    private final AssistSocket socket;
    private long generation = -1;
    private NCrypt lib;
    private Pointer provider;
    private Pointer key;

    public Tpm(AssistSocket socket) {
        this.socket = socket;
    }

    public void onAuthOk() {
        long attempt = socket.generation();
        WORKER.execute(() -> {
            if (attempt != socket.generation() || attempt == generation) return;
            generation = attempt;
            report(attempt);
        });
    }

    public void onChallenge(JsonObject msg) {
        long attempt = socket.generation();
        String blob = msg.has("blob") ? msg.get("blob").getAsString() : "";
        WORKER.execute(() -> {
            if (attempt == generation && attempt == socket.generation()) answer(attempt, blob);
        });
    }

    private void report(long attempt) {
        close();
        JsonObject out = new JsonObject();
        out.addProperty("type", "tpm");
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            out.addProperty("none", "os");
            send(attempt, out);
            return;
        }
        try {
            lib = Native.load("ncrypt", NCrypt.class);
            PointerByReference p = new PointerByReference();
            check(lib.NCryptOpenStorageProvider(p, new WString(PROVIDER), 0), "provider");
            provider = p.getValue();
            byte[] ek = null;
            for (String name : EK_PROPS) {
                ek = property(provider, name);
                if (ek != null) break;
            }
            if (ek == null) throw new IllegalStateException("no ek");
            JsonObject certs = new JsonObject();
            for (String name : CERT_PROPS) {
                byte[] c = property(provider, name);
                if (c != null && c.length > 0) certs.addProperty(name, b64(c));
            }
            byte[] binding = binding();
            out.addProperty("ek", b64(ek));
            out.addProperty("bind", b64(binding));
            out.add("certs", certs);
        } catch (Exception | LinkageError e) {
            close();
            out.addProperty("none", "error: " + e.getMessage());
        }
        if (send(attempt, out) && key != null) {
            WORKER.schedule(() -> {
                if (generation == attempt) close();
            }, 40, TimeUnit.SECONDS);
        } else {
            close();
        }
    }

    private byte[] binding() {
        PointerByReference k = new PointerByReference();
        check(lib.NCryptCreatePersistedKey(provider, k, new WString("RSA"), null, 0, 0), "create");
        key = k.getValue();
        setInt(key, "Length", 2048);
        setInt(key, "PCP_KEY_USAGE_POLICY", IDENTITY_KEY);
        check(lib.NCryptFinalizeKey(key, 0), "finalize");
        byte[] b = property(key, "PCP_TPM12_IDBINDING");
        if (b == null) throw new IllegalStateException("no binding");
        return b;
    }

    private void answer(long attempt, String blob) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "tpm_answer");
        try {
            if (key == null) throw new IllegalStateException("no key");
            byte[] in = Base64.getDecoder().decode(blob);
            Memory m = new Memory(in.length);
            m.write(0, in, 0, in.length);
            WString name = new WString("PCP_TPM12_IDACTIVATION");
            check(lib.NCryptSetProperty(key, name, m, in.length, 0), "activate");
            byte[] secret = property(key, "PCP_TPM12_IDACTIVATION");
            if (secret == null) throw new IllegalStateException("no secret");
            out.addProperty("secret", b64(secret));
        } catch (Exception | LinkageError e) {
            out.addProperty("error", String.valueOf(e.getMessage()));
        }
        close();
        send(attempt, out);
    }

    private byte[] property(Pointer object, String name) {
        IntByReference size = new IntByReference();
        WString w = new WString(name);
        if (lib.NCryptGetProperty(object, w, null, 0, size, 0) != 0 || size.getValue() <= 0) return null;
        Memory m = new Memory(size.getValue());
        if (lib.NCryptGetProperty(object, w, m, size.getValue(), size, 0) != 0) return null;
        return m.getByteArray(0, size.getValue());
    }

    private void setInt(Pointer object, String name, int value) {
        Memory m = new Memory(4);
        m.setInt(0, value);
        check(lib.NCryptSetProperty(object, new WString(name), m, 4, 0), name);
    }

    private static void check(int status, String what) {
        if (status != 0) throw new IllegalStateException(what + " 0x" + Integer.toHexString(status));
    }

    private void close() {
        if (lib == null) return;
        if (key != null) lib.NCryptFreeObject(key);
        if (provider != null) lib.NCryptFreeObject(provider);
        key = null;
        provider = null;
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private boolean send(long attempt, JsonObject message) {
        synchronized (socket) {
            return attempt == socket.generation() && socket.send(message);
        }
    }
}
