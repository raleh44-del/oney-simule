# Simu Oney — application Android

Enveloppe Android (WebView) autour du simulateur `index.html`.
Le fichier web reste la source unique : il est copie dans les assets a chaque
build par la tache Gradle `syncWebApp`. Modifier le simulateur met donc a jour
la page en ligne **et** l'APK au prochain build.

## Telecharger l'APK

Le build tourne automatiquement a chaque modification de `index.html`
ou de `android/`. L'APK est publie ici :

<https://github.com/raleh44-del/simulateur-armes/releases/tag/oney-apk-latest>

Telecharge `SimuOney-debug.apk`, ouvre-le sur le telephone et autorise
l'installation depuis cette source si Android le demande.

## Build local

```bash
cd android
./gradlew assembleDebug
# APK genere dans app/build/outputs/apk/debug/
```

L'application fonctionne entierement hors ligne : le HTML, le CSS et le
JavaScript sont embarques dans l'APK, aucune connexion n'est necessaire.
