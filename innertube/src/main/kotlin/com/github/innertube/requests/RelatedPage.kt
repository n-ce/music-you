package com.github.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import com.github.innertube.Innertube
import com.github.innertube.models.BrowseResponse
import com.github.innertube.models.MusicCarouselShelfRenderer
import com.github.innertube.models.NextResponse
import com.github.innertube.models.bodies.BrowseBody
import com.github.innertube.models.bodies.NextBody
import com.github.innertube.utils.from
import com.github.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.relatedPage(videoId: String) = runCatchingNonCancellable {
    val nextResponse = client.post(NEXT) {
        setBody(NextBody(videoId = videoId))
        mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)")
    }.body<NextResponse>()

    val tabs = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs

    // Find the Related tab dynamically or fall back to tab index 2
    val browseId = tabs
        ?.mapNotNull { it.tabRenderer }
        ?.firstOrNull { tab ->
            tab.endpoint?.browseEndpoint?.browseId?.contains("related", ignoreCase = true) == true ||
            tab.endpoint?.browseEndpoint?.browseId?.startsWith("FVC") == true
        }?.endpoint?.browseEndpoint?.browseId
        ?: tabs?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint?.browseId
        ?: return@runCatchingNonCancellable null

    val response = client.post(BROWSE) {
        setBody(
            BrowseBody(
                localized = false,
                browseId = browseId
            )
        )
    }.body<BrowseResponse>()

    val songs = mutableListOf<Innertube.SongItem>()
    val playlists = mutableListOf<Innertube.PlaylistItem>()
    val albums = mutableListOf<Innertube.AlbumItem>()
    val artists = mutableListOf<Innertube.ArtistItem>()

    // Metrolist-style iteration: parse all carousel shelves regardless of title strings
    response.contents?.sectionListRenderer?.contents?.forEach { section ->
        section.musicCarouselShelfRenderer?.contents?.forEach { content ->
            content.musicResponsiveListItemRenderer?.let { renderer ->
                Innertube.SongItem.from(renderer)?.let { songs.add(it) }
            }
            content.musicTwoRowItemRenderer?.let { renderer ->
                Innertube.PlaylistItem.from(renderer)?.let { playlists.add(it) }
                Innertube.AlbumItem.from(renderer)?.let { albums.add(it) }
                Innertube.ArtistItem.from(renderer)?.let { artists.add(it) }
            }
        }
    }

    Innertube.RelatedPage(
        songs = songs.ifEmpty { null },
        playlists = playlists.ifEmpty { null },
        albums = albums.ifEmpty { null },
        artists = artists.ifEmpty { null }
    )
}
