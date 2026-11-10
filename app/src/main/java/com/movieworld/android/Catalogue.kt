package com.movieworld.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException

data class Actor(val id: String, val name: String, val movieCount: Int)

data class Genre(val id: String, val name: String)
data class Movie(val id: String, val title: String, val year: String?, val rating: String?, val poster: String?)
data class MovieDetails(
    val overview: String?,
    val telegram: String?,
    val backdrop: String?,
    val genres: List<String>,
    val cast: List<String>,
)
data class Featured(val movie: Movie, val backdrop: String?, val telegram: String?, val genres: List<String>)

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

    internal fun subtitleIdentity(id: String): SubtitleIdentity? = read { db ->
        db.rawQuery("SELECT $title, m.release_year, m.imdb_id, m.tmdb_id FROM movies m WHERE m.id = ? AND $visible", arrayOf(id)).use { rows ->
            if (rows.moveToFirst()) SubtitleIdentity(rows.getString(0), rows.getString(1), rows.getString(2), rows.getString(3)) else null
        }
    }

    fun genres(): List<Genre> = read { db ->
        db.rawQuery(
            """SELECT g.id, g.name FROM genres g WHERE g.name <> 'Adult'
               AND EXISTS (SELECT 1 FROM movie_genres mg JOIN movies m ON m.id = mg.movie_id
                           WHERE mg.genre_id = g.id AND $visible)
               ORDER BY g.name COLLATE NOCASE""", null
        ).use { rows -> buildList { while (rows.moveToNext()) add(Genre(rows.getString(0), rows.getString(1))) } }
    }

    fun movies(query: String, byActor: Boolean, genre: String?, from: Int?, to: Int?, actorId: String? = null): List<Movie> = read { db ->
        val filters = mutableListOf(visible)
        val args = mutableListOf<String>()
        val term = query.trim().replace(Regex("\\s+"), " ").take(80)
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        if (byActor && actorId != null) {
            filters += "EXISTS (SELECT 1 FROM movie_actors ma WHERE ma.movie_id = m.id AND ma.actor_id = ?)"
            args += actorId
        } else if (term.isNotEmpty()) {
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

    /** Same actor picker as the web app: two letters, prefix first, then visible movie count. */
    fun actors(query: String): List<Actor> {
        val term = query.trim().replace(Regex("\\s+"), " ").take(80)
        if (term.length < 2) return emptyList()
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return read { db ->
            db.rawQuery(
                """SELECT a.id, a.name, COUNT(DISTINCT m.id) AS movie_count
                   FROM actors a JOIN movie_actors ma ON ma.actor_id = a.id
                   JOIN movies m ON m.id = ma.movie_id
                   WHERE a.name LIKE ? ESCAPE '\' AND $visible
                   GROUP BY a.id ORDER BY CASE WHEN a.name LIKE ? ESCAPE '\' THEN 0 ELSE 1 END,
                   movie_count DESC, a.name COLLATE NOCASE LIMIT 12""",
                arrayOf("%$escaped%", "$escaped%")
            ).use { rows -> buildList {
                while (rows.moveToNext()) add(Actor(rows.getString(0), rows.getString(1), rows.getInt(2)))
            } }
        }
    }

    private val columns = "m.id, $title, m.release_year, m.imdb_rating, m.poster_url"

    private fun android.database.Cursor.movies(): List<Movie> = use { rows ->
        buildList {
            while (rows.moveToNext()) {
                add(Movie(rows.getString(0), rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)))
            }
        }
    }

    /** Highest ranked titles for the home screen. */
    fun topRated(limit: Int): List<Movie> = read { db ->
        db.rawQuery(
            "SELECT $columns FROM movies m WHERE $visible AND m.imdb_rating IS NOT NULL ORDER BY $rank LIMIT ?",
            arrayOf(limit.toString())
        ).movies()
    }

    /** Highest ranked titles in one genre. */
    fun byGenre(genreId: String, limit: Int): List<Movie> = read { db ->
        db.rawQuery(
            """SELECT $columns FROM movies m WHERE $visible
               AND EXISTS (SELECT 1 FROM movie_genres WHERE movie_id = m.id AND genre_id = ?)
               ORDER BY $rank LIMIT ?""",
            arrayOf(genreId, limit.toString())
        ).movies()
    }

    /** Genres with the most visible titles, for the home rows. */
    fun popularGenres(limit: Int): List<Genre> = read { db ->
        db.rawQuery(
            """SELECT g.id, g.name, COUNT(*) AS total FROM genres g
               JOIN movie_genres mg ON mg.genre_id = g.id JOIN movies m ON m.id = mg.movie_id
               WHERE g.name <> 'Adult' AND $visible
               GROUP BY g.id ORDER BY total DESC, g.name COLLATE NOCASE LIMIT ?""",
            arrayOf(limit.toString())
        ).use { rows -> buildList { while (rows.moveToNext()) add(Genre(rows.getString(0), rows.getString(1))) } }
    }

    /**
     * Hero title: a well-known, highly rated movie with a backdrop. The pick rotates daily
     * through the top of the ranking so the home screen does not look frozen.
     */
    fun featured(dayOfYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)): Featured? = read { db ->
        val offset = dayOfYear % 40
        val movie = db.rawQuery(
            """SELECT $columns FROM movies m WHERE $visible AND m.backdrop_url IS NOT NULL
               AND m.imdb_rating IS NOT NULL ORDER BY $rank LIMIT 1 OFFSET ?""",
            arrayOf(offset.toString())
        ).movies().firstOrNull() ?: db.rawQuery(
            "SELECT $columns FROM movies m WHERE $visible AND m.backdrop_url IS NOT NULL ORDER BY $rank LIMIT 1", null
        ).movies().firstOrNull() ?: return@read null
        val (backdrop, telegram) = db.rawQuery("SELECT backdrop_url, telegram_url FROM movies WHERE id = ?", arrayOf(movie.id)).use { rows ->
            if (rows.moveToFirst()) rows.getString(0) to rows.getString(1) else null to null
        }
        val genres = db.rawQuery(
            """SELECT g.name FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
               WHERE mg.movie_id = ? AND g.name <> 'Adult' ORDER BY g.name LIMIT 3""", arrayOf(movie.id)
        ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }
        Featured(movie, backdrop, telegram, genres)
    }

    /** Titles sharing the most genres with [id], ranked. */
    fun similar(id: String, limit: Int): List<Movie> = read { db ->
        db.rawQuery(
            """SELECT $columns FROM movies m
               JOIN movie_genres mg ON mg.movie_id = m.id
               WHERE m.id <> ? AND $visible
               AND mg.genre_id IN (SELECT genre_id FROM movie_genres WHERE movie_id = ?)
               GROUP BY m.id ORDER BY COUNT(*) DESC, $rank LIMIT ?""",
            arrayOf(id, id, limit.toString())
        ).movies()
    }

    /** Visible titles for a set of ids (favourites), in rank order. */
    fun byIds(ids: Collection<String>): List<Movie> = read { db ->
        if (ids.isEmpty()) return@read emptyList()
        ids.chunked(500).flatMap { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT $columns FROM movies m WHERE m.id IN ($marks) AND $visible ORDER BY $rank",
                chunk.toTypedArray()
            ).movies()
        }
    }

    /** Inclusive release year bounds across visible titles, for the year filter. */
    fun yearRange(): IntRange? = read { db ->
        db.rawQuery("SELECT MIN(m.release_year), MAX(m.release_year) FROM movies m WHERE $visible", null).use { rows ->
            if (!rows.moveToFirst() || rows.isNull(0) || rows.isNull(1)) null else rows.getInt(0)..rows.getInt(1)
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
