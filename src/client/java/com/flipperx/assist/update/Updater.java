package com.flipperx.assist.update;

import com.flipperx.assist.net.VersionCheck;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Updater {
    public static final String MOD_ID = "bzassist";
    private static final String PREFIX = "bazaar-assist-";
    private static final String PENDING = ".jar.pending";
    private static final String PART = ".jar.part";
    private static final int MAX_BYTES = 16 << 20;
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,40}");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static volatile Path installed;
    private static volatile Path pending;
    private static volatile String pendingVersion;

    public static final class UpdateException extends RuntimeException {
        public UpdateException(String message) {
            super(message);
        }
    }

    private Updater() {}

    public static void init() {
        try {
            installed = findInstalledJar();
            if (installed == null) return;
            pruneOlder(installed, VersionCheck.current());
            Path found = newestPending(installed.getParent(), VersionCheck.current());
            if (found != null) {
                pending = found;
                pendingVersion = versionOf(found);
            }
        } catch (RuntimeException e) {
            installed = null;
            pending = null;
            pendingVersion = null;
        }
    }

    public static boolean available() {
        return installed != null;
    }

    public static String pendingVersion() {
        return pendingVersion;
    }

    public static CompletableFuture<Void> download(VersionCheck.Release release) {
        Path jar = installed;
        if (jar == null) {
            return CompletableFuture.failedFuture(
                    new UpdateException("this copy was not loaded from a jar in your mods folder"));
        }
        return download(release, jar.getParent(), "bzassist/" + VersionCheck.current());
    }

    public static CompletableFuture<Void> download(VersionCheck.Release release, Path dir, String agent) {
        if (!release.installable()) {
            return CompletableFuture.failedFuture(
                    new UpdateException("the server did not say where the new jar is"));
        }
        HttpRequest request = HttpRequest.newBuilder(release.download())
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", agent)
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenAccept(response -> {
            byte[] body;
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new UpdateException("the server answered " + response.statusCode());
                }
                body = in.readNBytes(MAX_BYTES + 1);
            } catch (IOException e) {
                throw new UpdateException("the download stopped partway");
            }
            if (body.length > MAX_BYTES) throw new UpdateException("the download was too large");
            if (release.size() >= 0 && body.length != release.size()) {
                throw new UpdateException("the download was " + body.length + " bytes, not " + release.size());
            }
            verify(body, release.sha256(), release.version());
            stage(dir, release.version(), body);
        });
    }

    public static void install() {
        Path from = pending;
        Path old = installed;
        if (from == null || old == null || !Files.exists(from)) return;
        try {
            apply(old, from, targetFor(from));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public static void apply(Path old, Path staged, Path target) throws IOException {
        Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        if (!old.equals(target)) delete(old);
    }

    public static void pruneOlder(Path running, String current) {
        for (Path p : list(running.getParent(), "{bazaar-assist,bzassist}*.jar")) {
            if (p.equals(running)) continue;
            String version = versionOf(p);
            if (version != null && VersionCheck.compare(version, current) < 0) delete(p);
        }
    }

    public static String reason(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        if (t instanceof UpdateException) return t.getMessage();
        if (t instanceof HttpTimeoutException) return "the download timed out";
        return "the download failed";
    }

    public static void verify(byte[] jar, String sha256, String version) {
        if (sha256 == null || !sha256(jar).equalsIgnoreCase(sha256)) {
            throw new UpdateException("the download did not match its checksum");
        }
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new UpdateException("the server sent a version this mod cannot use");
        }
        if (!version.equals(modVersion(new ByteArrayInputStream(jar)))) {
            throw new UpdateException("the file is not Bazaar Assist " + version);
        }
    }

    public static Path stage(Path dir, String version, byte[] jar) {
        try {
            clearPending(dir);
            Path part = dir.resolve(PREFIX + version + PART);
            Path staged = dir.resolve(PREFIX + version + PENDING);
            Files.write(part, jar);
            Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING);
            pending = staged;
            pendingVersion = version;
            return staged;
        } catch (IOException e) {
            throw new UpdateException("could not write to your mods folder");
        }
    }

    public static Path newestPending(Path dir, String current) {
        Path best = null;
        String bestVersion = null;
        for (Path p : list(dir, PREFIX + "*" + PENDING)) {
            String version = versionOf(p);
            if (version == null || VersionCheck.compare(version, current) <= 0) {
                delete(p);
            } else if (best == null || VersionCheck.compare(version, bestVersion) > 0) {
                if (best != null) delete(best);
                best = p;
                bestVersion = version;
            } else {
                delete(p);
            }
        }
        for (Path p : list(dir, PREFIX + "*" + PART)) delete(p);
        return best;
    }

    public static Path targetFor(Path staged) {
        String name = staged.getFileName().toString();
        return staged.resolveSibling(name.substring(0, name.length() - ".pending".length()));
    }

    public static String versionOf(Path jar) {
        try (InputStream in = Files.newInputStream(jar)) {
            return modVersion(in);
        } catch (IOException e) {
            return null;
        }
    }

    static String modVersion(InputStream in) {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().equals("fabric.mod.json")) continue;
                JsonObject o = JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!o.has("id") || !MOD_ID.equals(o.get("id").getAsString()) || !o.has("version")) return null;
                return o.get("version").getAsString();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static Path findInstalledJar() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(ModContainer::getOrigin)
                .filter(origin -> origin.getKind() == ModOrigin.Kind.PATH)
                .map(ModOrigin::getPaths)
                .filter(paths -> paths.size() == 1)
                .map(paths -> paths.get(0).toAbsolutePath())
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                .orElse(null);
    }

    private static void clearPending(Path dir) {
        for (Path p : list(dir, PREFIX + "*" + PENDING)) delete(p);
        for (Path p : list(dir, PREFIX + "*" + PART)) delete(p);
        pending = null;
        pendingVersion = null;
    }

    private static List<Path> list(Path dir, String glob) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            List<Path> out = new ArrayList<>();
            stream.forEach(out::add);
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void delete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
