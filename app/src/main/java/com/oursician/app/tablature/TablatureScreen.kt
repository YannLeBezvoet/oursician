package com.oursician.app.tablature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oursician.app.tuner.frequencyToNote
import com.oursician.app.tuner.midiNoteToFrequency

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
                TabStaff(
                    tablature = DEMO_TABLATURE,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private val STRING_ROW_HEIGHT = 26.dp
private val BEAT_COLUMN_WIDTH = 44.dp
private val MEASURE_DIVIDER_WIDTH = 13.dp // 6dp padding + 1dp line + 6dp padding

/** Renders a [Tablature] as a classic six-line guitar tab staff. */
@Composable
fun TabStaff(tablature: Tablature, modifier: Modifier = Modifier) {
    val strings = tablature.tuning.strings.sortedBy { it.stringNumber }
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val noteColor = MaterialTheme.colorScheme.onSurface
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val staffHeight = STRING_ROW_HEIGHT * strings.size

    val totalBeats = tablature.measures.sumOf { it.beats.size }
    val dividerCount = (tablature.measures.size - 1).coerceAtLeast(0)
    val measuresContentWidth = BEAT_COLUMN_WIDTH * totalBeats + MEASURE_DIVIDER_WIDTH * dividerCount

    Row(modifier = modifier.fillMaxWidth()) {
        StringLabelsColumn(strings = strings, rowHeight = STRING_ROW_HEIGHT)
        Spacer(modifier = Modifier.width(10.dp))

        // Measured against the available width so the staff lines can be extended with a
        // blank filler when the notated content is narrower than the screen (e.g. landscape).
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val fillerWidth = (maxWidth - measuresContentWidth).coerceAtLeast(0.dp)

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                tablature.measures.forEachIndexed { index, measure ->
                    MeasureColumn(
                        measure = measure,
                        strings = strings,
                        lineColor = lineColor,
                        noteColor = noteColor,
                    )
                    if (index != tablature.measures.lastIndex) {
                        MeasureDivider(color = dividerColor, height = staffHeight)
                    }
                }
                if (fillerWidth > 0.dp) {
                    StaffLinesFiller(
                        width = fillerWidth,
                        height = staffHeight,
                        stringCount = strings.size,
                        lineColor = lineColor,
                    )
                }
            }
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
private fun StaffLinesFiller(
    width: Dp,
    height: Dp,
    stringCount: Int,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.width(width).height(height)) {
        repeat(stringCount) { rowIndex ->
            val y = STRING_ROW_HEIGHT.toPx() * rowIndex + STRING_ROW_HEIGHT.toPx() / 2f
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
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
private fun MeasureColumn(
    measure: Measure,
    strings: List<TuningString>,
    lineColor: Color,
    noteColor: Color,
    modifier: Modifier = Modifier,
) {
    val width = BEAT_COLUMN_WIDTH * measure.beats.size.coerceAtLeast(1)
    val height = STRING_ROW_HEIGHT * strings.size

    Box(modifier = modifier.width(width).height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strings.indices.forEach { rowIndex ->
                val y = STRING_ROW_HEIGHT.toPx() * rowIndex + STRING_ROW_HEIGHT.toPx() / 2f
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            measure.beats.forEach { beat ->
                Column(modifier = Modifier.width(BEAT_COLUMN_WIDTH).fillMaxHeight()) {
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
}

private val DEMO_TABLATURE = Tablature(
    title = "Gamme de démonstration",
    tuning = STANDARD_TUNING,
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
