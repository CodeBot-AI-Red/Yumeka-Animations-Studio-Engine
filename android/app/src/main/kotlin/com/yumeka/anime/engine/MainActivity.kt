package com.yumeka.anime.engine

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.yumeka.anime.engine.fragments.EditorFragment
import com.yumeka.anime.engine.fragments.HomeFragment
import com.yumeka.anime.engine.fragments.SplashFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()
        if (savedInstanceState == null) showSplashScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    private fun showSplashScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SplashFragment())
            .commit()
    }

    fun showHomeScreen() {
        if (isFinishing || isDestroyed) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment(), "HOME")
            .setReorderingAllowed(true)
            .commitAllowingStateLoss()
    }

    /**
     * Abre o editor YASE para o projeto informado.
     * Adiciona na backstack para que o botao Voltar retorne ao Home.
     */
    fun openEditor(projectName: String) {
        if (isFinishing || isDestroyed) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EditorFragment.newInstance(projectName), "EDITOR")
            .addToBackStack("editor")
            .setReorderingAllowed(true)
            .commitAllowingStateLoss()
    }
}
