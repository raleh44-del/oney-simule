#!/usr/bin/env python3
"""Musique de fond de la pub : 30 s en boucle, 96 BPM, fa majeur.

Tout est synthétisé ici, sans aucun échantillon externe : la musique est libre
de droits. Nappe, piano électrique, kalimba, basse, batterie douce, carillon.
Calée sur la vidéo : la batterie entre quand la TV s'allume (5 s), la mélodie
avec la Smart TV (10 s), le carillon sur l'apparition du prix (23 s).

    python3 pub/musique.py sortie.wav
"""
import sys
import wave
import numpy as np

SR = 48000
TEMPS = 60 / 96                 # 0,625 s
MESURE = 4 * TEMPS              # 2,5 s : 12 mesures = les 30 s de la vidéo
DUREE = 30.0
N = int((DUREE + 4) * SR)       # marge pour les queues de notes
rng = np.random.default_rng(7)

# ---------------------------------------------------------------- harmonie
# basse, nappe, piano (accords), kalimba (arpège)
ACCORDS = {
    'F':   (41, [53, 57, 60, 64, 67], [64, 67, 69, 72], [77, 81, 84, 88]),   # Fa maj9
    'C/E': (40, [52, 55, 60, 64, 67], [62, 64, 67, 72], [76, 79, 84, 86]),   # Do add9 / Mi
    'Dm':  (38, [50, 57, 60, 64, 65], [62, 65, 69, 72], [74, 77, 81, 84]),   # Ré m9
    'Bb':  (34, [46, 53, 57, 60, 62], [60, 62, 65, 69], [77, 81, 82, 86]),   # Si♭ maj9
    'C11': (36, [48, 55, 58, 62, 65], [62, 65, 67, 70], [79, 82, 84, 86]),   # Do 11
}
# (début en temps, durée en temps, accord) : 3 fois la grille F | C/E | Dm | Bb
GRILLE = []
for m in range(12):
    nom = ['F', 'C/E', 'Dm', 'Bb'][m % 4]
    if m in (3, 7):              # relance vers le retour à Fa
        GRILLE += [(m * 4, 2, 'Bb'), (m * 4 + 2, 2, 'C11')]
    else:
        GRILLE.append((m * 4, 4, nom))

def accord(b):
    for debut, duree, nom in GRILLE:
        if debut <= b < debut + duree:
            return debut + duree, ACCORDS[nom]
    raise ValueError(b)

# mélodie : (temps dans la mesure, note, durée) par mesure de la grille
THEME = [
    [(0, 72, .5), (.5, 77, .5), (1, 81, 1.5), (2.5, 79, .5), (3, 77, 1)],
    [(0, 79, 1.5), (1.5, 76, .5), (2, 72, 2)],
    [(0, 72, .5), (.5, 77, .5), (1, 81, 1.5), (2.5, 84, .5), (3, 81, 1)],
    [(0, 77, 1.5), (1.5, 74, .5), (2, 79, 2)],
]
FIN = [(0, 77, 1.5), (1.5, 74, .5), (2, 81, 2)]     # dernière mesure : reste en suspens

# ---------------------------------------------------------------- outils
def hz(m):
    return 440 * 2 ** ((m - 69) / 12)

def temps(n):
    return np.arange(n) / SR

def enveloppe(t, attaque, fin, relache):
    a = np.clip(t / attaque, 0, 1)
    r = np.clip(1 - (t - fin) / relache, 0, 1)
    return (a * a * (3 - 2 * a)) * (r * r * (3 - 2 * r))

def filtre(x, bas=None, haut=None, ordre=2):
    """Passe-haut à `bas` Hz et/ou passe-bas à `haut` Hz, dans le domaine fréquentiel."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if haut:
        g /= np.sqrt(1 + (f / haut) ** (2 * ordre))
    if bas:
        g /= np.sqrt(1 + (bas / np.maximum(f, 1e-6)) ** (2 * ordre))
    return np.fft.irfft(X * g, len(x))

PISTES = ['nappe', 'piano', 'kalimba', 'melodie', 'basse', 'kick', 'claq', 'shaker', 'souffle', 'carillon']
pistes = {p: np.zeros((2, N)) for p in PISTES}

def poser(piste, t0, son, pan=0.0):
    i = int(round(t0 * SR))
    son = son[..., :N - i]
    if son.ndim == 1:
        a = (pan + 1) * np.pi / 4
        son = np.stack([son * np.cos(a), son * np.sin(a)])
    pistes[piste][:, i:i + son.shape[1]] += son

# ---------------------------------------------------------------- instruments
def nappe(notes, duree):
    t = temps(int((duree + 1.2) * SR))
    s = np.zeros((2, len(t)))
    for m in notes:
        for cents, pan in ((-7, -.8), (0, 0), (7, .8)):
            f = hz(m) * 2 ** (cents / 1200)
            v = sum(np.sin(2 * np.pi * k * f * t + rng.uniform(0, 2 * np.pi)) / k ** 1.8
                    for k in range(1, 10) if k * f < 5000)
            a = (pan + 1) * np.pi / 4
            s += np.stack([v * np.cos(a), v * np.sin(a)])
    return s * enveloppe(t, .35, duree, 1.2) / len(notes)

def piano(m, duree, vel, brillance=1.0):
    """Piano électrique en synthèse FM."""
    f = hz(m)
    t = temps(int((duree + .4) * SR))
    indice = vel * brillance * (.25 + 1.5 * np.exp(-t / .18))
    s = np.sin(2 * np.pi * f * t + indice * np.sin(2 * np.pi * f * t))
    s += .07 * vel * np.sin(2 * np.pi * 7 * f * t) * np.exp(-t / .015)
    return s * np.exp(-t / 1.6) * enveloppe(t, .003, duree, .25) * vel

def cloche(m, vel, tenue=.9):
    f = hz(m)
    t = temps(int((tenue * 5) * SR))
    s = np.sin(2 * np.pi * f * t + 1.1 * np.exp(-t / .3) * np.sin(2 * np.pi * 3.5 * f * t))
    return s * np.exp(-t / tenue) * enveloppe(t, .002, 99, 1) * vel

def kalimba(m, vel):
    f = hz(m)
    t = temps(int(1.4 * SR))
    s = np.sin(2 * np.pi * f * t) * np.exp(-t / .45)
    s += .22 * np.sin(2 * np.pi * 5.95 * f * t) * np.exp(-t / .04)
    return s * enveloppe(t, .002, 99, 1) * vel

def basse(m, duree, vel):
    f = hz(m)
    t = temps(int((duree + .12) * SR))
    s = np.sin(2 * np.pi * f * t) + .45 * np.sin(4 * np.pi * f * t) + .2 * np.sin(6 * np.pi * f * t)
    s = np.tanh(1.2 * s) / np.tanh(1.2)
    return s * enveloppe(t, .008, duree, .08) * (.75 + .25 * np.exp(-t / .25)) * vel

def kick(vel):
    t = temps(int(.5 * SR))
    phase = 2 * np.pi * np.cumsum(48 + 75 * np.exp(-t / .03)) / SR
    s = np.sin(phase) * np.exp(-t / .22) + .45 * np.sin(2 * phase) * np.exp(-t / .07)
    return s * enveloppe(t, .002, .38, .1) * vel

BRUIT_CLAQ = filtre(rng.standard_normal(2 * SR), bas=1300, haut=4200)
BRUIT_SHAKER = filtre(rng.standard_normal(2 * SR), bas=5500, haut=11000)
BRUIT_SOUFFLE = rng.standard_normal(3 * SR)

def tranche(bruit, n):
    i = rng.integers(0, len(bruit) - n)
    b = bruit[i:i + n]
    return b / b.std()

def claq(vel):
    n = int(.25 * SR)
    t = temps(n)
    e = np.exp(-t / .045) + .5 * np.exp(-np.abs(t - .009) / .003)   # double impact de claquement
    return tranche(BRUIT_CLAQ, n) * e * vel

def shaker(vel):
    n = int(.12 * SR)
    t = temps(n)
    return tranche(BRUIT_SHAKER, n) * (1 - np.exp(-t / .005)) * np.exp(-t / .03) * vel

def souffle(duree, vel):
    """Montée de bruit filtré qui s'ouvre jusqu'au changement de scène."""
    n = int((duree + .35) * SR)
    t = temps(n)
    b = BRUIT_SOUFFLE[:n]
    coupures = [300, 700, 1600, 3500, 8000]
    bandes = [filtre(b, haut=c) for c in coupures]
    x = np.clip(t / duree, 0, 1) * (len(coupures) - 1)
    s = np.zeros(n)
    for k, bande in enumerate(bandes):
        s += bande * np.clip(1 - np.abs(x - k), 0, 1)
    e = np.where(t < duree, (t / duree) ** 2.2, np.exp(-(t - duree) / .08))
    return s / s.std() * e * vel

# ---------------------------------------------------------------- partition
def b2s(b):
    return b * TEMPS

for debut, duree, nom in GRILLE:                       # nappe sur tout le morceau
    poser('nappe', b2s(debut), nappe(ACCORDS[nom][1], b2s(duree)))

for i in range(12 * 8):                                # arpège de kalimba en croches
    b = i / 2
    _, (_, _, _, arp) = accord(b)
    note = arp[[0, 1, 2, 3, 2, 1, 2, 3][i % 8]]
    vel = (.75 if b < 16 else .45) * (1 if i % 2 == 0 else .7)
    poser('kalimba', b2s(b), kalimba(note, vel), pan=-.35 if i % 2 else .35)

for mes in range(1, 12):                               # piano : rythme 3-3-2, discret dans l'intro
    debuts = [mes * 4 + x for x in (0, 1.5, 3)]
    if mes in (3, 7):
        debuts = [mes * 4, mes * 4 + 1.5, mes * 4 + 2, mes * 4 + 3]
    for k, b in enumerate(debuts):
        fin, (_, _, voix, _) = accord(b)
        vel = (.45 if mes < 2 else .8) * (1 if k == 0 else .75)
        for m in voix:
            poser('piano', b2s(b), piano(m, b2s(min(1.4, fin - b)), vel), pan=(-.25, .25)[k % 2])

for mes in range(2, 12):                               # basse et batterie à l'allumage de la TV
    for x, d, octave in ((0, 1.2, 0), (1.5, .4, 0), (2, 1.2, 0), (3.5, .4, 12)):
        b = mes * 4 + x
        _, (racine, _, _, _) = accord(b)
        poser('basse', b2s(b), basse(racine + octave, b2s(d), .85 if x in (0, 2) else .6))
    derniere = mes == 11
    for x, v in ((0, 1), (2, .9), (2.75, .5)):
        if not (derniere and x > 2):
            poser('kick', b2s(mes * 4 + x), kick(v))
    for x in (1, 3):
        if not (derniere and x > 2):
            poser('claq', b2s(mes * 4 + x), claq(1), pan=-.1)
    for k in range(16):
        if not (derniere and k >= 8):
            poser('shaker', b2s(mes * 4 + k / 4), shaker((.35, .2, .7, .25)[k % 4]), pan=.3)

for mes in range(4, 12):                               # thème avec la Smart TV, doublé à l'octave sur le prix
    phrase = FIN if mes == 11 else THEME[mes % 4]
    for x, m, d in phrase:
        b = mes * 4 + x
        poser('melodie', b2s(b), piano(m, b2s(d), .9, brillance=1.3))
        poser('melodie', b2s(b), cloche(m + 12, .3 if mes < 8 else .45, .6))

poser('souffle', 5.0 - 1.25, souffle(1.25, 1))          # la TV s'allume
for t in (10, 16, 22):                                  # changements de scène
    poser('souffle', t - .5, souffle(.5, .45))

for k, m in enumerate((84, 88, 91)):                    # carillon : le prix apparaît
    poser('carillon', 22.85 + k * .085, cloche(m, .6, .5), pan=(-.3, 0, .3)[k])
poser('carillon', 23.125, cloche(96, 1, 1.1) + cloche(91, .6, 1.1))

# ---------------------------------------------------------------- mixage
NIVEAU = {   # dBFS (RMS sur les passages joués) avant le master
    'melodie': -19, 'basse': -19, 'kick': -20, 'nappe': -24, 'piano': -24,
    'kalimba': -25, 'claq': -28, 'shaker': -31, 'souffle': -28, 'carillon': -21,
}
REVERB = {'nappe': .35, 'piano': .25, 'kalimba': .35, 'melodie': .35, 'basse': 0, 'kick': 0,
          'claq': .25, 'shaker': .12, 'souffle': .3, 'carillon': .55}

def niveau(x):
    blocs = (x[:, :N // 1024 * 1024] ** 2).mean(0).reshape(-1, 1024).mean(1)
    actifs = blocs[blocs > blocs.max() * 1e-3]
    return np.sqrt(actifs.mean())

def reponse_reverb(rt=2.2):
    n = int(2.6 * SR)
    t = temps(n)
    ir = np.zeros((2, n))
    for c in range(2):
        b = rng.standard_normal(n)
        grave = filtre(b, haut=2500)
        ir[c] = grave * np.exp(-6.9 * t / rt) + (b - grave) * np.exp(-6.9 * t / (rt * .45))
    ir = np.roll(ir, int(.022 * SR), axis=1)
    ir[:, :int(.022 * SR)] = 0
    return ir / np.sqrt((ir ** 2).sum(1, keepdims=True))

sec = np.zeros((2, N))
envoi = np.zeros((2, N))
for p in PISTES:
    x = pistes[p] * 10 ** (NIVEAU[p] / 20) / niveau(pistes[p])
    sec += x
    envoi += x * REVERB[p]

ir = reponse_reverb()
L = 1 << int(np.ceil(np.log2(N + ir.shape[1])))
humide = np.stack([np.fft.irfft(np.fft.rfft(envoi[c], L) * np.fft.rfft(ir[c], L), L)[:N] for c in range(2)])
mix = sec + .6 * humide

def eclat(x, gain_db=2.5, fc=5000):
    """Léger relevé des aigus : les haut-parleurs de TV étouffent le haut du spectre."""
    X = np.fft.rfft(x)
    f2 = np.fft.rfftfreq(len(x), 1 / SR) ** 2
    return np.fft.irfft(X * (1 + (10 ** (gain_db / 20) - 1) * f2 / (f2 + fc * fc)), len(x))

mix = np.stack([eclat(filtre(c, bas=40)) for c in mix])[:, :int(DUREE * SR)]
t = temps(mix.shape[1])
mix *= np.clip(t / .02, 0, 1)                                    # pas de clic au départ
fondu = np.clip((DUREE - t) / 1.1, 0, 1)                         # fondu final, avec celui de l'image
mix *= np.sin(fondu * np.pi / 2) ** 2

mix *= 10 ** (-18.5 / 20) / np.sqrt((mix ** 2).mean())           # ≈ -16 LUFS une fois en AAC
crete = .89                                                      # limiteur doux à -1 dBFS
mix = np.where(np.abs(mix) < crete * .7, mix,
               np.sign(mix) * (crete * .7 + crete * .3 * np.tanh((np.abs(mix) - crete * .7) / (crete * .3))))

pcm = mix.T * 32767 + rng.uniform(-.5, .5, mix.T.shape) + rng.uniform(-.5, .5, mix.T.shape)
with wave.open(sys.argv[1] if len(sys.argv) > 1 else 'musique.wav', 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(np.clip(np.round(pcm), -32768, 32767).astype('<i2').tobytes())
