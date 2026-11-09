package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class PersonalSnapshotTest {
    private fun row(time: Long, origin: String = "phone-a", removed: Long? = null, position: Long = 30) =
        PersonalRecord("1305", time, origin, removed = removed, position = position)
    private fun snapshot(rows: List<PersonalRecord> = listOf(row(1))) = PersonalSnapshot(
        accountId = 123, deviceId = "phone-a", favourites = rows, progress = rows)

    @Test fun offlineRemovalSurvivesAnOldDeviceReturning() {
        val removed = row(20, removed = 20)
        assertEquals(removed, SnapshotCodec.merge(listOf(row(10), removed, row(10))).single())
    }
    @Test fun laterReAddWinsOverRemoval() {
        assertNull(SnapshotCodec.merge(listOf(row(10, removed = 10), row(11))).single().removed)
    }
    @Test fun startOverWinsInsteadOfKeepingMaximumPosition() {
        assertEquals(0L, SnapshotCodec.merge(listOf(row(10, position = 720), row(11, position = 0))).single().position)
    }
    @Test fun equalTimestampsConvergeInEitherOrder() {
        val a = row(10, "a"); val b = row(10, "b", removed = 10)
        assertEquals(listOf(b), SnapshotCodec.merge(listOf(a, b)))
        assertEquals(listOf(b), SnapshotCodec.merge(listOf(b, a)))
    }
    @Test fun duplicatesAreIdempotent() {
        assertEquals(listOf(row(10)), SnapshotCodec.merge(List(5) { row(10) }))
    }
    @Test fun differentMoviesAreIndependent() {
        assertEquals(2, SnapshotCodec.merge(listOf(row(10), row(9).copy(movieId = "1306"))).size)
    }
    @Test fun portableFileRoundTripsAllFields() {
        val value = snapshot(listOf(row(99, removed = 99).copy(finished = true, duration = 100)))
        val restored = SnapshotCodec.json.decodeFromString<PersonalSnapshot>(SnapshotCodec.json.encodeToString(value))
        restored.validate()
        assertEquals(value, restored)
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedVersionIsRejected() { snapshot().copy(version = 2).validate() }
    @Test(expected = IllegalArgumentException::class) fun invalidAccountIsRejected() { snapshot().copy(accountId = 0).validate() }
    @Test(expected = IllegalArgumentException::class) fun duplicateKeysAreRejected() { snapshot(listOf(row(1), row(2))).validate() }
    @Test(expected = IllegalArgumentException::class) fun invalidMovieIdIsRejected() { snapshot(listOf(row(1).copy(movieId = "../../secret"))).validate() }
    @Test(expected = IllegalArgumentException::class) fun negativePositionIsRejected() { snapshot(listOf(row(1, position = -1))).validate() }
    @Test fun unknownOptionalFieldsAreTolerated() {
        val encoded = SnapshotCodec.json.encodeToString(snapshot()).dropLast(1) + ",\"futureFlag\":true}"
        SnapshotCodec.json.decodeFromString<PersonalSnapshot>(encoded).validate()
    }
    @Test fun telegramRetryAfterIsRecognized() {
        assertEquals(125L, TelegramRequestException(429, "Too Many Requests: retry after 125").retryAfterSeconds)
        assertEquals(90L, TelegramRequestException(429, "FLOOD_WAIT_90").retryAfterSeconds)
    }
    @Test fun ordinaryTelegramErrorsDoNotInventRetryIntervals() {
        assertNull(TelegramRequestException(404, "Message not found").retryAfterSeconds)
    }
}
