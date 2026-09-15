package com.aniforge.engine.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import com.yumeka.anime.engine.R

class FloatingHub(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private lateinit var btnMain: ImageButton
    private lateinit var btnLayers: ImageButton
    private lateinit var btnTimeline: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnExport: ImageButton

    private var isExpanded = false

    var onLayersClick: (() -> Unit)? = null
    var onTimelineClick: (() -> Unit)? = null
    var onUndoClick: (() -> Unit)? = null
    var onRedoClick: (() -> Unit)? = null
    var onPlayClick: (() -> Unit)? = null
    var onExportClick: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.component_floating_hub, this, true)
        bindViews()
        setupListeners()
        collapseActions(animate = false)
    }

    private fun bindViews() {
        btnMain     = findViewById(R.id.btn_hub_main)
        btnLayers   = findViewById(R.id.btn_hub_layers)
        btnTimeline = findViewById(R.id.btn_hub_timeline)
        btnUndo     = findViewById(R.id.btn_hub_undo)
        btnRedo     = findViewById(R.id.btn_hub_redo)
        btnPlay     = findViewById(R.id.btn_hub_play)
        btnExport   = findViewById(R.id.btn_hub_export)
    }

    private fun setupListeners() {
        btnMain.setOnClickListener { toggleExpand() }

        btnLayers.setOnClickListener {
            onLayersClick?.invoke()
                ?: Toast.makeText(context, "Layers", Toast.LENGTH_SHORT).show()
        }
        btnTimeline.setOnClickListener {
            onTimelineClick?.invoke()
                ?: Toast.makeText(context, "Timeline", Toast.LENGTH_SHORT).show()
        }
        btnUndo.setOnClickListener {
            onUndoClick?.invoke()
                ?: Toast.makeText(context, "Undo", Toast.LENGTH_SHORT).show()
        }
        btnRedo.setOnClickListener {
            onRedoClick?.invoke()
                ?: Toast.makeText(context, "Redo", Toast.LENGTH_SHORT).show()
        }
        btnPlay.setOnClickListener {
            onPlayClick?.invoke()
                ?: Toast.makeText(context, "Play Preview", Toast.LENGTH_SHORT).show()
        }
        btnExport.setOnClickListener {
            onExportClick?.invoke()
                ?: Toast.makeText(context, "Export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        if (isExpanded) expandActions() else collapseActions()
        btnMain.animate().rotation(if (isExpanded) 45f else 0f).setDuration(250).start()
    }

    private fun expandActions(animate: Boolean = true) {
        val actions = listOf(btnLayers, btnTimeline, btnUndo, btnRedo, btnPlay, btnExport)
        actions.forEachIndexed { index, btn ->
            btn.visibility = View.VISIBLE
            if (animate) {
                btn.alpha = 0f
                btn.scaleX = 0.5f
                btn.scaleY = 0.5f
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btn, "alpha",  0f, 1f),
                        ObjectAnimator.ofFloat(btn, "scaleX", 0.5f, 1f),
                        ObjectAnimator.ofFloat(btn, "scaleY", 0.5f, 1f)
                    )
                    startDelay = index * 40L
                    duration   = 200
                    start()
                }
            }
        }
    }

    private fun collapseActions(animate: Boolean = true) {
        val actions = listOf(btnLayers, btnTimeline, btnUndo, btnRedo, btnPlay, btnExport)
        if (animate) {
            actions.forEach { btn ->
                btn.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150)
                    .withEndAction { btn.visibility = View.GONE }.start()
            }
        } else {
            actions.forEach { it.visibility = View.GONE }
        }
    }
}
