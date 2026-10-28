# Règles de la coinche et variantes paramétrables

> Statut : brouillon de cadrage. Ce document décrit le jeu tel que le moteur devra
> l'implémenter. Chaque point de règle qui varie selon les tables est un **paramètre**.
>
> Légende des statuts :
> - ✅ **Choisi** : validé par l'auteur du projet.
> - ❓ **À trancher** : plusieurs options connues, décision en attente.
> - ⏸ **Plus tard** : souhaité, mais pas dans la première version. L'architecture ne doit pas l'empêcher.
> - ⛔ **Exclu** : variante volontairement non implémentée.
>
> Les identifiants de paramètres (colonne « Paramètre ») sont des propositions de noms
> pour le code (en anglais).

---

## 1. Matériel et joueurs

- Jeu de 32 cartes : 7, 8, 9, 10, Valet, Dame, Roi, As dans les quatre couleurs (♠ ♥ ♦ ♣).
- 4 joueurs en 2 équipes fixes ; les partenaires sont assis face à face.
- Dans l'application : l'utilisateur joue un siège, les trois autres sont des IA
  (un partenaire, deux adversaires).

## 2. Distribution

- Chaque joueur reçoit 8 cartes, distribuées par paquets 3-2-3.
- Le sens de jeu et la rotation du donneur sont traditionnellement anti-horaires.
- Aucun impact sur l'IA : la distribution est simplement aléatoire et uniforme.

| Paramètre | Options | Statut |
|---|---|---|
| `dealPattern` | 3-2-3 ; 3-3-2 ; 4-4 | ❓ sans impact sur le jeu (cosmétique) |
| distribution en deux temps (6 cartes, enchères, puis 2) | — | ⛔ proposé (change la nature des enchères) |

## 3. Types de contrat, valeur et ordre des cartes

Trois types de contrat (`ContractType`) :

| Type | Principe | Statut |
|---|---|---|
| **Couleur** | Une couleur est l'atout, les trois autres non | ✅ |
| **Sans-atout** | Aucune couleur n'est l'atout | ✅ dans le moteur de règles dès le départ (IA : voir `cadrage.md`) |
| **Tout-atout** | Toutes les couleurs se jouent comme l'atout | ✅ idem |

### Couleur d'atout (contrat couleur)

| Carte | V | 9 | A | 10 | R | D | 8 | 7 |
|---|---|---|---|---|---|---|---|---|
| Points | 20 | 14 | 11 | 10 | 4 | 3 | 0 | 0 |

### Couleur hors atout (contrat couleur)

| Carte | A | 10 | R | D | V | 9 | 8 | 7 |
|---|---|---|---|---|---|---|---|---|
| Points | 11 | 10 | 4 | 3 | 2 | 0 | 0 | 0 |

Total : 152 points de cartes + 10 de « dix de der » (dernier pli) = **162**
(**182** avec une belote).

### Sans-atout

Toutes les couleurs suivent l'ordre hors atout (A, 10, R, D, V, 9, 8, 7). L'As vaut 19
pour garder le total de 152 : 4 × (19 + 10 + 4 + 3 + 2) = 152. ✅

### Tout-atout

Toutes les couleurs suivent l'ordre atout (V, 9, A, 10, R, D, 8, 7). ✅

| Carte | V | 9 | A | 10 | R | D | 8 | 7 |
|---|---|---|---|---|---|---|---|---|
| Points | 14 | 9 | 6 | 5 | 3 | 1 | 0 | 0 |

Total : 4 × (14 + 9 + 6 + 5 + 3 + 1) = 152. (Un autre barème circule, V 13 / D 2 : non retenu.)

| Paramètre | Options | Statut |
|---|---|---|
| `allowNoTrump` | oui / non | ✅ paramètre, défaut **oui** |
| `allowAllTrump` | oui / non | ✅ paramètre, défaut **oui** |

## 4. Enchères

### Déroulement

- Le joueur à droite du donneur parle en premier ; on tourne dans le sens du jeu.
- À son tour, un joueur peut : **passer**, **annoncer** (valeur + type de contrat),
  **coincher** (seulement sur une annonce adverse), ou **annoncer capot**.
- Une annonce doit être strictement supérieure à la précédente : valeurs de 80 à 160
  par paliers de 10, puis capot.
- Un joueur qui a passé peut reparler plus tard si quelqu'un a annoncé entre-temps.
- Fin des enchères : trois passes consécutives après une annonce, ou une coinche (suivie de la
  réponse de l'équipe coinchée).
- **Capot** : c'est l'annonce la plus haute, sauf le **capot belote** (voir ci-dessous). Après un
  capot, on fait un **tour à blanc** : les autres joueurs ne peuvent que passer ou coincher. ✅
- **Capot belote** : annonce à **270**, au-dessus du capot. Pas encore implémenté. ❓ À préciser :
  faut-il détenir la belote pour l'annoncer, sur quel tour peut-on l'annoncer après un capot,
  et combien marque-t-il en réussite et en chute (modes A et B) ?
- Si les quatre joueurs passent d'entrée : les cartes sont redistribuées par le donneur suivant.

### Coinche et surcoinche

- **Coincher** : un adversaire du dernier annonceur parie que le contrat chutera.
- **La coinche arrête toujours les enchères** : la donne se joue telle quelle.
- **Surcoincher** : l'équipe preneuse répond qu'elle le fera quand même.

| Paramètre | Options | Statut |
|---|---|---|
| `bidStep` | 10 | ✅ |
| `minBid` / `maxBid` | 80 à 160 en pratique, puis capot. 170 et 180 sont possibles avec la belote (jamais vus en pratique) : le moteur les autorise | ✅ |
| `coincheTiming` | `ANY_TIME` (« à la volée » : n'importe quel adversaire, à n'importe quel moment) / `IN_TURN` (seulement à son tour) | ✅ les deux, paramétrables, défaut **à son tour**. ⚠️ Impact sur l'interface : en mode « à la volée », l'humain doit pouvoir coincher pendant que les IA parlent |
| `coincheEndsBidding` | oui | ✅ toujours (pas un paramètre) |
| réponse à la coinche | l'équipe coinchée répond à tour de rôle : d'abord le joueur qui suit le coincheur, puis son partenaire. Le premier qui surcoinche termine ; deux passes laissent le contrat coinché | ✅ |
| `capotCanBeCoinched` | oui | ✅ |
| `allowGenerale` | un seul joueur fait les 8 plis | ⏸ plus tard (absente des règles usuelles, impact sur les enchères) |
| `equalBidSuitRanking` | même valeur dans une couleur plus haute | ⛔ proposé (variante rare) |
| ordre sans-atout / tout-atout | SA et TA s'annoncent comme une couleur : seule la valeur compte. Ex. 80 ♥ → 90 TA → 100 SA → 120 ♠ | ✅ |

## 5. Jeu de la carte

Le joueur à droite du donneur entame le premier pli. Celui qui remporte un pli entame le suivant.

### Obligations (contrat couleur)

1. **Fournir** : on doit jouer dans la couleur demandée si on en possède.
2. **Hors atout demandé** : on joue n'importe quelle carte de la couleur ; pas d'obligation
   de monter.
3. **Atout demandé** : on doit **monter** (jouer un atout plus fort que le plus fort déjà
   posé) si possible, **y compris sur son partenaire** ; sinon jouer un atout plus faible.
4. **Si on ne peut pas fournir** :
   - si le **partenaire est maître** du pli : pas d'obligation de couper, défausse libre
     (même en ayant de l'atout) ;
   - sinon, on doit **couper**. Si un adversaire a déjà coupé, on doit **surcouper**
     si possible ; sinon voir `mustUnderTrump` ;
   - sans atout en main : défausse libre.
5. Le pli est remporté par le plus fort atout posé, à défaut par la plus forte carte de la couleur demandée.

### Sans-atout et tout-atout

- **Sans-atout** : fournir si possible (sans obligation de monter) ; sinon défausse libre.
- **Tout-atout** : fournir si possible, et **monter** si possible ; sinon défausse libre. ✅
- Dans les deux cas, **il n'y a pas de coupe** : c'est la différence majeure de jeu (voir §10).

| Paramètre | Options | Statut |
|---|---|---|
| `mustUnderTrump` (« pisser ») | oui : quand un adversaire a coupé et qu'on ne peut ni fournir ni surcouper, on doit quand même jouer un atout plus petit / non : défausse libre dans ce cas | ✅ paramètre prioritaire (variante très répandue, fort impact stratégique, voir §10) |
| `mustOvertrumpPartner` | oui : sur un atout demandé, on doit monter même sur le partenaire | ✅ règle retenue |
| `partnerMasterFreesDiscard` | oui : partenaire maître, pas d'obligation de couper | ✅ règle retenue |

## 6. Belote, dix de der, combinaisons

- **Dix de der** : l'équipe qui remporte le dernier pli marque 10 points.
- **Belote-rebelote** : Roi + Dame d'atout dans la même main = 20 points. On dit
  « belote » en jouant la première, « rebelote » en jouant la seconde.
- **La défense marque aussi la belote** (20 points), **toujours**, que le contrat soit
  réussi ou chuté, dans les deux modes de décompte. L'annonce est facultative pour les deux
  camps, mais comme elle rapporte toujours, elle est toujours faite en pratique.
- **La belote n'est jamais multipliée** par une coinche ou une surcoinche.

| Paramètre | Options | Statut |
|---|---|---|
| `beloteCountsForContract` | oui | ✅ La belote compte pour le contrat : le total passe de 162 à 182. Exemple : 120 avec la belote, il suffit de 100 points de plis |
| `beloteForDefenders` | oui | ✅ |
| `beloteKeptOnFailure` | oui : le preneur garde ses 20 points en cas de chute / non : ils passent à la défense | ✅ paramètre, défaut **non** : la défense récupère la belote. Ex. mode B, 100 avec belote chuté → défense 100 + 160 + 20 = 280 |
| annonce de la belote | automatique (toujours faite) | ✅ |
| belote en tout-atout | Roi + Dame d'une même couleur, donc **plusieurs belotes possibles** | ⛔ désactivée (choix de l'auteur). Impact de jeu majeur : avec deux belotes, un 80 ne demande que 40 points de plis. Réactivable plus tard |
| belote en sans-atout | n'existe pas | ✅ |
| Combinaisons (tierce, cinquante, cent, carrés) | — | ⛔ exclues |

## 7. Réussite du contrat

Le preneur doit atteindre **au moins** la valeur de son contrat (points de plis + dix de der
+ belote). Il n'est pas nécessaire de faire plus de la moitié des points : un 80 avec la
belote se réussit avec 60 points de plis. La vérification se fait **toujours sur les points
bruts**, avant arrondi. ✅

| Paramètre | Options | Statut |
|---|---|---|
| `successThreshold` | ≥ contrat | ✅ (règle de base) |
| exiger plus de la moitié des points (82 pour un 80) | — | ⏸ plus tard (ne compte qu'avec un 80 + belote) |

## 8. Décompte des points

C'est la variante la plus structurante pour l'IA (voir §10). Deux modes retenus, paramètre
`scoringMode`. Notation : C = valeur du contrat, F = points faits par le preneur (plis + dix
de der + belote), D = points faits par la défense, m = multiplicateur (1 normal, 2 coinché,
3 surcoinché). La belote n'est jamais multipliée.

### Mode A : « points au contrat » (`CONTRACT_ONLY`) ✅

Exemple : contrat 80 ♥.

Seul le contrat compte : qu'on fasse 80, 120 ou 162 points (même tous les plis), un 80
réussi rapporte 80.

| Issue | Preneur | Défense |
|---|---|---|
| Réussi (y compris tous les plis sans capot annoncé) | **C** (80) | **0** |
| Chuté | 0 | **160** |
| Réussi, coinché ou surcoinché | **m × C** | 0 |
| Chuté, coinché ou surcoinché | 0 | **m × C** |
| Capot annoncé, réussi | **m × 250** | 0 |
| Capot annoncé, chuté | 0 | **m × 250** |
| Belote de la défense | — | **+ 20**, toujours |

Remarque : un 80 coinché chuté donne 160, autant qu'une chute non coinchée. La coinche à 80
sert surtout à **figer le contrat** (voir §10).

En mode A, la belote **du preneur** ne rapporte rien en plus du contrat : elle sert seulement
à l'atteindre. ✅

⚠️ Écart avec les sources (voir §11) : Wikipédia décrit une chute à **C** (et non 160) et une
belote jamais ajoutée au score en mode A.

### Mode B : « points faits + contrat » (`POINTS_MADE_PLUS_CONTRACT`) ✅

Exemple : contrat 80 ♥, le preneur fait 100 points.

Avec une belote, le total de la donne passe de 162 à 182 ; preneur et défense se partagent
ces 182 points.

| Issue | Preneur | Défense |
|---|---|---|
| Réussi | **C + F** (80 + 100 = 180) | **D** (60) |
| Chuté | 0 | **160 + C** (ex. chute à 130 → 290), **+ 20** si une belote a été annoncée, quelle que soit l'équipe |
| Réussi, coinché ou surcoinché | **m × C + F** | **0** |
| Chuté, coinché ou surcoinché | 0 | **m × C + 160** |
| Tous les plis faits sans capot annoncé | **C + 250** (ex. 120 + 250 = 370) : les 250 remplacent les 160 de la donne | 0 |
| Capot annoncé, réussi | **m × 250 + 250** (500 sans coinche) | 0 |
| Capot annoncé, chuté | 0 | **m × 250 + 160** (410 sans coinche) |
| Belote de la défense | — | **+ 20**, toujours |

Règle générale : le contrat d'un capot annoncé vaut 250, et c'est **le contrat** que la
coinche multiplie, comme pour n'importe quel autre contrat.

### Arrondi des points faits (mode B)

- Toujours à la dizaine la plus proche, 5 arrondi au-dessus : 44 → 40, 48 → 50, 85 → 90,
  77 → 80.
- Conséquence : si les deux équipes finissent par 5/7 ou 6/6 (ex. 76 et 86 → 80 et 90),
  la donne rapporte 170 au total. Sans importance pratique, mais à implémenter tel quel.
- **Donne finale : pas d'arrondi.** La donne finale est celle où une équipe dépasse le score
  cible. Exemple (partie en 1000, mode B) : à 960, je fais un 100 avec 112 points ; je marque
  100 + 112 = 212 sans arrondi et termine à 1172. Une égalité exacte devient quasi impossible. ✅
  Hypothèse : les points de la défense ne sont pas arrondis non plus sur cette donne.

### Paramètres communs

| Paramètre | Options | Statut |
|---|---|---|
| `scoringMode` | `CONTRACT_ONLY` / `POINTS_MADE_PLUS_CONTRACT` | ✅ les deux |
| `coincheMultiplier` | × 2 | ✅ |
| `surcoincheMultiplier` | × 3 | ✅ |
| `capotValue` | 250 | ✅ |

## 9. Fin de partie

| Paramètre | Options | Statut |
|---|---|---|
| `targetScore` | libre ; usage : **1000 en mode A**, **2000 en mode B** ; certains jouent en 500 ou en 5000 | ✅ paramètre, défaut selon le mode |
| départage | si les deux équipes dépassent le score cible sur la même donne : **celle qui va le plus loin gagne** | ✅ |
| égalité exacte au-delà du score cible | on joue une donne de plus | ✅ quasi impossible (dernière donne non arrondie), mais le moteur doit le traiter |

Le score cible n'influence la façon de jouer que sur les deux ou trois cents derniers points,
surtout quand une équipe a beaucoup de retard (voir `cadrage.md` §8).

## 10. Impact des règles sur l'IA (notes de conception)

### Le mode de décompte change ce que l'IA cherche à maximiser

Exemple donné par l'auteur :

- **Mode A** : la chute coûte peu. Contrer un 120 adverse par un 130 qu'on rate ne coûte
  que 40 points de plus que laisser l'adversaire faire son 120. L'espérance pousse vers
  **les gros contrats, même peu probables**.
- **Mode B** : la chute coûte 160 + contrat. Le même pari coûte beaucoup plus cher.
  L'espérance pousse vers **la prudence** : on n'y va que si la probabilité de réussite
  est en notre faveur.

**Conséquence d'architecture** : le joueur expert évalue chaque décision par l'espérance
d'écart de score, calculée avec **la vraie fonction de décompte** (un paramètre). Il
adapte donc sa prise de risque au mode de décompte sans réglage spécifique. À l'inverse,
les joueurs à règles (niveaux faciles) ont besoin de seuils réglés **pour chaque mode**.

### La coinche fige le contrat

Coincher arrête les enchères. Coincher un 80 ♥ adverse, c'est souvent dire « je veux qu'on
joue exactement ça, et je vais te faire chuter » : on empêche le partenaire ou l'autre
adversaire d'annoncer une autre couleur. La décision de coincher ne dépend donc pas
seulement de la probabilité de chute, mais aussi de **ce qui se passerait si les enchères
continuaient**. Le joueur expert doit comparer « coincher maintenant » à « laisser parler »,
ce qui suppose de simuler la suite des enchères.

### Le dix de der structure la fin de donne

Les 10 points du dernier pli sont un enjeu majeur. Un bon joueur ne déroule pas toutes ses
cartes maîtresses pour laisser l'adversaire finir : il lui cède la main au bon moment (pour
les plis qu'il ne peut de toute façon pas faire), puis la reprend pour le dernier pli.

- Joueur expert : rien de spécifique à coder, le solveur compte le dix de der et trouve ces
  séquences.
- Joueurs à règles : comportement à coder explicitement, sinon ils seront facilement exploitables.

### L'obligation de pisser change la valeur des mains

Explication de l'auteur (reprise au plus près de ses mots) :

> Sans obligation de pisser, une Dame troisième d'atout est forte. Avec obligation de pisser,
> elle ne l'est généralement pas. Quand il reste un atout adverse et que j'ai le plus gros,
> plutôt que de tirer tous les atouts, je peux garder le mien : au moment où je coupe,
> la probabilité que l'adversaire soit obligé de sous-couper, et que je récupère son atout
> par défaut, est assez élevée. Inutile de gâcher un tour et mon atout pour aller chercher
> le dernier. C'est surtout vrai quand j'ai à peu près le même nombre d'atouts que
> l'adversaire : me débarrasser de mes derniers atouts, c'est perdre le contrôle de la partie.

Conséquences :

- Pour le joueur expert, rien de spécifique à coder : les coups légaux dépendent du paramètre,
  et la recherche en tire seule les conséquences. C'est un **scénario de test** (voir
  `cadrage.md` §7, scénario 4).
- Pour l'**évaluation d'une main aux enchères**, la valeur d'une Dame troisième (et plus
  généralement des atouts moyens) dépend du paramètre. Un barème fixe serait faux dans l'un
  des deux modes.

### Sans-atout et tout-atout : un seul moteur de règles, des évaluations distinctes

Les trois types de contrat partagent le même squelette de règles. Ce qui change se décrit
par des **tables** : ordre et valeur des cartes par couleur, et « cette couleur se joue-t-elle
comme un atout ? » (obligation de monter, de couper). Le moteur de règles et le solveur
restent donc uniques et paramétrés par le type de contrat.

Ce qui diffère réellement, c'est **l'évaluation d'une main et les conventions d'enchères** :
une main forte à cœur n'a rien à voir avec une main forte en sans-atout. Ces parties-là
seront spécifiques à chaque type de contrat.

Explication de l'auteur sur la différence majeure : **il n'y a pas de coupe**, donc aucun
moyen fort de reprendre la main. Le risque est de perdre la main et de ne jamais la
récupérer.

> Avec As marié dans trois couleurs, on a légitimement un jeu exceptionnel. Mais il reste
> toujours le cas où un adversaire a le dernier As avec As, 10, Roi, Dame, Valet : si on
> lui donne la main, il fait cinq plis et nous force à lui donner de gros points. L'aléa
> est bien plus fort, parce qu'on n'a pas de contrôle par l'atout sur le déroulement.

Conséquence sur la façon de jouer, selon l'expérience personnelle de l'auteur (c'est la
recherche probabiliste qui doit décider, pas une règle codée) : **garder le contrôle**. Avec les
quatre As en sans-atout, on a intérêt à ne pas jouer ses As en début de donne : ils permettent
de reprendre la main quand on veut. Les jouer tôt, c'est risquer de perdre la main sur un
adversaire qui a, par exemple, un 10 quatrième, et qui nous « plume » ensuite sur nos cartes
médiocres. Une fois les cartes maîtresses jouées, on peut perdre la main et ne plus jamais la
revoir, même avec des cartes intéressantes. Même logique en tout-atout, en plus complexe à
cause de l'obligation de monter.

Pour le joueur expert, ce risque est capté naturellement : les donnes tirées incluent ces
distributions défavorables, et l'espérance les pondère. La gestion du contrôle est un bon
**scénario de test** pour le solveur (`cadrage.md` §7). Pour une évaluation par barème
(niveaux faciles, première estimation aux enchères), il faut au contraire un critère
explicite de **contrôle** (nombre de couleurs tenues, longueurs adverses possibles).

### Note pour plus tard (fin de partie)

Quand une équipe est menée près de la fin, l'objectif n'est plus « maximiser l'espérance de
points de la donne » mais « maximiser la probabilité de gagner la partie ». Voir `cadrage.md`,
section « Notes pour plus tard ».

## 11. Vérification auprès des sources

Il n'existe pas de règle unique : la Fédération française de belote publie un règlement de
coinche, mais il diffère sur plusieurs points des pratiques courantes et se contredit
lui-même (belote « imprenable » dans une section, « prenable » dans une autre). Bilan :

| Règle de l'auteur | Verdict | Détail |
|---|---|---|
| Valeurs des cartes, 152 + 10 de der | ✅ confirmé | Toutes les sources |
| Arrondi 0-4 vers le bas, 5-9 vers le haut | ✅ confirmé | Wikipédia, FFBelote |
| Réussite sur points bruts, sans besoin de faire plus que l'adversaire | ✅ confirmé | Wikipédia, FFBelote (« 89 contre un 90 chute ») |
| Mode B : chute = 160 + C + belote, quelle que soit l'équipe qui l'a | ✅ confirmé | Wikipédia (ex. 90 + 160 + 20 = 270) ; la FFBelote se contredit |
| Capot mode B : 500 annoncé, 410 chuté, 250 + C non annoncé | ✅ confirmé | Wikipédia, FFBelote |
| Capot mode A : 250 pour l'une ou l'autre équipe, 500 coinché | ✅ confirmé | Wikipédia |
| La coinche arrête les enchères | ✅ confirmé | Wikipédia (« fige l'enchère, sauf surcoinche ») ; pagat |
| Monter à l'atout, même sur le partenaire ; partenaire maître = défausse libre | ✅ confirmé | Wikipédia, pagat |
| Pisser : obligatoire ou non | ✅ variante réelle | pagat : obligatoire (sauf « dans le Sud ») ; FFBelote : non obligatoire |
| Sans-atout : As à 19, pas de belote | ✅ confirmé | pagat, FFBelote |
| Tout-atout : V 14, 9 9, A 6, 10 5, R 3, D 1 | ✅ existe | pagat. La FFBelote utilise un autre système (valeurs d'atout normales, total 258 ramené à 162 par un coefficient) |
| Tout-atout sans belote | ✅ confirmé | FFBelote |
| Enchères de 80 à 160 | ✅ confirmé | Wikipédia ; la FFBelote va jusqu'à 650 (avec combinaisons) |
| Mode A : chute = **160** pour la défense | ⚠️ **diverge** | Wikipédia : la défense marque **C** (la valeur du contrat) |
| Mode A : belote de la défense toujours + 20 | ⚠️ **diverge** | Wikipédia : en points annoncés, la belote n'est **jamais** ajoutée au score |
| Coinche : seul le contrat est multiplié ; surcoinche × 3 | ⚠️ **variante** | pagat : contrat seul × 2 puis × 4 (× 3 cité en variante) ; Wikipédia : tous les points × 2 / × 4 ; FFBelote : (160 + C) × 2 |
| Donne finale non arrondie | ❔ non trouvé | Aucune source consultée ne le mentionne : règle de l'auteur, non vérifiable |
| Score cible 1000 (A) / 2000 (B) | ✅ usage variable | pagat : 2000 ; Wikipédia : « généralement 3000 » |

Les écarts ⚠️ ne sont pas des erreurs : ce sont des variantes. Ils justifient d'en faire des
paramètres. **Décision** ✅ : on démarre avec les règles de l'auteur comme valeurs par défaut.
📌 **À revérifier plus tard** (démarche itérative) :

| Paramètre | Défaut (auteur) | Alternative (sources) |
|---|---|---|
| `contractOnlyFailurePoints` | 160 | C |
| `contractOnlyDefenderBelote` | + 20 | 0 |
| `coincheAppliesTo` | contrat seul | tous les points |
| `surcoincheMultiplier` | × 3 | × 4 |

Variante rencontrée, non retenue : redistribuer si un joueur a 10 points ou moins dans sa main
(« souvent dans le sud de la France », Wikipédia). ⛔ proposé.

---

## Sources consultées

- [pagat.com — La Coinche](https://pagat.com/jass/coinche.html) : description détaillée et nombreuses variantes
- [Wikipédia — Belote contrée](https://fr.wikipedia.org/wiki/Belote_contr%C3%A9e)
- [Fédération française de belote — Règles de la coinche](https://www.ffbelote.org/regles-coinche/)
- [regles2jeux.fr — Règle de la coinche](https://regles2jeux.fr/regle-coinche/)
- [regles-du-jeu.net — Règles de la coinche](https://www.regles-du-jeu.net/regles-de-la-coinche/)
- [fr-academic.com — Belote contrée](https://fr-academic.com/dic.nsf/frwiki/201541)
- [fr-academic.com — Belote bridgée](https://fr-academic.com/dic.nsf/frwiki/201539)
- [Wikipedia (en) — Coinche](https://en.wikipedia.org/wiki/Coinche)

Les sources se contredisent sur plusieurs points (voir §11). Les choix ci-dessus sont ceux
de l'auteur du projet.
