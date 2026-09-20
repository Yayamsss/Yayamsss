# lnreader-desktop-poc

Étape 1 du portage : prouver que les extensions LNReader (JS compilé,
distribué via `.dist/plugins.min.json`) peuvent tourner **hors Android**,
dans un JVM avec GraalJS, sans passer par DexClassLoader/APK.

## Ce que fait ce POC

1. Télécharge `plugins.min.json` (manifeste de métadonnées).
2. Trouve un plugin par id (`allnovel` par défaut — novelfull.com), et
   télécharge son fichier `.js` compilé individuellement (voir `url` dans
   le manifeste).
3. Démarre un contexte GraalJS avec :
   - un `fetch()` global adossé à OkHttp (le vrai réseau, côté JVM),
   - `cheerio` + `htmlparser2` réels, embarqués via un bundle esbuild
     (`src/main/resources/cheerio-bundle.cjs`, ~2.5 Mo, déjà généré et
     testé dans ce repo),
   - un `require()` maison qui route `cheerio`, `htmlparser2`,
     `@libs/fetch`, `@libs/novelStatus` vers ces implémentations,
   - un `console` minimal.
4. Charge le plugin (`module.exports.default`) exactement comme le fait
   le script officiel `scripts/build-plugin-manifest.js` du repo LNReader.
5. Appelle `popularNovels(1, { showLatestNovels: false, filters: {} })`
   et affiche le résultat.

## Ce qui est validé vs. ce qui ne l'est pas

- **Validé** (testé en Node.js pur pendant le dev de ce POC) : le
  chargement du module CJS compilé, la résolution de `require`, et
  l'exécution jusqu'à l'appel réseau fonctionnent avec le vrai fichier
  `AllNovel[readnovelfull].js`.
- **Non testé** : la partie GraalJS/JVM elle-même (`Main.kt`), faute
  d'accès à Maven Central dans l'environnement où ce POC a été écrit.
  Attends-toi à corriger deux-trois trucs au premier `./gradlew run`
  (mismatch de signature GraalJS selon la version exacte, etc.) — c'est
  normal pour un premier jet, pas un signe que l'architecture est fausse.

## Build & run sur Arch/CachyOS

```bash
# JDK 21 (tu l'as déjà d'après tes notes précédentes)
sudo pacman -S jdk21-openjdk gradle

cd lnreader-desktop-poc
gradle run --args="https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json allnovel"
```

Si `gradle` seul plante sur la résolution de version Kotlin, utilise le
wrapper à la place : `gradle wrapper && ./gradlew run --args="..."`.

## Prochaines étapes (une fois que ça tourne)

1. Fixer les éventuels soucis de compat GraalJS (version exacte, API
   `HostAccess`/`ProxyExecutable` selon la release choisie).
2. Gérer les `filters` par défaut du plugin (dans ce POC on passe
   `filters: {}`, ce qui fait planter certains plugins qui s'attendent à
   des clés précises — regarder `plugin.filters` pour construire un objet
   par défaut correct).
3. Implémenter `parseNovel()` et `parseChapter()` de la même façon.
4. Remplacer le CLI par une petite UI Compose Multiplatform Desktop.
5. Charger dynamiquement plusieurs plugins depuis un manifeste complet
   plutôt qu'un seul id en dur.
