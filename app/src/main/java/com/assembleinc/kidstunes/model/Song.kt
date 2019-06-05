package com.assembleinc.kidstunes.model

import android.os.Parcel
import android.os.Parcelable
import android.support.v4.media.MediaMetadataCompat

/**
 * Created by Assemble, Inc. on 2019-05-13.
 */
data class Song(val id: String, val name: String, val artistName: String, val albumName: String,
                val artworkUrl: String, val fullArtworkUrl: String, val durationInMillis: Long): Parcelable {

    var backgroundColorHex: String = ""
    var textColorHex: String = ""
    var textColorAccentHex: String = ""

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong()
    ) {
        backgroundColorHex = parcel.readString() ?: ""
        textColorHex = parcel.readString() ?: ""
        textColorAccentHex = parcel.readString() ?: ""
    }

    fun toMediaMetadataCompat(): MediaMetadataCompat {
        val metadataBuilder = MediaMetadataCompat.Builder()
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumName)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistName)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name)
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationInMillis)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artworkUrl)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, fullArtworkUrl)
        return metadataBuilder.build()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(artistName)
        parcel.writeString(albumName)
        parcel.writeString(artworkUrl)
        parcel.writeString(fullArtworkUrl)
        parcel.writeLong(durationInMillis)
        parcel.writeString(backgroundColorHex)
        parcel.writeString(textColorHex)
        parcel.writeString(textColorAccentHex)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song {
            return Song(parcel)
        }

        override fun newArray(size: Int): Array<Song?> {
            return arrayOfNulls(size)
        }

        @JvmStatic
        fun newInstance(mediaMetadataCompat: MediaMetadataCompat): Song {
            val songId = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            val songName = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            val artistName = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            val albumName = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
            val artworkUrl = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            val fullArtworkUrl = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)
            val durationInMillis = mediaMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            return Song(songId, songName, artistName, albumName, artworkUrl, fullArtworkUrl, durationInMillis)
        }
    }
}