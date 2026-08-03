package com.reminder.app

import android.app.Application
import com.reminder.app.allday.schedulePeriodicAllDayPromptWork

class ReminderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePeriodicAllDayPromptWork(this)
    }
}
