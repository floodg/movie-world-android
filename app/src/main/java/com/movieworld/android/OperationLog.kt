package com.movieworld.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object OperationLog {
    private var helper: Database? = null
    @Synchronized private fun db(context: Context): SQLiteDatabase {
        if (helper == null) helper = Database(context.applicationContext)
        return helper!!.writableDatabase
    }
    internal fun clean(value: String): String = value
        .replace(Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE), "[address]")
        .replace(Regex("\\b(api[_-]?key|token|password|authorization|api_hash|phone)\\b\\s*[=:]\\s*[^\\s,;]+", RegexOption.IGNORE_CASE), "$1=[redacted]")
        .replace(Regex("\\b\\+?\\d[\\d ()-]{8,}\\d\\b"), "[number]")
        .take(1000)

    fun record(context: Context, operation: String, message: String, level: String = "info", movieId: String = "") {
        try {
            val row = JSONObject().put("event_id", UUID.randomUUID().toString())
                .put("timestamp", System.currentTimeMillis()).put("source", "android")
                .put("operation", clean(operation).take(80)).put("message", clean(message))
                .put("level", level).put("movie_id", movieId.take(32))
            synchronized(this) {
                val db = db(context)
                db.insertOrThrow("events", null, ContentValues().apply { put("event_id", row.getString("event_id")); put("payload", row.toString()); put("sent", 0) })
                db.execSQL("DELETE FROM events WHERE seq NOT IN (SELECT seq FROM events ORDER BY seq DESC LIMIT 2000)")
            }
            schedule(context)
        } catch (_: Exception) { /* Logging never interrupts the operation. */ }
    }

    @Synchronized fun rows(context: Context, pending: Boolean = false): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        db(context).rawQuery("SELECT payload FROM events" + (if (pending) " WHERE sent=0 ORDER BY seq LIMIT 50" else " ORDER BY seq DESC LIMIT 300"), null).use { cursor ->
            while (cursor.moveToNext()) out.add(JSONObject(cursor.getString(0)))
        }
        return out
    }
    @Synchronized fun acknowledge(context: Context, ids: JSONArray) {
        val db = db(context)
        db.beginTransaction()
        try {
            for (i in 0 until ids.length()) db.execSQL("UPDATE events SET sent=1 WHERE event_id=?", arrayOf(ids.getString(i)))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = OneTimeWorkRequestBuilder<OperationSyncWorker>().setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("operation-log-sync", ExistingWorkPolicy.KEEP, work)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("operation-log-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OperationSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
    }
    fun sync(context: Context) {
        val address = context.getSharedPreferences("tv", Context.MODE_PRIVATE).getString("pc-server", "").orEmpty()
        check(address.isNotBlank()) { "Save a PC server address in TV settings to see server logs." }
        val pc = PcPlayback(TvNetwork(context), address)
        repeat(40) {
            val rows = rows(context, pending = true)
            if (rows.isEmpty()) return
            val response = pc.uploadOperations(JSONArray(rows))
            acknowledge(context, response.getJSONArray("accepted"))
        }
    }
    private class Database(context: Context) : SQLiteOpenHelper(context, "operations.sqlite", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE events(seq INTEGER PRIMARY KEY AUTOINCREMENT,event_id TEXT UNIQUE NOT NULL,payload TEXT NOT NULL,sent INTEGER NOT NULL DEFAULT 0)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { }
    }
}

class OperationSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try { OperationLog.sync(applicationContext); Result.success() }
        catch (_: Exception) { Result.retry() }
}
