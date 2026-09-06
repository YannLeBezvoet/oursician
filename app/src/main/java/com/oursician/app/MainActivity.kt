package com.oursician.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oursician.app.tuner.TunerScreen
import com.oursician.app.ui.theme.OursicianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OursicianTheme {
                TunerScreen()
            }
        }
    }
}
