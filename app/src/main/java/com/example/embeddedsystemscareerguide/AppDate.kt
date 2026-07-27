package com.example.embeddedsystemscareerguide

import java.time.LocalDate

/**
 * Locale- and calendar-independent date helpers for streak tracking.
 *
 * The streak logic previously used `SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())`
 * in two different places (LearningPathFragment, FirestoreManager) and
 * `Locale.US` in a third (also FirestoreManager). Three problems fell out of that:
 *
 * 1. `Locale.getDefault()` picks up the device's calendar system, not just its digit
 *    script. On a device set to the Thai locale, `SimpleDateFormat` formats dates in
 *    the Buddhist Era (e.g. "2569-07-26" for what is 2026-07-26 in the Gregorian
 *    calendar), so a date written by one locale-dependent call site never matches a
 *    date written by a `Locale.US` call site for the same real day.
 * 2. Changing the device locale between visits made a previously-stored date string
 *    fail to re-parse under the new locale. The parse failure was caught and treated
 *    as "not yesterday", which silently reset an active streak to 1.
 * 3. `SimpleDateFormat` is not thread-safe; sharing an instance across coroutines
 *    would have been a latent bug even ignoring the locale issues.
 *
 * `java.time.LocalDate` (available unconditionally at this app's minSdk 26) is
 * always proleptic-Gregorian and its default `toString()` / `parse()` always use
 * ISO-8601 (`yyyy-MM-dd`), regardless of the device's locale or calendar. A day
 * stored by any call site is read back identically by every other call site.
 */
object AppDate {

    /** Today's date as an ISO-8601 string (`yyyy-MM-dd`), independent of device locale. */
    fun todayIso(): String = LocalDate.now().toString()

    /**
     * True if [dateString] (an ISO-8601 `yyyy-MM-dd` date, as written by [todayIso])
     * is exactly one calendar day before today. Returns false for a blank, malformed,
     * or unparseable value rather than throwing.
     */
    fun isYesterday(dateString: String): Boolean {
        if (dateString.isBlank()) return false
        return try {
            LocalDate.parse(dateString) == LocalDate.now().minusDays(1)
        } catch (e: Exception) {
            false
        }
    }
}
