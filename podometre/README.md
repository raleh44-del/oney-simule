# Podomètre

Petite appli Android qui ajoute des pas dans **Santé Connect** (Health Connect),
comme si le téléphone les avait comptés pendant une marche — pour les faire
remonter dans **Treely** (Treely for Teams).

**APK :** <https://github.com/raleh44-del/oney-simule/releases/tag/podometre>

## Ce qu'elle fait

- **Diagnostic** (se lance tout seul à l'ouverture) : vérifie Santé Connect,
  les autorisations, écrit 300 pas de test pour voir s'ils comptent dans le total
  (priorité des sources) puis les efface, liste les sources de pas du jour, et
  regarde par où Treely lit ses pas (Santé Connect ou Google Fit) et si l'accès
  est accordé. Le maillon Google Fit → Treely ne peut pas être vu depuis le
  téléphone : à vérifier en ouvrant Google Fit.
- **Ajouter une marche** : un nombre de pas, une durée (auto ≈ 105 pas/min si
  vide) et l'heure de fin. Les pas sont écrits minute par minute avec une cadence
  qui varie un peu, marqués « enregistrés automatiquement » par le téléphone.
  Si la plage horaire chevauche des pas déjà ajoutés par l'appli, la marche est
  reculée d'autant.
- **Marche en direct** : ajoute environ 110 pas/min tant que l'appli reste
  ouverte (l'écran reste allumé), écrits toutes les 30 secondes.
- **Total du jour** toutes sources confondues, et la part ajoutée par l'appli.
- **Effacer** les pas ajoutés aujourd'hui (Santé Connect ne permet de supprimer
  que les données de l'appli elle-même).

## Réglages pour Treely

L'appli a une section **Faire monter les pas sur Treely** avec des raccourcis :

1. Dans Treely, la source des pas doit être Santé Connect (ou Google Fit).
2. Dans Santé Connect, Treely doit avoir le droit de lire les **Pas** — bouton
   *Accès de Treely dans Santé Connect*.
3. Dans *Santé Connect › Données et accès › Activité › Pas › Sources de
   données*, placer **Podomètre** en premier.
4. Si Treely passe par Google Fit : *Google Fit › Profil › Paramètres* →
   activer « Synchroniser Fit avec Santé Connect ».
5. Tester avec 200 pas, puis ouvrir Treely (l'appli le propose après chaque ajout).

## Limites

- Treely doit lire ses pas **depuis Santé Connect ou Google Fit**. Si elle
  compte elle-même avec le capteur du téléphone, elle ne verra rien : ce capteur
  ne peut pas être simulé sans root.
- Santé Connect ne compte pas deux fois la même minute venant de deux sources :
  mettre Podomètre en premier, ou ajouter les pas sur des heures où le téléphone
  n'a pas bougé (champ « terminée il y a »).
- Android 9 minimum. Sur Android 13 et moins, l'appli Santé Connect doit être
  installée depuis le Play Store — l'appli propose le lien.

## Mise à jour

Les APK sont signés avec la clé `app/debug.keystore` : une nouvelle version
s'installe par-dessus l'ancienne. (La toute première version, 1.0, avait une autre
clé : la désinstaller une fois.)

## Build local

```bash
cd podometre
./gradlew assembleDebug
# APK dans app/build/outputs/apk/debug/
```
