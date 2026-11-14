# Nick Craft

Jogo voxel 3D open-source para Android (Versão `0.03`) com Launcher integrado ao GitHub (Launcher `1.1`).

## Downloads (`apk/`)

- **`NickCraft-Launcher.apk`** — Launcher oficial (`1.1`) que busca versões e novidades no GitHub para baixar e iniciar o jogo dentro dele.
- **`NickCraft-0.03.apk`** — APK independente do jogo (`0.03`) para jogar direto sem precisar do Launcher.

## Recursos

- **Launcher 1.1:** abas de **Versões**, **Novidades** (notas de cada atualização direto do GitHub) e **Commits** de `nickgamerr44/Nick-Craft`.
- **Jogo 3D (0.03):** mundo voxel procedural, ícones próprios em pixel art para cada item, multiplayer online à distância (transforme seu mundo em servidor com limite de 5 jogadores), Sobrevivência e Criativo, inventário, criação 3x3 e salvamento local de mundos.

## Estrutura

- `app/` — Launcher Android + Jogo + Multiplayer Online
- `game/` — APK independente do jogo + Multiplayer Online
- `gamecore/` — Motor 3D, blocos, ícones, multiplayer online, inventário e menus
- `apk/` — APKs prontos para instalar
- `CHANGELOG.md` — Notas de atualização lidas na aba Novidades

## Como compilar

```bash
gradle :app:assembleDebug :game:assembleDebug
```
