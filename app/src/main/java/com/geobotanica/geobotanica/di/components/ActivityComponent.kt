package com.geobotanica.geobotanica.di.components

import com.geobotanica.geobotanica.di.PerActivity
import com.geobotanica.geobotanica.di.modules.ActivityModule
import com.geobotanica.geobotanica.ui.BaseFragment
import com.geobotanica.geobotanica.ui.GpsCompoundView
import com.geobotanica.geobotanica.ui.map.MapActivity
import com.geobotanica.geobotanica.ui.map.MapFragment
import com.geobotanica.geobotanica.ui.new_plant.NewPlantFragment
import dagger.Component

@PerActivity
@Component(
    dependencies = [ApplicationComponent::class],
    modules = [ActivityModule::class]
)
interface ActivityComponent {
    fun inject(activity: MapActivity)
    fun inject(fragment: BaseFragment)
    fun inject(newPlantFragment: NewPlantFragment)
    fun inject(mapFragment: MapFragment)
    fun inject(gpsViewGroup: GpsCompoundView)
}
