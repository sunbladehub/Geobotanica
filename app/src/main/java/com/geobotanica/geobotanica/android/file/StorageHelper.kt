package com.geobotanica.geobotanica.android.file

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import com.geobotanica.geobotanica.data.entity.OnlineAsset
import com.geobotanica.geobotanica.util.GbTime
import com.geobotanica.geobotanica.util.Lg
import com.geobotanica.geobotanica.util.asFilename
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageHelper @Inject constructor(val appContext: Context) {

    @SuppressLint("UsableSpace")
    fun isStorageAvailable(onlineAsset: OnlineAsset): Boolean {
        val dir = File(getRootPath(onlineAsset))
        return dir.usableSpace > 2 * onlineAsset.decompressedSize
    }

    fun mkdirs(onlineAsset: OnlineAsset) = File(getLocalPath(onlineAsset)).mkdirs()

    fun getExtStorageRootDir() = "/sdcard/"

    fun getExtFilesDir() = appContext.getExternalFilesDir(null)?.absolutePath
    // /storage/emulated/0/Android/data/com.geobotanica/files/

    fun getDownloadPath() = getExtFilesDir()

    fun getPicturesDir() = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath
    //  /storage/emulated/0/Android/data/com.geobotanica/files/Pictures/

    fun getUserPhotosDir() = "${getPicturesDir()}"
    //  /storage/emulated/0/Android/data/com.geobotanica/files/Pictures/

    fun createPhotoFile(): File {
        val filename: String = GbTime.now().asFilename()
        val photosDir = File(getUserPhotosDir())
        photosDir.mkdirs()
        Lg.d("StorageHelper.createPhotoFile(): $photosDir/$filename.jpg")
        return File.createTempFile(filename, ".jpg", photosDir)
    }

    fun absolutePath(file: File) = file.absolutePath

    fun deleteFile(uri: String): Boolean = File(uri).delete()

    fun getMapsPath() = "${getDownloadPath()}/maps"

    fun getLocalPath(onlineAsset: OnlineAsset): String =
            getRootPath(onlineAsset) + "/${onlineAsset.relativePath}"

    private fun getRootPath(onlineAsset: OnlineAsset): String {
        return if (onlineAsset.isInternalStorage)
            appContext.filesDir.absolutePath.removeSuffix("/files")
        else
            appContext.getExternalFilesDir(null)?.absolutePath  ?: throw IllegalStateException()
    }

    fun isAssetAvailable(asset: OnlineAsset): Boolean {
        val file = File(getLocalPath(asset), asset.filename)
        return file.exists() && file.length() == asset.decompressedSize
    }

    fun photoUriFrom(filename: String): String = "${getUserPhotosDir()}/$filename"
}
