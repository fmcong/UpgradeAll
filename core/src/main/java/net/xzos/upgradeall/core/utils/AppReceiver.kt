package net.xzos.upgradeall.core.utils

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import net.xzos.upgradeall.core.utils.constant.ANDROID_APP_TYPE
import net.xzos.upgradeall.core.androidutils.app_info.AppInfo
import net.xzos.upgradeall.core.androidutils.app_info.AppReceiver
import net.xzos.upgradeall.core.manager.AppManager
import net.xzos.upgradeall.core.manager.HubManager


fun registerAppReceiver(_context: Context) {
    AppReceiver(_context) { context, intent ->
        if (!HubManager.isEnableApplicationsMode())
            return@AppReceiver
        val packageName = intent.data.toString()
        runBlocking {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> addApp(context, packageName)
                Intent.ACTION_PACKAGE_REPLACED -> renewApp(packageName)
                Intent.ACTION_PACKAGE_REMOVED -> delApp(packageName)
            }
        }
    }.register()
}

private suspend fun addApp(context: Context, packageName: String) {
    val appInfo = try {
        val pm = context.packageManager
        val applicationInfo = pm.getApplicationInfo(packageName, 0)
        val name = pm.getApplicationLabel(applicationInfo)
        AppInfo(name.toString(), mapOf(ANDROID_APP_TYPE to packageName))
    } catch (e: Throwable) {
        return
    }
    AppManager.updateApp(appInfo.toAppEntity())
}

private suspend fun renewApp(packageName: String) {
    AppManager.getAppList(ANDROID_APP_TYPE)
        .filter { it.appId.values.contains(packageName) }.forEach {
            AppManager.renewApp(it)
        }
}

private suspend fun delApp(packageName: String) {
    val appId = getAppId(packageName)
    AppManager.getAppById(appId)?.run {
        AppManager.removeApp(this)
    }
}

private fun getAppId(packageName: String) = mapOf(ANDROID_APP_TYPE to packageName)