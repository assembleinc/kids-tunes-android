package com.assembleinc.kidstunes.media

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.model.Song
import com.assembleinc.kidstunes.util.AppleMusicTokenProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONObject
import java.util.*
import kotlin.collections.HashMap


/**
 * Created by Assemble, Inc. on 2019-05-16.
 */
class MediaProvider(private val context: Context,
                    private val requestQueue: RequestQueue,
                    private val appleMusicTokenProvider: AppleMusicTokenProvider) {

    private var topTracksMetadata = TreeMap<String, Song>()
    private var favoriteTracksMetadata = TreeMap<String, Song>()
    var favoriteTracksPlaylistIdentifier: String? = null
        private set

    fun loadMediaItems(parentId: String, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        if (parentId == TOP_SONGS_ROOT_ID) {
            requestTopSongs(result)
        }
        else if (parentId == FAVORITES_ROOT_ID) {
            requestPlaylists(result)
        }
    }

    private val authorizationHeaders: MutableMap<String, String>
    get() {
        val headers = HashMap<String, String>()
        headers["Authorization"] = "Bearer ${appleMusicTokenProvider.developerToken}"
        return headers
    }

    private val musicTokenHeaders: MutableMap<String, String>
    get() {
        val headers = authorizationHeaders
        headers["Music-User-Token"] = appleMusicTokenProvider.userToken ?: ""
        return headers
    }

    private fun requestTopSongs(result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val url = "https://api.music.apple.com/v1/catalog/us/charts?types=$MEDIA_TYPE_SONGS&genre=$GENRE_KIDS&limit=100&offset=0"
        val request = object: StringRequest(
            Method.GET, url,
            Response.Listener { response ->
                doAsync {
                    val jsonObject = JsonParser().parse(response).asJsonObject
                    val songsData = jsonObject.get("results").asJsonObject.get("songs").asJsonArray.get(0).asJsonObject
                    val songs = songsData.get("data").asJsonArray ?: JsonArray()
                    val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                    topTracksMetadata = TreeMap()
                    songs.forEach {
                        val songJson = it as JsonObject
                        val songPlaybackId = songJson.get("id").asString
                        val songAttributes = songJson.get("attributes").asJsonObject
                        val songName = songAttributes.get("name").asString
                        val artistName = songAttributes.get("artistName").asString
                        val albumName = songAttributes.get("albumName").asString
                        val durationInMillis = songAttributes.get("durationInMillis").asLong
                        val artwork = songAttributes.get("artwork").asJsonObject
                        val imageSize = (context.resources.displayMetrics.density * 48).toInt()
                        val artworkUrl = artwork.get("url").asString.replace("{w}", imageSize.toString()).replace("{h}", imageSize.toString())
                        val fullImageSize = (context.resources.displayMetrics.density * 128).toInt()
                        val fullArtworkUrl = artwork.get("url").asString.replace("{w}", fullImageSize.toString()).replace("{h}", fullImageSize.toString())
                        val backgroundColor = artwork.get("bgColor").asString
                        val textColor1 = artwork.get("textColor1").asString
                        val textColor2 = artwork.get("textColor2").asString

                        val song = Song(songPlaybackId, songName, artistName, albumName, artworkUrl, fullArtworkUrl, durationInMillis)
                        song.backgroundColorHex = "#$backgroundColor"
                        song.textColorHex = "#$textColor1"
                        song.textColorAccentHex = "#$textColor2"
                        topTracksMetadata[songPlaybackId] = song

                        val metadata = song.toMediaMetadataCompat()
                        val mediaItem = MediaBrowserCompat.MediaItem(metadata.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                        mediaItems.add(mediaItem)
                    }

                    uiThread { result.sendResult(mediaItems) }
                }
            },
            Response.ErrorListener {
                doAsync {
                    val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                    topTracksMetadata.forEach {
                        val metadata = it.value.toMediaMetadataCompat()
                        val mediaItem = MediaBrowserCompat.MediaItem(metadata.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                        mediaItems.add(mediaItem)
                    }

                    uiThread { result.sendResult(mediaItems) }
                }
            })
        {
            override fun getHeaders(): MutableMap<String, String> {
                return authorizationHeaders
            }
        }
        request.retryPolicy = DefaultRetryPolicy(
            DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 2,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        requestQueue.add(request)
    }

    private fun requestPlaylists(result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val url = "https://api.music.apple.com/v1/me/library/playlists"
        val request = object: StringRequest(Method.GET, url,
            Response.Listener { response ->
                doAsync {
                    val jsonObject = JsonParser().parse(response).asJsonObject
                    val data = jsonObject.get("data").asJsonArray
                    val playlist = data.firstOrNull {
                        val attributes = it.asJsonObject.get("attributes").asJsonObject
                        attributes.get("name").asString == context.getString(R.string.playlist_name)
                    }

                    if (null != playlist) {
                        val playlistId = (playlist as JsonObject).get("id").asString
                        favoriteTracksPlaylistIdentifier = playlistId
                        requestFavoriteSongs(result, playlistId)
                    }
                    else {
                        createPlayList(result)
                    }
                }
            },
            Response.ErrorListener { error ->
                Log.e(TAG, "$error")
                val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                result.sendResult(mediaItems)
            })
        {
            override fun getHeaders(): MutableMap<String, String> {
                return musicTokenHeaders
            }
        }
        request.retryPolicy = DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 2,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        requestQueue.add(request)
    }

    private fun requestFavoriteSongs(result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>, playlistId: String) {
        val url = "https://api.music.apple.com/v1/me/library/playlists/$playlistId/tracks"
        val request = object: StringRequest(Method.GET, url,
            Response.Listener { response ->
                doAsync {
                    val jsonObject = JsonParser().parse(response).asJsonObject
                    val tracks = jsonObject.get("data").asJsonArray
                    val tracksIdentifiers = tracks.mapNotNull {
                        val attributes = it.asJsonObject.get("attributes").asJsonObject
                        val playParams = if (attributes.has("playParams")) attributes.get("playParams").asJsonObject else JsonObject()
                        if (playParams.has("catalogId")) playParams.get("catalogId").asString else null
                    }

                    requestCatalogSongs(tracksIdentifiers, result)
                }
            },
            Response.ErrorListener { error ->
                Log.e(TAG, "$error")
                val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                result.sendResult(mediaItems)
            })
        {
            override fun getHeaders(): MutableMap<String, String> {
                return musicTokenHeaders
            }
        }
        request.retryPolicy = DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 2,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        requestQueue.add(request)
    }

    private fun requestCatalogSongs(tracksIdentifiers: List<String>, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val url = "https://api.music.apple.com/v1/catalog/us/songs?ids=${tracksIdentifiers.joinToString(separator = ",")}&l=en-US"
        val request = object: StringRequest(Method.GET, url,
            Response.Listener { response ->
                doAsync {
                    val jsonObject = JsonParser().parse(response).asJsonObject
                    val data = jsonObject.get("data").asJsonArray
                    val songs = data.filter {
                        val songAttributes = it.asJsonObject.get("attributes").asJsonObject
                        songAttributes.get("genreNames").asJsonArray.contains(JsonPrimitive("Children's Music"))
                    }
                    val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                    favoriteTracksMetadata = TreeMap()
                    songs.forEach {
                        val songJson = it as JsonObject
                        val songPlaybackId = songJson.get("id").asString
                        val songAttributes = songJson.get("attributes").asJsonObject
                        val songName = songAttributes.get("name").asString
                        val artistName = songAttributes.get("artistName").asString
                        val albumName = songAttributes.get("albumName").asString
                        val durationInMillis = songAttributes.get("durationInMillis").asLong
                        val artwork = songAttributes.get("artwork").asJsonObject
                        val imageSize = (context.resources.displayMetrics.density * 48).toInt()
                        val artworkUrl = artwork.get("url").asString.replace("{w}", imageSize.toString()).replace("{h}", imageSize.toString())
                        val fullImageSize = (context.resources.displayMetrics.density * 128).toInt()
                        val fullArtworkUrl = artwork.get("url").asString.replace("{w}", fullImageSize.toString()).replace("{h}", fullImageSize.toString())
                        val backgroundColor = artwork.get("bgColor").asString
                        val textColor1 = artwork.get("textColor1").asString
                        val textColor2 = artwork.get("textColor2").asString

                        val song = Song(songPlaybackId, songName, artistName, albumName, artworkUrl, fullArtworkUrl, durationInMillis)
                        song.backgroundColorHex = "#$backgroundColor"
                        song.textColorHex = "#$textColor1"
                        song.textColorAccentHex = "#$textColor2"
                        favoriteTracksMetadata[songPlaybackId] = song

                        val metadata = song.toMediaMetadataCompat()
                        val mediaItem = MediaBrowserCompat.MediaItem(metadata.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                        mediaItems.add(mediaItem)
                    }


                    uiThread {
                        result.sendResult(mediaItems)
                    }
                }
            },
            Response.ErrorListener { error ->
                Log.e(TAG, "$error")
                val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                result.sendResult(mediaItems)
            })
        {
            override fun getHeaders(): MutableMap<String, String> {
                return musicTokenHeaders
            }
        }
        request.retryPolicy = DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 2,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        requestQueue.add(request)
    }

    private fun createPlayList(result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val url = "https://api.music.apple.com/v1/me/library/playlists"
        val attributes = HashMap<String, String>()
        attributes["name"] = context.getString(R.string.playlist_name)
        attributes["description"] = context.getString(R.string.playlist_name)
        val body = HashMap<String, HashMap<String, String>>()
        body["attributes"] = attributes

        val request = object: JsonObjectRequest(Method.POST, url, JSONObject(body),
            Response.Listener { response ->
                doAsync {
                    val jsonObject = JsonParser().parse(response.toString()).asJsonObject
                    val playlist = jsonObject.get("data").asJsonArray.first()
                    val playlistId = (playlist as JsonObject).get("id").asString
                    requestFavoriteSongs(result, playlistId)
                }
            },
            Response.ErrorListener { error ->
                Log.e(TAG, "$error")
                val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                result.sendResult(mediaItems)
            })
            {
                override fun getHeaders(): MutableMap<String, String> {
                    return musicTokenHeaders
                }
            }

        request.retryPolicy = DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 2,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        requestQueue.add(request)
    }

    fun getSong(mediaId: String): Song? {
        return topTracksMetadata[mediaId] ?: favoriteTracksMetadata[mediaId]
    }

    fun getTrackMetadata(mediaId: String): MediaMetadataCompat? {
        val song = getSong(mediaId)
        return song?.toMediaMetadataCompat()
    }

    companion object {
        private val TAG = MediaProvider::class.java.simpleName
        private val CANONICAL_NAME = MediaProvider::class.java.canonicalName
        private const val GENRE_KIDS = 4
        private const val MEDIA_TYPE_SONGS = "songs"
        val ROOT_ID = "$CANONICAL_NAME.root"
        val TOP_SONGS_ROOT_ID = "$CANONICAL_NAME.top_songs_root"
        val FAVORITES_ROOT_ID = "$CANONICAL_NAME.favorite_songs_root"
        val EXTRA_MEDIA_REQUEST_ERROR = "$CANONICAL_NAME.media_request_error"
    }
}
