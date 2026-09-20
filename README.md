# lnreader-desktop-poc

Étape 1 du portage : prouver que les extensions LNReader (JS compilé,
distribué via `.dist/plugins.min.json`) peuvent tourner **hors Android**,
dans un JVM avec GraalJS, sans passer par DexClassLoader/APK.

Étape 2 (celle-ci) : une UI Compose Multiplatform Desktop par-dessus ce
runtime, pour naviguer romans populaires → métadonnées/chapitres → contenu
d'un chapitre, sans passer par la ligne de commande.

## Architecture du code

Le code est découpé en runtime/service réutilisables, plus deux points
d'entrée (CLI et UI) qui les consomment :

- [`lnreader.runtime`](src/main/kotlin/lnreader/runtime/JsRuntime.kt) —
  câblage bas niveau du contexte GraalJS : `fetch()` adossé à OkHttp,
  `cheerio`/`htmlparser2` réels (bundle esbuild), `require()` maison,
  `console` minimal, et l'attente de Promises JS depuis Kotlin
  (`awaitJsPromise`). C'est `JsRuntime.createContext()` /
  `JsRuntime.loadPlugin()`.
- [`lnreader.service.LNReaderService`](src/main/kotlin/lnreader/service/LNReaderService.kt) —
  service réutilisable au-dessus du runtime : télécharge le manifeste,
  charge un plugin par id, puis expose des méthodes `suspend` typées
  Kotlin (`listPlugins`, `loadPlugin`, `popularNovels`, `parseNovel`,
  `parseChapter`) qui convertissent les `Value` GraalJS en data classes
  (`PluginManifestEntry`, `NovelSummary`, `NovelDetails`, `ChapterInfo`).
  Tout l'accès au `Context` GraalJS est confiné à un thread dédié (les
  contextes GraalJS ne sont pas thread-safe).
- [`lnreader.cli`](src/main/kotlin/lnreader/cli/Cli.kt) — le CLI d'origine,
  maintenant un fin wrapper autour de `LNReaderService`.
- [`lnreader.ui`](src/main/kotlin/lnreader/ui/App.kt) — l'UI Compose
  Desktop : liste des romans populaires → sélection d'un roman (métadonnées
  + liste des chapitres) → sélection d'un chapitre (contenu HTML/texte
  affiché en brut, scrollable).

## Build & run sur Arch/CachyOS

```bash
# JDK 21 (tu l'as déjà d'après tes notes précédentes)
sudo pacman -S jdk21-openjdk gradle

cd lnreader-desktop-poc
```

### UI Compose Desktop (point d'entrée par défaut)

```bash
./gradlew run
```

Ça ouvre une fenêtre avec un champ « Plugin id » (pré-rempli avec
`allnovel`) et un bouton « Load popular novels » : ça charge le plugin
depuis le manifeste officiel (voir `DEFAULT_MANIFEST_URL` dans
[`LNReaderService.kt`](src/main/kotlin/lnreader/service/LNReaderService.kt)),
puis affiche la liste des romans populaires. Cliquer sur un roman affiche
ses métadonnées et ses chapitres ; cliquer sur un chapitre en affiche le
contenu.

### CLI (comportement d'origine, toujours disponible)

```bash
./gradlew runCli --args="https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json allnovel"
```

Les chemins du roman et du chapitre peuvent être fournis explicitement en
troisième et quatrième arguments. Sinon, le premier roman et le premier
chapitre renvoyés par les appels précédents sont utilisés :

```bash
./gradlew runCli --args="<manifest-url> <plugin-id> <novel-path> <chapter-path>"
```

Si `gradle` seul plante sur la résolution de version Kotlin, utilise le
wrapper à la place : `gradle wrapper && ./gradlew run` (ou `runCli`).

## Ce qui est validé vs. ce qui ne l'est pas

- **Validé** (testé de bout en bout avec le vrai réseau, plugin
  `AllNovel[readnovelfull].js`) : manifeste → téléchargement du plugin →
  GraalJS/cheerio → `popularNovels()` → `parseNovel()` → `parseChapter()`,
  via `./gradlew runCli`, ainsi que la compilation de l'UI Compose Desktop
  (`./gradlew build`).
- **Non testé de façon automatisée** : l'UI Compose elle-même (pas de test
  d'intégration UI dans ce repo) — validée manuellement en lançant
  `./gradlew run` et en suivant le flux roman → chapitre.

## Prochaines étapes (une fois que ça tourne)

1. Fixer les éventuels soucis de compat GraalJS (version exacte, API
   `HostAccess`/`ProxyExecutable` selon la release choisie).
2. Gérer les `filters` par défaut du plugin (pour l'instant on passe
   `filters: {}` par défaut du plugin sélectionné, ce qui fait planter
   certains plugins qui s'attendent à des clés précises — regarder
   `plugin.filters` pour construire un objet par défaut correct).
3. ~~Implémenter `parseNovel()` et `parseChapter()` de la même façon.~~ Fait.
4. ~~Remplacer le CLI par une petite UI Compose Multiplatform Desktop.~~
   Fait (voir ci-dessus) — reste à faire : rendu HTML réel du chapitre
   (actuellement affiché en texte brut), recherche/filtre de romans,
   pagination de `popularNovels()`.
5. Charger dynamiquement plusieurs plugins depuis un manifeste complet
   plutôt qu'un seul id en dur (l'UI a un champ texte pour l'id, mais pas
   encore de sélecteur parcourant le manifeste).
