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

## Étape 3 — `ia4` Expert PIMC (tag `ia4-pimc`)

- **Tirage des donnes** (`HiddenCards.kt`) : les cartes non vues sont distribuées aux trois
  autres joueurs, en respectant le nombre de cartes de chacun et tout ce que leur jeu a
  montré. Une carte est exclue d'une main exactement quand la détenir aurait rendu illégale
  une carte jouée : couleur non fournie, atout non joué, atout non monté, sous-coupe. Les
  obligations du jeu viennent chacune d'une seule carte détenue, ce qui rend cette règle
  exacte. Les cartes les plus contraintes sont placées d'abord ; un tirage bloqué recommence.
- **`ia4`** (`PimcPlayer.kt`) : pour chaque décision, il tire des donnes, juge chaque carte
  jouable sur chacune avec le juge de l'étape 2 et joue la meilleure carte en moyenne ; à
  égalité, celle que jouerait ia2. Les enchères sont celles d'ia2. Trois réglages : rapide
  (16 donnes, 1 s au plus), normal (48 donnes, 2,5 s), fort (128 donnes, 6 s), avec au moins
  4 donnes même si le temps est dépassé. Les tirages viennent de la graine et de la position :
  une même position donne la même carte, sauf si la limite de temps coupe court.
- **Arène en parallèle** : les donnes d'un match sont tirées d'abord, puis jouées sur tous les
  processeurs (le Mac en a 4 physiques, 8 logiques). Le résultat ne dépend pas de l'ordre
  d'arrivée. Les temps de réflexion affichés sont donc mesurés sous charge, environ deux fois
  plus longs qu'en jeu réel.
- **Preuves** :
  1. 400 positions de jeu aléatoires × 20 tirages : chaque donne tirée rejoue toute la
     partie à travers le moteur sans coup illégal, et les vraies mains ne sont jamais exclues.
  2. Une couleur non fournie n'est jamais redonnée (plus de 100 cas vérifiés) ; sans
     contrainte, chaque carte va environ une fois sur trois à chacun des autres joueurs.
  3. ia4 joue toujours une carte légale, quels que soient le contrat (capot, capot belote,
     coinche) et le décompte (80 positions) ; même position et même graine, même carte.
  4. Sabotages : 3 sur 3 repérés (aucune exclusion ; exclusions réduites aux couleurs non
     fournies, ce qui oublie les obligations d'atout ; exclusions ignorées au tirage).
- **`ia4-rapide` contre `ia2`, 400 donnes : +21,3 ± 12,1 par donne (significatif)**, avec
  les mêmes enchères : c'est le gain du seul jeu de la carte. Le plafond mesuré par l'Oracle
  était +83. Contrats réussis : ia4-rapide 303 sur 392, ia2 271 sur 392. Réflexion : 47 ms
  en moyenne, 4,6 s au pire (sous la charge de 8 parties en parallèle).
- Le code de lecture des enchères et des appels (`Inference.kt`) entre dans ce commit, car
  `PimcPlayer` le référence, mais il n'est proposé et mesuré qu'aux étapes suivantes.

## Étape 4 — `ia5` inférence par les enchères (tag `ia5-encheres`)

- **Lecture des enchères** (`Inference.kt`, `AuctionReading`) : pour chaque donne tirée, on
  rejoue le petit jeu avec la main tirée de chaque autre joueur, appel par appel. Chaque
  appel que la convention n'aurait pas fait multiplie le poids de la donne par 0,1. La donne
  est gardée avec une probabilité égale à son poids (« tirage par rejet »). Après 200
  essais sans donne gardée, on prend celle qui explique le mieux les enchères : un joueur
  peut toujours s'écarter de la convention.
- **Preuves** : sur 300 donnes enchéries par quatre ia2, la vraie donne a toujours un poids
  de 1, alors qu'une donne tirée sans les enchères les contredit dans plus de la moitié des
  cas. Sabotage repéré : un modèle qui passe toujours.
- **`ia5-rapide` contre `ia4-rapide`** : +8,8 ± 10,3 par donne sur 400 donnes (graine 3, pas
  significatif seul), puis **+10,3 ± 7,3 sur 800 donnes (graine 11, significatif)**, soit
  environ +9,8 sur 1 200 donnes. Contrats réussis (graine 11) : ia5 614 sur 795, ia4 583 sur
  795. Réflexion : 68 ms en moyenne contre 53 ms, le prix du tirage par rejet.
- **Écart au plan, noté tel quel** : un premier lancement du match de 800 donnes a échoué,
  parce que j'ai modifié le catalogue pendant que Gradle compilait pour ce match. Depuis, les
  matchs tournent à partir d'une copie figée des classes compilées.

## Étape 5 — l'application (tag `ia-app`)

- **Page « Options »** dans le menu en bois : deux colonnes, « Partenaire » et
  « Adversaires », qui proposent toutes les IA honnêtes du catalogue (l'Oracle n'y est pas),
  avec la description de celle choisie. Le choix est gardé dans `options/ia.txt` et compte
  dès le coup suivant. Par défaut : `ia5-rapide`, la meilleure IA honnête mesurée à ce stade.
- **Calcul en arrière-plan** : chaque coup d'un joueur ordinateur est calculé hors du fil de
  l'interface, pendant la pause habituelle (0,8 s, 1,7 s après un pli). S'il dure plus
  longtemps, son étiquette affiche « réfléchit… ». Un verrou garantit qu'un joueur ne calcule
  jamais deux coups à la fois, même quand un calcul abandonné (menu ouvert) se termine en
  arrière-plan.
- **Enregistrement** : l'en-tête de chaque partie nomme l'IA de chaque place
  (`ia E:ia5-rapide N:ia2 O:ia5-rapide`). Les anciens fichiers, sans ce champ, se lisent
  toujours (test).
- APK prêt : `app/build/outputs/apk/debug/app-debug.apk`. **Rien n'est installé sur le
  téléphone.**
- **Non vérifié à l'écran** : sans téléphone ni émulateur, je n'ai pas vu la page Options ni
  le badge « réfléchit… ». Seule leur logique est testée (lecture et écriture du choix, repli
  sur l'IA par défaut pour un identifiant inconnu ou tricheur, places). Sur le téléphone, les
  experts seront plus lents que sur le Mac : en réglage rapide, la limite est d'une seconde
  par coup, mais au moins 4 donnes sont toujours résolues, ce qui peut la dépasser au premier
  pli.

## Étape 6 — `ia6` signaux (tag `ia6-signaux`)

- **Lecture des appels** (`Inference.kt`, `CallReading`) : pour la première défausse de
  chaque autre joueur sous la maîtrise de son partenaire, on rejoue le jeu de la carte d'ia2
  avec la main tirée à ce moment-là. S'il ne défaussait pas dans la même couleur, la donne
  perd du poids (× 0,2).
- **Ce que la mesure a corrigé** : la première version lisait chaque telle défausse comme
  « il a l'As de cette couleur ». Sur 600 donnes jouées par quatre ia2, la vraie donne ne
  l'expliquait que 85 fois sur 197 : une défausse sous la maîtrise du partenaire est souvent
  un cadeau de points et non un appel. La lecture par le jeu d'ia2 explique désormais la
  vraie donne à chaque fois (test), et une donne tirée au hasard la contredit dans 47 cas
  sur 600. Sabotage repéré : maîtrise de son propre camp prise pour celle du partenaire.
- Les signaux eux-mêmes sont imposés par la convention sans code nouveau : à score égal, un
  expert joue la carte que jouerait ia2, donc l'appel direct. Au décompte par défaut, les
  égalités sont fréquentes (seul « fait ou chuté » compte).
- **`ia6-rapide` contre `ia5-rapide`, 800 donnes (graine 11) : −0,0 ± 2,9 par donne, aucune
  différence mesurable.** Les deux camps réussissent exactement les mêmes contrats (591 sur
  795). La lecture des appels ne change presque jamais la décision. ia6 reste dans le
  catalogue pour la comparaison, mais n'apporte rien en l'état.

## Étape 7 — `ia7` enchères et coinche par simulation (tag `ia7-complet`)

- **Enchères par simulation** (`SimulatedBidding.kt`, « simulation de Borel » de GIB) : la
  convention propose son appel ; on le compare au passe, à l'appel 10 points plus haut et plus
  bas, à la coinche ou à la surcoinche quand elles sont permises, et, quand la convention
  passe, à la plus petite annonce dans la plus longue couleur où l'on a le Valet ou le 9. Pour
  chaque donne tirée compatible avec les enchères (lecture d'ia5), chaque appel candidat est
  essayé, la convention finit les enchères pour les quatre joueurs, et le contrat atteint est
  joué cartes ouvertes (une recherche à fenêtre nulle : fait ou chuté). L'appel au meilleur
  score moyen l'emporte ; à égalité, celui de la convention. Le jeu de la carte est celui
  d'ia6.
- **Limites connues** : les autres sont supposés enchérir selon la convention ; un contrat
  joué cartes ouvertes est plus facile qu'en vrai, ce qui flatte le déclarant.
- **Preuves** : appel toujours légal et identique pour une même position et une même graine ;
  avec les huit atouts, ia7 annonce plus haut que les 100 de la convention, puisque tout
  contrat à cœur est fait. Sabotages : 2 sur 2 repérés (score vu du côté adverse ; pas de
  candidat plus haut).
- **Temps** : la première mesure (+31,1 ± 22,2, graine 11) a montré une décision d'enchère de
  17,5 s au pire, faute de limite. Corrigé : au moins 4 donnes, puis arrêt à la limite de
  temps du réglage. Deux pièges évités en route : un filtre qui aurait testé l'heure pour
  toutes les donnes avant d'en juger aucune, et un dépassement de capacité quand il n'y a pas
  de limite (on compare désormais le temps écoulé).
- **`ia7-rapide` contre `ia6-rapide`, 400 donnes (graine 11), avec la limite : +27,4 ± 22,7
  par donne (significatif)**. ia7 prend beaucoup plus de contrats (474 contre 319) et en
  réussit 286 (60 %), contre 167 sur 319 (52 %) pour ia6. Réflexion d'ia7 : 225 ms en
  moyenne, 7,5 s au pire, sous la charge de 8 parties en parallèle.

## Étape 8 — tournoi final et bilan (tag `ia-soiree-fin`)

### Toutes les mesures (duplicate, écart moyen par donne et marge à 95 %)

| Match | Donnes | Écart | Verdict |
|---|---|---|---|
| ia1 Débutant contre ia0 Aléatoire | 400 | +165,1 ± 12,9 | significatif |
| ia2 Règles+ contre ia1 | 2 000 | +20,1 ± 6,1 | significatif |
| ia3 Oracle (triche) contre ia2, mêmes enchères | 400 | +83,1 ± 12,0 | plafond du jeu de la carte |
| ia4-rapide contre ia2, mêmes enchères | 400 | +21,3 ± 12,1 | significatif |
| ia5-rapide contre ia4-rapide | 800 | +10,3 ± 7,3 | significatif |
| ia6-rapide contre ia5-rapide | 800 | −0,0 ± 2,9 | aucune différence |
| ia7-rapide contre ia6-rapide | 400 | +27,4 ± 22,7 | significatif |
| **ia7-rapide contre ia2** | 400 | **+57,8 ± 22,1** | significatif |
| ia5 (normal, 48 donnes) contre ia5-rapide (16) | 200 | −2,8 ± 15,4 | aucune différence |

- **La meilleure IA est `ia7-rapide`** : +57,8 par donne contre ia2, et elle bat chacune des
  précédentes. Réflexion sur le Mac : 250 ms en moyenne, 6 à 7,5 s au pire sous la charge de
  8 parties en parallèle.
- **Plus de calcul n'aide pas** : 48 donnes tirées au lieu de 16 ne gagnent rien de
  mesurable. Les erreurs du PIMC viennent de sa méthode (jouer comme si l'on verrait ensuite
  toutes les cartes), pas du nombre de donnes. Les réglages rapides suffisent.
- **Ce qui rapporte** : le jeu de la carte par solveur (+21 face à ia2), la lecture des
  enchères (+10), les enchères et la coinche par simulation (+27). La lecture des appels
  (ia6) ne rapporte rien en l'état.

### Parties enregistrées et analysées

`./gradlew :engine:simulate --args="3 ia7-rapide 42"` accepte maintenant un identifiant d'IA
et écrit l'IA de chaque place dans l'en-tête. Sur 3 parties (25 donnes) entre quatre
ia7-rapide :

- 12 coinches : le contrat coinché a chuté 8 fois sur 12. La coinche par simulation est
  plutôt juste.
- 6 surcoinches, dont 2 seulement justifiées : c'est le point faible. Juger un contrat cartes
  ouvertes flatte le déclarant, et ce biais pèse le plus sur la surcoinche.
- Entre ia7, contrat moyen de 113 et 47 % de contrats réussis : on surenchérit l'un contre
  l'autre. Face à ia2, ia7 réussit 65 % de ses contrats.
- Je n'ai pas analysé le jeu de la carte coup par coup. Il faudrait un outil qui compare
  chaque carte jouée à celle de l'Oracle sur la vraie donne et relève les plus grosses pertes.

### Scénarios du cadrage (§7)

Mains complétées de façon plausible ; Sud tient V♥ 8♥ 7♥ A♠ 10♠ 9♦ 8♣ 7♣ et entame contre
le contrat de son partenaire. Décisions de 5 graines au réglage normal :

| Scénario | Attendu | ia2 | ia4 | ia5 |
|---|---|---|---|---|
| 1. Relance libre (le partenaire a le 9) | petit atout | V♥ | V♥ ×3, 10♠ ×2 | 10♠ ×4, 7♥ ×1 |
| 2. Relance forcée (9 non garanti) | V♥ | V♥ | 10♠ ×3, V♥ ×2 | V♥ ×4, 10♠ ×1 |

- **Scénario 2 réussi par ia5** (4 fois sur 5), qui change d'entame selon les enchères sans
  règle écrite à la main.
- **Scénario 1 : échec connu.** ia5 entame le 10♠ au lieu d'un petit atout. Il n'est pas
  ajouté aux tests pour ne pas casser la CI : il est noté ici.
- Les scénarios 3 (blocage) et 4 (garder l'atout maître) n'ont pas de main précise dans le
  cadrage : non testés.

### Ce qui reste à faire

1. **Toi** : installer l'APK et jouer contre `ia7-rapide` (menu → Options). C'est la seule
   façon de vérifier la page Options, le badge « réfléchit… » et la vitesse réelle sur le
   téléphone. L'IA par défaut est `ia5-rapide`, plus rapide ; si ia7 est assez vive sur le
   téléphone, elle pourra devenir l'IA par défaut.
2. Choisir ce qu'on garde de la branche `test_ia` et la fusionner dans `main`.
3. Intégrer le jeu de cartes svg-cards (LGPL), comme prévu après la soirée.
4. Pistes d'amélioration, par gain attendu : surcoinche plus prudente ; un outil d'analyse
   du jeu de la carte contre l'Oracle ; « alpha-mu » (Cazenave) contre le défaut de fusion
   des stratégies ; un solveur plus rapide (la première entame coûte le plus).
