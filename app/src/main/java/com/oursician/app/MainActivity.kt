package com.oursician.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.oursician.app.home.HomeScreen
import com.oursician.app.tablature.TablatureScreen
import com.oursician.app.tuner.TunerScreen
import com.oursician.app.ui.theme.OursicianTheme

private enum class Screen { HOME, TUNER, TABLATURE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OursicianTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onSelectTuner = { screen = Screen.TUNER },
                        onSelectTablature = { screen = Screen.TABLATURE },
                    )
                    Screen.TUNER -> TunerScreen(onBack = { screen = Screen.HOME })
                    Screen.TABLATURE -> TablatureScreen(onBack = { screen = Screen.HOME })
                }
            }
        }
    }
}
