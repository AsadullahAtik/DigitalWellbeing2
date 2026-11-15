package com.wellbeing.tracker.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager

/** Plays the system alarm tone until [stop] is called. */
object AlarmPlayer {
    private var ringtone: Ringtone? = null

    @Synchronized
    fun start(context: Context) {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (android.os.Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
    }

    @Synchronized
    fun stop() {
        ringtone?.stop()
        ringtone = null
    }
}
