# Pub magasin — TV QLED EDENWOOD 50" (ED50QT01FHD-RE)

`tv-edenwood-50-qled.mp4` : 30 s en boucle, 1920×1080, H.264 High 4.1, 30 i/s,
piste son muette. Se lit depuis une clé USB sur les TV de démonstration.

| Temps | Scène |
|---|---|
| 0 – 4,5 s | edenwood · TV QLED 50" |
| 4,5 – 10 s | 50" / 127 cm, QLED, Full HD 1080p, HDR10 |
| 10 – 16 s | Smart TV Tizen OS, AirPlay, Apple Home, Wi-Fi |
| 16 – 22 s | Points forts : Full HD, HDR10, Dolby Audio, 60 Hz, 3 HDMI, Wi-Fi |
| 22 – 30 s | 229 € TTC, classe énergie E, indice de durabilité 7,1/10 |

Le prix et la classe énergie restent affichés en coin de 6 à 22 s.

## Modifier puis refaire la vidéo

Les textes et le prix sont dans `tv-edenwood-50-qled.html` (l'ouvrir dans un
navigateur joue l'animation). Puis, depuis la racine du dépôt :

```
NODE_PATH=$(npm root -g) node pub/render.js
```

Prérequis : Node avec `playwright` (Chromium) et `ffmpeg`.
