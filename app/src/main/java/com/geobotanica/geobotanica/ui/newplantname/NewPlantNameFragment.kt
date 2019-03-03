package com.geobotanica.geobotanica.ui.newplantname

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.data.entity.PlantTypeConverter
import com.geobotanica.geobotanica.data_taxa.TaxaDatabase
import com.geobotanica.geobotanica.data_taxa.util.PlantNameSearchService.SearchResult
import com.geobotanica.geobotanica.ui.BaseFragment
import com.geobotanica.geobotanica.ui.BaseFragmentExt.getViewModel
import com.geobotanica.geobotanica.ui.ViewModelFactory
import com.geobotanica.geobotanica.util.Lg
import com.geobotanica.geobotanica.util.getFromBundle
import com.geobotanica.geobotanica.util.onTextChanged
import com.geobotanica.geobotanica.util.toTrimmedString
import kotlinx.android.synthetic.main.fragment_new_plant_name.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import javax.inject.Inject

// TODO: Prioritize previously selected names in list (need history table)
// TODO: MAYBE Add filter for vern/sci name (action bar / menu button)
class NewPlantNameFragment : BaseFragment() {
    @Inject lateinit var viewModelFactory: ViewModelFactory<NewPlantNameViewModel>
    private lateinit var viewModel: NewPlantNameViewModel

    private val mainScope = CoroutineScope(Dispatchers.Main)

    private lateinit var plantNamesAdapter: PlantNamesAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity.applicationComponent.inject(this)

        viewModel = getViewModel(viewModelFactory) {
            userId = getFromBundle(userIdKey)
            plantType = PlantTypeConverter.toPlantType(getFromBundle(plantTypeKey))
            photoUri = getFromBundle(photoUriKey)
            Lg.d("Fragment args: userId=$userId, plantType=$plantType, photoUri=$photoUri")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_new_plant_name, container, false)
    }

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView()
        bindViewListeners()
        TaxaDatabase.getInstance(appContext).close()
        searchEditText.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        searchJob?.cancel()
    }

    private fun initRecyclerView() = mainScope.launch {
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        plantNamesAdapter = PlantNamesAdapter(viewModel.getAllStarredPlantNames(), onClickItem, onClickStar)
        recyclerView.adapter = plantNamesAdapter
    }

    private var searchJob: Job? = null
    private var searchString = ""

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    private fun bindViewListeners() {
        fab.setOnClickListener(::onFabPressed)
        clearButton.setOnClickListener { searchEditText.text.clear() }
        searchEditText.onTextChanged(::onSearchEditTextChanged)
    }

    private val onClickItem = { item: SearchResult ->
        showToast(item.name)
    }

    private val onClickStar = { result: SearchResult ->
        viewModel.setStarred(result.plantNameType, result.id, result.isStarred)
    }

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    private fun onSearchEditTextChanged(editText: String) {
        if (searchString == editText)
            return@onSearchEditTextChanged
        searchJob?.cancel()
        searchString = editText

        progressBar.isVisible = true
        searchJob = mainScope.launch {
            delay(300)
            noResultsText.isVisible = false
            if (searchString.isEmpty()) {
                plantNamesAdapter.items = viewModel.getAllStarredPlantNames()
                plantNamesAdapter.notifyDataSetChanged()
            } else if (searchString == editText) {
                viewModel.searchPlantName(searchString).consumeEach {
                    plantNamesAdapter.items = it
                    plantNamesAdapter.notifyDataSetChanged()
                }
            }
        }
        searchJob?.invokeOnCompletion { completionError ->
            if (completionError == null) { // Coroutine completed normally
                progressBar.isVisible = false
                if (recyclerView.adapter?.itemCount == 0)
                    noResultsText.isVisible = true
            }
        }
    }

    // TODO: Push validation into the repo?
    @Suppress("UNUSED_PARAMETER")
    private fun onFabPressed(view: View) {
        Lg.d("NewPlantFragment: onSaveButtonPressed()")

        if (!areNamesValid())
            return
        saveViewModelState()

        val navController = activity.findNavController(R.id.fragment)
        navController.navigate(R.id.newPlantMeasurementFragment, createBundle())
    }

    private fun areNamesValid(): Boolean {
        if (commonNameTextInput.isEmpty() && latinNameTextInput.isEmpty()) {
            showSnackbar("Provide a plant name")
            return false
        }
        return true
    }

    private fun saveViewModelState() {
        commonNameTextInput.toString()
        val commonName = commonNameTextInput.toTrimmedString()
        val latinName = latinNameTextInput.toTrimmedString()
        viewModel.commonName = if (commonName.isNotEmpty()) commonName else null
        viewModel.latinName = if (latinName.isNotEmpty()) latinName else null
    }

    private fun createBundle(): Bundle {
        return bundleOf(
                userIdKey to viewModel.userId,
                plantTypeKey to viewModel.plantType.ordinal,
                photoUriKey to viewModel.photoUri
        ).apply {
            viewModel.commonName?.let { putString(commonNameKey, it) }
            viewModel.latinName?.let { putString(latinNameKey, it) }
        }
    }
}