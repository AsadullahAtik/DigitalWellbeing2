package com.wellbeing.tracker.domain

object Format {
    fun minutes(m: Int): String = if (m < 60) "${m} মি" else "${m / 60} ঘ ${m % 60} মি"

    fun clock(minuteOfDay: Int): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }

    fun mmss(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }
}

object AlertTexts {
    data class Text(val toast: String, val title: String, val body: String)

    fun label(key: String): String = when (LimitKeys.typeOf(key)) {
        LimitType.SCREEN_TIME -> "Screen time"
        LimitType.APP_TIME -> TrackedApps.byId(LimitKeys.appIdOf(key) ?: "")?.label ?: key
        LimitType.CONTENT_COUNT -> LimitKeys.contentOf(key)?.label ?: key
    }

    fun warn(key: String, current: Int, effective: Int): String {
        val name = label(key)
        return when (LimitKeys.typeOf(key)) {
            LimitType.SCREEN_TIME ->
                "Screen time ${Format.minutes(current)} / ${Format.minutes(effective)}। সীমার কাছে পৌঁছে গেছো।"
            LimitType.APP_TIME ->
                "${name}: ${Format.minutes(current)} / ${Format.minutes(effective)}। সময়ের সীমার কাছে পৌঁছে গেছো।"
            LimitType.CONTENT_COUNT ->
                "${name}: ${current}/${effective}। সীমার কাছে পৌঁছে গেছো।"
        }
    }

    fun over(key: String, current: Int, effective: Int): Text {
        val name = label(key)
        return when (LimitKeys.typeOf(key)) {
            LimitType.SCREEN_TIME -> Text(
                toast = "আজকের Screen time-এর সীমা শেষ (${Format.minutes(current)})",
                title = "Screen time-এর সীমা শেষ",
                body = "আজ ${Format.minutes(current)} ফোন ব্যবহার করেছো। সীমা ছিল ${Format.minutes(effective)}। এবার ফোনটা রাখার সময়।",
            )
            LimitType.APP_TIME -> Text(
                toast = "${name}-এর আজকের সময়ের সীমা শেষ (${Format.minutes(current)})",
                title = "${name}-এর সময় শেষ",
                body = "আজ ${name}-এ ${Format.minutes(current)} কাটিয়েছো। সীমা ছিল ${Format.minutes(effective)}।",
            )
            LimitType.CONTENT_COUNT -> {
                val unit = LimitKeys.contentOf(key)?.unit ?: ""
                Text(
                    toast = "${name}-এর আজকের সীমা শেষ (${current}/${effective})",
                    title = "${name}-এর সীমা শেষ",
                    body = "আজ ${current}টি ${unit} দেখেছো। সীমা ছিল ${effective}টি। এবার থামার সময়।",
                )
            }
        }
    }
}
