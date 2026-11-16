package com.movieworld.android

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Durable LAN command; retries when the PC is off or the phone is away from Wi-Fi. */
class FavouritePreparationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString("movie") ?: return Result.failure()
        val row = PlaybackDatabase.get(applicationContext).access().findFavourite(id)
        if (row == null || row.removed != null) return Result.success()
        val address = applicationContext.getSharedPreferences("tv", Context.MODE_PRIVATE).getString("pc-server", "").orEmpty()
        if (address.isBlank()) return Result.retry()
        return try {
            PcPlayback(TvNetwork(applicationContext), address).prepareFavourite(id)
            OperationLog.record(applicationContext, "preparation", "PC accepted background movie preparation", movieId = id)
            Result.success()
        } catch (failure: PcPlaybackError) {
            OperationLog.record(applicationContext, "preparation", failure.message ?: "PC command failed", "error", id)
            if (failure.code == "bad_request" || failure.code == "not_found" || failure.code == "bad_link") Result.failure() else Result.retry()
        } catch (failure: Exception) {
            OperationLog.record(applicationContext, "preparation", "PC unavailable; command will retry: ${failure.message.orEmpty()}", "warning", id)
            Result.retry()
        }
    }
    companion object {
        fun enqueue(context: Context, id: String) {
            val work = OneTimeWorkRequestBuilder<FavouritePreparationWorker>()
                .setInputData(workDataOf("movie" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("prepare-favourite-$id", ExistingWorkPolicy.KEEP, work)
        }
    }
}
