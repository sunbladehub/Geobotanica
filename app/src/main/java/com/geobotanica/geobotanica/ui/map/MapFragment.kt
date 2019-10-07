package com.geobotanica.geobotanica.ui.map

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.data.GbDatabase
import com.geobotanica.geobotanica.data.entity.Location
import com.geobotanica.geobotanica.data.entity.User
import com.geobotanica.geobotanica.network.FileDownloader
import com.geobotanica.geobotanica.ui.BaseFragment
import com.geobotanica.geobotanica.ui.BaseFragmentExt.getViewModel
import com.geobotanica.geobotanica.ui.ViewModelFactory
import com.geobotanica.geobotanica.ui.map.MapViewModel.GpsFabDrawable.GPS_FIX
import com.geobotanica.geobotanica.util.IdDiffer.computeDiffs
import com.geobotanica.geobotanica.util.Lg
import com.geobotanica.geobotanica.util.get
import com.geobotanica.geobotanica.util.put
import kotlinx.android.synthetic.main.fragment_map.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// TODO: Force location markers to be drawn on top of plant markers (sometimes incorrect after delete plant)
// -> Re-adding location markers seems to be ineffective -> Ask on Github?

// TODO: Fix fragment anim not showing if popUpTo non-null

// TODO: Use Dispatchers.Default for JSON deserialization

// LONG TERM
// TODO: Add photoType + editPhoto buttons in PlantDetails image (like confirm frag)
// TODO: Check that coroutine result is handled properly in dialog where user taps outside to close (no result given to getStatus)
// TODO: Check Google Developer samples for best practices
// - fragment bundle -> viewModel save/restore
// - Where to launch coroutines? Can launch in vm even if result to be returned to fragment?
// TODO: Use Okio everywhere
// TODO: Check for memory leaks. Is coroutine holding on to Warning Dialog?
// TODO: Login screen
// https://developer.android.com/training/id-auth/identify.html
// https://developer.android.com/training/id-auth/custom_auth
// TODO: Group nearby markers into clusters
// TODO: Use MediaStore to store photos. They should apppear in Gallery as an album.
// TODO: Use Koin for DI to simplify testing?
// TODO: Use interfaces instead of concrete classes for injected dependencies where appropriate
// TODO: Make custom camera screen so Espresso can be used for UI testing (New CameraX API)
// TODO: Implement dark theme
// TODO: Try using object detection for assisted plant measurements
// LONG TERM NIT PICK
// TODO: Get rid of warning on using null as root layout in inflate calls in onCreateDialog()
// TODO: Learn how to use only the keyboard
// TODO: Check that no hard-coded strings are used -> resources.getString(R.string.trunk_diameter)
// TODO: Use code reformatter:
// Check tabs on fn params / data class
// Subclass in class declaration: colon needs space on both sides
// TODO: Use vector graphics for all icons where possible
// TODO: Decide on Lg.v/d/i etc.
// TODO: Double check proper placement of methods in lifecycle callbacks
    // https://developer.android.com/guide/components/activities/activity-lifecycle
// https://github.com/osmdroid/osmdroid/wiki/Offline-Map-Tiles
// TODO: Fix screen rotation crashes and consider landscape layouts
// TODO: Figure out how to resume app state after onStop, then process death (e.g. home or switch app, then kill app)
// https://medium.com/google-developers/viewmodels-persistence-onsaveinstancestate-restoring-ui-state-and-loaders-fc7cc4a6c090
// TODO: Expose Location events as LiveData in LocationService
// TODO: Consider allowing app to be installed on external storage

// DEFERRED
// TODO: Show PlantType icon in map bubble (and PlantDetail?)


class MapFragment : BaseFragment() {
    @Inject lateinit var viewModelFactory: ViewModelFactory<MapViewModel>
    @Inject lateinit var fileDownloader: FileDownloader

    private lateinit var viewModel: MapViewModel

    private var locationMarker: LocationMarker? = null
    private var locationPrecisionCircle: LocationCircle? = null

    private val sharedPrefsMapLatitude = "mapLatitude"
    private val sharedPrefsMapLongitude = "mapLongitude"
    private val sharedPrefsMapZoomLevel = "MapZoomLevel"
    private val sharedPrefsWasGpsSubscribed = "gpsUpdatesSubscribed"

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity.applicationComponent.inject(this)

        lifecycleScope.launch(Dispatchers.IO) {
//            GbDatabase.getInstance(appContext).clearAllTables()
            GbDatabase.getInstance(appContext).userDao().insert(User(1, "Guest")) // TODO: Move to Login Screen
        }
        viewModel = getViewModel(viewModelFactory) {
            userId = 1L // TODO: Retrieve userId from LoginFragment Navigation bundle (or shared prefs)
        }
        loadSharedPrefsToViewModel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_map, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showToast("Settings")
                true
            }
            R.id.download_maps -> {
                mapView.mapScaleBar.isVisible = false // Prevents crash on fragment anim
                navigateTo(R.id.action_map_to_localMaps)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAfterPermissionsGranted()
    }

    private fun initAfterPermissionsGranted() {
        if (! wasPermissionGranted(ACCESS_FINE_LOCATION))
            requestPermission(ACCESS_FINE_LOCATION)
        else
            init()
    }

    override fun onStop() {
        super.onStop()

        // TODO: Remove this conditional after Login screen (permission check will happen there)
        if (wasPermissionGranted(ACCESS_FINE_LOCATION)) // Conditional required for first boot if GPS permission denied
            saveMapStateToViewModel()
        saveSharedPrefsFromViewModel()
        viewModel.unsubscribeGps()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.destroyAll()
    }

    private fun loadSharedPrefsToViewModel() {
        sharedPrefs.let { sp ->
            viewModel.let { vm ->
                vm.mapLatitude = sp.get(sharedPrefsMapLatitude, vm.mapLatitude)
                vm.mapLongitude = sp.get(sharedPrefsMapLongitude, vm.mapLongitude)
                vm.mapZoomLevel = sp.get(sharedPrefsMapZoomLevel, vm.mapZoomLevel)
                vm.wasGpsSubscribed = sp.get(sharedPrefsWasGpsSubscribed, vm.wasGpsSubscribed)
            }
        }
    }

    private fun saveSharedPrefsFromViewModel() {
        sharedPrefs.put(
            sharedPrefsMapLatitude to viewModel.mapLatitude,
            sharedPrefsMapLongitude to viewModel.mapLongitude,
            sharedPrefsMapZoomLevel to viewModel.mapZoomLevel,
            sharedPrefsWasGpsSubscribed to viewModel.wasGpsSubscribed
        )
    }

    private fun saveMapStateToViewModel() {
        viewModel.mapZoomLevel = mapView.model.mapViewPosition.zoomLevel.toInt()
        viewModel.mapLatitude = mapView.model.mapViewPosition.center.latitude
        viewModel.mapLongitude = mapView.model.mapViewPosition.center.longitude
        viewModel.wasGpsSubscribed = viewModel.isGpsSubscribed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            getRequestCode(ACCESS_FINE_LOCATION) -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Lg.i("permission.ACCESS_FINE_LOCATION: PERMISSION_GRANTED")
                    initAfterPermissionsGranted()
                } else {
                    Lg.i("permission.ACCESS_FINE_LOCATION: PERMISSION_DENIED")
                    showToast("GPS permission required") // TODO: Find better UX approach (separate screen)
                    activity.finish()
                }
            }
            else -> { }
        }
    }

    private fun init() = lifecycleScope.launch {
        defaultSharedPrefs.put(sharedPrefsIsFirstRunKey to false)
        initMap()
        viewModel.initGpsSubscribe()
        setClickListeners()
        bindViewModel()
    }

    private suspend fun initMap() {
        with(viewModel) {
            mapView.init(getDownloadedMapFileList(), mapLatitude, mapLongitude, mapZoomLevel)
        }

        coordinatorLayout.isVisible = true // TODO: Remove this after LoginScreen implemented
        locationPrecisionCircle = null
        locationMarker = null

//        if (viewModel.isFirstRun)
//            viewModel.getLastLocation()?.let { centerMapOnLocation(it, false) } // TODO: CHECK IF THIS SHOULD STAY (never worked)

        registerMapDownloadObserver()
        mapView.reloadMarkers.observe(viewLifecycleOwner, Observer { reloadMarkers() })
    }

    private fun registerMapDownloadObserver() {
        activity.downloadComplete.observe(viewLifecycleOwner, Observer { downloadId ->
            if (fileDownloader.isMap(downloadId) && ! mapView.isMapLoaded(fileDownloader.filenameFrom(downloadId))) {
                lifecycleScope.launch {
                    mapView.reloadMaps(viewModel.getDownloadedMapFileList())
                }
            }
        })
    }

    private fun reloadMarkers() {
        Lg.d("reloadMarkers()")
        viewModel.plantMarkerData.removeObserver(onPlantMarkers)
        viewModel.plantMarkerData.observe(viewLifecycleOwner, onPlantMarkers)
    }

    @Suppress("DEPRECATION")
    private fun setClickListeners() {
        gpsFab.setOnClickListener { viewModel.onClickGpsFab() }
        fab.setOnClickListener { viewModel.onClickNewPlantFab() }
    }

    private fun bindViewModel() {
        with(viewModel) {
            gpsFabIcon.observe(viewLifecycleOwner,
                Observer { @Suppress("DEPRECATION")
                    gpsFab.setImageDrawable(resources.getDrawable(it))
                }
            )
            showGpsRequiredSnackbar.observe(viewLifecycleOwner, onGpsRequiredSnackbar)
            showPlantNamesMissingSnackbar.observe(viewLifecycleOwner, onPlantNamesMissingSnackbar)
            navigateToNewPlant.observe(viewLifecycleOwner, onNavigateToNewPlant)
            plantMarkerData.observe(viewLifecycleOwner, onPlantMarkers)
            currentLocation.observe(viewLifecycleOwner, onLocation)
        }
    }

    private val onGpsRequiredSnackbar = Observer<Unit> {
        showSnackbar(R.string.gps_must_be_enabled, R.string.enable) {
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private val onPlantNamesMissingSnackbar = Observer<Unit> {
        showSnackbar(R.string.wait_for_plant_name_database_to_download)
    }

    private val onNavigateToNewPlant = Observer<Unit> {
        mapView.mapScaleBar.isVisible = false // Prevents crash on fragment anim
        navigateTo(R.id.action_map_to_newPlantPhoto, bundleOf("userId" to viewModel.userId) )
    }

//     TODO: REMOVE (Temp for NewPlantConfirmFragment)
//    private val onNavigateToNewPlant = Observer<Unit> {
//        navigateTo(R.id.newPlantConfirmFragment, createBundle() )
//    }

    // TODO: REMOVE
//    private fun createBundle(): Bundle {
//        return bundleOf(
//                userIdKey to viewModel.userId,
//                plantTypeKey to Plant.Type.TREE.flag,
//                photoUriKey to fileFromDrawable(R.drawable.photo_type_complete, "photo_type_complete"),
//                commonNameKey to "Common",
//                scientificNameKey to "Latin",
//                heightMeasurementKey to Measurement(1.0f, Units.M),
//                diameterMeasurementKey to Measurement(2.0f, Units.IN),
//                trunkDiameterMeasurementKey to Measurement(3.5f, Units.FT)
//        ).apply {
//            putSerializable(locationKey, Location(
//                49.477, -119.592, 1.0, 3.0f, 10, 20))
//        }
//    }

    private val onPlantMarkers = Observer< List<PlantMarkerData> > { newPlantMarkersData ->
        Lg.d("onPlantMarkers: $newPlantMarkersData")

        val currentPlantMarkers = mapView.layerManager.layers.filterIsInstance<PlantMarker>()
        val plantMarkerDiffs = computeDiffs(
                currentPlantMarkers.map { it.plantId }, newPlantMarkersData.map { it.plantId }
        )

//        mapView.layerManager.layers.removeAll { // CowArray -> UnsupportedOperationException!!
//            it is PlantMarker && plantMarkerDiffs.removeIds.contains(it.plantId)
//        }

        mapView.layerManager.layers.filter {
            it is PlantMarker && plantMarkerDiffs.removeIds.contains(it.plantId)
        }.forEach {
            mapView.layerManager.layers.remove(it, false)
        }
        
        mapView.layerManager.layers.addAll(
            newPlantMarkersData
                .filter { plantMarkerDiffs.insertIds.contains(it.plantId) }
                .map { PlantMarker(it, activity, mapView) },
            false)

        mapView.layerManager.layers.forEachIndexed { index, layer ->
            Lg.d("layer $index = $layer")
        }

//        forceLocationMarkerOnTop()
//        mapView.layerManager.redrawLayers()
    }

    // TODO: See if logic here can be pushed to VM
    @Suppress("DEPRECATION")
    private val onLocation = Observer<Location> {
        Lg.v("MapFragment: onLocation() = $it")
        if (it.latitude != null && it.longitude != null) { // OK in VM
            if (it.isRecent()) { // OK in VM
                gpsFab.setImageResource(GPS_FIX.drawable) // Could be LiveData
                updateLocationPrecision(it) // Could be LiveData
                if (mapView.isLocationOffScreen(it)) // Problem: Check requires map
                    mapView.centerMapOnLocation(it) // Could be LiveData
                viewModel.setStaleGpsLocationTimer() // OK in VM
            }
            updateLocationMarker(it) // Could be LiveData
            mapView.layerManager.redrawLayers()
        }
        // TODO: Update satellitesInUse
    }

    private fun updateLocationPrecision(location: Location) {
        if (locationPrecisionCircle == null)
            locationPrecisionCircle = LocationCircle(mapView)
        locationPrecisionCircle?.updateLocation(location)
    }

    private fun updateLocationMarker(location: Location) {
            createLocationMarker()
        locationMarker?.updateLocation(location)
    }

    private fun createLocationMarker() {
        locationMarker = LocationMarker(resources.getDrawable(R.drawable.person, null), mapView)
        locationMarker?.showLocationMarkerToast?.observe(viewLifecycleOwner, Observer {
            showToast(R.string.you_are_here)
        })
    }
}


//    private fun forceLocationMarkerOnTop() {
//        locationPrecisionCircle?.let {
//            mapView.layerManager.layers.remove(it, false)
//            locationPrecisionCircle = null
//            createLocationPrecisionCircle()
//        }
//        locationMarker?.let {
//            mapView.layerManager.layers.remove(it, false)
//            locationMarker = null
//            createLocationMarker()
//        }
//    }