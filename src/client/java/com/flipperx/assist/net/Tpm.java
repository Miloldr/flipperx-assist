package com.flipperx.assist.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.nio.charset.StandardCharsets;
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

        int NCryptDeleteKey(Pointer key, int flags);
    }

    private interface Crypt32 extends StdCallLibrary {
        Pointer CertEnumCertificatesInStore(Pointer store, Pointer previous);

        boolean CertFreeCertificateContext(Pointer context);

        boolean CertCloseStore(Pointer store, int flags);
    }

    private interface Shell32 extends StdCallLibrary {
        boolean IsUserAnAdmin();
    }

    private static final String PROVIDER = "Microsoft Platform Crypto Provider";
    private static final int IDENTITY_KEY = 0x8;
    private static final int OVERWRITE_KEY = 0x80;
    private static final String EK = "PCP_EKPUB";
    private static final String RSA_EK = "PCP_RSA_EKPUB";
    private static final String ACTIVATION = "PCP_TPM12_IDACTIVATION";
    private static final int SECRET_CAP = 256;
    private static final String[] CERT_PROPS = {"PCP_RSA_EKNVCERT", "PCP_EKNVCERT", "PCP_RSA_EKCERT",
            "PCP_EKCERT", "PCP_INTERMEDIATE_CA_EKCERT"};
    private static final int MAX_CERTS = 4;
    private static final int MAX_CERT_BYTES = 16384;

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
    private int status;
    private int got;

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
        out.addProperty("admin", admin());
        try {
            lib = Native.load("ncrypt", NCrypt.class);
            PointerByReference p = new PointerByReference();
            check(lib.NCryptOpenStorageProvider(p, new WString(PROVIDER), 0), "provider");
            provider = p.getValue();
            String platform = text(property(provider, "PCP_PLATFORM_TYPE"));
            if (platform != null) out.addProperty("platform", platform);
            byte[] ek = property(provider, EK);
            byte[] rsaEk = property(provider, RSA_EK);
            if (ek == null) ek = rsaEk;
            if (ek == null) throw new IllegalStateException("no ek " + hex(status));
            JsonObject certs = new JsonObject();
            for (String name : CERT_PROPS) {
                JsonArray c = certs(name);
                if (!c.isEmpty()) certs.add(name, c);
            }
            byte[] binding = binding();
            out.addProperty("ek", b64(ek));
            if (rsaEk != null && rsaEk != ek) out.addProperty("ek_rsa", b64(rsaEk));
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
        WString name = new WString("bzassist-ak-" + ProcessHandle.current().pid());
        check(lib.NCryptCreatePersistedKey(provider, k, new WString("RSA"), name, 0, OVERWRITE_KEY), "create");
        key = k.getValue();
        setInt(key, "Length", 2048);
        setInt(key, "PCP_KEY_USAGE_POLICY", IDENTITY_KEY);
        check(lib.NCryptFinalizeKey(key, 0), "finalize");
        byte[] b = property(key, "PCP_TPM12_IDBINDING");
        if (b == null) throw new IllegalStateException("no binding " + hex(status));
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
            check(lib.NCryptSetProperty(key, new WString(ACTIVATION), m, in.length, 0), "activate");
            byte[] secret = fixed(key, ACTIVATION, SECRET_CAP);
            String read = "fixed";
            if (secret == null) {
                String first = "fixed " + hex(status) + " n=" + got;
                secret = property(key, ACTIVATION);
                if (secret == null) {
                    throw new IllegalStateException("no secret " + first + ", sized " + hex(status) + " n=" + got);
                }
                read = "sized after " + first;
            }
            out.addProperty("secret", b64(secret));
            out.addProperty("read", read);
        } catch (Exception | LinkageError e) {
            out.addProperty("error", String.valueOf(e.getMessage()));
        }
        close();
        send(attempt, out);
    }

    private byte[] property(Pointer object, String name) {
        IntByReference size = new IntByReference();
        WString w = new WString(name);
        status = lib.NCryptGetProperty(object, w, null, 0, size, 0);
        got = size.getValue();
        if (status != 0 || got <= 0) return null;
        Memory m = new Memory(got);
        status = lib.NCryptGetProperty(object, w, m, got, size, 0);
        got = size.getValue();
        if (status != 0 || got <= 0 || got > m.size()) return null;
        return m.getByteArray(0, got);
    }

    private JsonArray certs(String name) {
        JsonArray out = new JsonArray();
        Memory handle = new Memory(Native.POINTER_SIZE);
        handle.clear();
        IntByReference size = new IntByReference();
        if (lib.NCryptGetProperty(provider, new WString(name), handle, Native.POINTER_SIZE, size, 0) != 0) {
            return out;
        }
        Pointer store = handle.getPointer(0);
        if (store == null) return out;
        try {
            Crypt32 crypt = Native.load("crypt32", Crypt32.class);
            try {
                Pointer cert = null;
                while (out.size() < MAX_CERTS && (cert = crypt.CertEnumCertificatesInStore(store, cert)) != null) {
                    Pointer data = cert.getPointer(Native.POINTER_SIZE);
                    int len = cert.getInt(2L * Native.POINTER_SIZE);
                    if (data != null && len > 0 && len <= MAX_CERT_BYTES) out.add(b64(data.getByteArray(0, len)));
                }
                if (cert != null) crypt.CertFreeCertificateContext(cert);
            } finally {
                crypt.CertCloseStore(store, 0);
            }
        } catch (Exception | LinkageError e) {
            return out;
        }
        return out;
    }

    private byte[] fixed(Pointer object, String name, int cap) {
        IntByReference size = new IntByReference();
        Memory m = new Memory(cap);
        status = lib.NCryptGetProperty(object, new WString(name), m, cap, size, 0);
        got = size.getValue();
        if (status != 0 || got <= 0 || got > cap) return null;
        return m.getByteArray(0, got);
    }

    private static String text(byte[] b) {
        if (b == null) return null;
        String s = new String(b, StandardCharsets.UTF_16LE);
        int end = s.indexOf('\0');
        s = (end >= 0 ? s.substring(0, end) : s).trim();
        return s.isEmpty() ? null : s.substring(0, Math.min(s.length(), 200));
    }

    private static boolean admin() {
        try {
            return Native.load("shell32", Shell32.class).IsUserAnAdmin();
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    private static String hex(int status) {
        return "0x" + Integer.toHexString(status);
    }

    private void setInt(Pointer object, String name, int value) {
        Memory m = new Memory(4);
        m.setInt(0, value);
        check(lib.NCryptSetProperty(object, new WString(name), m, 4, 0), name);
    }

    private static void check(int status, String what) {
        if (status != 0) throw new IllegalStateException(what + " " + hex(status));
    }

    private void close() {
        if (lib == null) return;
        if (key != null && lib.NCryptDeleteKey(key, 0) != 0) lib.NCryptFreeObject(key);
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
