package cafe.oeee.util

import android.content.Context
import cafe.oeee.R
import java.util.Date

fun formatRelativeTime(context: Context, date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    return when {
        years > 0 -> context.getString(R.string.time_years_ago, years)
        months > 0 -> context.getString(R.string.time_months_ago, months)
        weeks > 0 -> context.getString(R.string.time_weeks_ago, weeks)
        days > 0 -> context.getString(R.string.time_days_ago, days)
        hours > 0 -> context.getString(R.string.time_hours_ago, hours)
        minutes > 0 -> context.getString(R.string.time_minutes_ago, minutes)
        seconds > 5 -> context.getString(R.string.time_seconds_ago, seconds)
        else -> context.getString(R.string.time_just_now)
    }
}
