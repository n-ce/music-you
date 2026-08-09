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
import com.github.innertube.utils.findSectionByStrapline
import com.github.innertube.utils.findSectionByTitle
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

    // Explicitly find the tab titled "Related" or fallback to index 2 / last tab
    val relatedTab = tabs?.find { 
        it.tabRenderer?.title?.runs?.any { run -> 
            run.text.equals("Related", ignoreCase = true) || run.text.contains("related", ignoreCase = true)
        } == true 
    }?.tabRenderer ?: tabs?.getOrNull(2)?.tabRenderer ?: tabs?.lastOrNull()?.tabRenderer

    val browseId = relatedTab?.endpoint?.browseEndpoint?.browseId 
        ?: return@runCatchingNonCancellable null

    val response = client.post(BROWSE) {
        setBody(
            BrowseBody(
                localized = false,
                browseId = browseId
            )
        )
        // Removed restrictive field mask to prevent stripping updated YouTube schemas
    }.body<BrowseResponse>()

    val sectionListRenderer = response
        .contents
        ?.sectionListRenderer

    val songSection = sectionListRenderer?.findSectionByTitle("You might also like")
        ?: sectionListRenderer?.findSectionByTitle("Quick picks")
        ?: sectionListRenderer?.findSectionByTitle("Quick picks for you")
        ?: sectionListRenderer?.contents?.firstOrNull()

    Innertube.RelatedPage(
        songs = songSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        playlists = (sectionListRenderer?.findSectionByTitle("Recommended playlists")
            ?: sectionListRenderer?.findSectionByTitle("Playlists for you"))
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.PlaylistItem::from)
            ?.sortedByDescending { it.channel?.name == "YouTube Music" },
        albums = sectionListRenderer
            ?.findSectionByStrapline("MORE FROM")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from),
        artists = (sectionListRenderer?.findSectionByTitle("Similar artists")
            ?: sectionListRenderer?.findSectionByTitle("Fans also like"))
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.ArtistItem::from),
    )
}
