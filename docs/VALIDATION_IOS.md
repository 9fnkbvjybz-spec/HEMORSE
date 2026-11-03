# Vérifications et essais restants

## Exécuté dans cette session

- Swift officiel **6.1.2**, Linux x86_64 : compilation du package `HerMorseCore`.
- **33 tests XCTest passés, aucun échec** : alphabet, ordre Koch, timing PARIS/Farnsworth, niveaux et vitesses, profils Android JSON, sauvegarde atomique, corrections, trophées, pioche et iambique A/B.
- Le signal audio synthétique **SOS à 600 Hz / 12 WPM** traverse le détecteur et produit S, O, S.
- Un signal synthétique à **740 Hz** est retrouvé par le suivi de fréquence ; le silence ne produit aucun caractère.
- Continuité du rééchantillonnage vérifiée entre blocs audio.
- Syntaxe des 11 fichiers Swift de l’app analysée par le compilateur Linux (`swiftc -frontend -parse`). Cette analyse ne résout pas les modules SwiftUI, UIKit ou AVFoundation : **elle ne remplace pas une compilation iOS**.
- Le fichier `project.pbxproj` est lisible par `PropertyListSerialization` et les fichiers plist/XML ont été vérifiés.

Le message final « Testing Library … 0 tests » de Swift concerne le runner Swift Testing, non utilisé ici. Les 33 tests sont ceux de la suite XCTest qui le précède.

## À exécuter avec Xcode / un appareil Apple

1. Construire le projet pour simulateur avec la commande du README ; corriger tout diagnostic du SDK Apple avant installation.
2. Créer deux profils, passer de l’un à l’autre, fermer puis relancer l’app ; vérifier que leurs données restent séparées.
3. Importer un véritable profil Android, vérifier les niveaux, paramètres, statistiques et trophées ; exporter puis réimporter sur Android.
4. Écouter K et M ; vérifier les vitesses caractère/effective, l’arrêt et le redémarrage, le volume, le casque, la route Bluetooth et le mode silencieux.
5. Tester la pioche et les deux palettes simultanées, les modes A/B, l’inversion, la sortie de l’écran pendant un appui et le passage en arrière-plan. Aucun son ni contact ne doit rester actif.
6. Réaliser des séries d’écoute à 89 %, 90 % et 100 % ; contrôler la correction et l’évolution de la seule piste réception. Une série annulée ne doit pas faire progresser le niveau.
7. En manipulation, faire une erreur puis réussir après correction : le caractère ne doit pas compter comme juste du premier coup. Tester aussi `=` et `<BT>`.
8. Refuser l’autorisation micro puis l’accorder dans Réglages ; tester une vraie tonalité radio, le spectre, la fréquence manuelle, l’AFC et la sensibilité. Vérifier que l’écoute cesse à la sortie de l’écran, en arrière-plan et lors d’une interruption audio.
9. Connecter le boîtier réel : vérifier Ctrl gauche/droite distincts, relâchements, appui simultané et débranchement pendant un appui. La réception clavier reste une hypothèse tant que cet essai n’a pas réussi.
10. Vérifier les tailles de texte, VoiceOver, iPhone/iPad, rotations iPad et mode sombre. Confirmer les déclarations de confidentialité avant distribution.

Le workflow GitHub Actions fourni peut effectuer les tests du moteur et la compilation pour simulateur sur macOS après envoi du projet au dépôt. Il n’effectue pas ces essais matériels et ne signe pas l’application.
