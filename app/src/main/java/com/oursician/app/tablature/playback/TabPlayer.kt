package com.oursician.app.tablature.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays synthesized note buffers through the device speaker, one beat at a time. Each call to
 * [playOneShot] gets its own [AudioTrack] rather than reusing/restarting a single one, so a
 * plucked note's decay tail can keep ringing while the next beat's notes start — restarting a
 * shared track would cut the previous note off dead. Mirrors [com.oursician.app.tuner.AudioSource]
 * for playback.
 */
class TabPlayer {
    private val activeTracks = mutableListOf<AudioTrack>()

    fun playOneShot(pcm: ShortArray, sampleRate: Int = TabAudioRenderer.SAMPLE_RATE_HZ) {
        releaseFinishedTracks()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        activeTracks += track
    }

    /** Stops and releases every note currently ringing, e.g. when the user leaves the screen. */
    fun stopAll() {
        activeTracks.forEach { it.stop(); it.release() }
        activeTracks.clear()
    }

    /**
     * A [AudioTrack.MODE_STATIC] track never transitions to a "stopped" playback state on its own
     * once its buffer has finished playing, so a finished track is detected by its playback head
     * having reached the end of its buffer instead.
     */
    private fun releaseFinishedTracks() {
        val finished = activeTracks.filter { it.playbackHeadPosition >= it.bufferSizeInFrames }
        finished.forEach { it.stop(); it.release() }
        activeTracks.removeAll(finished)
    }
}
