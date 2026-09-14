# 🎢 Yumeka Animations Studio Engine

> Engine nativa Android para reprodução e criação de animções estilo anime.

![Build APK](https://github.com/CodeBot-AI-REd/Yumeka-Animations-Studio-Engine/actions/workflows/build-apk.yml/badge.svg)(https://github.com/CodeBot-AI-Red/Yumeka-Animations-Studio-Engine/actions)

---

## 📈 Estrutura do Projeto

```
Yumeka-Animations-Studio-Engine/
├── android/
│   ├── app/
│      ├── src/

├── engine/
│   ├── anime/
│   │   ├── AnimePlayer.kt
│   │   ├── SpriteSheet.kt
│   │   ├── SceneDirector.kt
│   │   └── CharacterRig.kt
│   ├── core/
│   ├── renderer/
│   ├── scene/
│   └── gameobject/

└── .github/workflows/
      └ build-apk.yml
```

---

## 😈 Modulos principais

| Modulo | Descricao |
|-----------------|-------------------------------------------|
| `AnimePlayer` | Reproduz frames a 24 fps, suporta loop, pause e seek |
| `SpriteSheet` | Extrai e desenha frames de spritesheets em grid |
| `SceneDirector` | Orquestra sequencia de cenas com dialogo e personagens |
| `CharacterRig` | Personagem com clips, expressoes faciais e posicionamento |

---

## 🔗 CI/CD - Geracao de APK

O workflow `.github/workflows/build-apk.yml` roda automaticamente em:

- 🔁 Push na branch `main`
- 📢 Pull Request para `main`
- 🤕 Dispare manual via workflow_dispatch (debug ou release)

O APK gerado e salvo como artefato no GitHub Actions por 30 dias (debug) ou 90 dias (release).

---

## 💩 Dependencias principais

- **Lottie Android** - animacoes vetoriais estilo anime
- **Coil** - carregamento de sprites e backgrounds
- **Kotlin Coroutines** - animacoes assincronas
- **Android ViewBinding** - UI fluida e type-safe

---

## 🔅 Build local

```bash
# APK Debug
./gradlew :app:assembleDebug

# APK Release
./gradlew :app:assembleRelease
```

---

> Build por Yumeka Studio | Powered by Yumeka Animations Engine
