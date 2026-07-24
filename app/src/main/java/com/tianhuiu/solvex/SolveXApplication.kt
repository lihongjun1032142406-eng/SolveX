package com.tianhuiu.solvex

import android.app.Application
import com.tianhuiu.solvex.ui.MainViewModel
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SolveXApplication : Application() {
    internal lateinit var container: AppContainer
        private set

    /**
     * 全局持有 MainViewModel 引用，方便 Service 触发状态刷新。
     */
    var viewModel: MainViewModel? = null

    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }
        container = AppContainer(applicationContext)
    }
}
