package com.geobotanica.geobotanica.ui.plantdetail

import androidx.lifecycle.ViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModelProvider
import com.geobotanica.geobotanica.data.repo.*

class PlantDetailViewModelFactory @Inject constructor(
        private val userRepo: UserRepo,
        private val plantRepo: PlantRepo,
        private val  plantLocationRepo: PlantLocationRepo,
        private val  photoRepo: PhotoRepo,
        private val  measurementRepo: MeasurementRepo
) : ViewModelProvider.Factory {
    var plantId = 0L

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PlantDetailViewModel(userRepo, plantRepo, plantLocationRepo, photoRepo, measurementRepo).also {
            it.plantId = this.plantId  // Manual field injection of dynamic dependency (better approaches exist)
        } as T
    }
}