package dev.poldi.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Laedt die Java-Laufzeit, die Mojang fuer die gewaehlte Minecraft-Version vorsieht
 * (gleiche Quelle wie der offizielle Launcher), nach %APPDATA%\PoldiLauncher\runtime\<component>.
 * So startet jede Version mit dem richtigen Java (26.x: Java 25, 1.18-1.20.4: Java 17 ...).
 */
public final class JavaRuntime {
    private JavaRuntime() {}
    private static final String ALL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(), arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        if (os.contains("win")) return arm ? "windows-arm64" : "windows-x64";
        if (os.contains("mac")) return arm ? "mac-os-arm64" : "mac-os";
        return "linux";
    }

    /** Pfad zu javaw/java der passenden Laufzeit, oder null wenn das eingebaute Java reicht und der Download nicht klappt. */
    public static Path ensure(String component, int major, BiConsumer<String, Double> progress) throws IOException {
        if (component == null || component.isBlank()) component = "java-runtime-delta";
        Path dir = Config.DIR.resolve("runtime").resolve(component);
        Path stamp = dir.resolve(".poldi-ok");
        try {
            progress.accept("Java " + major + " prüfen …", 0.30);
            Map<String, Object> all = Json.obj(Json.parse(Http.get(ALL)));
            List<Object> l = Json.arr(Json.obj(all.get(platform())).get(component));
            if (l.isEmpty()) throw new IOException("Mojang hat keine Laufzeit " + component + " für " + platform());
            Map<String, Object> man = Json.obj(Json.obj(l.get(0)).get("manifest"));
            String sha = Json.str(man.get("sha1"));
            if (Files.isRegularFile(stamp) && Files.readString(stamp, StandardCharsets.UTF_8).trim().equals(sha) && exe(dir) != null) return exe(dir);

            Map<String, Object> files = Json.obj(Json.obj(Json.parse(Http.get(Json.str(man.get("url"))))).get("files"));
            List<Object[]> todo = new ArrayList<>();
            for (var e : files.entrySet()) {
                Map<String, Object> f = Json.obj(e.getValue());
                Path p = dir.resolve(e.getKey()).normalize();
                if (!p.startsWith(dir)) continue;
                switch (Json.str(f.get("type"))) {
                    case "directory" -> Files.createDirectories(p);
                    case "file" -> {
                        Map<String, Object> raw = Json.obj(Json.obj(f.get("downloads")).get("raw"));
                        todo.add(new Object[]{Json.str(raw.get("url")), p, Json.str(raw.get("sha1")), Boolean.TRUE.equals(f.get("executable"))});
                    }
                    case "link" -> {
                        try { Files.createDirectories(p.getParent()); Files.deleteIfExists(p); Files.createSymbolicLink(p, Paths.get(Json.str(f.get("target")))); } catch (Exception ignored) {}
                    }
                    default -> {}
                }
            }
            int n = todo.size(); AtomicInteger done = new AtomicInteger();
            var ex = java.util.concurrent.Executors.newFixedThreadPool(8);
            try {
                List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
                for (Object[] t : todo) fs.add(ex.submit(() -> {
                    Http.download((String) t[0], (Path) t[1], (String) t[2]);
                    if ((Boolean) t[3]) ((Path) t[1]).toFile().setExecutable(true);
                    int k = done.incrementAndGet(); if (k % 10 == 0 || k == n) progress.accept("Java " + major + " laden " + k + "/" + n, 0.30 + 0.05 * k / n);
                    return null;
                }));
                for (var f : fs) {
                    try { f.get(); }
                    catch (java.util.concurrent.ExecutionException e) { throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Abgebrochen"); }
                }
            } finally { ex.shutdownNow(); }
            Path java = exe(dir);
            if (java == null) throw new IOException("java fehlt in " + dir);
            Files.writeString(stamp, sha, StandardCharsets.UTF_8);
            return java;
        } catch (IOException e) {
            Path java = exe(dir);
            if (java != null && Files.isRegularFile(stamp)) return java;         // offline: vorhandene Laufzeit nehmen
            if (Runtime.version().feature() >= major) return null;              // eingebautes Java reicht
            throw new IOException("Java " + major + " konnte nicht geladen werden: " + e.getMessage());
        }
    }

    private static Path exe(Path dir) {
        for (String s : new String[]{"bin/javaw.exe", "bin/java.exe", "bin/java", "jre.bundle/Contents/Home/bin/java"}) {
            Path p = dir.resolve(s); if (Files.isRegularFile(p)) return p;
        }
        return null;
    }
}
