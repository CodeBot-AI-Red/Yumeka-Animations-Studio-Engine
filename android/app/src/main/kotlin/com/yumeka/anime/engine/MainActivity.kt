package com.yumeka.anime.engine

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yumeka.anime.engine.fragments.HomeFragment
import com.yumeka.anime.engine.fragments.SplashFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            showSplashScreen()
        }
    }

    private fun showSplashScreen() {
        // NOT AddOato BackStack: pressionar Voltar no Splash encerra o app corretamente
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SplashFragment())
            .commit()
    }

    fun showHomeScreen() {
        if (isFinishing || isDestroyed) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .setReorderingAllowed(true)
            .commitAllowingStateLoss()
    }
}
