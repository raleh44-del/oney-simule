# Podomètre

Petite appli Android qui ajoute des pas dans **Health Connect**, comme si le
téléphone les avait comptés pendant une marche.

**APK :** <https://github.com/raleh44-del/oney-simule/releases/tag/podometre>

## Ce qu'elle fait

- **Ajouter une marche** : un nombre de pas, une durée (auto ≈ 105 pas/min si
  vide) et l'heure de fin. Les pas sont écrits minute par minute avec une cadence
  qui varie un peu, marqués « enregistrés automatiquement » par le téléphone.
  Si la plage horaire chevauche des pas déjà ajoutés par l'appli, la marche est
  reculée d'autant.
- **Marche en direct** : ajoute environ 110 pas/min tant que l'appli reste
  ouverte (l'écran reste allumé), écrits toutes les 30 secondes.
- **Total du jour** toutes sources confondues, et la part ajoutée par l'appli.
- **Effacer** les pas ajoutés aujourd'hui (Health Connect ne permet de supprimer
  que les données de l'appli elle-même).

## Limites

- L'appli du challenge doit lire ses pas **depuis Health Connect**. Une appli qui
  compte elle-même avec le capteur du téléphone ne verra rien : ce capteur ne peut
  pas être simulé sans root.
- Health Connect ne compte pas deux fois la même minute venant de deux sources :
  dans *Health Connect › Données et accès › Pas › Sources de données*, placer
  **Podomètre** en premier, ou ajouter les pas sur des heures où le téléphone n'a
  pas bougé.
- Android 9 minimum (Health Connect). Sur Android 13 et moins, l'appli Health
  Connect doit être installée depuis le Play Store — l'appli propose le lien.

## Build local

```bash
cd podometre
./gradlew assembleDebug
# APK dans app/build/outputs/apk/debug/
```
