# Third-party notices

## ashish-makes/chat-app

The visual layout and interaction structure of the web console were informed by
[ashish-makes/chat-app](https://github.com/ashish-makes/chat-app), an HTML/CSS/JavaScript
chat application released under the MIT License.

The project in this repository uses an independently rewritten Spring Boot backend,
native JavaScript client, sakura-themed visual system, accessibility behavior, and
QQ/OpenCode-specific functionality. The upstream project is not bundled as a runtime
dependency.

## OpenCode

OpenCode is installed and run in a separate Docker container. See
[anomalyco/opencode](https://github.com/anomalyco/opencode) for its license and
distribution terms.

## NapCatQQ

NapCat is used as the recommended OneBot v11 implementation. It is not bundled in
this repository.

## AOrbitron/Eridanus adapted assets

The following adapted assets are from
[AOrbitron/Eridanus](https://github.com/AOrbitron/Eridanus):

- `src/main/resources/static/entertainment/pigs/`: source path
  `run/group_fun/service/today_pig/resource/img`
- `src/main/resources/static/entertainment/tarot/`: source path
  `data/pictures/tarot/TarotImages`
- `src/main/resources/static/entertainment/mfaces/`: source path
  `data/pictures/Mface`
- `src/main/resources/entertainment/emoji-kitchen.tsv.gz`: derived from the
  upstream `data/dataBase/emoji_metadata.db` combination metadata

Those assets are licensed under the
[Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](https://creativecommons.org/licenses/by-nc-sa/4.0/)
(CC BY-NC-SA 4.0). They are included for non-commercial use only and remain under
that license. Attribution and share-alike terms apply to modified versions of the
assets. The project's MIT License does not apply to these assets. The full license
text is bundled at `src/main/resources/licenses/Eridanus-CC-BY-NC-SA-4.0.txt`.

Only selected, non-adult pig reaction images are included. No NSFW image sources,
adult modules, sexual-content search, or explicit upstream features are bundled.
The tarot and reaction images were manually reviewed before inclusion.
