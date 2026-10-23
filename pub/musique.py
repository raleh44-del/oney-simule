#!/usr/bin/env python3
"""Musique de fond de la pub : 30 s en boucle, 96 BPM, fa majeur.

Tout est synthétisé ici, sans aucun échantillon externe : la musique est libre
de droits. Nappe, piano électrique, kalimba, synthé pincé, basse, batterie,
carillon. Mixée pour la barre de son 2.1 du rayon (caisson de basses).
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

PISTES = ['nappe', 'piano', 'kalimba', 'melodie', 'pluck', 'basse', 'kick', 'claq', 'charley', 'shaker',
          'crash', 'souffle', 'carillon']
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
            v = sum(np.sin(2 * np.pi * k * f * t + rng.uniform(0, 2 * np.pi)) / k ** 1.5
                    for k in range(1, 16) if k * f < 8000)
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

def pluck(m, duree, vel):
    """Synthé pincé (dent de scie dont les harmoniques aigus s'éteignent en premier)."""
    f = hz(m)
    t = temps(int((duree + .3) * SR))
    s = sum(np.sin(2 * np.pi * k * f * t) / k * np.exp(-t * (2 + 3.5 * k))
            for k in range(1, 24) if k * f < 12000)
    return s * enveloppe(t, .003, duree, .15) * vel

def basse(m, duree, vel):
    f = hz(m)
    t = temps(int((duree + .12) * SR))
    s = np.sin(2 * np.pi * f * t) + .45 * np.sin(4 * np.pi * f * t) + .2 * np.sin(6 * np.pi * f * t)
    s = np.tanh(1.2 * s) / np.tanh(1.2) + .7 * np.sin(2 * np.pi * f * t)    # fondamentale propre pour le caisson
    return s * enveloppe(t, .008, duree, .08) * (.75 + .25 * np.exp(-t / .25)) * vel

BRUIT_CLAQ = filtre(rng.standard_normal(2 * SR), bas=1300, haut=4200)
BRUIT_CORPS = filtre(rng.standard_normal(2 * SR), bas=600, haut=1800)
BRUIT_SHAKER = filtre(rng.standard_normal(2 * SR), bas=5500, haut=11000)
BRUIT_CHARLEY = filtre(rng.standard_normal(2 * SR), bas=7500, haut=15000)
BRUIT_CRASH = filtre(rng.standard_normal(4 * SR), bas=3500, haut=14000, ordre=1)
BRUIT_CLIC = filtre(rng.standard_normal(SR // 10), bas=2500, haut=9000)
BRUIT_SOUFFLE = rng.standard_normal(3 * SR)

def kick(vel):
    t = temps(int(.6 * SR))
    phase = 2 * np.pi * np.cumsum(50 + 110 * np.exp(-t / .025)) / SR
    s = np.sin(phase) * np.exp(-t / .3) + .45 * np.sin(2 * phase) * np.exp(-t / .08)
    clic = np.zeros(len(t))
    clic[:len(BRUIT_CLIC)] = BRUIT_CLIC / BRUIT_CLIC.std() * .12 * np.exp(-t[:len(BRUIT_CLIC)] / .004)
    return (s + clic) * enveloppe(t, .002, .45, .12) * vel

def tranche(bruit, n):
    i = rng.integers(0, len(bruit) - n)
    b = bruit[i:i + n]
    return b / b.std()

def claq(vel):
    n = int(.25 * SR)
    t = temps(n)
    e = np.exp(-t / .045) + .5 * np.exp(-np.abs(t - .009) / .003)   # double impact de claquement
    corps = tranche(BRUIT_CORPS, n) * np.exp(-t / .07) * .6          # le corps du clap
    return (tranche(BRUIT_CLAQ, n) * e + corps) * vel

def shaker(vel):
    n = int(.12 * SR)
    t = temps(n)
    return tranche(BRUIT_SHAKER, n) * (1 - np.exp(-t / .005)) * np.exp(-t / .03) * vel

def charley(vel, ouvert=False):
    n = int((.5 if ouvert else .1) * SR)
    t = temps(n)
    return tranche(BRUIT_CHARLEY, n) * (1 - np.exp(-t / .001)) * np.exp(-t / (.16 if ouvert else .022)) * vel

def crash(vel):
    n = int(3 * SR)
    t = temps(n)
    return tranche(BRUIT_CRASH, n) * (1 - np.exp(-t / .004)) * np.exp(-t / .9) * vel

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
    poser('kalimba', b2s(b), kalimba(note, vel), pan=-.5 if i % 2 else .5)

for mes in range(1, 12):                               # piano : rythme 3-3-2, discret dans l'intro
    debuts = [mes * 4 + x for x in (0, 1.5, 3)]
    if mes in (3, 7):
        debuts = [mes * 4, mes * 4 + 1.5, mes * 4 + 2, mes * 4 + 3]
    for k, b in enumerate(debuts):
        fin, (_, _, voix, _) = accord(b)
        vel = (.45 if mes < 2 else .8) * (1 if k == 0 else .75)
        for m in voix:
            poser('piano', b2s(b), piano(m, b2s(min(1.4, fin - b)), vel), pan=(-.35, .35)[k % 2])

KICKS = []
for mes in range(2, 12):                               # basse et batterie à l'allumage de la TV
    for x, d, octave in ((0, 1.2, 0), (1.5, .4, 0), (2, 1.2, 0), (3.5, .4, 12)):
        b = mes * 4 + x
        _, (racine, _, _, _) = accord(b)
        poser('basse', b2s(b), basse(racine + octave, b2s(d), .85 if x in (0, 2) else .6))
    derniere = mes == 11
    coups = ((0, 1), (2, .9), (2.75, .5)) if mes < 4 or derniere else ((0, 1), (1, .8), (2, .95), (3, .8))
    for x, v in coups:                                 # quatre temps dès le thème : ça avance
        if not (derniere and x > 2):
            poser('kick', b2s(mes * 4 + x), kick(v))
            KICKS.append(b2s(mes * 4 + x))
    for x in (1, 3):
        if not (derniere and x > 2):
            poser('claq', b2s(mes * 4 + x), claq(1), pan=-.1)
    for k in range(16):
        if not (derniere and k >= 8):
            poser('shaker', b2s(mes * 4 + k / 4), shaker((.35, .2, .7, .25)[k % 4]), pan=.3)
            if k % 2 or mes >= 4:                      # charley sur les contretemps, doubles croches ensuite
                ouvert = k == 14 and 4 <= mes < 11 and mes % 2 == 0
                poser('charley', b2s(mes * 4 + k / 4),
                      charley(.9 if k % 4 == 2 else .35, ouvert), pan=-.3)

for mes in range(4, 12):                               # thème avec la Smart TV, doublé à l'octave sur le prix
    phrase = FIN if mes == 11 else THEME[mes % 4]
    for x, m, d in phrase:
        b = mes * 4 + x
        poser('melodie', b2s(b), piano(m, b2s(d), .9, brillance=1.3))
        poser('melodie', b2s(b), cloche(m + 12, .3 if mes < 8 else .45, .6))
        poser('pluck', b2s(b), pluck(m, b2s(d), 1))

for t in (5, 10, 20):                                   # cymbale aux grandes entrées
    poser('crash', t, crash(1), pan=.2)

poser('souffle', 5.0 - 1.25, souffle(1.25, 1))          # la TV s'allume
for t in (10, 16, 22):                                  # changements de scène
    poser('souffle', t - .5, souffle(.5, .45))

for k, m in enumerate((84, 88, 91)):                    # carillon : le prix apparaît
    poser('carillon', 22.85 + k * .085, cloche(m, .6, .5), pan=(-.3, 0, .3)[k])
poser('carillon', 23.125, cloche(96, 1, 1.1) + cloche(91, .6, 1.1))

# ---------------------------------------------------------------- mixage
NIVEAU = {   # dBFS (RMS sur les passages joués) avant le master
    'melodie': -19, 'pluck': -22, 'basse': -17, 'kick': -17, 'nappe': -24, 'piano': -23,
    'kalimba': -25, 'claq': -25, 'charley': -27, 'shaker': -31, 'crash': -31,
    'souffle': -27, 'carillon': -20,
}
REVERB = {'nappe': .35, 'piano': .25, 'kalimba': .35, 'melodie': .35, 'pluck': .3, 'basse': 0, 'kick': 0,
          'claq': .3, 'charley': .05, 'shaker': .12, 'crash': .2, 'souffle': .3, 'carillon': .55}
# les accords s'effacent un instant sous chaque kick : la musique respire et pulse
POMPE = {'nappe': .4, 'piano': .25, 'kalimba': .2, 'basse': .3, 'pluck': .15, 'melodie': .1}

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

pompe = np.zeros(N)
noyau = temps(int(.5 * SR))
noyau = np.clip(noyau / .006, 0, 1) * np.exp(-noyau / .14)
for tk in KICKS:
    i = int(round(tk * SR))
    pompe[i:i + len(noyau)] = np.maximum(pompe[i:i + len(noyau)], noyau[:N - i])

def ping_pong(x, pas=.75 * TEMPS, retour=.4, taps=4):
    """Échos alternés gauche/droite en croche pointée."""
    mono = filtre(x.mean(0), bas=350, haut=5000)
    y = np.zeros((2, N))
    for k in range(1, taps + 1):
        d = int(round(k * pas * SR))
        y[(k + 1) % 2, d:] += mono[:N - d] * retour ** k
    return y

sec = np.zeros((2, N))
envoi = np.zeros((2, N))
for p in PISTES:
    x = pistes[p] * 10 ** (NIVEAU[p] / 20) / niveau(pistes[p])
    x *= 1 - POMPE.get(p, 0) * pompe
    if p == 'nappe':                                             # nappe élargie (milieu / côtés)
        milieu, cotes = x.mean(0), (x[0] - x[1]) / 2 * 1.8
        x = np.stack([milieu + cotes, milieu - cotes])
    if p in ('melodie', 'pluck'):
        x = x + .55 * ping_pong(x)
    sec += x
    envoi += x * REVERB[p]

ir = reponse_reverb()
L = 1 << int(np.ceil(np.log2(N + ir.shape[1])))
humide = np.stack([np.fft.irfft(np.fft.rfft(envoi[c], L) * np.fft.rfft(ir[c], L), L)[:N] for c in range(2)])
mix = sec + .6 * humide

def eclat(x, gain_db=4, fc=3500):
    """Relevé des aigus : présence et brillance sur la barre de son."""
    X = np.fft.rfft(x)
    f2 = np.fft.rfftfreq(len(x), 1 / SR) ** 2
    return np.fft.irfft(X * (1 + (10 ** (gain_db / 20) - 1) * f2 / (f2 + fc * fc)), len(x))

mix = np.stack([eclat(filtre(c, bas=28)) for c in mix])[:, :int(DUREE * SR)]
t = temps(mix.shape[1])
mix *= np.clip(t / .02, 0, 1)                                    # pas de clic au départ
fondu = np.clip((DUREE - t) / 1.1, 0, 1)                         # fondu final, avec celui de l'image
mix *= np.sin(fondu * np.pi / 2) ** 2

def compresseur(x, seuil_db=-6, ratio=2.5, attaque=.01, relache=.15):
    """Compression de bus : soude les instruments et donne de la densité."""
    bloc = int(.005 * SR)
    n = x.shape[1] // bloc * bloc
    rms = np.sqrt((x[:, :n] ** 2).mean(0).reshape(-1, bloc).mean(1) + 1e-12)
    exces = np.maximum(20 * np.log10(rms / np.sqrt((x ** 2).mean())) - seuil_db, 0)
    cible = exces * (1 - 1 / ratio)
    gr = np.zeros_like(cible)
    a, r = np.exp(-.005 / attaque), np.exp(-.005 / relache)
    g = 0.
    for k, c in enumerate(cible):
        g = a * g + (1 - a) * c if c > g else r * g + (1 - r) * c
        gr[k] = g
    gain = np.interp(np.arange(x.shape[1]), np.arange(len(gr)) * bloc + bloc / 2, 10 ** (-gr / 20))
    return x * gain

mix = compresseur(mix)
mix *= 10 ** (-16.8 / 20) / np.sqrt((mix ** 2).mean())           # ≈ -14 LUFS une fois en AAC
crete = .85                                                      # limiteur doux à -1,4 dBFS
mix = np.where(np.abs(mix) < crete * .7, mix,
               np.sign(mix) * (crete * .7 + crete * .3 * np.tanh((np.abs(mix) - crete * .7) / (crete * .3))))

pcm = mix.T * 32767 + rng.uniform(-.5, .5, mix.T.shape) + rng.uniform(-.5, .5, mix.T.shape)
with wave.open(sys.argv[1] if len(sys.argv) > 1 else 'musique.wav', 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(np.clip(np.round(pcm), -32768, 32767).astype('<i2').tobytes())
