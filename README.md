# 🎢 Yumeka Animations Studio Engine

> Engine nativa Android para reproducao e criacao de animacoes estilo anime.

![Build APK](https://github.com/CodeBot-AI-Red/Yumeka-Animations-Studio-Engine/actions/workflows/build-apk.yml/badge.svg)

## Yumeka Studio (editor)

Editor totalmente novo, adaptavel a celular (retrato e paisagem) e tablet:

- **Desenho quadro a quadro**: pincel, lapis, marcador, borracha, balde de tinta, linha, retangulo, elipse, conta-gotas, zoom/pan com dois dedos, desfazer/refazer, papel cebola.
- **Camadas**: visibilidade, opacidade, ordem, frente/atras dos personagens, copiar do quadro anterior.
- **Linha do tempo**: miniaturas, adicionar/duplicar/mover/excluir quadros, duracao por quadro (×1–×4), reproducao em loop, 6/8/12/24 fps.
- **Personagens anime**: rig com cabeca, tronco, bracos e pernas; expressoes, piscar, boca (lip-sync), cabelo/olhos/roupa/pele; poses-chave com interpolacao automatica; movimentos prontos (andar, acenar, pular, falar, piscar).
- **Cenarios**: ceu, por do sol, noite estrelada, cidade, sala de aula, sakura (animados) e chroma key.
- **Camera**: zoom/pan com chaves, movimentos prontos e tremor.
- **Exportacao**: video MP4 1280×720, GIF animado e sequencia PNG (pasta `Exportados` do projeto).

Codigo em `android/app/src/main/kotlin/com/yumeka/anime/engine/studio/`.

## Editor de episodios

Botao **🎬 Editar episódio** no topo do estudio: pre-visualizacao, linha do tempo multi-faixa (arrastar, mover,
redimensionar, cortar, duplicar, excluir), falas (voz sintetizada ou arquivo), audio, musica, efeitos sonoros,
efeitos visuais, imagens, videos, texto, introducao/desfecho, reorganizar cenas, desfazer/refazer, salvamento
automatico local e exportacao (PNG 12 fps ou pacote .zip). Codigo em `.../engine/episode/`.

## Quadros com IA (no aparelho)

Botao **✨ Gerar quadro com IA**: DreamShaper XL v2 Turbo (GGUF) para gerar e Moondream2 para analisar,
baixados somente quando o usuario pede. Os motores nativos ainda precisam ser integrados — veja
`android/app/src/main/cpp/ai/README.md`. Codigo em `.../engine/ai/`.

---

> Build por Yumeka Studio | Powered by Yumeka Animations Engine
