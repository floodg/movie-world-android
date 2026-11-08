package com.movieworld.android

import android.content.Context
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.drinkless.tdlib.TdApi

interface PersonalDataStore {
    suspend fun accountId(): Long
    suspend fun pull(): List<PersonalSnapshot>
    suspend fun cleanup(snapshot: PersonalSnapshot)
    suspend fun publish(snapshot: PersonalSnapshot)
}

class TelegramPersonalStore(private val app: Context, private val source: TelegramSource) : PersonalDataStore {
    private val hints = app.getSharedPreferences("telegram-sync-messages", Context.MODE_PRIVATE)
    private fun hintKey(account: Long, device: String) = "$account-$device"
    private fun known(key: String): Set<String> = hints.getStringSet(key, emptySet()).orEmpty().toSet()

    private suspend fun chat(): Long {
        val id = accountId()
        return source.request(TdApi.CreatePrivateChat().apply { userId = id }).id
    }
    override suspend fun accountId(): Long = source.request(TdApi.GetMe()).id
    private suspend fun messages(chat: Long): List<TdApi.Message> {
        val result = mutableListOf<TdApi.Message>()
        // Direct lookups bridge Telegram search indexing delays. Deleted hints are harmless.
        hints.all.filterKeys { it.startsWith("$chat-") }.values.forEach { ids ->
            (ids as? Set<*>)?.filterIsInstance<String>()?.forEach { id ->
                try { result.add(source.request(TdApi.GetMessage(chat, id.toLong()))) }
                catch (error: TelegramRequestException) { if (error.code != 404 && error.code != 400) throw error }
            }
        }
        var cursor = 0L
        do {
            val page = source.request(TdApi.SearchChatMessages().apply {
                chatId = chat; query = "Movie World Sync v1"; fromMessageId = cursor; limit = 100
            })
            val fresh = page.messages.filter { m -> result.none { it.id == m.id } }
            result.addAll(fresh)
            val next = page.nextFromMessageId
            if (next == 0L) break
            if (next == cursor) break
            cursor = next
        } while (true)
        return result.filter { it.sendingState == null && it.content is TdApi.MessageDocument }
    }
    private suspend fun read(message: TdApi.Message): PersonalSnapshot {
        val doc = (message.content as TdApi.MessageDocument).document
        require(SyncAttachment.isSnapshot(doc.fileName)) { "Unrecognized sync attachment" }
        require(maxOf(doc.document.size, doc.document.expectedSize) <= SnapshotCodec.MAX_BYTES)
        val file = source.request(TdApi.DownloadFile().apply {
            fileId = doc.document.id; priority = 1; synchronous = true
        })
        check(file.local.isDownloadingCompleted) { "Snapshot download incomplete" }
        val path = File(file.local.path)
        require(path.length() <= SnapshotCodec.MAX_BYTES)
        return SnapshotCodec.json.decodeFromString<PersonalSnapshot>(path.readText()).also { it.validate() }
    }
    override suspend fun pull(): List<PersonalSnapshot> {
        val account = accountId()
        val snapshots = mutableListOf<PersonalSnapshot>()
        for (message in messages(chat())) {
            // Skip unrelated documents, but surface damaged/unsupported sync files rather than silently losing data.
            val doc = (message.content as TdApi.MessageDocument).document
            if (!SyncAttachment.isSnapshot(doc.fileName)) continue
            val snapshot = read(message)
            require(snapshot.accountId == account) { "Snapshot belongs to another Telegram account" }
            snapshots.add(snapshot)
        }
        return snapshots
    }
    override suspend fun publish(snapshot: PersonalSnapshot) {
        snapshot.validate()
        check(accountId() == snapshot.accountId) { "Telegram account changed" }
        val chat = chat()
        val file = File(app.cacheDir, "movie-world-sync-v1-${snapshot.deviceId}-${java.util.UUID.randomUUID()}.json")
        try {
            file.writeText(SnapshotCodec.json.encodeToString(snapshot))
            require(file.length() <= SnapshotCodec.MAX_BYTES)
            val outgoing = source.request(TdApi.SendMessage().apply {
                chatId = chat
                options = TdApi.MessageSendOptions().apply { disableNotification = true; fromBackground = true }
                inputMessageContent = TdApi.InputMessageDocument().apply {
                    document = TdApi.InputDocument(TdApi.InputFileLocal(file.absolutePath), null, true)
                    caption = TdApi.FormattedText("Movie World Sync v1 · ${snapshot.deviceId}", emptyArray())
                }
            })
            val confirmed = source.awaitSent(outgoing)
            val key = hintKey(snapshot.accountId, snapshot.deviceId)
            check(hints.edit().putStringSet(key, known(key) + confirmed.id.toString()).commit()) {
                "Backup uploaded; could not save local sync receipt"
            }
        } finally { file.delete() }
    }
    override suspend fun cleanup(snapshot: PersonalSnapshot) {
        check(accountId() == snapshot.accountId) { "Telegram account changed" }
        val chat = chat()
        val own = messages(chat).filter { message ->
            val document = message.content as TdApi.MessageDocument
            SyncAttachment.isDeviceSnapshot(document.document.fileName, document.caption.text, snapshot.deviceId)
        }.sortedByDescending { it.id }
        if (own.size <= 2) return
        // Validate all candidate contents on-device before deleting any remote file, including files
        // from before local upload receipts were introduced. The caller already merged every snapshot.
        val validated = own.associate { message ->
            val backup = read(message)
            check(backup.accountId == snapshot.accountId && backup.deviceId == snapshot.deviceId) {
                "Backup identity mismatch; cleanup skipped"
            }
            message.id to backup
        }
        check(accountId() == snapshot.accountId) { "Telegram account changed" }
        val retained = own.take(2).map { validated.getValue(it.id) }
        val remove = own.drop(2).filter { backupsCover(retained, validated.getValue(it.id)) }.map { it.id }
        if (remove.isEmpty()) return
        // TDLib accepts at most 100 message identifiers per delete call.
        remove.chunked(100).forEach { source.request(TdApi.DeleteMessages(chat, it.toLongArray(), true)) }
        val key = hintKey(snapshot.accountId, snapshot.deviceId)
        hints.edit().putStringSet(key, own.filter { it.id !in remove }.map { it.id.toString() }.toSet()).commit()
    }

}
