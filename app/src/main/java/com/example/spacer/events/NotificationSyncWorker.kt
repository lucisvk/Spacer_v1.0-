package com.example.spacer.events

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NotificationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repository = NotificationsRepository()

    override suspend fun doWork(): Result {
        return runCatching {
            UserNotificationDispatcher.flushUnreadToPhone(applicationContext, repository)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
