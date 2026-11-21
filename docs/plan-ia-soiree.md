# Plan IA — soirée du 21 novembre 2026

> **Fichier figé.** Ce plan n'est plus modifié une fois validé. L'avancement, les résultats
> et les écarts au plan sont consignés dans `docs/ia-journal.md`. Chaque version est un
> commit et un tag git : on peut revenir à n'importe quelle version ou choisir celle qu'on
> garde.

## 1. Objectif

Livrer une **première famille d'IA** fondée sur les méthodes des programmes de bridge,
en **plusieurs versions de plus en plus élaborées**. Toutes coexistent dans le code, se
mesurent entre elles dans l'arène, et se choisissent dans l'application pour les tester
au retour.

Méthodes reprises du bridge (cadrage §5) :

| Méthode | Origine bridge | Rôle ici |
|---|---|---|
| **Solveur « double mort »** (cartes ouvertes) | GIB, Bridge Baron, les solveurs DDS | Calcule le résultat exact d'une donne si l'on voyait toutes les cartes |
| **Échantillonnage de Monte-Carlo (PIMC)** | GIB (Ginsberg) | On tire des donnes compatibles avec ce qu'on sait, on résout chacune, on joue la carte qui marche le mieux en moyenne |
| **Inférence par les enchères** | Simulations de GIB et Jack | On ne garde que les donnes tirées où les autres auraient annoncé comme ils l'ont fait |
| **Enchères par simulation** | « Borel simulation » de GIB | La convention propose des annonces candidates ; la simulation tranche entre elles |
| **Signalisation imposée par la convention** | Limite connue du PIMC (cadrage §5, point 5) | Les défausses d'appel suivent la convention ; celles du partenaire nourrissent l'inférence |

## 2. Les versions prévues

Chaque version est un joueur nommé (`Player`), enregistré dans un **catalogue d'IA**
(identifiant, nom français, description). L'arène et l'application les choisissent par leur
identifiant.

| Id | Nom | Contenu | Coût de calcul |
|---|---|---|---|
| `ia1` | Débutant | Le joueur à règles actuel, inchangé (référence) | nul |
| `ia2` | Règles+ | Enchères selon le barème du petit jeu (§4.2 du cadrage) ; coinche simple ; jeu de la carte : compte des atouts, garde de l'atout maître, défausse d'appel directe et lecture de celle du partenaire | nul |
| `ia3` | Oracle (triche) | Voit toutes les cartes et joue le coup exact. **Référence de mesure uniquement**, jamais proposé comme adversaire normal | moyen |
| `ia4` | Expert PIMC | Tire des donnes compatibles avec le jeu (cartes vues, couleurs manquantes, coupes), les résout cartes ouvertes, joue la meilleure carte en moyenne. Enchères de `ia2` | élevé |
| `ia5` | Expert PIMC + enchères | Comme `ia4`, mais les donnes tirées doivent être cohérentes avec les enchères, selon le modèle de convention ; pondération souple pour accepter les blocages et les écarts | élevé |
| `ia6` | Expert PIMC + signaux | Comme `ia5`, avec l'inférence des défausses d'appel du partenaire | élevé |
| `ia7` | Expert complet | Comme `ia6`, plus les enchères et la coinche par simulation : les annonces candidates de la convention sont départagées en résolvant des donnes tirées | très élevé |

**Niveaux de force.** Chaque expert existe en trois réglages de calcul : *rapide*
(environ 16 donnes tirées), *normal* (environ 48) et *fort* (environ 128). Un temps maximal
de réflexion par coup est aussi fixé. On obtient ainsi une vingtaine de versions à
comparer.

**Hors soirée, si le temps manque** : `ia6` et `ia7` dans cet ordre. En option, si tout le
reste est fait : une variante « alpha-mu » (Cazenave), qui limite le défaut de fusion des
stratégies du PIMC.

## 3. Le solveur cartes ouvertes (cœur technique)

- **Ce qu'il calcule** : les points que fait l'attaque si les quatre joueurs jouent
  parfaitement à cartes ouvertes. Il compte les cartes, le dix de der et le capot quand il
  compte. La belote ne dépend pas du jeu, c'est une constante.
- **Pourquoi des points suffisent** : le score d'une donne est une fonction croissante des
  points de l'attaque. Pour une donne connue, on peut donc chercher le meilleur nombre de
  points puis le convertir en score. Le PIMC fait ensuite la **moyenne des scores**, et non
  des points, ce qui intègre la marche d'escalier « contrat fait ou chuté ».
- **Technique** : alpha-bêta avec fenêtre nulle, table de transposition aux débuts de pli,
  tri des coups, et regroupement des cartes équivalentes. Deux cartes qui se suivent dans
  l'ordre parmi celles qui restent ont la même valeur, sauf pour les points, qui sont
  traités à part.
- **Preuves exigées** :
  1. Sur des milliers de fins de donne aléatoires de 2 à 4 plis, le solveur et une recherche
     exhaustive naïve donnent exactement le même résultat.
  2. Quelques positions calculées à la main.
  3. Le solveur sait échouer : sabotage volontaire, comme pour le reste du moteur.
  4. Temps mesuré sur le Mac : résolution complète d'une donne (8 plis), moyenne et pire
     cas sur 1 000 donnes.

## 4. Inférence et échantillonnage

- **Contraintes dures** : les cartes déjà jouées ; les couleurs manquantes, déduites d'un
  joueur qui n'a pas fourni ; les absences d'atout, déduites d'un joueur qui n'a pas coupé
  alors qu'il le devait.
- **Contraintes des enchères** (`ia5`) : on rejoue le modèle de convention sur la donne
  tirée. Si le joueur modèle aurait annoncé autre chose que ce qui a été dit, la donne perd
  du poids, sans être exclue.
- **Tirage** : distribution aléatoire des cartes inconnues qui respecte les contraintes
  dures, puis rejet ou pondération. Graine fixe en test, pour pouvoir reproduire.

## 5. Mesure (arène)

- Chaque version est confrontée en **duplicate** à `ia1` puis à la meilleure version
  précédente, sur 400 à 2 000 donnes selon sa vitesse, au décompte par défaut (points au
  contrat).
- On mesure : l'écart moyen par donne et sa marge à 95 %, le taux de contrats réussis, et le
  temps de réflexion moyen et maximal par coup.
- À la fin, un **tournoi** entre les meilleures versions.
- Les parties jouées sont **enregistrées** au format habituel, et j'en analyse un
  échantillon pour repérer les coups absurdes, comme convenu.
- **Scénarios du cadrage** : je complète les mains des scénarios 1, 2 et 4 de façon
  plausible et j'en fais des tests. S'ils échouent, ils sont signalés comme échecs connus,
  pas cachés.

## 6. Dans l'application

- Page **« Options »** dans le menu en bois, pour choisir l'IA du partenaire et celle des
  adversaires séparément. On peut ainsi jouer avec un partenaire `ia2` contre des
  adversaires `ia5`, par exemple.
- Le calcul se fait **en arrière-plan** (pas sur le fil de l'interface), avec un temps
  maximal par coup. Le badge affiche « réfléchit… » pendant le calcul.
- Le choix de l'IA est enregistré dans l'en-tête de chaque partie, pour l'analyse. Le
  format de partie reste lisible : le champ est ajouté sans casser les anciens fichiers.
- Un APK prêt à installer est laissé dans `app/build/outputs/apk/debug/`. **Rien n'est
  installé sur le téléphone** sans ton accord.

## 7. Ordre d'exécution et points d'arrêt

Chaque étape se termine par un état stable : build vert, tests comptés, commit et tag. Si je
m'arrête n'importe où, ce qui est fait est utilisable.

| # | Étape | Tag |
|---|---|---|
| 0 | Base propre : l'interface actuelle commitée ; branche `test_ia` ; catalogue d'IA ; arène en ligne de commande qui prend des identifiants d'IA ; `docs/ia-journal.md` | `ia-base` |
| 1 | `ia2` Règles+ | `ia2-regles-plus` |
| 2 | Solveur cartes ouvertes, ses preuves et ses mesures ; `ia3` Oracle | `ia3-oracle` |
| 3 | `ia4` PIMC (trois niveaux) | `ia4-pimc` |
| 4 | `ia5` inférence par les enchères | `ia5-encheres` |
| 5 | Application : choix des IA, calcul en arrière-plan, APK | `ia-app` |
| 6 | `ia6` signaux | `ia6-signaux` |
| 7 | `ia7` enchères par simulation | `ia7-complet` |
| 8 | Tournoi final, rapport dans le journal | `ia-soiree-fin` |

Les étapes 6 et 7 sont les plus longues et les plus incertaines. Elles passent après
l'application, pour que tu aies de quoi jouer au retour même si la soirée ne suffit pas.

## 8. Règles respectées pendant la soirée

- Code et commentaires en anglais ; interface en français.
- Pour chaque version : tests, comptage des tests exécutés, au moins un sabotage repéré, et
  detekt vert.
- **`main` n'est pas touchée** : tout se fait sur la branche `test_ia`, qu'on fusionne au
  retour après ton choix.
- Rien n'est installé ni téléchargé sur le téléphone. Si le disque sature, je vide les caches
  de build, après avoir arrêté Gradle.
- Aucun nom réel dans le code, les commits ou les métadonnées.
- Si une étape échoue, l'échec est écrit tel quel dans le journal, avec la sortie des tests.

## 9. Décisions prises avant le départ ✅

1. L'interface actuelle (feutre, menu en bois, grandes cartes) est commitée et poussée sur
   `main` **avant** de commencer. La branche part de là.
2. Branche **`test_ia`**, poussée sur GitHub après chaque version, pour que la CI la vérifie
   (le dépôt est public). `main` n'est pas touchée.
3. Le jeu de cartes svg-cards (LGPL) sera intégré **après** la soirée.
4. **Rien n'est installé sur le téléphone** : l'APK reste prêt sur le Mac.
