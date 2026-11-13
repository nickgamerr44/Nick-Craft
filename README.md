# Nick Craft

Projeto open-source de jogo voxel 3D para Android desenvolvido em **Kotlin**, **Jetpack Compose** e **OpenGL ES**, acompanhado de um **Launcher** conectado diretamente ao repositório [`nickgamerr44/Nick-Craft`](https://github.com/nickgamerr44/Nick-Craft).

---

## APKs Disponíveis

Os builds pré-compilados ficam na pasta [`apk/`](apk/):

| Arquivo | Módulo | Package ID | Função | Permissões |
| :--- | :--- | :--- | :--- | :--- |
| `apk/NickCraft-Launcher.apk` | `:app` | `com.aistudio.craftcraft.vzkxqy` | Launcher oficial que lista e baixa versões direto do GitHub e inicia o jogo internamente. | `INTERNET` |
| `apk/NickCraft-0.00.apk` | `:game` | `com.aistudio.nickcraft.game` | APK independente do jogo (**Versão 0.00**) para jogar direto sem precisar do Launcher. | Nenhuma |

> **Observação de instalação (Sideload):** como os APKs são distribuídos fora da Google Play Store, o Google Play Protect pode exibir o aviso padrão de desenvolvedor desconhecido na primeira instalação. Para prosseguir, toque em **Mais detalhes** → **Instalar assim mesmo**.

---

## Como o Launcher Funciona

- Sincroniza automaticamente as versões publicadas no repositório `nickgamerr44/Nick-Craft` via API REST do GitHub, com cache local em disco e **fallback automático para os feeds Atom** (`releases.atom`, `tags.atom`, `commits.atom`) caso a API atinja limite de requisições (HTTP 403/429).
- Permite **baixar**, **iniciar (`Jogar`)** e **remover** versões diretamente pelos cartões da lista de versões, salvando os pacotes internamente em `filesDir/nickcraft_versions/`.
- Lista versões provenientes de **Releases do GitHub**, pasta **`/versions`**, **Tags Git** ou snapshot da branch principal (arquivos da pasta `/apk` são ignorados na lista do Launcher).
- Exibe a aba **Commits** com o histórico recente de alterações do repositório.

---

## Estrutura Multi-Módulo

```text
.
├── app/        # Aplicativo Launcher (UI do Launcher, cliente GitHub, gerenciador de versões)
├── game/       # Aplicativo Standalone do jogo (abre direto no menu principal do Nick Craft)
├── gamecore/   # Biblioteca compartilhada: motor voxel 3D, física, mundo, inventário, áudio e UI do jogo
└── apk/        # Builds APK prontos (Launcher e Jogo independente)
```

---

## Recursos do Jogo (`0.00`)

- **Motor Voxel 3D** em OpenGL ES com atlas de texturas pixeladas e modos de câmera em primeira/terceira pessoa.
- **Modos de Jogo:** Sobrevivência e Criativo.
- **Criação e Inventário:** receitas com bancada 3x3 e inventário integrado.
- **Mundos Salvos:** criação com seed personalizada, carregamento e exclusão de mundos no armazenamento local do dispositivo.

---

## Como Compilar

Requisitos: **Android SDK** (`compileSdk = 36`, `minSdk = 24`) e **JDK 11+**.

### Build de Debug
```bash
gradle :app:assembleDebug :game:assembleDebug
```

### Build de Release Assinado
Configure as variáveis de ambiente apontando para sua keystore (`.jks`, alias `upload`):
```bash
KEYSTORE_PATH=/caminho/para/keystore.jks \
STORE_PASSWORD=sua_senha \
KEY_PASSWORD=sua_senha \
gradle :app:assembleRelease :game:assembleRelease
```

Os APKs gerados ficam em:
- `app/build/outputs/apk/release/app-release.apk`
- `game/build/outputs/apk/release/game-release.apk`

### Testes Unitários
```bash
gradle :app:testDebugUnitTest
```
