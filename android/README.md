# Delrobi — application Android

Enveloppe Android (WebView) autour de l'app web `app-muscu.html`. Le fichier HTML
reste la **source unique** : il est copié automatiquement dans les assets à chaque
build par la tâche Gradle `syncWebApp`.

## Récupérer l'APK (le plus simple)

Un workflow GitHub Actions (`.github/workflows/build-apk.yml`) compile l'APK à
chaque push sur la branche.

1. Onglet **Actions** du dépôt → run **« Build Delrobi APK »**.
2. Section **Artifacts** en bas → télécharger **`Delrobi-apk`** (contient `Delrobi-debug.apk`).
3. Copier l'APK sur ton téléphone Android et l'installer
   (autoriser « Sources inconnues » pour ton navigateur/gestionnaire de fichiers).

On peut aussi relancer le build manuellement : Actions → « Build Delrobi APK » → **Run workflow**.

## Compiler en local (si tu as le SDK Android)

```bash
cd android
./gradlew assembleDebug
# APK généré : app/build/outputs/apk/debug/app-debug.apk
```

## Détails techniques

- `minSdk` 26 (Android 8.0+), `targetSdk`/`compileSdk` 34
- APK **debug** signé avec la clé de debug → parfait pour tester, pas pour le Play Store
- `localStorage` activé (`domStorage`) : les séances sont sauvegardées dans l'app
- Permission `INTERNET` uniquement pour la police web et three.js (démo 3D des mouvements),
  tous deux chargés depuis un CDN. Sans réseau, l'app fonctionne normalement : la démo 3D
  s'efface et les icônes des machines restent affichées
