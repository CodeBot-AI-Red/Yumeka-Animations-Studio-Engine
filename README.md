# 🎢 Yumeka Animations Studio Engine

> Engine nativa Android para reprodução e criação de animções estilo anime.

![Build APK](https://github.com/CodeBot-AI-REd/Yumeka-Animations-Studio-Engine/actions/workflows/build-apk.yml/badge.svg)(https://github.com/CodeBot-AI-Red/Yumeka-Animations-Studio-Engine/actions)

---

## 📈 Estrutura do Projeto

```
Yumeka-Animations-Studio-Engine/
├── android/                   # Ap Android host
│   ├── app/
│      ├── src/                # Sources Kotlin

├── engine/                  # M�dulos da Engine
│   ├── anime/                # 🎢 Módulos de anime
│   │   ├── AnimePlayer.kt       # Reprodutor de frames
│   │   ├── SpriteSheet.kt       # Gerenciador de spritesheets
│   │   ├── SceneDirector.kt     # Orquestrador de cenas
│   │   └── CharacterRig.kt      # Sistema de personagens
│   ├── core/                  # Loop principal da engine
│   ├── renderer/              # Renderização
│   ├── scene/                 # Gerenciamento de cenas
│   ├── gameobject/           # Entidades
│   └── transform/             # Transformações 2D

└── .github/workflows/
      └  build-apk.yml           # CI/CD \u2192 Gera APK automaticamente
```

---

## 😈 Módulos principais

| Módulo | Descrição |
|-----------------|-----------------------------------------|
| `AnimePlayer` | Reproduz frames a 24 fps, suporta loop, pause e seek |
| `SpriteSheet` | Extrai e desenha frames de spritesheets em grid |
| `SceneDirector` | Orquestra sequência de cenas com diálogo e personagens |
| `CharacterRig` | Personagem com clips, expressões faciais e posicionamento |

---

## 💗 CI/CD - Geração de APK

O workflow `.github/workflows/build-apk.yml` roda automaticamente em:

- 🔁 Push na branch `main`
- 📠 Pull Request para `main`
- 🤕 Disparo manual via workflow_dispatch (debug ou release)

O APK gerado é salvo como artefato no GitHub Actions por 30 dias (debug) ou 90 dias (release).

---

## 💩 Dependências principais

- **Lottie Android** — animações vetoriais estilo anime
- **Coil** — carregamento de sprites e backgrounds
- **Kotlin Coroutines** — animações assíncronas sem travQr
- **Android ViewBinding** — UI fluida e type-safe

---

## 🔅 Build local

```bash
# APK Debug
./gradlew :android:app:assembleDebug

# APK Release
./gradlew :android:app:assembleRelease
```

---

> Build por Yumeka Studio │ Powered by Yumeka Animations Engine
