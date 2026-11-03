# Boîtier USB HerMorse

Permet de brancher un vrai manipulateur (pioche ou double palette) sur le téléphone.

## Matériel

- 1 carte **Arduino Pro Micro** (ATmega32U4). Prends de préférence une version à prise USB‑C.
- 1 prise **jack femelle 3,5 mm stéréo** (ou 6,35 mm selon ton manipulateur).
- 1 câble **USB‑C ↔ USB‑C** (ou un adaptateur OTG USB‑C vers micro‑USB si ta carte est en micro‑USB).
- Un petit boîtier plastique.

## Câblage

```
  Jack femelle stéréo            Pro Micro
  ───────────────────            ─────────
  Pointe (tip)   ─────────────►  broche 2   (point, ou pioche)
  Bague  (ring)  ─────────────►  broche 3   (trait)
  Corps  (sleeve)─────────────►  GND
```

Aucune résistance n'est nécessaire (pull-up internes). Pour une pioche, seuls la pointe et le corps servent.

## Programmation (une seule fois, depuis un PC)

1. Installer l'IDE Arduino.
2. Ouvrir `HerMorseKey/HerMorseKey.ino`.
3. Outils > Type de carte : **Arduino Leonardo** (ou « SparkFun Pro Micro » si les cartes SparkFun sont installées).
4. Choisir le port, puis **Téléverser**.

## Utilisation

1. Brancher le boîtier sur le téléphone en USB‑C (le téléphone le voit comme un clavier).
2. HerMorse > menu burger > Réglages > Manipulateur > Source : **Externe USB**, puis Type : Pioche, Iambique A ou B.
3. Dans Apprendre (saisie libre ou Lire → manipuler), les voyants « Manipulateur externe » s'allument quand tu appuies.

Remarque : quand un clavier USB est branché, Android masque parfois le clavier à l'écran. Pour l'exercice « Écouter → écrire », débranche le boîtier, ou active « Afficher le clavier virtuel » dans les paramètres Android (Clavier physique).

## Pour plus tard (v0.6, DOB)

Une sortie optocoupleur (4N35) sur la broche 4 permettra au boîtier de manipuler directement la prise KEY de l'émetteur, et une deuxième sur la broche 5 de commander le PTT.
