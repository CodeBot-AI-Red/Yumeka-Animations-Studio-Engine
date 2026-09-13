package com.yumeka.anime.engine.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yumeka.anime.engine.MainActivity
import com.yumeka.anime.engine.R

class SplashFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val logo = view.findViewById<ImageView>(R.id.splash_logo)
        val subtitle = view.findViewById<TextView>(R.id.splash_subtitle)
        val status = view.findViewById<TextView>(R.id.splash_status)
        val version = view.findViewById<TextView>(R.id.splash_version)

        logo.alpha = 0f
        subtitle.alpha = 0f
        status.alpha = 0f
        version.alpha = 0f

        logo.animate().alpha(1f).duration = 800
        subtitle.animate().alpha(1f).setStartDelay(400).duration = 800
        status.animate().alpha(1f).setStartDelay(800).duration = 800
        version.animate().alpha(1f).setStartDelay(1200).duration = 800

        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                (activity as? MainActivity)?.showHomeScreen()
            }
        }, 3000)
    }
}
