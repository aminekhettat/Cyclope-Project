# Qualité du firmware ESP32Cam Cyclope III

Cette chaîne est additive : le sketch de Viet-An reste inchangé. Le gate principal
est une compilation réelle du sketch, pas une simulation du matériel.

## Outils et compilation

- Python 3 standard, Arduino CLI 1.5.1, core officiel Espressif épinglé dans
  `toolchain.json`, et cppcheck (analyse indicative).
- Installer Arduino CLI avec le binaire officiel correspondant à son système :
  <https://arduino.github.io/arduino-cli/latest/installation/>.
- Depuis ce dossier :

```sh
python3 scripts/quality.py install
python3 scripts/quality.py version
python3 scripts/quality.py compile
python3 scripts/quality.py cppcheck
```

La compilation utilise `esp32:esp32:esp32s3`, PSRAM OPI et flash 16 Mo, selon la
DFR1154 (ESP32-S3R8, PSRAM 8 Mo) : <https://wiki.dfrobot.com/dfr1154/>.
Les autres options Arduino et la table de partitions restent celles par défaut.
Ce profil est une cible de compilation ; confirmer toutes les options sur la
carte réelle avant un éventuel flashage. Aucune commande ici ne flashe de carte.
`toolchain.json` est partagé entre local et CI et limite la compilation à deux
processus. Les sorties locales ignorées par Git sont dans `.build/firmware/`.

Arduino CLI est retenu : il compile directement le `.ino` avec les bibliothèques
WiFi, WebServer et esp_camera du core officiel. PlatformIO est une alternative,
et le sketch a aussi compilé lors de la comparaison avec PlatformIO Core
6.1.19 / espressif32 7.0.1 / Arduino 2.0.17. Cette voie utilise un core différent
du core officiel courant 4.0.0 retenu ici ; maintenir deux gates compilerait
des versions différentes des mêmes API. Le profil exploratoire PlatformIO
(ESP32-S3-DevKitC-1 générique) ne constitue pas une validation de la DFR1154.

## Version canonique et détection de dérive

`VERSION` est l'unique source de version sémantique `MAJOR.MINOR.PATCH`.
La version initiale `0.1.0` est une référence de développement, pas une déclaration
de validation du matériel ni une release publique.

Pour changer la version :

1. Modifier seulement `VERSION`.
2. Exécuter `python3 scripts/quality.py version --write`.
3. Commiter ensemble `VERSION` et le fichier généré
   `esp32_s3_ai_camera_vv/firmware_version.h`.
4. Vérifier avec `python3 scripts/quality.py version`, puis compiler.

Ne pas modifier le header généré à la main. La CI bloque si la copie générée
ne correspond pas exactement à `VERSION` ou si la version est invalide.
La compilation force l'inclusion de ce header sans modifier le `.ino` et dépose
un `version.json` associant version, SHA Git, état modifié/non modifié, empreinte
SHA-256 du sketch, FQBN et versions de l'outillage aux binaires. La macro est disponible à la compilation ; elle n'est pas encore
exposée par HTTP ou affichée au démarrage, et sa présence dans le binaire n'est
pas garantie tant que le sketch ne l'utilise pas. L'identification d'un firmware
déployé reste donc une amélioration possible, pas une fonction prétendue acquise.

## GitHub Actions

Le workflow `ESP32Cam firmware CI` se déclenche sur les push et PR visant
`CYCLOPE_GEN3_ESP32CAMSW`, seulement si ce sous-projet ou son workflow change.
Un lancement manuel reste possible depuis l'onglet Actions.

- **Bloquant** : version sans dérive, installation du core épinglé, compilation.
- **Indicatif** : cppcheck, profil warning/style/performance/portability,
  inconclusive, C++17 et tailles `unix32` adaptées à cette analyse ESP32 32 bits.
  Ce profil ne modélise pas toute l'ABI Xtensa. Les headers SDK ne sont pas
  analysés : l'absence de finding n'est pas une preuve d'absence de défaut.
- **Artefacts** : ELF, BIN, métadonnées de version et rapports, conservés 14 jours.

La présence de `continue-on-error` ne concerne que cppcheck, jamais la compilation.
Consulter aussi le rapport cppcheck : un job vert ne signifie pas que ce rapport
est vide, ni que l'analyse a pu aboutir. Les secrets Wi-Fi actuellement présents
dans le sketch peuvent aussi être présents dans les binaires ; les remplacer et
les renouveler avant toute utilisation sur un réseau réel.

## Tests et limites

Pas de tests unitaires firmware ajoutés : `initCamera`, `handleRoot`,
`handleCapture`, `setup` et `loop` dépendent tous des entrées/sorties ESP32,
Arduino ou réseau. Il n'y a pas de parsing ou de logique pure à inclure dans un
harness natif Unity sans introduire un refactoring ou des mocks de SDK.
Tester seulement ces mocks ne démontrerait pas le comportement du firmware.

Prochaine étape pédagogique proposée : séparer la configuration et la politique
de reconnexion/temporisation des appels Wi-Fi/caméra/HTTP, puis tester la logique
pure avec Unity dans un environnement PlatformIO `native`. Cette recommandation
n'impose pas de réécriture du travail de Viet-An.

Aucune validation sur carte, caméra, réseau local, watchdog, PSRAM ou brochage
n'est réalisée. Ni QEMU ni test matériel. À terme, un runner auto-hébergé muni
d'une carte USB pourrait ajouter les essais de démarrage et de capture.
