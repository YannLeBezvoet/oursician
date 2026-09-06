package com.oursician.app.tuner

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers

/** Captures microphone audio as a stream of normalized mono PCM buffers in [-1, 1]. */
class AudioSource(private val sampleRate: Int = SAMPLE_RATE_HZ) {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun audioFlow(): Flow<FloatArray> = callbackFlow {
        val minBufferSizeBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSizeBytes > 0) { "This device does not support the requested audio format" }

        val recordBufferSizeBytes = maxOf(minBufferSizeBytes, ANALYSIS_BUFFER_SAMPLES * 2 * 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufferSizeBytes,
        )

        audioRecord.startRecording()

        val rawBuffer = ShortArray(ANALYSIS_BUFFER_SAMPLES)
        try {
            while (isActive) {
                val samplesRead = audioRecord.read(rawBuffer, 0, rawBuffer.size)
                if (samplesRead > 0) {
                    val normalized = FloatArray(samplesRead) { i -> rawBuffer[i] / Short.MAX_VALUE.toFloat() }
                    trySend(normalized)
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE_HZ = 44100
        const val ANALYSIS_BUFFER_SAMPLES = 4096
    }
}
