package com.movieworld.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException

data class Genre(val id: String, val name: String)
data class Movie(val id: String, val title: String, val year: String?, val rating: String?, val poster: String?)
data class MovieDetails(
    val overview: String?,
    val telegram: String?,
    val backdrop: String?,
    val genres: List<String>,
    val cast: List<String>,
)

class Catalogue(private val context: Context) {
    private val title = "COALESCE(m.title, m.source_title)"
    private val visible = """NOT EXISTS (
        SELECT 1 FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
        WHERE mg.movie_id = m.id AND g.name = 'Adult'
    )"""
    private val rank = "m.imdb_rating IS NULL, m.imdb_rating DESC, m.imdb_votes DESC, $title COLLATE NOCASE, m.id"

    private fun <T> read(block: (SQLiteDatabase) -> T): T {
        val file = File(context.filesDir, "catalogue-v1.sqlite")
        if (!file.exists()) {
            val pending = File.createTempFile("catalogue-", ".tmp", context.filesDir)
            try {
                context.assets.open("movie-world.sqlite").use { source ->
                    pending.outputStream().use { source.copyTo(it) }
                }
                if (!pending.renameTo(file) && !file.exists()) throw IOException("Catalogue install failed")
            } finally {
                pending.delete()
            }
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(block)
    }

    fun genres(): List<Genre> = read { db ->
        db.rawQuery(
            """SELECT g.id, g.name FROM genres g WHERE g.name <> 'Adult'
               AND EXISTS (SELECT 1 FROM movie_genres mg JOIN movies m ON m.id = mg.movie_id
                           WHERE mg.genre_id = g.id AND $visible)
               ORDER BY g.name COLLATE NOCASE""", null
        ).use { rows -> buildList { while (rows.moveToNext()) add(Genre(rows.getString(0), rows.getString(1))) } }
    }

    fun movies(query: String, byActor: Boolean, genre: String?, from: Int?, to: Int?): List<Movie> = read { db ->
        val filters = mutableListOf(visible)
        val args = mutableListOf<String>()
        val term = query.trim().replace(Regex("\\s+"), " ").take(80)
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        if (term.isNotEmpty()) {
            filters += if (byActor) """EXISTS (
                SELECT 1 FROM movie_actors ma JOIN actors a ON a.id = ma.actor_id
                WHERE ma.movie_id = m.id AND a.name LIKE ? ESCAPE '\'
            )""" else """$title LIKE ? ESCAPE '\'"""
            args += "%$escaped%"
        }
        if (genre != null) {
            filters += "EXISTS (SELECT 1 FROM movie_genres WHERE movie_id = m.id AND genre_id = ?)"
            args += genre
        }
        if (from != null) {
            filters += "m.release_year >= ?"
            args += from.toString()
        }
        if (to != null) {
            filters += "m.release_year <= ?"
            args += to.toString()
        }
        val order = if (term.isNotEmpty() && !byActor) {
            args += "$escaped%"
            """CASE WHEN $title LIKE ? ESCAPE '\' THEN 0 ELSE 1 END, $rank"""
        } else rank
        db.rawQuery(
            """SELECT m.id, $title, m.release_year, m.imdb_rating, m.poster_url
               FROM movies m WHERE ${filters.joinToString(" AND ")} ORDER BY $order""",
            args.toTypedArray()
        ).use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(Movie(rows.getString(0), rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)))
                }
            }
        }
    }

    fun movie(id: String): Movie? = read { db ->
        db.rawQuery(
            "SELECT m.id, $title, m.release_year, m.imdb_rating, m.poster_url FROM movies m WHERE m.id = ? AND $visible",
            arrayOf(id)
        ).use { rows ->
            if (!rows.moveToFirst()) null else Movie(rows.getString(0), rows.getString(1),
                rows.getString(2), rows.getString(3), rows.getString(4))
        }
    }
    fun details(id: String): MovieDetails? = read { db ->
        fun names(sql: String) = db.rawQuery(sql, arrayOf(id)).use { rows ->
            buildList { while (rows.moveToNext()) add(rows.getString(0)) }
        }
        db.rawQuery(
            "SELECT m.overview, m.telegram_url, m.backdrop_url FROM movies m WHERE m.id = ? AND $visible",
            arrayOf(id)
        ).use { rows ->
            if (!rows.moveToFirst()) return@read null
            MovieDetails(
                rows.getString(0), rows.getString(1), rows.getString(2),
                names("""SELECT g.name FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
                         WHERE mg.movie_id = ? AND g.name <> 'Adult' ORDER BY g.name"""),
                names("""SELECT a.name FROM movie_actors ma JOIN actors a ON a.id = ma.actor_id
                         WHERE ma.movie_id = ? ORDER BY ma.billing_order, a.name LIMIT 12"""),
            )
        }
    }
}
