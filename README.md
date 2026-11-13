# Nick Craft

Jogo voxel 3D open-source para Android (Versão `0.01`) com Launcher integrado ao GitHub (Launcher `1.1`).

## Downloads (`apk/`)

- **`NickCraft-Launcher.apk`** — Launcher oficial (`1.1`) que busca versões e novidades no GitHub para baixar e iniciar o jogo dentro dele.
- **`NickCraft-0.01.apk`** — APK independente do jogo (`0.01`) para jogar direto sem precisar do Launcher.

## Recursos

- **Launcher 1.1:** abas de **Versões**, **Novidades** (notas de cada atualização direto do GitHub) e **Commits** de `nickgamerr44/Nick-Craft`.
- **Jogo 3D (0.01):** mundo voxel procedural, modos Sobrevivência e Criativo, inventário, criação 3x3 e salvamento local de mundos.

## Estrutura

- `app/` — Launcher Android
- `game/` — APK independente do jogo
- `gamecore/` — Motor 3D, blocos, inventário e menus do jogo
- `apk/` — APKs prontos para instalar
- `CHANGELOG.md` — Notas de atualização lidas na aba Novidades

## Como compilar

```bash
gradle :app:assembleDebug :game:assembleDebug
```
