package com.geobotanica.geobotanica.ui.downloadassets

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.geobotanica.geobotanica.R
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
            Lg.d("Fragment args: userId=$userId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_download_assets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAfterPermissionsGranted()
    }

    private fun initAfterPermissionsGranted() {
        if (! wasPermissionGranted(WRITE_EXTERNAL_STORAGE))
            requestPermission(WRITE_EXTERNAL_STORAGE)
        else
            init()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            getRequestCode(WRITE_EXTERNAL_STORAGE) -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Lg.i("permission.WRITE_EXTERNAL_STORAGE: PERMISSION_GRANTED")
                    initAfterPermissionsGranted()
                } else {
                    Lg.i("permission.WRITE_EXTERNAL_STORAGE: PERMISSION_DENIED")
                    showToast("External storage permission required") // TODO: Find better UX approach (separate screen)
                    activity.finish()
                }
            }
            else -> { }
        }
    }

    private fun init() = lifecycleScope.launch {
        constraintLayout.isVisible = true
        downloadButton.setOnClickListener(::onClickDownload)
        initUi()
        bindViewModel()
    }

    private fun bindViewModel() {
        viewModel.navigateToNext.observe(viewLifecycleOwner, Observer {
            if (it) {
                Lg.d("DownloadAssetsFragment: Map data imported and asset downloads initialized -> navigateToNext()")
                navigateToNext()
            }
        })

        viewModel.showStorageSnackbar.observe(this, Observer { asset ->
            Lg.i("Error: Insufficient storage for ${asset.description}")
            if (asset.isInternalStorage) {
                showSnackbar(R.string.not_enough_internal_storage, R.string.Inspect) {
                    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                }
            } else {
                showSnackbar(R.string.not_enough_external_storage, R.string.Inspect) {
                    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                }
            }
        })
    }

    @SuppressLint("UsableSpace")
    private suspend fun initUi() {
        worldMapText.text = viewModel.getWorldMapText()
        plantNameDbText.text = viewModel.getPlantNameDbText()
        internalStorageText.text = getString(R.string.internal_storage,
                File(appContext.filesDir.absolutePath).usableSpace / 1024 / 1024)
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

    private fun navigateToNext() {
        val navController = NavHostFragment.findNavController(this)
        navController.popBackStack()
        navController.navigate(R.id.suggestedMapsFragment, createBundle())
    }

    private fun createBundle(): Bundle =
        bundleOf(userIdKey to viewModel.userId)
}