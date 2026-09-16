package com.yumeka.anime.engine

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yumeka.anime.engine.fragments.EditorFragment
import com.yumeka.anime.engine.fragments.HomeFragment
import com.yumeka.anime.engine.fragments.SplashFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) showSplashScreen()
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
