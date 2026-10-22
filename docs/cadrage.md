# Cadrage du projet JustCoinche

> Statut : brouillon vivant, complété au fil des échanges. Les règles du jeu et leurs
> variantes sont dans [`regles.md`](regles.md).

## 1. Objectif

Une application mobile de coinche (belote contrée), hors ligne, open source et non
commerciale, avec des joueurs IA de **très haut niveau**.

- L'utilisateur joue une main ; ses trois adversaires et partenaire sont des IA.
- Constat de départ : les applications existantes reposent sur des moteurs de règles
  déterministes. Un bon joueur finit par prévoir leurs réactions et les exploiter.
- Le défi principal du projet est l'algorithme de décision des joueurs, aux enchères comme
  au jeu de la carte, pas l'interface.

## 2. Principes directeurs

1. **Tout ce qui varie entre tables est un paramètre** : règles (voir `regles.md`),
   système d'enchères, système de signalisation.
2. **Les conventions sont des données, pas du code.** Un seul moteur lit la description
   d'une convention, plutôt qu'un moteur de règles par convention.
3. **Le joueur expert raisonne, il n'applique pas la convention à la lettre.** Il connaît
   la convention, sait ce que ses annonces communiquent, et peut s'en écarter quand
   c'est plus rentable (forcing, blocage…).
4. **La force se mesure.** Toute amélioration de l'IA est prouvée par une mesure
   statistique (arène de parties « duplicate », voir §6), pas par une impression.
5. **Les situations vécues par l'auteur sont des tests.** Chacune est consignée dans un
   format fixe (§7) et sert de test de régression.

## 3. Glossaire

| Terme | Sens |
|---|---|
| **Preneur** | Joueur (et par extension son équipe) qui a remporté les enchères |
| **Contrat** | Dernière annonce : une valeur (80 à 160, capot) et une couleur d'atout |
| **Coinche / surcoinche** | Pari de la défense que le contrat chute / réponse du preneur |
| **Chute** | Contrat non réalisé |
| **Forcé** | Se dit d'une annonce faite **par-dessus une annonce adverse**. Son sens est plus large que celui de la même annonce faite librement (voir §4.2) |
| **Non forcé** | Annonce faite sans intervention adverse entre-temps ; elle a son sens conventionnel strict |
| **Valider l'atout** | Annoncer qu'on tient l'atout seul (dans la convention n° 1 : Valet + 9) |
| **Complément (à l'atout)** | La carte maîtresse d'atout que le partenaire n'a pas (le 9 s'il a le Valet, le Valet s'il a le 9) |
| **Bloquer** | Annoncer plus que ce que sa main justifie, pour obliger l'adversaire à monter trop haut |
| **As marié** | As + 10 de la même couleur : deux plis assurés dans cette couleur |
| **10 troisième** | Un 10 accompagné de deux autres cartes de la couleur (protégé contre l'As) |
| **Appel direct** | Je défausse la couleur de mon As |
| **Appel indirect** | Je défausse une couleur où je n'ai pas l'As |
| **Appel en cycle** | Chaque couleur défaussée désigne l'As d'une autre couleur |
| **Pisser** (ou sous-couper) | Jouer un atout inférieur quand un adversaire a coupé et qu'on ne peut pas surcouper ; obligatoire ou non selon la variante |
| **Dame troisième** | Dame d'atout accompagnée de deux autres atouts |
| **Coinche à la volée** | Variante où un adversaire peut coincher à tout moment, pas seulement à son tour |

## 4. Conventions

Les systèmes d'enchères sont privés à chaque équipe, mais la grande majorité des joueurs
utilise **3 ou 4 familles connues**. Les systèmes très marginaux ou purement privés sont
hors périmètre.

### 4.1 Signalisation au jeu de la carte

Paramètre `signalingSystem`. Systèmes décrits par l'auteur, confrontés aux sources :

| Système (nom de l'auteur) | Défausser une couleur signifie… | Nom dans les sources |
|---|---|---|
| Direct | « J'ai l'As de cette couleur » | **Appel direct** (même nom) |
| Indirect | « Je n'ai **pas** l'As de cette couleur » (signal négatif : l'As est dans l'une des deux autres couleurs) | **Appel défausse** / refus (Ludicash, Coinche & Contrée) |
| Cycle (1ʳᵉ description : ♠ → As de ♣, ♥ → As de ♦) | « J'ai l'As de la couleur associée » | **Appel indirect** : couleurs associées par paires de même teinte, ♠ ↔ ♣ et ♥ ↔ ♦. D'autres paires existent (♠ ↔ ♥ et ♦ ↔ ♣), ainsi qu'un indirect « croisé » (♠ → ♦, ♥ → ♣) |
| Cycle (2ᵉ description : ♥ → ♦ → ♣ → ♠ → ♥ ?) | « J'ai l'As de la couleur suivante dans le cycle » | Non trouvé dans les sources |

**Décisions** ✅ :

1. **Vocabulaire** : ce que l'auteur appelle « indirect » est appelé « défausse » dans les
   sources, et ce qu'il appelle « cycle » y est appelé « indirect ». Dans le code, on nomme
   les systèmes par leur mécanique, sans ambiguïté : `DIRECT` (même couleur), `NEGATIVE`
   (couleur sans As), `PAIRED` (couleur associée, table d'association en paramètre).
2. **Table d'association** : celle des sources, ♠ ↔ ♣ et ♥ ↔ ♦. Le cycle à quatre couleurs
   (2ᵉ description) est abandonné ; l'auteur n'y a jamais joué.

Règles d'usage relevées dans les sources (à confirmer) : l'appel se fait sur la première
défausse possible, quand le partenaire est maître ; un cas particulier quand la couleur
associée est l'atout (avec atout ♦, défausser ♥ pour l'As de ♥ revient à un appel direct).
D'autres systèmes existent (« appel cumulé », « muet ») mais leur page n'était pas lisible
(certificat expiré) : non documentés, et hors périmètre sauf demande.

### 4.2 Enchères : « petit jeu », version de l'auteur (convention n° 1)

L'auteur joue **petit jeu**. Sa version diffère volontairement de la description de Wikipédia
sur un point (voir §4.3) : il connaît la variante « 90 = Valet + 9 », mais joue Valet + 9
plutôt à **100**. Le message principal du 100 est : « à partir de 100, ne t'interroge pas sur
les atouts, je m'en occupe ; occupe-toi uniquement du reste du jeu (hors atout) ». Le choix
exact dépend souvent de ce qu'il y a à côté.

| Situation | Sens |
|---|---|
| 80 ou 90 (ouverture) | Atout plutôt faible : Valet sans 9, **ou** 9 sans Valet, avec éventuellement un petit jeu à côté |
| 100 (ouverture ou reprise) | L'atout est validé seul : Valet + 9. J'attends maintenant que mon partenaire m'annonce ses As |
| Partenaire relance un 80, **non forcé** | « J'ai le complément à l'atout. » Sans ce complément, il ne relance pas, **même avec 3 As** (risqué s'il n'a pas d'atout pour soutenir) |
| Ouverture à 80 et tout le monde passe | Le partenaire n'a pas besoin de relancer pour que l'équipe joue la couleur |
| Partenaire annonce dans ma couleur, **forcé** (par-dessus une annonce adverse) | « Ta couleur me va et je t'apporte assez de points, sous une forme quelconque (ex. deux As). » Je ne dois **pas** supposer qu'il a le complément |
| Annonce de blocage | Ex. : 100, l'adversaire dit 110 ; mon partenaire, avec deux As et éventuellement un 10 troisième ou un As marié, dit 130. Ce n'est pas « trois As » : il s'accorde 10 points de trop pour que l'adversaire doive monter à 140 |

**Annonces à 80 en sans-atout ou tout-atout : des appels, pas des contrats.** Elles visent
rarement à jouer ce contrat :

| Annonce | Sens |
|---|---|
| 80 tout-atout | « J'ai des atouts dans toutes les couleurs, mais rien de solide seul. N'hésite pas à annoncer une couleur un peu fragile : normalement, je suis dedans. » |
| 80 sans-atout | « Je suis blindé, fais quelque chose. » |

Conséquence de conception : une annonce peut avoir un **sens sans rapport avec son contrat
nominal**. Le modèle de convention ne doit donc pas lier le sens d'une annonce à l'intention
de jouer ce contrat. Il reste néanmoins le risque que tout le monde passe et que l'équipe
doive jouer ce 80 ; le joueur expert doit l'intégrer dans son calcul.

**Barème des relances.** On n'annonce que les As et les honneurs d'atout :

| Carte | Relance |
|---|---|
| Chaque As | + 10 |
| 9 d'atout | + 10 |
| Valet d'atout | + 20 |

Ce barème est confirmé par une source généraliste (neopoker.fr : « chaque As ou le 9 d'atout
ajoute 10, le Valet d'atout ou Roi-Dame ajoute 20 ») et correspond aux réponses de la famille
« enchères à la contrée » de Wikipédia (§4.3).

**La belote (+ 20) ne s'annonce que forcé.** En enchère libre, on ne relance pas pour la
belote : si mon partenaire dit 100 et que je dis 120 pour ma belote, il peut comprendre
« deux As » et annoncer capot alors qu'on ne l'a pas. Il faut respecter sa convention. En
revanche, quand l'enchère est forcée, la convention « saute » à l'appréciation du partenaire,
et la belote peut alors justifier une relance.

**Une part de jugement, toujours.** Le barème est « à peu près tout le temps » appliqué, mais :

- partenaire ouvre à 80, j'ai son complément (qui vaudrait + 10 ou + 20) mais un jeu
  **éclaté** : je reste prudent et ne relance que de 10 ;
- une ouverture sous 100 dit « j'ai un Valet ou le 9 second » ; ensuite, liberté selon la
  main : avec un **9 quatrième** (blindé à l'atout), on peut aller jusqu'à 100 ; avec un
  **Valet sec** et des cartes horribles à côté, on s'arrête à 90.

- avec un **Valet cinquième**, l'auteur s'autorise à annoncer 100 : « je veux qu'on joue ma
  couleur et je veux connaître tes As. On n'ira pas chercher le capot, puisque je ne sais pas
  où est le 9, mais je m'occupe de l'atout : au pire, on perd un pli à l'atout et on fait le
  reste. »

Conséquence : même dans la convention de l'auteur, **100 ne garantit pas strictement
Valet + 9** (un 9 quatrième ou un Valet cinquième peuvent y mener). L'interprétation doit
être probabiliste. Le partenaire qui a le 9 et entend 100 doit, de son côté, en tenir compte
avant d'envisager le capot.

### 4.3 Familles d'enchères connues

Recensées par l'auteur et dans les sources :

| Famille | Principe | Source |
|---|---|---|
| **Petit jeu** | 80 : il manque le Valet ou le 9, avec une autre carte d'atout. 90 : Valet + 9. 100 : Valet + 9 + deux atouts + un As ailleurs. 110 : + deux As. 120 : bicolore | Wikipédia |
| **Aux As** | 80 : au moins deux As. 90 : petit jeu (Valet ou 9 + un As). 100 : maître à l'atout. 110 : maître à l'atout + une coupe. 120 : bicolore | Wikipédia ; citée par l'auteur (« 80 = deux As ») |
| **Enchères à la contrée** | Réponses à un 80 : 90 = le 9, 100 = le Valet, 110 = Valet + 9, puis + 10 par As. 90 demande au partenaire + 10 par As. 100 = maître à l'atout, demande + 10 par As | Wikipédia |
| **Maîtrise de l'atout** | Ouvertures par « type » d'atout, défini par la longueur et les honneurs (90 = Valet + 9 quatrième ou 9 + As troisième, 100 = un atout de plus…). Réponses très codifiées, recherche de capot | Blog « Coinche : méthode d'enchères » ; correspond sans doute à la famille « relances au nombre de cartes d'atout » citée par l'auteur |
| **Paire / impaire** | Annonces paires (80, 100, 120) : il manque un honneur d'atout ; impaires (90, 110) : on a les deux | Résultat de recherche, source non consultée directement |
| **Petit jeu, version de l'auteur** | Voir §4.2. 80 et 90 faibles, 100 = atout validé ; barème des réponses proche de « à la contrée » | L'auteur |

**Décision** ✅ : implémenter **toutes ces familles** comme conventions sélectionnables, pour
que chacun joue comme il l'entend et pour pouvoir s'entraîner avec d'autres conventions.

⚠️ **Limite** : seule la version de l'auteur du petit jeu est décrite par quelqu'un qui la
pratique. Les autres familles reposent sur des descriptions partielles trouvées en ligne, et
l'auteur n'a pas de source plus fiable. Elles seront donc validées en les faisant jouer et en
relisant les enchères produites. Ordre proposé : petit jeu (auteur) d'abord, puis les autres, chacune
avec ses propres scénarios de test.

### 4.4 Connaissance des conventions dans l'application ✅

- **On ne connaît jamais la convention adverse**, ni pour les enchères ni pour les appels.
  En pratique, elle se devine en deux ou trois parties, en comparant les enchères aux cartes
  sorties (exemple : un joueur défausse du ♥, son partenaire rejoue ♥ et l'As de ♥ tombe
  aussitôt : ils sont en appel direct).
- Version retenue : **chaque équipe a une convention fixe** (enchères + appels) pour toute
  la partie. Pas de changements en cours de partie ni selon l'attaque ou la défense.
- Option amusante : tirer **au hasard** la convention des adversaires IA au début de la partie.

**Conséquence de conception majeure : l'IA doit identifier la convention adverse.**
L'ensemble des conventions étant fini (quelques familles × quelques systèmes d'appels), le
joueur expert peut tenir une **probabilité sur chaque convention possible** et la mettre à
jour à chaque observation. Le signal est fort : à la fin de chaque donne, toutes les cartes
ont été jouées, donc on connaît rétrospectivement les quatre mains et on peut vérifier
quelles conventions expliquent les enchères et les défausses observées. C'est exactement ce
que fait un joueur humain « en deux ou trois parties ».

Cela vaut dans les deux sens :
- les IA adverses doivent deviner la convention de l'équipe de l'utilisateur ;
- l'utilisateur peut chercher à deviner celle des adversaires, comme dans la réalité.

Un humain peut aussi ne suivre **aucune** convention connue, ou la suivre approximativement :
l'identification doit rester probabiliste et tolérer une hypothèse « convention inconnue ».

## 5. Modèle de joueur envisagé

```
Convention d'équipe (paramètre, données)
 ├─ système d'enchères : règles contexte → annonce → sens
 │      contexte = qui parle, forcé ou non, historique de l'équipe, niveau
 │      sens     = strict (cartes précises) ou large (fourchette de valeur)
 ├─ barème de valeur (As, 10 troisième, belote…)
 └─ système de signalisation : direct | indirect | cycle

Joueur
 ├─ Interprète : annonces et défausses → probabilités sur les mains cachées
 │      (probabiliste, pas binaire : un partenaire peut bloquer, un humain peut s'écarter)
 ├─ Niveau facile : applique la convention à la lettre
 └─ Niveau expert : tire des donnes probables (pondérées par l'interprète),
                    résout chacune exactement, et choisit la décision de meilleure
                    espérance de score. Il tient compte de ce que le partenaire
                    comprendra de chaque annonce.
```

### Points de conception déjà identifiés

1. **Le contexte forcé / non forcé est central** dans le sens des annonces.
2. **Deux natures de sens** : contrainte sur des cartes (« j'ai le 9 ») ou fourchette de
   valeur (« j'apporte environ 20 points »).
3. **Le blocage** n'a pas à être codé côté décision : le joueur expert le découvre en
   simulant les réponses adverses. Il doit en revanche être pris en compte **côté
   interprétation** (« 130 = trois As **ou** deux As + blocage »).
4. **Les enchères doivent guider le jeu de la carte**, par l'inférence et non par des règles
   écrites à la main (scénarios 1 et 2, §7).
5. **Limite connue de la recherche par échantillonnage** : chaque donne tirée est résolue
   cartes ouvertes, où informer son partenaire ne rapporte rien. L'IA ne choisira donc
   jamais spontanément une défausse d'appel. Les appels doivent être **imposés par la
   convention** (on ne choisit qu'entre les défausses conformes). Dans l'autre sens, les
   défausses du partenaire alimentent l'inférence.
6. **La fonction objectif est un paramètre** : le décompte (mode A ou B) change la prise
   de risque optimale (voir `regles.md` §10).

## 6. Mesure de la force

Une **arène** en ligne de commande fait jouer des milliers de donnes entre joueurs IA au
format **duplicate** : les mêmes donnes sont rejouées en inversant les équipes, ce qui
neutralise la chance des cartes. Elle produit un écart de score moyen par donne, avec un
intervalle de confiance. Toute évolution de l'IA doit être validée par l'arène.

## 6 bis. Historique des parties

Exigence de l'auteur : **enregistrer, de façon compacte, tout ce qui s'est passé dans chaque
donne**, pour trois usages :

1. retrouver et analyser après coup les erreurs flagrantes de l'IA (« pourquoi a-t-elle joué ça ? ») ;
2. garder un historique des donnes jouées (et pouvoir les rejouer) ;
3. servir plus tard de données d'entraînement ou de calibration.

Principes proposés :

- **N'enregistrer que les faits**, tout le reste se recalcule en rejouant la donne dans le
  moteur : règles utilisées, donneur, distribution, enchères, cartes jouées dans l'ordre,
  identité et version de chaque joueur (humain ou IA, niveau, convention, graine aléatoire).
  Les plis, les points et le score s'en déduisent. Rejouer un historique et retrouver le même
  score est en soi un test du moteur.
- **Compacité** : une distribution tient en 64 bits (2 bits par carte pour désigner son
  propriétaire), les cartes jouées en 32 octets. Une donne complète fait de l'ordre de
  50 octets avant tout format d'enregistrement : un million de donnes reste raisonnable.
- **Traces de raisonnement en option** : pour les décisions de l'IA, on peut enregistrer les
  options évaluées et leur espérance. C'est ce qui permet de comprendre une erreur, mais c'est
  plus volumineux : à activer pour l'arène et le débogage, pas forcément sur le téléphone.
- **Format versionné** dès le départ, pour que les historiques restent lisibles quand le
  moteur évolue.

❓ À trancher au moment de l'implémenter (fin de l'étape 1) : format texte compact (une ligne
par donne, lisible et facile à parcourir, compressible) ou binaire. Proposition : texte compact.

## 7. Scénarios (situations de référence)

Format de saisie :

```
Scénario n° :
Règles (mode de décompte, variantes utiles) :
Convention de mon équipe / des adversaires :
Position (donneur, qui parle, qui entame) :
Ma main :
Enchères :
(jeu de la carte) Plis déjà joués :
Décision attendue :
Autres décisions acceptables :
Raisonnement :
Nature : convention | jugement
```

Les scénarios de **convention** enrichissent la description de la convention. Les
scénarios de **jugement** vérifient que la recherche trouve d'elle-même la bonne décision ;
s'ils échouent, on corrige l'évaluation ou l'inférence, on n'ajoute pas une règle.

### Scénario 1 : entame après relance non forcée

- Convention : n° 1.
- Ma main : Valet de ♥ sans le 9 (reste de la main ❓ à compléter).
- Enchères : moi 80 ♥, adversaire passe, partenaire 90 ♥ (non forcé = « j'ai le complément », donc le 9).
- Décision attendue (si j'ai la main) : entamer **petit atout**. Le partenaire prend au 9
  et me renvoie la main au Valet.
- Nature : jugement (doit découler de l'inférence « le partenaire a le 9 »).

### Scénario 2 : entame après relance forcée

- Convention : n° 1.
- Ma main : la même que dans le scénario 1.
- Enchères : moi 80 ♥, adversaire 90 ♣, partenaire 100 ♥ (forcé = soutien, sans garantie
  du 9).
- Décision attendue : entamer **au Valet**, puis jouer normalement.
- Nature : jugement. **Avec le scénario 1, c'est le test clé de l'architecture** : même
  main, enchères différentes, entame différente, sans règle écrite à la main.

### Scénario 3 : blocage

- Convention : n° 1.
- Main du partenaire : deux As, et éventuellement un 10 troisième ou un As marié (❓ à compléter).
- Enchères : moi 100 ♥, adversaire 110, partenaire **130** ♥.
- Décision attendue (partenaire) : 130, pour obliger l'adversaire à monter à 140.
- Interprétation attendue (moi) : 130 n'implique **pas** trois As.
- Nature : jugement (côté décision) + convention (côté interprétation).

### Scénario 4 : garder l'atout maître quand pisser est obligatoire

- Règles : `mustUnderTrump` = oui, puis la même position avec `mustUnderTrump` = non.
- Situation : il reste un seul atout adverse, je détiens le plus gros atout, et j'ai à peu
  près le même nombre d'atouts que l'adversaire (❓ main précise à compléter).
- Décision attendue (pisser obligatoire) : **ne pas** tirer le dernier atout ; garder le
  mien. Quand je couperai, l'adversaire risque fort de devoir sous-couper, et je récupérerai
  son atout sans dépenser de tour.
- Décision attendue (pisser non obligatoire) : ❓ à préciser (vraisemblablement tirer
  l'atout, puisque l'adversaire pourra défausser au lieu de sous-couper).
- Nature : jugement. Même position, règle différente, décision différente : la recherche doit
  le trouver seule, à partir des coups légaux.

## 8. Notes pour plus tard

- **Prise de risque en fin de partie** : une équipe menée près de la fin peut s'autoriser
  des paris plus risqués. L'objectif passe de « maximiser l'espérance de points de la donne »
  à « maximiser la probabilité de gagner la partie ». À traiter après le niveau expert de
  base, mais l'architecture doit permettre de changer la fonction objectif. L'effet se
  concentre sur les deux ou trois cents derniers points, surtout en cas de gros retard.
- **Sans-atout et tout-atout** : intégrés au moteur de règles dès le départ (même squelette,
  tables différentes). L'évaluation des mains et les conventions d'enchères propres à ces
  contrats viendront après celles du contrat couleur, car la dynamique de jeu est très différente.
- **Générale** : plus tard, pour le plaisir ; absente des règles usuelles.
- **Seuil de réussite « plus de la moitié des points »** (82 pour un 80) : variante à
  ajouter plus tard ; elle ne compte qu'avec un 80 + belote.

## 9. Questions ouvertes

1. Architecture de l'IA (§5) : validation de l'approche proposée.
2. Feuille de route (§10) : validation de l'ordre des étapes, en particulier la place de
   l'interface Android.
3. Plateforme : Android seul, ou iOS plus tard (Kotlin Multiplatform) ?
4. Identité open source : pseudonyme, identifiant de paquet, e-mail git (nécessaires dès
   l'étape 0 ; aucune identité git globale n'est configurée sur la machine).

## 10. Feuille de route (proposition)

Chaque étape se termine par un incrément testable, avec ce qu'il faut vérifier et où.

| Étape | Contenu | Preuve attendue |
|---|---|---|
| **0. Socle** | Dépôt git (identité pseudonyme), projet Gradle multi-module : `engine` en Kotlin pur (sans Android), `app` Android plus tard. Intégration continue (GitHub Actions), analyse statique (detekt, ktlint) | `./gradlew check` vert en local et en CI ; un test volontairement faux rend la CI rouge ; le nombre de tests exécutés est vérifié, pas seulement le code retour |
| **1. Moteur de règles** | Cartes, donne, enchères (coinche, surcoinche, capot), coups légaux pour les trois types de contrat, plis, belote, décompte modes A et B, fin de partie. Tous les paramètres de `regles.md` | Chaque exemple chiffré de `regles.md` devient un test (80 + 100 → 180 / 60 ; 100 belote chuté → 280 ; capot 120 → 370 ; 76 / 86 → 170 ; donne finale 960 → 1172…). Tests de propriétés sur des milliers de donnes aléatoires (total de 162 ou 182, chaque carte jouée est légale, 8 plis). Enregistrement des donnes (§6 bis) : rejouer un historique redonne le même score |
| **2. Arène et premiers joueurs** | Joueur aléatoire, joueur à règles simple ; arène en ligne de commande au format duplicate | Le joueur à règles bat le joueur aléatoire avec un intervalle de confiance qui exclut zéro |
| **3. Solveur exact** | Résolution d'une donne cartes ouvertes (alpha-bêta, table de transposition) | Résultats contrôlés sur des positions calculées à la main ; temps de résolution mesuré sur le Mac |
| **4. Joueur expert, jeu de la carte** | Échantillonnage de donnes + solveur ; contraintes simples issues des enchères | Bat le joueur à règles dans l'arène ; scénarios 1, 2 et 4 |
| **5. Conventions et enchères expertes** | Format de description des conventions, petit jeu de l'auteur, interprète probabiliste, enchères par simulation | Scénario 3 (blocage) ; gain mesuré dans l'arène |
| **6. Application Android** | Interface Compose : jouer une partie contre les IA, réglages des règles et conventions. Persistance (partie en cours, statistiques) avec tests de migration dès la première version du schéma | Partie complète jouable sur le téléphone (installation **avec accord préalable**) ; mesure du temps de réflexion de l'IA sur l'appareil |
| **7. Ensuite** | Autres familles de conventions, identification de la convention adverse, niveaux de difficulté, objectif de fin de partie, apprentissage éventuel | Chaque ajout validé dans l'arène |

L'interface arrive tard dans cet ordre, parce que le cœur du projet est l'IA et que tout ce
qui précède se teste sans téléphone. Elle peut être avancée (par exemple juste après
l'étape 2) si on veut jouer plus tôt contre un joueur à règles.
