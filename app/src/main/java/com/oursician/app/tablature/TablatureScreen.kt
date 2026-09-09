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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oursician.app.tablature.library.SongRepository
import com.oursician.app.tablature.playback.TabAudioRenderer
import com.oursician.app.tablature.playback.TabPlaybackViewModel
import com.oursician.app.tablature.playback.TimedBeat
import com.oursician.app.tuner.frequencyToNote
import com.oursician.app.tuner.midiNoteToFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun TablatureScreen(songId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tablature = remember(songId) {
        val repository = SongRepository(context)
        repository.loadTablature(repository.listSongs().first { it.id == songId })
    }

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
                text = tablature.title,
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
                    ScrollingTabStaff(tablature = tablature, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val STRING_ROW_HEIGHT = 26.dp
private val BEAT_COLUMN_WIDTH = 44.dp
private val MEASURE_DIVIDER_WIDTH = 13.dp // 6dp padding + 1dp line + 6dp padding
private val PLAYHEAD_INSET = 24.dp

private fun beatWidth(beat: Beat): Dp = BEAT_COLUMN_WIDTH * beat.duration.quarterNoteMultiple

private enum class PlaybackMode { NONE, SCROLL_ONLY, SCROLL_WITH_AUDIO }

/**
 * Renders a [Tablature] that auto-scrolls right-to-left at a speed derived from its tempo,
 * Guitar Hero-style: notes travel toward the fixed playhead line instead of the reader
 * scrolling through a static page. Two independent triggers share this one scroll clock: the
 * round play/pause button (scroll only, to play along) and "Écouter la tablature" (scroll +
 * synthesized audio, delayed to start exactly when the first note reaches the playhead).
 */
@Composable
fun ScrollingTabStaff(
    tablature: Tablature,
    modifier: Modifier = Modifier,
    playbackViewModel: TabPlaybackViewModel = viewModel(),
) {
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

    // Each beat's on-screen offset from the start of the content — the same distance the layout
    // below places it at, measure dividers included — converted to seconds via pxPerSecond. A
    // beat's musical duration alone (as TabAudioRenderer's TimedBeat.musicalStartSeconds tracks
    // it) undercounts this for every measure after the first, since the MEASURE_DIVIDER_WIDTH gap
    // between measures adds real on-screen distance that isn't musical time — using musical time
    // alone to schedule triggers made every beat fire increasingly early relative to when it
    // actually crosses the playhead, the more measures had already scrolled by. One entry per beat
    // (rests included), so it stays index-aligned with TabAudioRenderer's timeline.
    val beatOffsetSeconds = remember(tablature, pxPerSecond) {
        val offsets = ArrayList<Float>(totalBeats)
        var accumulatedPx = 0f
        tablature.measures.forEachIndexed { measureIndex, measure ->
            measure.beats.forEach { beat ->
                offsets += accumulatedPx / pxPerSecond
                accumulatedPx += with(density) { beatWidth(beat).toPx() }
            }
            if (measureIndex != tablature.measures.lastIndex) {
                accumulatedPx += with(density) { MEASURE_DIVIDER_WIDTH.toPx() }
            }
        }
        offsets
    }

    var playbackMode by remember { mutableStateOf(PlaybackMode.NONE) }
    var elapsedSeconds by remember(tablature) { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    val isPlaying = playbackMode != PlaybackMode.NONE

    val totalDurationSeconds = (viewportWidthPx + with(density) { contentWidth.toPx() }) / pxPerSecond
    // Notes start off-screen and take this long to reach the fixed playhead — every beat's trigger
    // time is offset by this same amount so its sound fires exactly when it visually crosses the
    // playhead, whether or not audio is playing.
    val leadInSeconds = ((viewportWidthPx - with(density) { PLAYHEAD_INSET.toPx() }) / pxPerSecond).coerceAtLeast(0f)

    // Resets the scroll/UI state only. Used when the song reaches its natural end: any note
    // triggered right before the last frame (the last beat, most obviously) is left to ring out on
    // its own instead of being cut off mid-sound — killing every active track the instant the
    // scroll stops is what made the last note sound truncated or silent.
    fun stopPlayback() {
        playbackMode = PlaybackMode.NONE
        elapsedSeconds = 0f
    }

    // Used when the user explicitly interrupts playback (pauses, or picks it back up) — here
    // going silent immediately is the expected behavior.
    fun interruptPlayback() {
        stopPlayback()
        playbackViewModel.stop()
    }

    fun startPlayback(mode: PlaybackMode) {
        playbackMode = mode
        elapsedSeconds = 0f
    }

    DisposableEffect(Unit) {
        onDispose { playbackViewModel.stop() }
    }

    LaunchedEffect(playbackMode, totalDurationSeconds) {
        if (playbackMode == PlaybackMode.NONE) return@LaunchedEffect

        val timeline: List<TimedBeat> = if (playbackMode == PlaybackMode.SCROLL_WITH_AUDIO) {
            withContext(Dispatchers.Default) { TabAudioRenderer.renderTimeline(tablature) }
        } else {
            emptyList()
        }
        var nextBeatIndex = 0

        // The scroll starts advancing immediately — each beat's own trigger time (leadIn + its
        // on-screen offset) is what keeps its sound aligned to the playhead, not a delay before the
        // clock starts. Two clocks racing to match each other via a startup delay is exactly what
        // caused the drift this replaces.
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                elapsedSeconds += (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
            }
            while (nextBeatIndex < timeline.size && leadInSeconds + beatOffsetSeconds[nextBeatIndex] <= elapsedSeconds) {
                val pcm = timeline[nextBeatIndex].pcm
                if (pcm.isNotEmpty()) playbackViewModel.trigger(pcm)
                nextBeatIndex++
            }
            if (elapsedSeconds >= totalDurationSeconds) {
                stopPlayback()
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayPauseButton(
                isPlaying = isPlaying,
                accentColor = playheadColor,
                onClick = { if (isPlaying) interruptPlayback() else startPlayback(PlaybackMode.SCROLL_ONLY) },
            )
            ListenButton(
                isListening = playbackMode == PlaybackMode.SCROLL_WITH_AUDIO,
                accentColor = playheadColor,
                onClick = { if (playbackMode == PlaybackMode.SCROLL_WITH_AUDIO) interruptPlayback() else startPlayback(PlaybackMode.SCROLL_WITH_AUDIO) },
            )
        }
    }
}

/** Toggles the scroll + synthesized audio started from [ScrollingTabStaff]'s shared clock. */
@Composable
private fun ListenButton(isListening: Boolean, accentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
    ) {
        Text(if (isListening) "Arrêter" else "Écouter la tablature")
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
