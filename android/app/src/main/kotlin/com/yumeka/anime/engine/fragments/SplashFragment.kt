package com.yumeka.anime.engine.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.yumeka.anime.engine.MainActivity
import com.yumeka.anime.engine.R

class SplashFragment : Fragment() {

    private var navHandler: Handler? = null
    private var navRunnable: Runnable? = null

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

        // Entrada: fade-in + leve scale up
        logo.alpha = 0f
        logo.scaleX = 0.85f
        logo.scaleY = 0.85f
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(900)
            .start()

        // Navegar para Home após 3 segundos: fade-out suave antes
        val navRunnable = Runnable {
            if (!isAdded) return@Runnable
            logo.animate()
                .alpha(0f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(600)
                .withEndAction {
                    if (isAdded) {
                        (activity as? MainActivity)?.showHomeScreen()
                    }
                }
                .start()
        }
        this.navRunnable = navRunnable
        navHandler = Handler(Looper.getMainLooper()).also {
            it.postDelayed(navRunnable, 3000)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancelar timer se o fragment for destrído
        navRunnable?.let { navHandler?.removeCallbacks(it) }
    }
}
