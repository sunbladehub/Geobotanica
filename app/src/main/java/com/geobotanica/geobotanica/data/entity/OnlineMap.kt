package com.geobotanica.geobotanica.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.geobotanica.geobotanica.network.FileDownloader.DownloadStatus.NOT_DOWNLOADED
import com.geobotanica.geobotanica.util.capitalizeWords
import com.geobotanica.geobotanica.util.replacePrefix
import com.squareup.moshi.JsonClass


@Entity(tableName = "maps",
// NOTE: Disabled foreign key constraint as asynchronous nature of asset downloads causes crashes
//    foreignKeys = [ForeignKey(
//        entity = OnlineMapFolder::class,
//        parentColumns = ["id"],
//        childColumns = ["parentFolderId"],
//        onDelete = ForeignKey.CASCADE)
//    ],
    indices = [
        Index(value = ["url"]),
        Index(value = ["parentFolderId"]),
        Index(value = ["status"])
    ])
@JsonClass(generateAdapter = true)
data class  OnlineMap(
        val url: String,
        val size: String,
        val timestamp: String,
        val parentFolderId: Long?,

//        @Transient // Exclude from JSON serialization // TODO: REMOVE AFTER SCRAPER IS MOVED TO SERVER
//        @ColumnInfo(name = "status") // Force include in Room DB, despite @Transient // TODO: REMOVE AFTER SCRAPER IS MOVED TO SERVER
        var status: Long = NOT_DOWNLOADED
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0L

    val filename: String
        get() = url.substringAfterLast('/')

    val printName: String
        get() = filename
            .replacePrefix("us-", "US ")
            .removeSuffix(".map")
            .removeSuffix(">") // Present if filename is too long on scraped Mapsforge website
            .replace('-', ' ')
            .capitalizeWords() +
            " ($size)"
}




@Entity(tableName = "mapFolders",
    foreignKeys = [ForeignKey(
        entity = OnlineMapFolder::class,
        parentColumns = ["id"],
        childColumns = ["parentFolderId"],
        onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["url"]),
        Index(value = ["parentFolderId"])
    ])
@JsonClass(generateAdapter = true)
data class  OnlineMapFolder(
        val url: String,
        val timestamp: String,
        val parentFolderId: Long?
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0L

    val name: String
        get() = url
                .removeSuffix("/")
                .substringAfterLast('/')

    val printName: String
        get() = name
                .replace('-', ' ')
                .replacePrefix("us", "US") // FOLDER ONLY
                .capitalizeWords()
}