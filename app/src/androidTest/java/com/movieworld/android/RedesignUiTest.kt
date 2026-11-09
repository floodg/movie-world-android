package com.movieworld.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.movieworld.android.ui.detail.DetailContent
import com.movieworld.android.ui.detail.DetailUiState
import com.movieworld.android.ui.home.Continuing
import com.movieworld.android.ui.home.GenreRow
import com.movieworld.android.ui.home.HomeContent
import com.movieworld.android.ui.home.HomeUiState
import com.movieworld.android.ui.player.MiniPlayer
import com.movieworld.android.ui.search.SearchContent
import com.movieworld.android.ui.search.SearchUiState
import com.movieworld.android.ui.theme.MovieWorldTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedesignUiTest {
    @get:Rule val compose = createComposeRule()

    private val movie = Movie("1", "Sample Movie", "2019", "7.8", null)
    private val other = Movie("2", "Other Movie", "2001", "6.1", null)

    @Test fun homeRendersHeroAndRows() {
        val progress = PlaybackDatabase.Progress().apply { movieId = "1"; position = 600; duration = 6000 }
        val state = HomeUiState(
            featured = Featured(movie, null, null, listOf("Drama")), featuredLoaded = true,
            continuing = listOf(Continuing(movie, progress)), myList = emptyList(),
            topRated = listOf(movie, other), genreRows = listOf(GenreRow(Genre("g", "Comedy"), listOf(other))),
        )
        compose.setContent {
            MovieWorldTheme { HomeContent(state, emptySet(), CastState(), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Sample Movie").assertIsDisplayed()
        compose.onNodeWithText("Play on TV").assertIsDisplayed()
        compose.onNodeWithText("Continue Watching").assertIsDisplayed()
        compose.onNodeWithText("Top Rated").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sample Movie, 2019, 10 percent watched").assertIsDisplayed()
        compose.onNodeWithText("My List").assertIsDisplayed()
        compose.onNodeWithContentDescription("TV settings").assertIsDisplayed()
    }

    @Test fun searchFiltersDriveCallbacks() {
        var query = ""
        var byActor = false
        var genre: String? = "unset"
        val state = SearchUiState(genres = listOf(Genre("g1", "Horror")), results = emptyList())
        compose.setContent {
            MovieWorldTheme {
                SearchContent(state, { query = it }, { byActor = it }, { genre = it }, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Search").performTextInput("alien")
        assertEquals("alien", query)
        compose.onNodeWithText("Actor").performClick()
        assertEquals(true, byActor)
        compose.onNodeWithText("Horror").performClick()
        assertEquals("g1", genre)
        compose.onNodeWithText("No matching movies").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithText("Release year").assertIsDisplayed()
    }

    @Test fun actorDropdownShowsCountsAndSelectsTheActorId() {
        var selected: Actor? = null
        val actors = listOf(Actor("7", "Tom Hanks", 12), Actor("8", "Tom Hardy", 1))
        compose.setContent {
            MovieWorldTheme {
                SearchContent(
                    SearchUiState(query = "Tom", byActor = true, actors = actors,
                        actorSuggestionsVisible = true, results = emptyList()),
                    {}, {}, {}, { _, _ -> }, {}, {}, onActor = { selected = it },
                )
            }
        }
        compose.onNodeWithText("Tom Hanks").assertIsDisplayed()
        compose.onNodeWithText("12 movies").assertIsDisplayed()
        compose.onNodeWithText("1 movie").assertIsDisplayed()
        compose.onNodeWithText("Tom Hardy").performClick()
        assertEquals("8", selected?.id)
    }

    @Test fun detailShowsPlayOrResumeAndDisablesDuringSession() {
        val details = MovieDetails("Overview", "https://t.me/channel/1", null, listOf("Drama"), listOf("Actor One"))
        compose.setContent {
            MovieWorldTheme {
                DetailContent(DetailUiState(movie, details, false, resume = 0, similar = emptyList()), false, CastState(), null,
                    {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Play on TV").assertIsDisplayed()
        compose.onNodeWithText("Telegram").assertIsDisplayed()
        compose.onNodeWithText("Actor One").assertIsDisplayed()
    }

    @Test fun detailResumeLabelAndStartOver() {
        compose.setContent {
            MovieWorldTheme {
                DetailContent(DetailUiState(movie, null, false, resume = 3723, watched = 0.5f, similar = emptyList()), true,
                    CastState(active = true, movieId = "9", title = "Other", message = "Playing on TV"), null,
                    {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Resume · 01:02:03").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Start over").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("In My List").assertIsDisplayed()
    }

    @Test fun miniPlayerStates() {
        var command = ""
        val playing = CastState(true, "1", "Sample Movie", "Playing on Lounge TV", 120, 600, playing = true, controllable = true)
        compose.setContent {
            MovieWorldTheme { MiniPlayer(playing, null, {}, { command = "pause" }, { command = "stop" }, {}) }
        }
        compose.onNodeWithText("Playing on Lounge TV").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals("pause", command)
        compose.onNodeWithContentDescription("Stop TV").performClick()
        assertEquals("stop", command)
    }

    @Test fun miniPlayerDownloadingAndErrorStates() {
        compose.setContent {
            MovieWorldTheme {
                androidx.compose.foundation.layout.Column {
                    MiniPlayer(CastState(true, "1", "Sample Movie", "Downloading 42%"), null, {}, {}, {}, {})
                    MiniPlayer(CastState(false, "1", "Sample Movie", "The TV did not start playback."), null, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Downloading 42%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
        compose.onNodeWithText("The TV did not start playback.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
    }
}
