package com.geobotanica.geobotanica.data.repo

import androidx.lifecycle.LiveData
import com.geobotanica.geobotanica.data.dao.PlantLocationDao
import com.geobotanica.geobotanica.data.entity.PlantLocation
import javax.inject.Inject

class PlantLocationRepo @Inject constructor(private val plantLocationDao: PlantLocationDao) {
    fun insert(plantLocation: PlantLocation): Long = plantLocationDao.insert(plantLocation)

    fun get(id: Long): LiveData<PlantLocation> = plantLocationDao.get(id)

    fun getPlantLocation(plantId: Long): LiveData<PlantLocation> = plantLocationDao.getPlantLocation(plantId)
}