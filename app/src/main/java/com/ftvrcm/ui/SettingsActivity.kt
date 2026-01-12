package com.ftvrcm.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import com.ftvrcm.data.SettingsStore

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore(this).initializeDefaultsIfNeeded()

        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }
    }
}
