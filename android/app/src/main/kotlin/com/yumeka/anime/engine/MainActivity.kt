package com.yumeka.anime.engine

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yumeka.anime.engine.fragments.SplashFragment
import com.yumeka.anime.engine.fragments.HomeFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            showSplashScreen()
        }
    }

    private fun showSplashScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SplashFragment())
            .addToBackStack(null)
            .commit()
    }

    fun showHomeScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .setReorderingAllowed(true)
            .commit()
    }
}
