package com.yumeka.anime.engine.anime

/**
 * CharacterRig - representa um personagem anime com suas animações,
 * expressões faciais e estados.
 */
data class AnimationClip(
    val name: String,
    val spriteSheetAsset: String,
    val frameCount: Int,
    val fps: Int = 24,
    val loop: Boolean = true
)

enum class FacialExpression {
    NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, BLUSHING, DETERMINED
}

data class CharacterState(
    val currentClip: String,
    val expression: FacialExpression,
    val positionX: Float = 0.0f,
    val positionY: Float = 0.0f,
    val scale: Float = 1.0f,
    val flipHorizontal: Boolean = false
)

class CharacterRig(
    val name: String,
    val displayName: String
) {
    private val clips: MutableMap<String, AnimationClip> = mutableMapOf()
    private var state: CharacterState = CharacterState("idle", FacialExpression.NEUTRAL)

    fun registerClip(clip: AnimationClip) {
        clips[clip.name] = clip
    }

    fun playClip(name: String) {
        require(clips.containsKey(name)) { "Clip '$name' não registrado para ${this.name}" }
        state = state.copy(currentClip = name)
    }

    fun setExpression(expr: FacialExpression) {
        state = state.copy(expression = expr)
    }

    fun moveTo(x: Float, y: Float) {
        state = state.copy(positionX = x, positionY = y)
    }

    fun flip(horizontal: Boolean) {
        state = state.copy(flipHorizontal = horizontal)
    }

    fun getState(): CharacterState = state
    fun getClip(name: String): AnimationClip? = clips[name]
    fun getCurrentClip(): AnimationClip? = clips[state.currentClip]
}
