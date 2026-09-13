package com.aniforge.engine.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.yumeka.anime.engine.R

class FloatingHub(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    init {
        LayoutInflater.from(context).inflate(R.layout.component_floating_hub, this, true)
        setupListeners()
    }
    
    private fun setupListeners() {
        // Implementar listeners dos botoes flutuantes
    }
}
