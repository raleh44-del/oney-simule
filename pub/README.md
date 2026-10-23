# Pub magasin — TV QLED EDENWOOD 50" (ED50QT01FHD-RE)

`tv-edenwood-50-qled.mp4` : 30 s en boucle, 1920×1080, H.264 High 4.1, 30 i/s,
son AAC à −14 LUFS. Se lit depuis une clé USB sur les TV de démonstration.

| Temps | Scène |
|---|---|
| 0 – 4,5 s | edenwood · TV QLED 50" |
| 4,5 – 10 s | 50" / 127 cm, QLED, Full HD 1080p, HDR10 |
| 10 – 16 s | Smart TV Tizen OS, AirPlay, Apple Home, Wi-Fi |
| 16 – 22 s | Points forts : Full HD, HDR10, Dolby Audio, 60 Hz, 3 HDMI (dont 2.1), Wi-Fi |
| 22 – 30 s | 229 € TTC dont 11,11 € d'éco-participation, classe énergie E, indice de durabilité 7,1/10, pièces 11 ans, garantie 2 ans |

Le prix, l'éco-participation et la classe énergie restent affichés en coin de 6 à 22 s.

## Musique

`musique.py` la synthétise entièrement (aucun échantillon externe, donc libre
de droits) : 96 BPM en fa majeur, 12 mesures = 30 s. La batterie entre quand la TV
s'allume (5 s), la mélodie avec la Smart TV (10 s), un carillon souligne le prix
(23 s), et le fondu final rejoint le début pour boucler sans coupure.

Mixée pour la barre de son 2.1 du rayon : graves pleins pour le caisson, batterie
en quatre temps dès le thème, accords qui respirent sous la grosse caisse, échos
stéréo sur la mélodie et aigus relevés.

## Modifier puis refaire la vidéo

Les textes et le prix sont dans `tv-edenwood-50-qled.html` (l'ouvrir dans un
navigateur joue l'animation). Puis, depuis la racine du dépôt :

```
NODE_PATH=$(npm root -g) node pub/render.js
```

Prérequis : Node avec `playwright` (Chromium), `ffmpeg`, Python 3 avec `numpy`.
