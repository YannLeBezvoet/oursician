# Oursician

Alternative open source à Yousician, en Kotlin.

## Étape 1 — Accordeur (en cours)

Une app Android qui écoute le micro et détecte la note jouée (algorithme YIN),
avec l'écart en cents par rapport à la note la plus proche et la corde de
guitare standard la plus proche (E2, A2, D3, G3, B3, E4).

## Étape 2 — Lecture de tablature (à venir)

Défilement des positions sur le manche à partir d'une tablature, avec
validation des notes jouées en temps réel.

## Architecture

- Kotlin + Jetpack Compose, MVVM, module unique `app`.
- `tuner/PitchDetector.kt` : détection de fréquence (YIN), Kotlin pur, testable sans Android.
- `tuner/AudioSource.kt` : capture micro (`AudioRecord`) exposée en `Flow<FloatArray>`.
- `tuner/NoteUtils.kt` : conversion fréquence → note + cents.
- `tuner/TunerViewModel.kt` / `tuner/TunerScreen.kt` : état et UI Compose.

## Licence

GPL-3.0 — voir [LICENSE](LICENSE).
