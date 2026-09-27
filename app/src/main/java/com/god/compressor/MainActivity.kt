package com.god.compressor

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.god.compressor.ui.GodTheme
import com.god.compressor.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Edge-to-edge untuk Android 15+ & kompatibel 14
            enableEdgeToEdge()
            setContent {
                GodTheme {
                    Surface(Modifier.fillMaxSize()) {
                        HomeScreen(vm)
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("GOD", "Crash di onCreate", t)
            throw t
        }
    }
}