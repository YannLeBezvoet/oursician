package com.oursician.app.tablature.playback

import androidx.lifecycle.ViewModel

/**
 * Triggers a [TimedBeat]'s synthesized sound on demand. Deliberately stateless about timing (no
 * clock or schedule of its own): the caller — [com.oursician.app.tablature.ScrollingTabStaff] —
 * owns the single scroll clock and decides exactly when each beat's sound should fire, since that
 * same clock also drives the visual scroll and, eventually, hit-timing judgement.
 */
class TabPlaybackViewModel @JvmOverloads constructor(
    private val player: TabPlayer = TabPlayer(),
) : ViewModel() {

    fun trigger(pcm: ShortArray) {
        player.playOneShot(pcm)
    }

    fun stop() {
        player.stopAll()
    }

    override fun onCleared() {
        stop()
    }
}
