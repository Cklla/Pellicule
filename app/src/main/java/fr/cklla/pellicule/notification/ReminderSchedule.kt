package fr.cklla.pellicule.notification

import java.util.Calendar
import java.util.TimeZone

/** Heure locale à laquelle le contrôle quotidien des rappels est calé. */
const val REMINDER_CHECK_HOUR = 9

/**
 * Délai en millisecondes entre [nowMillis] et la prochaine occurrence de [hourOfDay] h 00 dans
 * [timeZone] : aujourd'hui si l'heure n'est pas encore passée, demain sinon.
 */
fun delayUntilNextHour(nowMillis: Long, hourOfDay: Int, timeZone: TimeZone = TimeZone.getDefault()): Long {
    val next = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_MONTH, 1)
    }
    return next.timeInMillis - nowMillis
}
