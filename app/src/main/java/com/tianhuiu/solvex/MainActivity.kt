package com.tianhuiu.solvex

import android.app.ActivityManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.SolveXApp
import com.tianhuiu.solvex.ui.UpdateViewModel
import com.tianhuiu.solvex.utils.NotificationUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 应用主 Activity
 */
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private val updateViewModel by viewModels<UpdateViewModel>()

    private var pendingQuickStart = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if ((result.resultCode == RESULT_OK) && (result.data != null)) {
            viewModel.startMainService(result.resultCode, result.data, pendingQuickStart)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SolveX", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        handleDeepLink(intent)

        lifecycleScope.launch {
            viewModel.requestMediaProjection.collect { isQuick ->
                pendingQuickStart = isQuick
                val manager =
                    getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(manager.createScreenCaptureIntent())
            }
        }

        lifecycleScope.launch {
            val repository = SettingsRepository(applicationContext)
            repository.appConfigFlow.collectLatest { config ->
                updateRecentsVisibility(config.permissions.enableStealthMode)
            }
        }

        viewModel.registerShizukuListeners()
        updateViewModel.initialize()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.registerShizukuListeners()
                viewModel.checkPermissions()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.unregisterShizukuListeners()
            }
        })

        setContent {
            SolveXApp(viewModel, updateViewModel)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            viewModel.checkPermissions()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun updateRecentsVisibility(exclude: Boolean) {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { task ->
                task.setExcludeFromRecents(exclude)
            }
        } catch (e: Exception) {
            Log.e("SolveX", "Failed to update recents visibility", e)
        }
    }

    private fun handleDeepLink(intent: Intent) {
        if (intent.action == NotificationUtils.ACTION_VIEW_HISTORY) {
            val historyId = intent.getStringExtra(NotificationUtils.EXTRA_HISTORY_ID)
            if (historyId != null) {
                viewModel.deepLinkHistoryId = historyId
            }
        }
    }
}