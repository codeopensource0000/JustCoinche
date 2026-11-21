# Journal de la soirée IA

Suivi de l'exécution de `docs/plan-ia-soiree.md` (figé), sur la branche `test_ia`.
Chaque entrée donne le commit, le tag, les preuves (tests, sabotages) et les mesures de
l'arène. Les échecs sont notés tels quels.

## Étape 0 — base (tag `ia-base`)

- Catalogue d'IA (`engine/.../ai/AiCatalog.kt`) : `ia0` Aléatoire, `ia1` Débutant.
- Arène en ligne de commande : `./gradlew :engine:duel --args="ia1 ia0 400 3"` (IA A, IA B,
  donnes, graine). Elle donne l'écart par donne, les contrats réussis et le temps de
  réflexion.
- Mesure de contrôle : `ia1` contre `ia0`, 400 donnes → **+165,1 ± 12,9** par donne
  (significatif). Contrats réussis : `ia1` 311 sur 367, `ia0` 79 sur 400.
- Tests : 151 exécutés, 0 échec ; detekt vert.

