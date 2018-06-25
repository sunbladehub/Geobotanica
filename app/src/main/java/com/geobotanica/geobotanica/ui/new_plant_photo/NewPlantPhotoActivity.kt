package com.geobotanica.geobotanica.ui.new_plant_photo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.widget.Toast
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.ui.BaseActivity
import com.geobotanica.geobotanica.ui.new_plant_name.NewPlantNameActivity
import com.geobotanica.geobotanica.util.Lg
import kotlinx.android.synthetic.main.fragment_new_plant.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class NewPlantPhotoActivity : BaseActivity() {
    override val name = this.javaClass.name.substringAfterLast('.')

    private var userId = 0L
    private var plantType = 0
    private val requestTakePhoto = 2
    private var photoFilePath: String = ""
    private var oldPhotoFilePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_plant_photo)

        userId = intent.getLongExtra(getString(R.string.extra_user_id), -1L)
        plantType = intent.getIntExtra(getString(R.string.extra_plant_type), -1)
        Lg.d("Intent extras: userId=$userId, plantType=$plantType")



        val capturePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        capturePhotoIntent.resolveActivity(this.packageManager)

        try {
            val photoFile = createImageFile()
            val authorities = "${this.packageName}.fileprovider"
            Lg.d("authorities = $authorities")
            val photoUri: Uri? = FileProvider.getUriForFile(this, authorities, photoFile)
            Lg.d("photoUri = ${photoUri?.path}")
            capturePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startActivityForResult(capturePhotoIntent, requestTakePhoto)
        } catch (e: IOException) {
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show()
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            requestTakePhoto -> {
                if (resultCode == Activity.RESULT_OK) {
                    Lg.d("New photo received")
//                    plantPhoto.setImageBitmap(getScaledBitmap())
                    if (oldPhotoFilePath.isNotEmpty()) {
                        Lg.d("Deleting old photo: $oldPhotoFilePath")
                        Lg.d("Delete photo result = ${File(oldPhotoFilePath).delete()}")
                        oldPhotoFilePath = ""
                    }
                    oldPhotoFilePath = photoFilePath

                    val intent = Intent(this, NewPlantNameActivity::class.java)
                            .putExtra(getString(R.string.extra_user_id), userId)
                            .putExtra(getString(R.string.extra_plant_type), plantType)
                            .putExtra(getString(R.string.extra_plant_photo_path), photoFilePath)
                    startActivity(intent)
                    finish()

                } else {
                    Toast.makeText(this, "Error taking photo", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(this, "Unrecognized request code", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val fileName: String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val storageDir: File? = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
//        /storage/emulated/0/Android/data/com.geobotanica/files/Pictures
        val image: File = File.createTempFile(fileName, ".jpg", storageDir)
        photoFilePath = image.absolutePath
        return image
    }
}
