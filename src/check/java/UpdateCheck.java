import com.flipperx.assist.net.VersionCheck;
import com.flipperx.assist.update.Updater;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.zip.*;

public class UpdateCheck {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static byte[] jar(String id, String version) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("com/flipperx/Placeholder.class"));
            zip.write(new byte[]{1, 2, 3});
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"id\":\"" + id + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
    static String sha(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    static String downloadFailure(VersionCheck.Release release, Path dir) {
        try {
            Updater.download(release, dir, "bzassist/check").join();
            return "no failure";
        } catch (CompletionException e) {
            return Updater.reason(e);
        }
    }
    static boolean refused(Runnable r) {
        try {
            r.run();
            return false;
        } catch (Updater.UpdateException e) {
            return true;
        }
    }
    public static void main(String[] args) throws Exception {
        VersionCheck.Release release = VersionCheck.parse(
                "{\"version\":\"0.1.16\",\"url\":\"https://flipperx.digital/assistmod\",\"download\":\"/public/assistmod\","
                        + "\"sha256\":\"ab\",\"size\":10}");
        check(release.download().toString().equals("https://api.flipperx.digital/public/assistmod"),
                "Download path did not resolve against the API host: " + release.download());
        check(release.installable(), "A full answer was not installable");
        check(VersionCheck.parse("{\"version\":\"0.1.16\",\"download\":\"https://example.com/x.jar\",\"sha256\":\"ab\"}")
                .download() == null, "A download on another host was accepted");
        VersionCheck.Release old = VersionCheck.parse("{\"version\":\"0.1.16\",\"url\":\"https://flipperx.digital/assistmod\"}");
        check(!old.installable() && old.size() == -1, "An answer without a download was installable");
        check(VersionCheck.parse("{\"version\":null}") == null, "No version still made a release");

        byte[] good = jar("bzassist", "0.1.16");
        Updater.verify(good, sha(good), "0.1.16");
        Updater.verify(good, sha(good).toUpperCase(), "0.1.16");
        check(refused(() -> Updater.verify(good, "00" + sha(good).substring(2), "0.1.16")), "A wrong checksum was accepted");
        check(refused(() -> Updater.verify(good, null, "0.1.16")), "A missing checksum was accepted");
        check(refused(() -> Updater.verify(good, sha(good), "0.1.17")), "A jar of another version was accepted");
        byte[] other = jar("somethingelse", "0.1.16");
        check(refused(() -> Updater.verify(other, sha(other), "0.1.16")), "A jar of another mod was accepted");
        byte[] junk = "not a zip".getBytes(StandardCharsets.UTF_8);
        check(refused(() -> Updater.verify(junk, sha(junk), "0.1.16")), "A file that is not a jar was accepted");
        byte[] sneaky = jar("bzassist", "../../evil");
        check(refused(() -> Updater.verify(sneaky, sha(sneaky), "../../evil")), "A version that walks out of the folder was accepted");

        Path dir = Files.createTempDirectory("bzassist-update-check");
        try {
            Path installed = dir.resolve("bazaar-assist-0.1.15.jar");
            Files.write(installed, jar("bzassist", "0.1.15"));
            Files.write(dir.resolve("bazaar-assist-0.1.14.jar.pending"), jar("bzassist", "0.1.14"));
            Files.write(dir.resolve("bazaar-assist-0.1.16.jar.pending"), jar("bzassist", "0.1.16"));
            Files.write(dir.resolve("bazaar-assist-0.1.17.jar.pending"), jar("bzassist", "0.1.17"));
            Files.write(dir.resolve("bazaar-assist-0.1.18.jar.pending"), junk);
            Files.write(dir.resolve("bazaar-assist-0.1.19.jar.part"), good);
            Path newest = Updater.newestPending(dir, "0.1.15");
            check(newest != null && newest.getFileName().toString().equals("bazaar-assist-0.1.17.jar.pending"),
                    "Picked the wrong pending update: " + newest);
            try (var files = Files.list(dir)) {
                List<String> left = files.map(p -> p.getFileName().toString()).sorted().toList();
                check(left.equals(List.of("bazaar-assist-0.1.15.jar", "bazaar-assist-0.1.17.jar.pending")),
                        "Stale or broken pending files were left behind: " + left);
            }
            check(Updater.newestPending(dir, "0.1.17") == null, "A pending update no newer than the running jar was kept");

            Path staged = Updater.stage(dir, "0.1.16", good);
            check(staged.getFileName().toString().equals("bazaar-assist-0.1.16.jar.pending"), "Staged under the wrong name");
            check(Updater.pendingVersion().equals("0.1.16"), "Staging did not record the version");
            Path target = Updater.targetFor(staged);
            check(target.getFileName().toString().equals("bazaar-assist-0.1.16.jar"), "Wrong install name: " + target);

            Updater.apply(installed, staged, target);
            check(!Files.exists(installed) && !Files.exists(staged), "The old jar or the staged file was left behind");
            check(Arrays.equals(Files.readAllBytes(target), good), "The installed jar is not the downloaded one");

            Path stuck = dir.resolve("bazaar-assist-0.1.16-locked.jar");
            Files.createDirectories(stuck.resolve("inside"));
            Path next = Updater.stage(dir, "0.1.17", jar("bzassist", "0.1.17"));
            Path nextTarget = Updater.targetFor(next);
            Updater.apply(stuck, next, nextTarget);
            check(Files.exists(nextTarget) && !Files.exists(next),
                    "An old jar that could not be removed stopped the new one going in");
            check(Files.exists(stuck), "The locked old jar was not simply left for the next start");
            Files.delete(stuck.resolve("inside"));
            Files.delete(stuck);

            Files.write(dir.resolve("bazaar-assist-0.1.15.jar"), jar("bzassist", "0.1.15"));
            Files.write(dir.resolve("bzassist-0.1.16.jar"), jar("bzassist", "0.1.16"));
            Files.write(dir.resolve("bazaar-assist-0.1.17 (1).jar"), jar("bzassist", "0.1.17"));
            Files.write(dir.resolve("bazaar-assist-0.1.18.jar"), jar("bzassist", "0.1.18"));
            Files.write(dir.resolve("bazaar-assist-renamed.jar"), jar("somethingelse", "0.0.1"));
            Files.write(dir.resolve("othermod-0.0.1.jar"), jar("bzassist", "0.0.1"));
            Updater.pruneOlder(nextTarget, "0.1.17");
            try (var files = Files.list(dir)) {
                List<String> left = files.map(p -> p.getFileName().toString()).sorted().toList();
                check(left.equals(List.of("bazaar-assist-0.1.17 (1).jar", "bazaar-assist-0.1.17.jar",
                                "bazaar-assist-0.1.18.jar", "bazaar-assist-renamed.jar", "othermod-0.0.1.jar")),
                        "Pruning removed the wrong jars: " + left);
            }

            byte[] served = jar("bzassist", "0.1.19");
            List<String> agents = Collections.synchronizedList(new ArrayList<>());
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/public/assistmod", exchange -> {
                agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
                exchange.sendResponseHeaders(200, served.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(served);
                }
            });
            server.createContext("/missing", exchange -> {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });
            server.start();
            try {
                URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
                Updater.download(new VersionCheck.Release("0.1.19", VersionCheck.PAGE, base.resolve("/public/assistmod"),
                        sha(served), served.length), dir, "bzassist/0.1.18").join();
                check(Arrays.equals(Files.readAllBytes(dir.resolve("bazaar-assist-0.1.19.jar.pending")), served),
                        "The downloaded jar was not staged");
                check(Updater.pendingVersion().equals("0.1.19"), "The download did not record its version");
                check(agents.equals(List.of("bzassist/0.1.18")), "The download did not name itself: " + agents);

                String bad = downloadFailure(new VersionCheck.Release("0.1.19", VersionCheck.PAGE,
                        base.resolve("/public/assistmod"), "00" + sha(served).substring(2), served.length), dir);
                check(bad.contains("checksum"), "A tampered download gave: " + bad);
                String cut = downloadFailure(new VersionCheck.Release("0.1.19", VersionCheck.PAGE,
                        base.resolve("/public/assistmod"), sha(served), served.length + 1), dir);
                check(cut.contains("bytes"), "A short download gave: " + cut);
                String missing = downloadFailure(new VersionCheck.Release("0.1.19", VersionCheck.PAGE,
                        base.resolve("/missing"), sha(served), served.length), dir);
                check(missing.contains("404"), "A missing file gave: " + missing);
                check(Files.exists(dir.resolve("bazaar-assist-0.1.19.jar.pending")),
                        "A failed download removed the update that was already staged");
            } finally {
                server.stop(0);
            }
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        System.out.println("UpdateCheck passed");
    }
}
