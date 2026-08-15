# Simulateur Oney — ES & EC

Application mobile (PWA installable) qui reproduit la simulation de financement
Oney **CRÉDIT ROND, TAEG fixe 20,50 %**, avec les garanties ElectroSûr (ES)
et l'assurance mobile (EC).

## Utilisation

1. Ouvrir <https://raleh44-del.github.io/simulateur-armes/oney/>
2. Choisir le mode en haut de l'écran :
   - **Garanties ES** — les cartes comparent base / XL / 2XL / 3XL.
     L'assurance mobile EC reste disponible en option : elle s'ajoute alors à
     chaque garantie (financement d'un appareil **et** d'un mobile).
   - **Assurance EC** — les cartes ES sont remplacées par les tarifs mobile
     0 / 2 / 4 / 7 / 10 €. Le tarif correspondant au montant à financer
     (= prix du mobile) est marqué **CONSEILLÉ**.
3. Saisir le **montant à financer** et le **budget mensuel**
   (pastilles de budget rapides : 20 à 150 €)
4. Tout se recalcule en direct, sans bouton

Chaque carte affiche la durée en gros, une jauge qui compare visuellement les
durées entre elles, les trois chiffres clés (coût du crédit, coût par mois,
total payé) et l'écart par rapport à la garantie de base (« +1 mois · +12,97 € »).

Le bouton ☀/☾ en haut à droite bascule entre thème sombre et thème clair
(pratique sous les néons du magasin). Le choix est mémorisé.

Installation sur le téléphone : « Ajouter à l'écran d'accueil » (Android : menu Chrome,
iPhone : bouton Partager). L'app fonctionne ensuite hors ligne.

## Règles de calcul

**Taux mensuel** = (1 + 20,50 %)^(1/12) − 1 ≈ **1,5661 % / mois**

**Amortissement** — chaque mois :

```
solde = solde × (1 + taux mensuel) − mensualité crédit
```

jusqu'à ce que le solde + ses intérêts tiennent dans une dernière échéance réduite.

**Point clé** : la mensualité qui rembourse le crédit n'est pas le budget, mais

```
mensualité crédit = budget mensuel − garantie ES − assurance EC
```

Le client paie bien son budget (ex. 60 €/mois), mais avec ES XL seulement 54 €
remboursent le crédit → la durée s'allonge et le coût du financement augmente.

**Coût du financement** = total des intérêts.
**Coût par mois** = intérêts ÷ (nb de mois pleins + dernière échéance ÷ mensualité crédit).

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

Valeurs de référence relevées sur les simulateurs officiels :

| Cas | Attendu | Calculé |
|---|---|---|
| Oney 500 € / 100 € | 5 × 100 € + 24,87 € — intérêts 24,87 € | identique |
| 800 € / 60 € — base | 15 × 60 € + 4,36 € — coût 104,35 € | 4,39 € — 104,39 € |
| 800 € / 60 € — XL | 16 × 60 € + 53,35 € — coût 117,35 € | identique |
| 800 € / 60 € — 2XL | 19 × 60 € + 0,15 € — coût 131,14 € | 0,14 € — 131,14 € |
| 800 € / 60 € — 3XL | 20 × 60 € + 44,94 € — coût 144,94 € | identique |

Sur de très petits montants (100 € / 50 €) un écart de 2 à 7 centimes subsiste avec
l'ancienne capture : le simulateur d'origine y arrondit ses valeurs intermédiaires
(son propre coût affiché, 2,39 €, diffère déjà d'un centime de sa dernière échéance
de 2,40 €).

Simulation indicative — ne remplace pas l'édition officielle Oney.
