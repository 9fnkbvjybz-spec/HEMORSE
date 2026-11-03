# HerMorse web : Netlify et écran d’accueil

La version `web/` est une application web progressive (PWA). Elle ne nécessite ni Mac, ni compilation Xcode, ni compte développeur Apple. Elle s’ouvre dans Safari et peut être ajoutée à l’écran d’accueil. Le projet SwiftUI existant est conservé ; ce sont deux applications utilisant le même format de profils.

## GitHub et Netlify

Le fichier `netlify.toml` à la racine définit :

| Paramètre | Valeur |
|---|---|
| Branche de production | `main` à sélectionner lors de l’import |
| Base directory | `web` |
| Build command | `npm run build` |
| Publish directory | `dist`, relatif au dossier de base |
| Node | `22` |
| Secrets | Aucun |

Le script de build utilise uniquement Node et produit un site statique. Les modules JavaScript, styles, icônes, manifeste et worklets audio sont hébergés sur le même domaine ; aucune API ni police externe n’est appelée. Les dépendances npm de développement servent aux tests navigateur.

Dans Netlify, importer le dépôt GitHub, laisser la configuration être détectée, puis lancer le déploiement. Après connexion du dépôt à Netlify, les prochains envois sur `main` déclencheront normalement de nouveaux déploiements. Les liens de prévisualisation Netlify ne sont pas nécessaires à l’installation : utiliser l’adresse HTTPS de production.

Deux workflows indépendants sont fournis : **Validate HerMorse Web** (tâche **Web - Netlify PWA**, sous Linux) et **Validate HerMorse iOS** (sous macOS). Les changements dans `web/` ou `netlify.toml` déclenchent la validation web ; les modifications du code natif déclenchent la validation iOS. Une pastille jaune/orange avec « Queued » signifie que le workflow attend un runner ou une capacité de compte disponible. Un échec d’attribution d’un runner macOS ne démontre pas une erreur du site web. La disponibilité et la facturation des runners restent gérées par GitHub.

## Sur l’iPhone

1. Ouvrir l’adresse HTTPS Netlify dans Safari.
2. Créer un profil ou importer un fichier `.hermorse` exporté depuis Android.
3. Utiliser **Partager → Sur l’écran d’accueil** (ou **Ajouter à l’écran d’accueil**).
4. Si Safari affiche **Ouvrir comme app web**, l’activer. Confirmer avec **Ajouter**.
5. Ouvrir l’icône et attendre la préparation du mode hors connexion, avec Internet disponible pour cette première ouverture.

Le manifeste fournit le nom HerMorse, une icône, un mode autonome et le démarrage à la racine. Une icône `apple-touch-icon` de 180 px est fournie pour Safari. Le service worker prépare un cache versionné contenant les fichiers du site et du moteur audio. Les mises à jour du cache attendent la fermeture des fenêtres de l’ancienne version pour éviter de mélanger les versions pendant un exercice.

## Fonctions implémentées

- Profils locaux, création/sélection/suppression avec confirmation et import/export Android `.hermorse` v1.
- 60 caractères, ordre Koch, fiches et écoute sonore ; prosignes atomiques et ponctuations équivalentes.
- Saisie libre avec pioche, double palette iambique A/B et inversion.
- Écoute → saisie avec correction et prise en compte des caractères ajoutés.
- Lecture → manipulation, correction sonore des erreurs et réussite du premier coup.
- Deux progressions indépendantes et 42 niveaux, seuil de 90 %, paliers Farnsworth, mots/indicatifs/phrases.
- Statistiques par caractère, historique, confusions, pratique quotidienne et trophées.
- QSO au microphone : traitement local en AudioWorklet, Goertzel, spectre, AFC et fréquence manuelle.
- Mode hors connexion après préparation du cache, apparence claire/sombre, aide à l’installation.

Les exercices de mots et de phrases sont une adaptation du contenu Android, pas une copie exhaustive de son vocabulaire. Le mode Live n’existe pas dans la source Android v0.4 et n’a pas été ajouté.

## Données et limites sur iPhone

- Le son doit être activé par une action de l’utilisateur. Au premier appui, le navigateur initialise le moteur audio ; les lectures suivantes utilisent le worklet déjà chargé. Le moteur s’arrête et les contacts sont relâchés quand l’application passe en arrière-plan. Aucun audio d’arrière-plan n’est promis.
- Le microphone demande l’autorisation de Safari et une origine HTTPS. Le refus est signalé ; l’écoute peut être relancée après autorisation. L’analyse ne transmet et ne conserve aucun enregistrement audio.
- Le retour haptique web n’est pas disponible dans Safari sur iPhone. Le routage casque/Bluetooth reste celui d’iOS.
- Les touches Ctrl gauche/droite et `[` / `]` sont reconnues si le navigateur les transmet. **La compatibilité du boîtier USB avec Safari iOS n’a pas été établie.** Le manipulateur tactile reste disponible ; aucune prise en charge WebUSB/WebSerial sur iPhone n’est annoncée.
- Les profils utilisent le stockage du navigateur, distinct selon le domaine et potentiellement le contexte d’installation. Safari peut effacer ce stockage ; un changement de domaine Netlify ne transfère pas les données. **Exporter régulièrement le profil `.hermorse`**, notamment avant de changer de téléphone, d’installer la PWA ou d’effacer Safari. Si le profil n’apparaît pas dans la version installée, le réimporter.
- En navigation privée ou si le stockage est bloqué, la sauvegarde peut être indisponible. Un fichier local invalide est signalé et conservé ; il n’est pas remplacé silencieusement par des profils vides.
- Les essais Chromium ne remplacent pas un essai Safari sur l’iPhone réel, notamment pour le son, les gestes simultanés et le microphone près d’un poste radio.

## Architecture et vérification

`src/core.js` porte les données, profils, progression, timing et manipulateur ; `src/signal.js` contient le décodeur audio et le rééchantillonnage. La synthèse et la capture sont dans deux AudioWorklets. L’interface n’injecte pas les noms ou contenus de profils sans échappement HTML.

`npm test` exécute les tests Node du moteur, du format de profils et des signaux synthétiques. `npm run test:e2e` construit le site puis vérifie les parcours dans Chromium : profil, fiches/audio, exercice d’écoute, pioche, import/export, accès micro simulé, réouverture hors réseau et rejet des fichiers invalides. Ces tests ne prouvent pas la réussite d’un déploiement Netlify ou d’une installation réelle sur iPhone.

Vérification locale effectuée le 3 novembre 2026 : **25 tests Node et 8 tests Chromium passés**, sans échec. Le serveur de tests applique les mêmes en-têtes de sécurité que Netlify. Les essais comprennent un fichier audio Morse synthétique, le worklet de synthèse réel, la capture d’un microphone simulé, la correction puis la répétition d’un caractère, et l’ouverture/lecture audio en mode hors connexion. L’affichage mobile a été inspecté à 390 × 844 pixels. Aucun essai Safari sur iPhone ni déploiement Netlify n’a été réalisé dans cette session.
