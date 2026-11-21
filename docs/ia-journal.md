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

## Étape 1 — `ia2` Règles+ (tag `ia2-regles-plus`)

- Enchères : le petit jeu (`PetitJeu.kt`), déterministe pour servir aussi de modèle aux
  experts. Ouverture 100 avec Valet et 9 ou Valet cinquième ; 90 avec quatre atouts dont le
  Valet ou le 9 ; 80 avec trois atouts dont le Valet ou le 9 et quelque chose à côté.
  Réponse libre seulement avec le complément ; au-delà de 100, 10 par As. Réponse forcée
  avec tout ce qui aide, belote comprise. Coinche à partir de 110 avec des levées sûres.
  Deux annonces au plus par joueur.
- Jeu de la carte : les réflexes de `ia1`, plus l'appel direct, le jeu dans la couleur
  appelée par le partenaire, et le petit atout quand le partenaire est le déclarant.
- **Mesure qui a changé la convention** : avec la reprise de l'ouvreur (qui ajoute ses As
  après la réponse du partenaire), ia2 ne faisait pas mieux que ia1 (+2,3 ± 9,0). Ses
  contrats de 130 à 150 n'étaient réussis qu'une fois sur deux. Sans la reprise : +21. La
  reprise est donc retirée ; la raison est écrite dans le code.
- Mesures séparées : le jeu de la carte seul vaut +7,9 ± 5,8 par donne ; les enchères seules
  (sans reprise) +20,5 ± 5,7.
- **`ia2` contre `ia1`, 2 000 donnes : +20,1 ± 6,1 par donne (significatif)**. Contrats
  réussis : ia2 1 697 sur 2 310 (73 %), ia1 1 126 sur 1 652 (68 %). Moins de 0,01 ms par
  décision.
- Tests : 162 exécutés, 0 échec. 4 sabotages sur 4 repérés. Il a fallu changer une main de
  test pour que le dernier soit détecté.

