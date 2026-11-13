# Nick Craft

Jogo voxel 3D open-source para Android (Versão `0.00`) com Launcher integrado ao GitHub.

## Downloads (`apk/`)

- **`NickCraft-Launcher.apk`** — Launcher oficial que busca e baixa as versões do repositório no GitHub para iniciar o jogo dentro dele.
- **`NickCraft-0.00.apk`** — APK do jogo independente para jogar direto sem precisar do Launcher.

## Recursos

- **Launcher:** lista Releases, Tags e commits do repositório `nickgamerr44/Nick-Craft`, com download direto e botão **Jogar** em cada versão baixada.
- **Jogo 3D:** mundo voxel procedural, modos Sobrevivência e Criativo, inventário, criação 3x3 e salvamento local de mundos.

## Estrutura

- `app/` — Launcher Android
- `game/` — APK independente do jogo
- `gamecore/` — Motor 3D, blocos, inventário e menus do jogo
- `apk/` — APKs prontos para instalar

## Como compilar

```bash
gradle :app:assembleDebug :game:assembleDebug
```
