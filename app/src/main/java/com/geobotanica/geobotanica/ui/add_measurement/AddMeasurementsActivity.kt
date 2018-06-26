package com.geobotanica.geobotanica.ui.add_measurement

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.Toast
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.data.entity.Measurement
import com.geobotanica.geobotanica.data.entity.Photo
import com.geobotanica.geobotanica.data.entity.Plant
import com.geobotanica.geobotanica.data.repo.LocationRepo
import com.geobotanica.geobotanica.data.repo.MeasurementRepo
import com.geobotanica.geobotanica.data.repo.PhotoRepo
import com.geobotanica.geobotanica.data.repo.PlantRepo
import com.geobotanica.geobotanica.ui.BaseActivity
import com.geobotanica.geobotanica.util.Lg
import kotlinx.android.synthetic.main.activity_measurements.*
import kotlinx.android.synthetic.main.gps_compound_view.view.*
import kotlinx.android.synthetic.main.measurement_compound_view.view.*
import javax.inject.Inject

// TODO: Carry location through if held prior
class AddMeasurementsActivity : BaseActivity() {
    @Inject lateinit var plantRepo: PlantRepo
    @Inject lateinit var locationRepo: LocationRepo
    @Inject lateinit var photoRepo: PhotoRepo
    @Inject lateinit var measurementRepo: MeasurementRepo

    override val name = this.javaClass.name.substringAfterLast('.')
    private var userId = 0L
    private var plantType = 0
    private var photoFilePath: String = ""
    private var commonName: String = ""
    private var latinName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurements)

        activityComponent.inject(this)

        userId = intent.getLongExtra(getString(R.string.extra_user_id), -1L)
        plantType = intent.getIntExtra(getString(R.string.extra_plant_type), -1)
        photoFilePath = intent.getStringExtra(getString(R.string.extra_plant_photo_path))
        commonName = intent.getStringExtra(getString(R.string.extra_plant_common_name))
        latinName = intent.getStringExtra(getString(R.string.extra_plant_latin_name))
    }

    override fun onStart() {
        super.onStart()

        heightMeasure.textView.text = resources.getString(R.string.height)
        diameterMeasure.textView.text = resources.getString(R.string.diameter)
        if (plantType == Plant.Type.TREE.ordinal)
            trunkDiameterMeasure.textView.text = resources.getString(R.string.trunk_diameter)
        else
            trunkDiameterMeasure.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        measurementsSwitch.setOnCheckedChangeListener(::onToggleAddMeasurement)
        measurementRadioGroup.setOnCheckedChangeListener(::onRadioButtonChecked)
        fab.setOnClickListener(::onFabPressed)
    }

    private fun onToggleAddMeasurement(buttonView: CompoundButton, isChecked: Boolean) {
        Lg.d("onToggleHoldPosition(): isChecked=$isChecked")
        if (isChecked) {
            manualRadioButton.isEnabled = true
            assistedRadioButton.isEnabled = true
            heightMeasure.visibility = View.VISIBLE
            diameterMeasure.visibility = View.VISIBLE
            if (plantType == Plant.Type.TREE.ordinal)
                trunkDiameterMeasure.visibility = View.VISIBLE
        } else {
            manualRadioButton.isEnabled = false
            assistedRadioButton.isEnabled = false
            heightMeasure.visibility = View.GONE
            diameterMeasure.visibility = View.GONE
            trunkDiameterMeasure.visibility = View.GONE
        }
    }

    private fun onRadioButtonChecked(radioGroup: RadioGroup, checkedId: Int) {
        when (checkedId) {
            manualRadioButton.id -> {
                Lg.d("onRadioButtonChecked(): Manual")
                heightMeasure.visibility = View.VISIBLE
                diameterMeasure.visibility = View.VISIBLE

                if (plantType == Plant.Type.TREE.ordinal)
                    trunkDiameterMeasure.visibility = View.VISIBLE
            }
            assistedRadioButton.id -> {
                Lg.d("onRadioButtonChecked(): Assisted")
                heightMeasure.visibility = View.GONE
                diameterMeasure.visibility = View.GONE
                trunkDiameterMeasure.visibility = View.GONE
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

        val plant = Plant(userId, plantType, commonName, latinName)
        plant.id = plantRepo.insert(plant)
        Lg.d("Plant: $plant (id=${plant.id})")

        val photo = Photo(userId, plant.id, Photo.Type.COMPLETE.ordinal, photoFilePath)
        photo.id = photoRepo.insert(photo)
        Lg.d("Photo: $photo (id=${photo.id})")

        gps.currentLocation?.let {
            it.plantId = plant.id
            it.id = locationRepo.insert(it)
            Lg.d("Location: $it (id=${it.id})")
        }

        if (measurementsSwitch.isChecked) {
            val height = heightMeasure.editText.text.toString().toFloat()
            val diameter = diameterMeasure.editText.text.toString().toFloat()
            val trunkDiameter = trunkDiameterMeasure.editText.text.toString().toFloatOrNull()
            measurementRepo.insert(Measurement(userId, plant.id, Measurement.Type.HEIGHT.ordinal, height))
            measurementRepo.insert(Measurement(userId, plant.id, Measurement.Type.DIAMETER.ordinal, diameter))
            trunkDiameter?.let {
                measurementRepo.insert(Measurement(userId, plant.id, Measurement.Type.TRUNK_DIAMETER.ordinal, trunkDiameter))
            }
            measurementRepo.getAllMeasurementsOfPlant(plant.id).forEach {
                Lg.d("Measurement: $it (id=${it.id})")
            }
        }

        Toast.makeText(this, "Plant saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun isMeasurementEmpty(): Boolean {
        return heightMeasure.editText.text.isEmpty() ||
                diameterMeasure.editText.text.isEmpty() ||
                (plantType == Plant.Type.TREE.ordinal && trunkDiameterMeasure.editText.text.isEmpty() )
    }
}
