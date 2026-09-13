package com.yumeka.anime.engine.anime

/**
 * SceneDirector - orquestra cenas de um episódio anime.
 * Cada cena tem um nome, duração, diálogos e anímações.
 */
data class AnimeScene(
    val name: String,
    val durationMs: Long,
    val backgroundAsset: String,
    val characters: List<String> = emptyList(),
    val dialogues: List<DialogueLine> = emptyList()
)

data class DialogueLine(
    val character: String,
    val text: String,
    val delayMs: Long = 0L
)

class SceneDirector {

    private val sceneQueue: ArrayDeque<AnimeScene> = ArrayDeque()
    private var currentScene: AnimeScene? = null
    private var onSceneStart: ((scene: AnimeScene) -> Unit)? = null
    private var onSceneEnd: ((scene: AnimeScene) -> Unit)? = null
    private var onEpisodeEnd: (() -> Unit)? = null

    fun addScene(scene: AnimeScene) { sceneQueue.addLast(scene) }

    fun startEpisode() {
        playNextScene()
    }

    private fun playNextScene() {
        if (sceneQueue.isEmpty()) {
            onEpisodeEnd?.invoke()
            return
        }
        currentScene = sceneQueue.removeFirst()
        currentScene?.let { scene ->
            onSceneStart?.invoke(scene)
            // Agenda fim da cena após duração
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                onSceneEnd?.invoke(scene)
                playNextScene()
            }, scene.durationMs)
        }
    }

    fun onSceneStart(listener: (AnimeScene) -> Unit) { onSceneStart = listener }
    fun onSceneEnd(listener: (AnimeScene) -> Unit) { onSceneEnd = listener }
    fun onEpisodeEnd(listener: () -> Unit) { onEpisodeEnd = listener }

    fun getCurrentScene(): AnimeScene? = currentScene
}
