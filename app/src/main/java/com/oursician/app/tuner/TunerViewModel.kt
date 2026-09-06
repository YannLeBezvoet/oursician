package com.oursician.app.tuner

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunerUiState(
    val isListening: Boolean = false,
    val detectedNote: DetectedNote? = null,
    val closestString: GuitarString? = null,
)

class TunerViewModel @JvmOverloads constructor(
    private val audioSource: AudioSource = AudioSource(),
    private val pitchDetector: PitchDetector = YinPitchDetector(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    private var listeningJob: Job? = null
    private var clearNoteJob: Job? = null
    private val recentFrequencies = ArrayDeque<Float>(SMOOTHING_WINDOW)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (listeningJob?.isActive == true) return
        recentFrequencies.clear()
        listeningJob = viewModelScope.launch {
            _uiState.update { it.copy(isListening = true) }
            audioSource.audioFlow().collect { buffer ->
                val frequency = pitchDetector.detectPitch(buffer, AudioSource.SAMPLE_RATE_HZ)
                onFrequencyDetected(frequency)
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        clearNoteJob?.cancel()
        clearNoteJob = null
        recentFrequencies.clear()
        _uiState.update { TunerUiState(isListening = false) }
    }

    private fun onFrequencyDetected(frequency: Float?) {
        if (frequency == null) {
            recentFrequencies.clear()
            scheduleNoteClear()
            return
        }

        clearNoteJob?.cancel()
        clearNoteJob = null

        if (recentFrequencies.size == SMOOTHING_WINDOW) recentFrequencies.removeFirst()
        recentFrequencies.addLast(frequency)
        val smoothedFrequency = recentFrequencies.sorted()[recentFrequencies.size / 2]

        _uiState.update {
            it.copy(
                detectedNote = frequencyToNote(smoothedFrequency),
                closestString = closestGuitarString(smoothedFrequency),
            )
        }
    }

    private fun scheduleNoteClear() {
        if (clearNoteJob?.isActive == true) return
        clearNoteJob = viewModelScope.launch {
            delay(NOTE_HOLD_MS)
            _uiState.update { it.copy(detectedNote = null, closestString = null) }
        }
    }

    override fun onCleared() {
        stopListening()
    }

    private companion object {
        const val SMOOTHING_WINDOW = 5
        const val NOTE_HOLD_MS = 1200L
    }
}
