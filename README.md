# Poldi Client (Minecraft 26.2, Fabric)

Survival-Client mit rotem Vanilla-Design. Mod-Menü: **Right Shift**.

## Bauen (GitHub Actions)
1. Diesen Ordner in ein GitHub-Repo hochladen (auch `.github/` mit hochladen).
2. Tab **Actions** → "Build Poldi Client" läuft automatisch.
3. Fertig: unter **Artifacts** liegt `poldi-client-jar`.
4. Wenn der Build fehlschlägt: Artifact `build-log` herunterladen und den Fehler schicken.

Benötigt im Spiel: Fabric Loader + Fabric API für 26.2, Java 25.

## Auto-Update
Jeder Push auf `main` baut die Mod und veröffentlicht sie als GitHub-Release (`build-<Nummer>`).
Der Client prüft beim Start das neueste Release, lädt es im Hintergrund und tauscht die .jar beim Beenden des Spiels.
Ab dem nächsten Start ist die neue Version aktiv. Abschalten: Menü → General → Settings → Auto-Update.
Das Repo muss öffentlich sein.

## Befehle
`/poldi menu|lag|perf|lite|hud|keys|sell|trailer|update|leak|scale`, `/waypoint`, `/note`, `/profile`,
`/timer`, `/stopwatch`, `/giveaway`, `/twitch`, `/youtube`, `/calc`, `/glass`, `/fullbright`

## Logo
`src/main/resources/assets/poldi/textures/gui/logo.png` (64×64) und `icon.png` (128×128) einfach ersetzen.
