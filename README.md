# Simulateur Oney — ES & EC

Outil interne d'aide à la vente. Reproduit la simulation de financement Oney
**crédit rond, TAEG fixe 20,50 %**, avec les garanties ElectroSûr (ES) et
l'assurance mobile (EC).

**Application en ligne :** <https://raleh44-del.github.io/oney-simule/>
**APK Android :** <https://github.com/raleh44-del/oney-simule/releases/tag/apk>

Installable sur l'écran d'accueil (Android : menu Chrome, iPhone : bouton Partager)
et fonctionne ensuite entièrement hors ligne.

## Utilisation

1. Saisir le **montant à financer** et, au choix, le **budget mensuel** ou la
   **durée souhaitée** — le second champ bascule entre les deux.
2. Choisir le mode en haut de l'écran :
   - **Garanties ES** — comparaison base / XL / 2XL / 3XL. L'assurance mobile EC
     reste disponible en option et s'ajoute alors à chaque garantie.
   - **Assurance EC** — n'affiche que le tarif correspondant au prix du mobile,
     puisque ce tarif est imposé par le barème et non choisi.
3. Tout se recalcule en direct, sans bouton.

Sur poste fixe, les options passent côte à côte et la garantie de base devient une
ligne de référence en pleine largeur.

## Règles de calcul

**Taux mensuel** = (1 + 20,50 %)^(1/12) − 1 ≈ **1,5661 % / mois**

**Budget imposé** — amortissement mois par mois :

```
solde = solde × (1 + taux mensuel) − mensualité crédit
```

jusqu'à ce que le solde et ses intérêts tiennent dans une dernière échéance réduite.

**Durée imposée** — annuité constante :

```
mensualité crédit = C × i / (1 − (1 + i)^−N)
```

**Point clé** : la mensualité qui rembourse le crédit n'est pas le budget, mais

```
mensualité crédit = budget mensuel − garantie ES − assurance EC
```

Le client paie bien son budget (ex. 60 €/mois), mais avec ES XL seulement 54 €
remboursent le crédit → la durée s'allonge et le coût du financement augmente.
À l'inverse, à durée imposée, une garantie n'ajoute **aucun intérêt** : elle
augmente seulement la mensualité.

## Barèmes

| Garantie ES | Prime |
|---|---|
| Garantie de base | 0 € |
| ElectroSûr XL | 6 €/mois |
| ElectroSûr 2XL | 11 €/mois |
| ElectroSûr 3XL | 15 €/mois |

| Prix du mobile TTC | Assurance EC |
|---|---|
| jusqu'à 149,99 € | 2 €/mois |
| 150 à 299,99 € | 4 €/mois |
| 300 à 499,99 € | 7 €/mois |
| 500 € et + | 10 €/mois |

## Contrôle du moteur

Valeurs relevées sur les simulations officielles :

| Cas | Attendu | Calculé |
|---|---|---|
| Oney 500 € / 100 € | 5 × 100 € + 24,87 € — intérêts 24,87 € | identique |
| 800 € / 60 € — base | 15 × 60 € + 4,36 € — coût 104,35 € | 4,39 € — 104,39 € |
| 800 € / 60 € — XL | 16 × 60 € + 53,35 € — coût 117,35 € | identique |
| 800 € / 60 € — 2XL | 19 × 60 € + 0,15 € — coût 131,14 € | 0,14 € — 131,14 € |
| 800 € / 60 € — 3XL | 20 × 60 € + 44,94 € — coût 144,94 € | identique |

Sur de très petits montants, un écart de quelques centimes subsiste avec le
simulateur d'origine, qui arrondit ses valeurs intermédiaires.

## Structure

| Chemin | Rôle |
|---|---|
| `index.html` | l'application entière — aucune dépendance externe |
| `manifest.webmanifest`, `sw.js`, `icon-*.png` | installation sur l'écran d'accueil et mode hors ligne |
| `android/` | enveloppe Android (WebView) autour de `index.html` |
| `.github/workflows/build-apk.yml` | build et publication automatiques de l'APK |

La tâche Gradle `syncWebApp` copie `index.html` dans les assets avant chaque build :
la page en ligne et l'APK ne peuvent pas diverger.

## Mentions

Outil interne d'aide à la vente, **non officiel**. Simulation indicative, qui ne
remplace pas l'édition officielle Oney. L'application ne collecte aucune donnée :
rien ne sort du téléphone, aucun compte, aucune connexion nécessaire.
