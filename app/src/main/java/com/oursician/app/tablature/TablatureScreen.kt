package com.oursician.app.tablature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oursician.app.tuner.frequencyToNote
import com.oursician.app.tuner.midiNoteToFrequency
import kotlin.math.roundToInt

@Composable
fun TablatureScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "←",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp),
                )
                Text(
                    text = "OURSICIAN · TABLATURES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = DEMO_TABLATURE.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ScrollingTabStaff(tablature = DEMO_TABLATURE, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val STRING_ROW_HEIGHT = 26.dp
private val BEAT_COLUMN_WIDTH = 44.dp
private val MEASURE_DIVIDER_WIDTH = 13.dp // 6dp padding + 1dp line + 6dp padding
private val PLAYHEAD_INSET = 24.dp

private fun beatWidth(beat: Beat): Dp =
    BEAT_COLUMN_WIDTH * (beat.duration.fractionOfWhole / NoteDuration.QUARTER.fractionOfWhole).toFloat()

/**
 * Renders a [Tablature] that auto-scrolls right-to-left at a speed derived from its tempo,
 * Guitar Hero-style: notes travel toward the fixed playhead line instead of the reader
 * scrolling through a static page.
 */
@Composable
fun ScrollingTabStaff(tablature: Tablature, modifier: Modifier = Modifier) {
    // Lowest string (highest string number) on top, highest string (e.g. high E) on the bottom.
    val strings = tablature.tuning.strings.sortedByDescending { it.stringNumber }
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val noteColor = MaterialTheme.colorScheme.onSurface
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val playheadColor = MaterialTheme.colorScheme.secondary
    val staffHeight = STRING_ROW_HEIGHT * strings.size
    val density = LocalDensity.current

    val totalBeats = tablature.measures.sumOf { it.beats.size }
    val dividerCount = (tablature.measures.size - 1).coerceAtLeast(0)
    val contentWidth = tablature.measures.sumOf { measure -> measure.beats.sumOf { beatWidth(it).value.toDouble() } }.dp +
        MEASURE_DIVIDER_WIDTH * dividerCount
    val secondsPerQuarterNote = 60f / tablature.tempoBpm
    val pxPerSecond = with(density) { BEAT_COLUMN_WIDTH.toPx() } / secondsPerQuarterNote

    var isPlaying by remember { mutableStateOf(false) }
    var elapsedSeconds by remember(tablature) { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    val totalDurationSeconds = (viewportWidthPx + with(density) { contentWidth.toPx() }) / pxPerSecond

    LaunchedEffect(isPlaying, totalDurationSeconds) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                elapsedSeconds += (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
            }
            if (elapsedSeconds >= totalDurationSeconds) {
                isPlaying = false
                elapsedSeconds = 0f
                break
            }
        }
    }

    Column(modifier = modifier) {
        Row {
            StringLabelsColumn(strings = strings, rowHeight = STRING_ROW_HEIGHT)
            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(staffHeight)
                    .onSizeChanged { viewportWidthPx = it.width.toFloat() },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    strings.indices.forEach { rowIndex ->
                        val y = STRING_ROW_HEIGHT.toPx() * rowIndex + STRING_ROW_HEIGHT.toPx() / 2f
                        drawLine(color = lineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }

                val contentOffsetPx = (viewportWidthPx - pxPerSecond * elapsedSeconds).roundToInt()
                Row(
                    modifier = Modifier.offset { IntOffset(contentOffsetPx, 0) },
                ) {
                    tablature.measures.forEachIndexed { index, measure ->
                        MeasureNotes(measure = measure, strings = strings, noteColor = noteColor)
                        if (index != tablature.measures.lastIndex) {
                            MeasureDivider(color = dividerColor, height = staffHeight)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(x = PLAYHEAD_INSET)
                        .width(2.dp)
                        .height(staffHeight)
                        .background(playheadColor),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlayPauseButton(isPlaying = isPlaying, accentColor = playheadColor, onClick = { isPlaying = !isPlaying })
        }
    }
}

@Composable
private fun MeasureDivider(color: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(height)
            .background(color),
    )
}

@Composable
private fun StringLabelsColumn(strings: List<TuningString>, rowHeight: Dp, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        strings.forEach { string ->
            val name = frequencyToNote(midiNoteToFrequency(string.openStringMidiNote)).name
            Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun MeasureNotes(measure: Measure, strings: List<TuningString>, noteColor: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        measure.beats.forEach { beat ->
            Column(modifier = Modifier.width(beatWidth(beat)).fillMaxHeight()) {
                strings.forEach { string ->
                    val position = beat.positions.find { it.stringNumber == string.stringNumber }
                    Box(
                        modifier = Modifier.height(STRING_ROW_HEIGHT).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (position != null) {
                            Text(
                                text = position.fret.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = noteColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, accentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(accentColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseIcon(isPlaying = isPlaying, tint = Color.White, iconSize = 22.dp)
    }
}

@Composable
private fun PlayPauseIcon(isPlaying: Boolean, tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(iconSize)) {
        if (isPlaying) {
            val barWidth = size.width * 0.26f
            val corner = CornerRadius(1.5.dp.toPx())
            drawRoundRect(color = tint, topLeft = Offset(size.width * 0.14f, 0f), size = Size(barWidth, size.height), cornerRadius = corner)
            drawRoundRect(color = tint, topLeft = Offset(size.width * 0.60f, 0f), size = Size(barWidth, size.height), cornerRadius = corner)
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.2f, 0f)
                lineTo(size.width * 0.2f, size.height)
                lineTo(size.width * 0.9f, size.height / 2f)
                close()
            }
            drawPath(path, color = tint)
        }
    }
}

private val DEMO_TABLATURE = Tablature(
    title = "Gamme de démonstration",
    tuning = STANDARD_TUNING,
    tempoBpm = 90,
    measures = listOf(
        Measure(
            beats = listOf(
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 0))),
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 2))),
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 4))),
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 5))),
            ),
        ),
        Measure(
            beats = listOf(
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 7))),
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 9))),
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 11))),
                Beat(NoteDuration.QUARTER, listOf(FretPosition(stringNumber = 6, fret = 12))),
            ),
        ),
    ),
)
