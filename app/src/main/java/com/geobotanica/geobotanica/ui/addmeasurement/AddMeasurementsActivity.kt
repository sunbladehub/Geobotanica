package com.geobotanica.geobotanica.ui.addmeasurement

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.Toast
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.data.entity.*
import com.geobotanica.geobotanica.data.repo.PlantLocationRepo
import com.geobotanica.geobotanica.data.repo.MeasurementRepo
import com.geobotanica.geobotanica.data.repo.PhotoRepo
import com.geobotanica.geobotanica.data.repo.PlantRepo
import com.geobotanica.geobotanica.ui.BaseActivity
import com.geobotanica.geobotanica.util.Lg
import kotlinx.android.synthetic.main.activity_measurements.*
import kotlinx.android.synthetic.main.gps_compound_view.view.*
import kotlinx.android.synthetic.main.measurement_compound_view.view.*
import javax.inject.Inject

class AddMeasurementsActivity : BaseActivity() {
    @Inject lateinit var plantRepo: PlantRepo
    @Inject lateinit var plantLocationRepo: PlantLocationRepo
    @Inject lateinit var photoRepo: PhotoRepo
    @Inject lateinit var measurementRepo: MeasurementRepo

    override val name = this.javaClass.name.substringAfterLast('.')
    private var userId = 0L
    private lateinit var plantType: Plant.Type
    private lateinit var photoFilePath: String
    private var commonName: String? = null
    private var latinName: String? = null
    private var plantLocation: Location? = null

    // TODO: Use Toolbar instead of ActionBar. Maybe use single activity, multiple fragments for new plant creation
    // TODO: Handle back button better. Delete temp photo if needed, allow edit prev. activity, etc.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurements)

        activityComponent.inject(this)

        userId = intent.getLongExtra(getString(R.string.extra_user_id), -1L)
        plantType = PlantTypeConverter.toPlantType(intent.getIntExtra(getString(R.string.extra_plant_type), -1))
        photoFilePath = intent.getStringExtra(getString(R.string.extra_plant_photo_path))
        commonName = intent.getStringExtra(getString(R.string.extra_plant_common_name))
        latinName = intent.getStringExtra(getString(R.string.extra_plant_latin_name))
        plantLocation = intent.getSerializableExtra(getString(R.string.extra_location)) as? Location
        Lg.d("Intent extras: userId=$userId, plantType=$plantType, commonName=$commonName, " +
                "latinName=$latinName, location=$plantLocation, photoFilePath=$photoFilePath")
        plantLocation?.let { gps.setLocation(it) }
    }

    override fun onStart() {
        super.onStart()

        heightMeasurement.textView.text = resources.getString(R.string.height)
        diameterMeasurement.textView.text = resources.getString(R.string.diameter)
        if (plantType == Plant.Type.TREE)
            trunkDiameterMeasurement.textView.text = resources.getString(R.string.trunk_diameter)
        else
            trunkDiameterMeasurement.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        measurementsSwitch.setOnCheckedChangeListener(::onToggleAddMeasurement)
        measurementRadioGroup.setOnCheckedChangeListener(::onRadioButtonChecked)
        fab.setOnClickListener(::onFabPressed)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onToggleAddMeasurement(buttonView: CompoundButton, isChecked: Boolean) {
        Lg.d("onToggleHoldPosition(): isChecked=$isChecked")
        if (isChecked) {
            manualRadioButton.isEnabled = true
            assistedRadioButton.isEnabled = true
            heightMeasurement.visibility = View.VISIBLE
            diameterMeasurement.visibility = View.VISIBLE
            if (plantType == Plant.Type.TREE)
                trunkDiameterMeasurement.visibility = View.VISIBLE
        } else {
            manualRadioButton.isEnabled = false
            assistedRadioButton.isEnabled = false
            heightMeasurement.visibility = View.GONE
            diameterMeasurement.visibility = View.GONE
            trunkDiameterMeasurement.visibility = View.GONE
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onRadioButtonChecked(radioGroup: RadioGroup, checkedId: Int) {
        when (checkedId) {
            manualRadioButton.id -> {
                Lg.d("onRadioButtonChecked(): Manual")
                heightMeasurement.visibility = View.VISIBLE
                diameterMeasurement.visibility = View.VISIBLE

                if (plantType == Plant.Type.TREE)
                    trunkDiameterMeasurement.visibility = View.VISIBLE
            }
            assistedRadioButton.id -> {
                Lg.d("onRadioButtonChecked(): Assisted")
                heightMeasurement.visibility = View.GONE
                diameterMeasurement.visibility = View.GONE
                trunkDiameterMeasurement.visibility = View.GONE
            }
        }
    }

    // TODO: Push validation into the repo?
    private fun onFabPressed(view: View) {
        if (!gps.gpsSwitch.isEnabled) {
            Snackbar.make(view, "Wait for GPS fix", Snackbar.LENGTH_LONG).setAction("Action", null).show()
            return
        }
        if (!gps.gpsSwitch.isChecked) {
            Snackbar.make(view, "Plant position must be held", Snackbar.LENGTH_LONG).setAction("Action", null).show()
            return
        }
        if (measurementsSwitch.isChecked && isMeasurementEmpty() ) {
            Snackbar.make(view, "Provide plant measurements", Snackbar.LENGTH_LONG).setAction("Action", null).show()
            return
        }
        Lg.d("Saving plant to database now...")
        val plant = Plant(userId, plantType, commonName, latinName)
        plant.id = plantRepo.insert(plant)
        Lg.d("Plant: $plant (id=${plant.id})")

        val photo = Photo(userId, plant.id, Photo.Type.COMPLETE, photoFilePath)
        photo.id = photoRepo.insert(photo)
        Lg.d("Photo: $photo (id=${photo.id})")

        gps.currentLocation?.let {
            val plantLocation = PlantLocation(plant.id, it)
            plantLocation.id = plantLocationRepo.insert(plantLocation)
            Lg.d("PlantLocation: $plantLocation (id=${plantLocation.id})")
        }

        if (measurementsSwitch.isChecked) {
            val height = heightMeasurement.getInCentimeters()
            val diameter = diameterMeasurement.getInCentimeters()
            val trunkDiameter = trunkDiameterMeasurement.getInCentimeters()
            measurementRepo.insert(Measurement(userId, plant.id, Measurement.Type.HEIGHT, height))
            measurementRepo.insert(Measurement(userId, plant.id, Measurement.Type.DIAMETER, diameter))
            if (trunkDiameter != 0F)
                measurementRepo.insert(Measurement(userId, plant.id, Measurement.Type.TRUNK_DIAMETER, trunkDiameter))
//            measurementRepo.getAllMeasurementsOfPlant(plant.id).forEachIndexed { i, measurement ->
//                Lg.d("#$i $measurement (id=${measurement.id})")
//            }
        }

        Toast.makeText(this, "Plant saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun isMeasurementEmpty(): Boolean {
        return heightMeasurement.editText.text.isEmpty() ||
                diameterMeasurement.editText.text.isEmpty() ||
                (plantType == Plant.Type.TREE && trunkDiameterMeasurement.editText.text.isEmpty() )
    }
}
