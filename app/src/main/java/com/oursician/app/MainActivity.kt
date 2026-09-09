package com.oursician.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oursician.app.home.HomeScreen
import com.oursician.app.tablature.TablatureScreen
import com.oursician.app.tablature.library.TabLibraryScreen
import com.oursician.app.tuner.TunerScreen
import com.oursician.app.ui.theme.OursicianTheme

private enum class Screen { HOME, TUNER, TAB_LIBRARY, TAB_VIEW }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemNavigationBar()
        setContent {
            OursicianTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                var selectedSongId by rememberSaveable { mutableStateOf<String?>(null) }
                BackHandler(enabled = screen != Screen.HOME) {
                    screen = if (screen == Screen.TAB_VIEW) Screen.TAB_LIBRARY else Screen.HOME
                }

                // The scrolling tab staff is unreadable in portrait and its auto-scroll timing was
                // only ever tuned/tested in landscape — lock to landscape while reading a
                // tablature, and let every other screen rotate freely as before.
                LaunchedEffect(screen) {
                    requestedOrientation = if (screen == Screen.TAB_VIEW) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onSelectTuner = { screen = Screen.TUNER },
                        onSelectTablature = { screen = Screen.TAB_LIBRARY },
                    )
                    Screen.TUNER -> TunerScreen(onBack = { screen = Screen.HOME })
                    Screen.TAB_LIBRARY -> TabLibraryScreen(
                        onBack = { screen = Screen.HOME },
                        onSelectSong = { song ->
                            selectedSongId = song.id
                            screen = Screen.TAB_VIEW
                        },
                    )
                    Screen.TAB_VIEW -> TablatureScreen(
                        songId = requireNotNull(selectedSongId),
                        onBack = { screen = Screen.TAB_LIBRARY },
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system can re-show the navigation bar when the window regains focus
        // (e.g. after unlocking the screen), so it needs to be re-hidden each time.
        if (hasFocus) hideSystemNavigationBar()
    }

    private fun hideSystemNavigationBar() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
