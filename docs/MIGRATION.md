# Correspondance Android → iOS

Les fichiers reçus ont été traités comme des sources à analyser. Aucun script Gradle ni firmware joint n’a été exécuté. Le firmware original est conservé sans changement.

| Android | Adaptation iOS |
|---|---|
| Jetpack Compose, `App.kt` | SwiftUI, `HerMorseApp.swift`, navigation et sélection de profil |
| `Morse.kt` | `HerMorseCore/Morse.swift`, 60 entrées et ordre Koch identiques |
| `Levels.kt`, `AppModel.kt` | Générateur de contenu et progression pure dans `Training.swift`, état observable dans l’app |
| `Store.kt` / SharedPreferences | Données Codable et repository JSON atomique ; format d’export Android conservé |
| `ToneEngine.kt` / AudioTrack | AVAudioEngine + AVAudioSourceNode, oscillateur 48 kHz et rampe 5 ms |
| `KeyController.kt` | Machine d’états monotone testable, pilotée par une horloge côté interface |
| Boutons Compose | Vues UIKit pour appui/relâchement, annulation et contacts simultanés |
| `CwListener.kt` / AudioRecord | AVAudioEngine en entrée, analyse en file série dans `SignalAnalyzer` |
| FFT Android | Analyse spectrale DFT fenêtrée sur les mêmes bins utiles, toutes les 100 ms |
| `ExternalKey.kt` | Événements clavier UIKit avec usages HID gauche/droite séparés |
| `StatsScreen.kt` | Swift Charts et tableaux de réussite |
| Vibration continue Android | Retour haptique bref à l’appui sous iOS |
| Sortie forcée haut-parleur/filaire/BT | Sélection de route système iOS, avec maintien du champ `output` dans les exports |

## Différences intentionnelles

- Une réponse d’écoute avec des caractères ajoutés est pénalisée : une saisie contenant toutes les réponses possibles n’obtient pas 100 %. Le correcteur Android ne comptait pas les insertions dans son pourcentage.
- Les prosignes (`<AR>`, `<BT>`, etc.) sont des unités atomiques dans les exercices personnalisés et la lecture audio. Le réglage `useProsigns` est pris en compte.
- En manipulation, une ponctuation et un prosigne ayant le même motif sont équivalents (`=` / `<BT>`, `+` / `<AR>`, `(` / `<KN>`).
- Les séries, fenêtres statistiques, historiques et objectifs Koch reprennent les règles Android. Le calcul des jours consécutifs utilise le calendrier local, y compris aux changements d’heure.
- Le suivi spectral utilise une DFT ciblée plutôt qu’une FFT complète. Les tests audio exécutés ne démontrent pas encore la qualité du décodage sur une radio réelle ni en présence de bruit.
- Le rééchantillonnage micro est linéaire vers 8 kHz. Les essais matériels devront notamment vérifier les effets du bruit et du filtrage de l’entrée audio.
- L’icône Android fournie a été adaptée à la taille d’asset iOS ; une source graphique haute définition serait préférable pour une diffusion finale.

## Boîtier USB

Le code fourni `HerMorseKey.ino` utilise `Keyboard.h` sur ATmega32U4. Il envoie `KEY_LEFT_CTRL` pour la pointe du jack et `KEY_RIGHT_CTRL` pour la bague. Il n’utilise pas de protocole série propriétaire : l’intégration iOS passe par les événements clavier publics, pas par un accès USB brut.

Le code iOS reconnaît ces deux usages HID et les touches alternatives `[` / `]`. Aucun voyant « détecté » n’apparaît avant réception d’un événement. Les contacts sont relâchés lors d’une annulation ou de la fermeture de la vue.

**Compatibilité matérielle non établie** : il reste à vérifier la remise des touches modificatrices seules par iOS, l’alimentation du boîtier et l’adaptateur approprié au téléphone. Ne pas reflasher le boîtier avant cet essai. Si iOS intercepte les Ctrl seuls, on pourra préparer séparément un firmware utilisant deux touches ordinaires, puis tester cette variante.

Les fonctions de commande directe d’émetteur/PTT évoquées comme futures dans la documentation du boîtier ne sont pas implémentées.

## Diffusion

La compilation, la signature et l’installation sur appareil utilisent les outils Apple. Aucun IPA signé, test TestFlight, publication App Store ou vérification sur un nouveau Mac n’a été réalisé. Le manifeste de confidentialité déclare uniquement le temps monotone utilisé pour mesurer les appuis ; les déclarations devront être revues avec les exigences Apple au moment de la diffusion.
