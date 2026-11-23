package dev.poldi.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * Auto-Update von GitHub: Beim Spielstart wird das neueste Release von ventlukas-web/poldi-client geprueft.
 * Ist es neuer, wird die .jar im Hintergrund geladen und beim Beenden des Spiels gegen die alte getauscht
 * (Windows sperrt die laufende .jar, deshalb erst nach dem Schliessen). Beim naechsten Start ist die neue Version aktiv.
 */
public final class PoldiUpdater {
    public static final String REPO = "ventlukas-web/poldi-client";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static volatile String state = "nicht geprüft";
    private static boolean started, hookAdded;

    private PoldiUpdater() {}

    /** Build-Nummer aus "1.0.17" oder "build-17" -> 17. */
    static int buildNo(String v) {
        if (v == null) return -1;
        String digits = v.replaceAll("^.*?(\\d+)\\D*$", "$1");
        try { return Integer.parseInt(digits); } catch (Exception e) { return -1; }
    }

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(PoldiClient.ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static Path ownJar() {
        return FabricLoader.getInstance().getModContainer(PoldiClient.ID)
            .flatMap(c -> c.getOrigin().getPaths().stream().findFirst())
            .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
            .orElse(null);
    }

    public static void checkAsync(boolean manual) {
        if (!manual && (started || !PoldiConfig.get().autoUpdate)) return;
        started = true;
        Thread t = new Thread(() -> check(manual), "poldi-updater");
        t.setDaemon(true);
        t.start();
    }

    private static void notify(String msg, int color) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) PoldiClient.chat(msg);
            else PoldiNotify.push(PoldiClient.NAME, msg, color);
        });
    }

    private static void check(boolean manual) {
        try {
            Path jar = ownJar();
            if (jar == null) { state = "nur in der .jar möglich"; return; }
            state = "prüfe …";
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(API))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "PoldiClient")
                .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) { state = "kein Release gefunden"; if (manual) notify("Update: kein Release gefunden.", PoldiTheme.MUTED); return; }
            JsonObject rel = JsonParser.parseString(r.body()).getAsJsonObject();
            String tag = rel.get("tag_name").getAsString();
            int latest = buildNo(tag), mine = buildNo(currentVersion());
            if (latest <= mine) { state = "aktuell (" + currentVersion() + ")"; if (manual) notify("Poldi Client ist aktuell.", PoldiTheme.GOOD); return; }
            String url = null, name = null;
            for (var a : rel.getAsJsonArray("assets")) {
                JsonObject o = a.getAsJsonObject();
                String n = o.get("name").getAsString();
                if (n.endsWith(".jar") && !n.contains("sources")) { url = o.get("browser_download_url").getAsString(); name = n; break; }
            }
            if (url == null) { state = "Release ohne .jar"; return; }
            state = "lade " + tag + " …";
            Path dir = jar.getParent().resolve(".poldi-update");
            Files.createDirectories(dir);
            Path tmp = dir.resolve(name + ".part"), ready = dir.resolve(name);
            HttpResponse<Path> d = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "PoldiClient")
                .timeout(Duration.ofMinutes(3)).build(), HttpResponse.BodyHandlers.ofFile(tmp));
            if (d.statusCode() != 200 || Files.size(tmp) < 10_000) { state = "Download fehlgeschlagen"; Files.deleteIfExists(tmp); return; }
            Files.move(tmp, ready, StandardCopyOption.REPLACE_EXISTING);
            installOnExit(jar, ready);
            state = tag + " bereit – aktiv nach Neustart";
            notify("Update " + tag + " geladen – wird beim nächsten Start aktiv.", PoldiTheme.GOOD);
        } catch (Throwable e) {
            state = "Fehler: " + e.getClass().getSimpleName();
        }
    }

    /** Nach dem Schliessen des Spiels: alte .jar loeschen, neue in den mods-Ordner schieben. */
    private static synchronized void installOnExit(Path oldJar, Path newJar) {
        if (hookAdded) return;
        hookAdded = true;
        Path target = oldJar.getParent().resolve(newJar.getFileName().toString());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
                if (win) {
                    String ps = "$o='" + oldJar + "';$n='" + newJar + "';$t='" + target + "';"
                        + "for($i=0;$i -lt 60;$i++){try{if(Test-Path $o){Remove-Item $o -Force -ErrorAction Stop};break}catch{Start-Sleep -Milliseconds 500}};"
                        + "Move-Item $n $t -Force";
                    new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps).start();
                } else {
                    String sh = "sleep 2; rm -f '" + oldJar + "'; mv -f '" + newJar + "' '" + target + "'";
                    new ProcessBuilder("sh", "-c", sh).start();
                }
            } catch (Throwable ignored) {}
        }, "poldi-update-install"));
    }
}
