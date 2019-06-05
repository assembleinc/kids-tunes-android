# Kids Tunes With Apple MusicKit - Android

`kids-tunes` is a sample Apple MusicKit project that demonstrates how to integrate Apple MusicKit with your iOS, Android or web app.  A fully-functional sample app is available for each platform, and includes a platform-specific README with a simple walkthrough of the main features.

This README will provide a brief overview and install steps.

Note: as of 6/3/2019 we’ve experienced periodic latency issues and sporadic errors. You may experience similar issues when working with the MusicKit API.

## Requirements
- Android Lollipop (5.0) or later

## Installation
1. Clone or download this project repo to your local system
2. Setup the latest version of Android Studio
3. Load the project into Android Studio
4. Create a new values resource file named tokens.xml inside app/res/values/ with your Apple Developer Token:
 ```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="developer_token">YOUR_DEVELOPER_TOKEN</string>
</resources>
 ```
5. Compile the project
6. Load the APK on your Android device.  It will not run correctly on a simulator.

## Apple Music Pre-Requisites
In order for the sample app to work correctly with the Apple Music APIs, you will need to generate your own **Apple Music ID** and **Apple Music Keys**. These will be tied to an existing Apple Developer account and an active Apple Music Subscription. Only one Apple Music ID is needed, and can be used across all 3 platforms. A unique Apple Music Key will be needed for each environment (Dev, Prod), but can be used across all 3 platforms. Your app will fail to run without these generated and entered into your sample app.

### Generate Apple Music ID

1. Go to your [Apple Developer Portal](https://developer.apple.com/account)
2. Navigate to Certificates, Identifiers & Profiles
3. Select Identifiers on the left
4. Hit the + to Add a new Identifier
5. Select Music IDs from the list and hit Continue
6. Follow instructions to generate your Music ID

### Generate Apple MusicKit Developer Token

1. Go to your [Apple Developer Portal](https://developer.apple.com/account)
2. Navigate to Keys
3. Hit the + to Add a new Key for each Environment you need
4. Follow instructions to generate your MusicKit Key, selecting the Music ID you generated in the previous section
5. This will be used as your Developer Token in the app

## About this app
This repository provides a starter project, called Kids Tunes, that will allow a user to listen to children's music from their personal Apple Music account. The main features allow you to:
  - Sign-in with your Apple Music Subscription Account
  - Pull the top songs from the children's music genre in the Apple Music catalog
  - Play/Pause/Rewind/Fast Forward songs
  - Mark songs as Favorites and add them to a Favorites playlist

## Platform specific documentation
Detailed documentation is available in each platform's repository.
- [iOS Project](https://github.com/assembleinc/kids-tunes-ios)
- [Android Project](https://github.com/assembleinc/kids-tunes-android)
- [Web Project](https://github.com/assembleinc/kids-tunes-react)

### About the creators
This app was created by [Assemble Inc.](https://assembleinc.com) as a demonstration project for WWDC 2019.

## Want to contribute?
Feel free to submit a PR for review.

---

# Android Key Functionality Walkthrough

## Application Features

The `kids-tunes` project has 4 main features that interact with Apple Music: Authentication, Pulling Catalog Items, Music Playback, and Playlist Management.

### Authentication
The Apple Music APIs require authentication before they can be accessed.  The app will need an Apple Developer Token, and each user of the app will need to login to the Apple Music service to get their Apple Music Subscription User Token.

#### Developer Token (Bearer Token)
Your Apple Developer Token will be used as the Bearer Token for all your calls, and must be present.  This should be set in your tokens.xml file.

#### User Token
To get the individual User Token, the user will need to authenticate with their Apple Music Subscription login.  The Apple Music app needs to already be installed on the device, and will take care of authentication, after being called from the app. After the user is properly authenticated, the user token is returned and saved for use throughout the rest of the session.  
```kotlin
class SignInActivity: AppCompatActivity() {
    private var authenticationManager = AuthenticationFactory.createAuthenticationManager(this)
    ...
    private fun signIn() {
        val intent = authenticationManager.createIntentBuilder(getString(R.string.developer_token))
            .setHideStartScreen(true)
            .setStartScreenMessage("Connect with apple music!")
            .build()
        startActivityForResult(intent, REQUEST_CODE_APPLE_MUSIC_AUTH)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_APPLE_MUSIC_AUTH) {
            val result = authenticationManager.handleTokenResult(data)
            if (result.isError) {
                val error = result.error
                Log.e(TAG, "error: $error")
            }
            else {
                saveToken(result.musicUserToken)
                startMainActivity()
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    ...
    companion object {
        private const val REQUEST_CODE_APPLE_MUSIC_AUTH = 1
    }
}
```
### Pulling Catalog Items
The Kids Tunes app is specifically set to only pull music from the Apple Music Catalog that is tagged with the 'children's music' genre. For the sake of this sample app, the genre is hard-coded to 'kids'.

You'll first need to set the bearer token on your call to use your Apple Developer Token.
  
```kotlin
class LocalMediaProvider(private val context: Context,
                         private val requestQueue: RequestQueue,
                         private val appleMusicTokenProvider: AppleMusicTokenProvider) {
    private var topTracksMetadata = TreeMap<String, MediaMetadataCompat>()
    private val authorizationHeaders: MutableMap<String, String>
    get() {
        val headers = HashMap<String, String>()
        headers["Authorization"] = "Bearer ${appleMusicTokenProvider.developerToken}"
        return headers
    }
    private val musicTokenHeaders: MutableMap<String, String>
    get() {
        val headers = authorizationHeaders
        headers["Music-User-Token"] = appleMusicTokenProvider.userToken
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
                        val song = it as JsonObject
                        val songPlaybackId = song.get("id").asString
                        val songAttributes = song.get("attributes").asJsonObject
                        val songName = songAttributes.get("name").asString
                        ...
                        fullImageSize.toString()).replace("{h}", fullImageSize.toString())
                        val metadataBuilder = MediaMetadataCompat.Builder()
                        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, songPlaybackId)
                        ...
                        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, fullArtworkUrl)
                        val metadata = metadataBuilder.build()
                        topTracksMetadata[songPlaybackId] = metadata
                        val mediaItem = MediaBrowserCompat.MediaItem(metadata.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                        mediaItems.add(mediaItem)
                    }
                    uiThread { result.sendResult(mediaItems) }
                }
            },
            Response.ErrorListener {
                val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
                topTracksMetadata.forEach {
                    val mediaItem = MediaBrowserCompat.MediaItem(it.value.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                    mediaItems.add(mediaItem)
                }
                result.sendResult(mediaItems)
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
    companion object {
        private const val GENRE_KIDS = 4
        private const val MEDIA_TYPE_SONGS = "songs"
    }
}
```
### Music Playback
Now that you've pulled the list of all the songs from the API, you'll want to be able to control playing the song.  You'll use an instance of com.apple.android.music.playback.controller.MediaPlayerController.
```kotlin
val tokenProvider = object : TokenProvider {
                    override fun getDeveloperToken(): String? = getString(R.string.developer_token)
                    override fun getUserToken(): String? = keychainService.fetch(KeychainService.KEY_MUSIC_USER_TOKEN)
                }
val mediaPlayerController = MediaPlayerControllerFactory.createLocalController(applicationContext, tokenProvider)
val queueProviderBuilder = CatalogPlaybackQueueItemProvider.Builder()
                val tracksIds = queueItems.map { it.description.mediaId }
                queueProviderBuilder.items(MediaItemType.SONG, *tracksIds.toTypedArray())
                queueProviderBuilder.startItemIndex(queueIndex)
                mediaPlayerController.prepare(queueProviderBuilder.build(), true)
```
### Playlist Management

```kotlin
val tokenProvider = object : TokenProvider {
                    override fun getDeveloperToken(): String? = getString(R.string.developer_token)
                    override fun getUserToken(): String? = keychainService.fetch(KeychainService.KEY_MUSIC_USER_TOKEN)
                }
val mediaPlayerController = MediaPlayerControllerFactory.createLocalController(applicationContext, tokenProvider)
val queueProviderBuilder = CatalogPlaybackQueueItemProvider.Builder()
                val tracksIds = queueItems.map { it.description.mediaId }
                queueProviderBuilder.items(MediaItemType.SONG, *tracksIds.toTypedArray())
                queueProviderBuilder.startItemIndex(queueIndex)
                mediaPlayerController.prepare(queueProviderBuilder.build(), true)
```

To see more, visit [Apple MusicKit Documentation](https://developer.apple.com/documentation/musickitjs).
