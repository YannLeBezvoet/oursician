package com.oursician.app.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import com.oursician.app.ui.theme.OursicianAmber
import com.oursician.app.ui.theme.OursicianTeal

@SuppressLint("MissingPermission")
@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMicPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(hasMicPermission, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (hasMicPermission) viewModel.startListening()
                Lifecycle.Event.ON_STOP -> viewModel.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopListening()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!hasMicPermission) {
                Text(
                    text = "L'accès au micro est nécessaire pour accorder ta guitare.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Autoriser le micro")
                }
            } else {
                val note = uiState.detectedNote
                Text(
                    text = note?.let { "${it.name}${it.octave}" } ?: "—",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                StringList(
                    closestString = uiState.closestString,
                    cents = note?.cents,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                CentsGauge(
                    cents = note?.cents ?: 0.0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )
                Text(
                    text = note?.let { "%.1f Hz".format(it.frequency) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun StringList(
    closestString: GuitarString?,
    cents: Double?,
    modifier: Modifier = Modifier,
) {
    // STANDARD_GUITAR_TUNING is already ordered from the lowest string (E2) to the highest (E4).
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (string in STANDARD_GUITAR_TUNING) {
            val isActive = string == closestString
            val color = when {
                !isActive -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                cents != null && abs(cents) < 5 -> OursicianTeal
                else -> OursicianAmber
            }
            Text(
                text = "${string.label} · %.2f Hz".format(string.frequency),
                style = if (isActive) {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = color,
            )
        }
    }
}

@Composable
private fun CentsGauge(cents: Double, modifier: Modifier = Modifier) {
    val clampedCents = cents.coerceIn(-50.0, 50.0)
    val inTune = abs(cents) < 5
    val needleColor = if (inTune) OursicianTeal else OursicianAmber

    Canvas(modifier = modifier.height(48.dp)) {
        val centerX = size.width / 2f
        val trackY = size.height / 2f

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, trackY),
            end = Offset(size.width, trackY),
            strokeWidth = 4f,
        )
        drawLine(
            color = Color.White,
            start = Offset(centerX, trackY - 12f),
            end = Offset(centerX, trackY + 12f),
            strokeWidth = 4f,
        )

        val needleX = centerX + (clampedCents / 50.0).toFloat() * centerX
        drawCircle(color = needleColor, radius = 14f, center = Offset(needleX, trackY))
    }
}
