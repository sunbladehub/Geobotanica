package com.geobotanica.geobotanica.ui.downloadassets

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.data.entity.OnlineAsset
import com.geobotanica.geobotanica.network.NetworkValidator
import com.geobotanica.geobotanica.network.NetworkValidator.NetworkState.*
import com.geobotanica.geobotanica.ui.BaseFragment
import com.geobotanica.geobotanica.ui.BaseFragmentExt.getViewModel
import com.geobotanica.geobotanica.ui.ViewModelFactory
import com.geobotanica.geobotanica.ui.dialog.WarningDialog
import com.geobotanica.geobotanica.util.Lg
import com.geobotanica.geobotanica.util.getFromBundle
import kotlinx.android.synthetic.main.fragment_download_assets.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class DownloadAssetsFragment : BaseFragment() {
    @Inject lateinit var viewModelFactory: ViewModelFactory<DownloadAssetsViewModel>
    private lateinit var viewModel: DownloadAssetsViewModel

    @Inject lateinit var networkValidator: NetworkValidator

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity.applicationComponent.inject(this)

        viewModel = getViewModel(viewModelFactory) {
            userId = getFromBundle(userIdKey)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_download_assets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewModel.init()
            initUi()
            bindViewModel()
            downloadButton.setOnClickListener(::onClickDownload)
        }
    }

    @SuppressLint("UsableSpace")
    private suspend fun initUi() {
        worldMapText.text = viewModel.getWorldMapText()
        plantNameDbText.text = viewModel.getPlantNameDbText()
        internalStorageText.text = getString(R.string.internal_storage,
                File(appContext.filesDir.absolutePath).usableSpace / 1024 / 1024)
    }

    private fun bindViewModel() {
        viewModel.navigateToNext.observe(viewLifecycleOwner, onNavigateToNextObserver)
        viewModel.showStorageSnackbar.observe(viewLifecycleOwner, onShowStorageSnackbar)
    }

    private val onNavigateToNextObserver = Observer<Boolean> {
        if (it) {
            Lg.d("DownloadAssetsFragment: Map data imported and asset downloads initialized -> navigateToNext()")
            progressBar.isVisible = false
            navigateToNext()
        }
    }

    private val onShowStorageSnackbar = Observer<OnlineAsset> { onlineAsset ->
        Lg.i("Error: Insufficient storage for ${onlineAsset.description}")
        if (onlineAsset.isInternalStorage) {
            showSnackbar(R.string.not_enough_internal_storage, R.string.Inspect) {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }
        } else {
            showSnackbar(R.string.not_enough_external_storage, R.string.Inspect) {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onClickDownload(view: View?) {
        when (networkValidator.getStatus()) {
            INVALID -> showSnackbar(resources.getString(R.string.internet_unavailable))
            VALID -> downloadAssets()
            VALID_IF_METERED_PERMITTED -> {
                WarningDialog(
                        getString(R.string.metered_network),
                        getString(R.string.metered_network_confirm))
                {
                    networkValidator.allowMeteredNetwork(); downloadAssets()
                }
                .show(requireFragmentManager(), "tag")
            }
        }
    }

    private fun downloadAssets() {
        downloadButton.isVisible = false
        progressBar.isVisible = true
        viewModel.downloadAssets()
    }

    private fun navigateToNext() =
        navigateTo(R.id.action_downloadAssets_to_localMaps, createBundle(), R.id.downloadAssetsFragment)

    private fun createBundle() = bundleOf(userIdKey to viewModel.userId)
}