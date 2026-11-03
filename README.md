# HerMorse — application web installable et projet iOS

**Pour obtenir une icône sur l’écran d’accueil de l’iPhone sans Mac, utiliser la version web/PWA dans `web/`.** La configuration `netlify.toml` à la racine prépare le déploiement de cette version sur Netlify. Le projet SwiftUI est conservé dans `iOS/` pour la voie native.

## Publier la version web sur Netlify

1. Dans Netlify, choisir **Add new project → Import an existing project → GitHub**.
2. Sélectionner **9fnkbvjybz-spec/HEMORSE**, branche **main**.
3. Laisser Netlify lire `netlify.toml`. Les paramètres sont : dossier de base **web**, commande **npm run build**, dossier publié **dist** (relatif à `web`). Aucune variable secrète n’est requise.
4. Lancer le déploiement et ouvrir l’adresse HTTPS attribuée par Netlify.
5. Sur l’iPhone, ouvrir cette adresse dans **Safari → Partager → Sur l’écran d’accueil → Ajouter**. Si l’option est proposée, activer **Ouvrir comme app web**.
6. Ouvrir HerMorse depuis cette icône avec Internet une première fois. Attendre le message **Prêt pour l’entraînement hors connexion** avant de l’utiliser sans réseau.

Dans GitHub Actions, le workflow **Validate HerMorse Web** vérifie cette version ; **Validate HerMorse iOS** concerne le projet natif et reste indépendant du site Netlify. Les changements du site web ne relancent pas le runner macOS. Le détail du fonctionnement et des limites est dans [docs/WEB_PWA.md](docs/WEB_PWA.md).

### Développement web local

Depuis le dossier `web`, avec Node 22 ou ultérieur :

```sh
npm ci
npm test
npm run build
npm run dev
```

Pour les tests navigateur, installer Chromium avec `npx playwright install chromium`, puis lancer `npm run test:e2e`. Sur une machine équipée d’un Chromium système, on peut passer son chemin dans `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.

## Version native iOS

Projet SwiftUI pour iPhone et iPad **iOS 17 ou ultérieur**, réalisé à partir des sources Android HerMorse v0.4.0 et du firmware fournis par l’utilisateur.

**État : première version du portage source.** Le moteur Swift a été compilé et ses tests exécutés sous Linux. L’application SwiftUI n’a pas été compilée avec le SDK Apple ni essayée sur un appareil. Cette livraison est un projet de développement, pas un fichier IPA installable ou une version prête pour l’App Store.

## Ouvrir la version iOS

1. Sur un Mac équipé de **Xcode 16 ou ultérieur**, décompresser le projet entier.
2. Ouvrir `iOS/HerMorse.xcodeproj`. Conserver `Package.swift` et `Sources/` à leur emplacement : le projet utilise ce package local.
3. Choisir le schéma **HerMorse** et un simulateur iPhone sous iOS 17 ou ultérieur, puis **Run**.
4. Pour un iPhone réel, sélectionner votre équipe Apple dans **Signing & Capabilities**, ajuster l’identifiant d’application si nécessaire, puis lancer sur l’appareil.
5. Exécuter les essais de [validation iOS](docs/VALIDATION_IOS.md), notamment le microphone, les interruptions et le boîtier.

Aucun compte serveur, aucune clé API et aucune dépendance Swift distante ne sont nécessaires. Les sélecteurs audio et fichiers utilisent les interfaces système d’iOS. Le microphone est demandé uniquement quand l’utilisateur lance l’écoute QSO.

## Fonctions présentes dans le code

- Profils locaux : création, sélection, suppression avec confirmation, import/export `.hermorse` version 1.
- Fiches des 60 caractères : lettres, chiffres, ponctuation, accents et prosignes ; écoute sonore.
- Saisie libre avec pioche ou double palette iambique A/B, palettes inversables.
- Exercices « Écouter → écrire » et « Lire → manipuler », avec correction.
- Deux progressions Koch indépendantes, seuil de 90 %, 42 niveaux, paliers de vitesse et Farnsworth.
- Synthèse audio avec rampe anti-clic et retour haptique ponctuel à l’appui.
- Écoute QSO : acquisition micro, rééchantillonnage, détection Goertzel, spectre, AFC et traduction locale.
- Statistiques, activité, historique, confusions, rapport trait/point, trophées et thèmes clair/sombre.
- Réception des touches Ctrl gauche/droite et `[` / `]` pour le boîtier clavier USB, **à valider sur matériel iOS**.

Le mode **Live** est déjà annoncé pour une version future dans les sources Android v0.4 ; il n’est pas implémenté ici. Les adaptations et les différences intentionnelles sont détaillées dans [MIGRATION.md](docs/MIGRATION.md).

## Structure

```text
Package.swift                 Package Swift indépendant des outils Apple
Sources/HerMorseCore/          Morse, données, timing, décodeur, progression et profils
Tests/HerMorseCoreTests/       Tests du moteur et des formats d’échange
iOS/HerMorse/                 Interface SwiftUI, audio, micro et clavier USB
iOS/HerMorse.xcodeproj/        Projet Xcode avec package local et schéma partagé
AndroidReference/             Sources Android et firmware fournis, conservés sans modification
docs/                         Équivalences, limites et validation sur Apple
scripts/                      Génération reproductible du projet Xcode
```

## Vérifications reproductibles

Avec Swift 5.9 ou ultérieur sur Mac ou Linux, à la racine :

```sh
swift test --jobs 2
```

Sur Mac, construire ensuite l’interface pour simulateur sans signature :

```sh
xcodebuild -project iOS/HerMorse.xcodeproj -scheme HerMorse \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath DerivedData CODE_SIGNING_ALLOWED=NO build
```

Le workflow `.github/workflows/validate.yml` prépare ces deux vérifications sur un runner macOS. Son exécution et ses résultats sont consultables dans l’onglet **Actions** du dépôt GitHub ; les vérifications locales décrites ici ne constituent pas une compilation iOS.

Le projet Xcode est déjà fourni. Après ajout de fichiers Swift dans `iOS/HerMorse`, on peut le régénérer avec Python 3, sans bibliothèque supplémentaire :

```sh
python3 scripts/generate_xcode_project.py
```

Ne pas régénérer après des personnalisations manuelles de signature ou de configuration sans les reporter dans le générateur.

## Transférer un profil Android

Depuis Android, exporter le profil au format `.hermorse`, puis ouvrir ce fichier avec HerMorse sur iOS ou utiliser **Importer un profil**. Le nom est suffixé si un profil du même nom existe déjà. Les paramètres, les statistiques, les dates en millisecondes et la clé Android `rxSpeed3` sont conservés. La compatibilité a été testée avec des fixtures au format de `Store.kt` ; un essai aller-retour avec un véritable profil utilisateur reste à faire.

Les profils sont sauvegardés dans `Application Support/HerMorse/profiles.json`, par écriture atomique. Un fichier local illisible est signalé et n’est pas remplacé automatiquement.

L’archive Android reçue ne contient que le module `app/src` et son fichier Gradle : `AndroidReference` est une référence de portage, pas un projet Android complet recompilable à lui seul.
