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


## Étape 2 — solveur cartes ouvertes et `ia3` Oracle (tag `ia3-oracle`)

- **Solveur** (`Solver.kt`) : alpha-bêta sur masques de bits, table de transposition aux
  débuts de pli (bornes et meilleure entame), borne « l'attaque ne peut pas faire plus que
  ce qui reste », tri des coups, et cartes sans points voisines jouées une seule fois.
  Objectif « points » (cartes et dix de der) ou « plis » (capot).
- **Juge de cartes** (`CardJudge.kt`) : pour une donne connue, le score net de chaque carte
  jouable. Au décompte par défaut (points au contrat), seul « fait ou chuté » compte : une
  recherche à fenêtre nulle par carte suffit, environ 9 fois moins chère qu'une valeur exacte.
- **`ia3` Oracle** (`OraclePlayer.kt`) : voit les quatre mains et joue la carte de meilleur
  score ; à égalité, celle que jouerait ia2. Enchères d'ia2. Marqué « triche » : il ne sera
  jamais proposé comme adversaire dans l'application.
- **Bugs trouvés en route** par le test contre la recherche exhaustive : la clé de la table
  mélangeait l'entameur aux mains ; les plis suivants écrasaient les cartes du pli en cours ;
  la carte gagnante n'était pas remise à l'entame au pli suivant (écart de 10 points
  exactement sur une position de fin de donne).
- **Preuves** :
  1. 3 000 fins de donne aléatoires (1 à 3 plis, contrats et règles au hasard) : le solveur
     et une recherche exhaustive à travers le moteur donnent la même valeur, pour la
     position et pour chaque carte.
  2. 1 500 fins de donne : le juge donne, pour chaque carte, exactement le score que donne
     le moteur (`scoreDeal`) en jeu parfait, pour les deux décomptes, la coinche, la
     surcoinche, le capot et le capot belote.
  3. La table de transposition ne change aucun résultat (150 positions de 4 et 5 plis).
  4. Une fin de 2 plis calculée à la main (44 points en jouant d'abord le trèfle, 34 en
     entamant le Valet d'atout).
  5. Sabotages : 6 sur 6 repérés (sous-coupe ignorée, dix de der oublié, l'atout ne coupe
     plus, cartes à points fusionnées, borne de la table décalée d'un, belote oubliée dans
     le juge). Un premier sabotage des « cartes équivalentes » ne changeait en fait rien
     (la carte juste au-dessus d'une carte à points a toujours des points) : remplacé par
     un vrai.
- **Temps sur le Mac** (`./gradlew :engine:solverBench --args="1000 5"`, 1 000 donnes
  entières, depuis l'entame) :

  | Mesure | Moyenne | Médiane | 99 % | Pire |
  |---|---|---|---|---|
  | Valeur exacte de la donne | 71 ms | 35 ms | 545 ms | 2,2 s |
  | Valeur exacte de chaque entame | 153 ms | 76 ms | 1,2 s | 5,3 s |
  | Chaque entame : 82 points atteints ou non | 17 ms | 6 ms | 181 ms | 468 ms |

  Le premier pli est le plus cher ; les suivants coûtent beaucoup moins.
- **`ia3` contre `ia2`, 400 donnes : +83,1 ± 12,0 par donne (significatif)**, avec les
  mêmes enchères. C'est le plafond de ce que le jeu de la carte peut rapporter face à ia2.
  Contrats réussis : ia3 357 sur 392 (91 %), ia2 231 sur 392 (59 %). Réflexion d'ia3 :
  2 ms en moyenne, 420 ms au pire.
- Tests : 183 exécutés (moteur et application), 0 échec ; `./gradlew check` vert (detekt et
  lint compris).
