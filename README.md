# ðŸŽ Yumeka Animations Studio Engine

> Engine nativa Android para reproduÃ§Ã£o e criaÃ§Ã£o de animÃ§Ãµes estilo anime.

![Build APK](https://github.com/CodeBot-AI-REd/Yumeka-Animations-Studio-Engine/actions/workflows/build-apk.yml/badge.svg)(https://github.com/CodeBot-AI-Red/Yumeka-Animations-Studio-Engine/actions)

---

## ðŸ“ˆ Estrutura do Projeto

```
Yumeka-Animations-Studio-Engine/
â”œâ”€â”€ android/                   # Ap Android host
â”‚   â”œâ”€â”€ app/
â”‚      â”œâ”€â”€ src/                # Sources Kotlin

â”œâ”€â”€ engine/                  # Módulos da Engine
â”‚   â”œâ”€â”€ anime/                # ðŸŽ¢ MÃ³dulos de anime
â”‚   â”‚   â”œâ”€â”€ AnimePlayer.kt       # Reprodutor de frames
â”‚   â”‚   â”œâ”€â”€ SpriteSheet.kt       # Gerenciador de spritesheets
â”‚   â”‚   â”œâ”€â”€ SceneDirector.kt     # Orquestrador de cenas
â”‚   â”‚   â””â”€â”€ CharacterRig.kt      # Sistema de personagens
â”‚   â”œâ”€â”€ core/                  # Loop principal da engine
â”‚   â”œâ”€â”€ renderer/              # RenderizaÃ§Ã£o
â”‚   â”œâ”€â”€ scene/                 # Gerenciamento de cenas
â”‚   â”œâ”€â”€ gameobject/           # Entidades
â”‚   â””â”€â”€ transform/             # TransformaÃ§Ãµes 2D

â””â”€â”€ .github/workflows/
      â””  build-apk.yml           # CI/CD \u2192 Gera APK automaticamente
```

---

## ðŸ˜ˆ MÃ³dulos principais

| MÃ³dulo | DescriÃ§Ã£o |
|-----------------|-----------------------------------------|
| `AnimePlayer` | Reproduz frames a 24 fps, suporta loop, pause e seek |
| `SpriteSheet` | Extrai e desenha frames de spritesheets em grid |
| `SceneDirector` | Orquestra sequÃªncia de cenas com diÃ¡logo e personagens |
| `CharacterRig` | Personagem com clips, expressÃµes faciais e posicionamento |

---

## ðŸ’— CI/CD - GeraÃ§Ã£o de APK

O workflow `.github/workflows/build-apk.yml` roda automaticamente em:

- ðŸ” Push na branch `main`
- ðŸ“  Pull Request para `main`
- ðŸ¤• Disparo manual via workflow_dispatch (debug ou release)

O APK gerado Ã© salvo como artefato no GitHub Actions por 30 dias (debug) ou 90 dias (release).

---

## ðŸ’© DependÃªncias principais

- **Lottie Android** â€” animaÃ§Ãµes vetoriais estilo anime
- **Coil** â€” carregamento de sprites e backgrounds
- **Kotlin Coroutines** â€” animaÃ§Ãµes assÃ­ncronas sem travQr
- **Android ViewBinding** â€” UI fluida e type-safe

---

## ðŸ”… Build local

```bash
# APK Debug
./gradlew :android:app:assembleDebug

# APK Release
./gradlew :android:app:assembleRelease
```

---

> Build por Yumeka Studio â”‚ Powered by Yumeka Animations Engine
