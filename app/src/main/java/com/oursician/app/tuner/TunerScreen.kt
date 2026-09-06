package com.oursician.app.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import kotlin.math.roundToInt
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EyebrowBar(isListening = hasMicPermission && uiState.isListening)
            Spacer(modifier = Modifier.height(20.dp))

            if (!hasMicPermission) {
                PermissionContent(onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
            } else {
                val note = uiState.detectedNote
                val inTune = note != null && abs(note.cents) < 5
                val tuneColor = if (inTune) OursicianTeal else OursicianAmber

                TunerHero(
                    noteName = note?.let { "${it.name}${it.octave}" } ?: "—",
                    cents = note?.cents,
                    tuneColor = tuneColor,
                    frequencyText = note?.let { "%.2f Hz".format(it.frequency) } ?: "",
                )
                Spacer(modifier = Modifier.height(20.dp))
                StringCard(
                    closestString = uiState.closestString,
                    cents = note?.cents,
                    tuneColor = tuneColor,
                )
            }
        }
    }
}

@Composable
private fun EyebrowBar(isListening: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundwaveIcon(
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                iconSize = 16.dp,
            )
            Text(
                text = "OURSICIAN · ACCORDEUR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
        if (isListening) {
            Surface(
                shape = RoundedCornerShape(50),
                color = OursicianTeal.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MicIcon(tint = OursicianTeal, iconSize = 12.dp)
                    Text(
                        text = "EN ÉCOUTE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = OursicianTeal,
                    )
                }
            }
        }
    }
}

@Composable
private fun TunerHero(
    noteName: String,
    cents: Double?,
    tuneColor: Color,
    frequencyText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(tuneColor.copy(alpha = 0.30f), Color.Transparent),
                        ),
                    ),
            )
            Text(
                text = noteName,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (cents != null) {
            Text(
                text = cents.roundToInt().let { if (it > 0) "+$it" else "$it" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = tuneColor,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        CentsGaugePill(
            cents = cents ?: 0.0,
            tuneColor = tuneColor,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = frequencyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun CentsGaugePill(cents: Double, tuneColor: Color, modifier: Modifier = Modifier) {
    val clampedCents = cents.coerceIn(-50.0, 50.0)
    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val tickColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)

    Canvas(modifier = modifier.height(24.dp)) {
        val trackHeight = 6.dp.toPx()
        val trackY = size.height / 2f
        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = cornerRadius,
        )

        val bandWidth = size.width * 0.10f
        drawRoundRect(
            color = tuneColor.copy(alpha = 0.28f),
            topLeft = Offset(size.width / 2f - bandWidth / 2f, trackY - trackHeight / 2f),
            size = Size(bandWidth, trackHeight),
            cornerRadius = cornerRadius,
        )

        drawLine(
            color = tickColor,
            start = Offset(size.width / 2f, trackY - 7.dp.toPx()),
            end = Offset(size.width / 2f, trackY + 7.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )

        val knobX = ((clampedCents + 50.0) / 100.0).toFloat() * size.width
        drawCircle(color = tuneColor.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = Offset(knobX, trackY))
        drawCircle(color = tuneColor, radius = 8.dp.toPx(), center = Offset(knobX, trackY))
    }
}

@Composable
private fun StringCard(
    closestString: GuitarString?,
    cents: Double?,
    tuneColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // STANDARD_GUITAR_TUNING is already ordered from the lowest string (E2) to the highest (E4).
            STANDARD_GUITAR_TUNING.forEachIndexed { index, string ->
                val isActive = string == closestString
                val isLast = index == STANDARD_GUITAR_TUNING.lastIndex
                val inTune = cents != null && abs(cents) < 5
                val textColor = if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                }
                val freqColor = if (isActive) tuneColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                val rowBackground = if (isActive) tuneColor.copy(alpha = 0.10f) else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isActive) tuneColor else Color.Transparent),
                        )
                        Text(
                            text = string.label,
                            style = if (isActive) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = textColor,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "%.2f Hz".format(string.frequency),
                            style = MaterialTheme.typography.bodySmall,
                            color = freqColor,
                        )
                        if (isActive && !inTune && cents != null) {
                            TriangleIcon(
                                color = freqColor,
                                pointingUp = cents < 0,
                                iconSize = 9.dp,
                            )
                        }
                    }
                }
                if (isActive || !isLast) {
                    HorizontalDivider(
                        color = if (isActive) tuneColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = if (isActive) 2.dp else 1.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionContent(onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            MicIcon(tint = OursicianAmber, iconSize = 36.dp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "L'accès au micro est nécessaire pour accorder ta guitare.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRequestPermission, shape = RoundedCornerShape(50)) {
            Text("Autoriser le micro")
        }
    }
}

@Composable
private fun SoundwaveIcon(tint: Color, iconSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(iconSize)) {
        val barWidth = 1.8.dp.toPx()
        val heights = listOf(0.5f, 0.85f, 1f, 0.65f)
        val gap = size.width / heights.size
        heights.forEachIndexed { index, heightFraction ->
            val x = gap * index + gap / 2f
            val barHeight = size.height * heightFraction
            drawLine(
                color = tint,
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MicIcon(tint: Color, iconSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = size.width * 0.12f
        val capsuleWidth = size.width * 0.34f
        val capsuleHeight = size.height * 0.55f
        val capsuleLeft = (size.width - capsuleWidth) / 2f

        drawRoundRect(
            color = tint,
            topLeft = Offset(capsuleLeft, 0f),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
        )
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.1f, capsuleHeight * 0.35f),
            size = Size(size.width * 0.8f, size.height * 0.55f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(size.width / 2f, capsuleHeight * 0.35f + size.height * 0.55f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun TriangleIcon(color: Color, pointingUp: Boolean, iconSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(iconSize).rotate(if (pointingUp) 0f else 180f)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = color)
    }
}
