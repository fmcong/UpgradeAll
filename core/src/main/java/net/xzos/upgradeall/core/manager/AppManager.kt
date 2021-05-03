package net.xzos.upgradeall.core.manager

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.xzos.upgradeall.core.database.metaDatabase
import net.xzos.upgradeall.core.database.table.AppEntity
import net.xzos.upgradeall.core.database.table.isInit
import net.xzos.upgradeall.core.database.table.recheck
import net.xzos.upgradeall.core.module.app.App
import net.xzos.upgradeall.core.module.app.Updater.Companion.APP_LATEST
import net.xzos.upgradeall.core.module.app.Updater.Companion.APP_NO_LOCAL
import net.xzos.upgradeall.core.module.app.Updater.Companion.APP_OUTDATED
import net.xzos.upgradeall.core.module.app.Updater.Companion.NETWORK_ERROR
import net.xzos.upgradeall.core.utils.android_app.AppReceiver
import net.xzos.upgradeall.core.utils.android_app.getInstalledAppList
import net.xzos.upgradeall.core.utils.coroutines.*
import net.xzos.upgradeall.core.utils.oberver.Informer


object AppManager : Informer {

    const val DATA_UPDATING_NOTIFY = "DATA_UPDATING_NOTIFY"
    const val DATA_UPDATE_CHANGED_NOTIFY = "DATA_UPDATE_CHANGED_NOTIFY"
    const val DATA_UPDATED_NOTIFY = "DATA_UPDATED_NOTIFY"

    const val APP_DATABASE_CHANGED_NOTIFY = "APP_DATABASE_CHANGED_NOTIFY"
    const val APP_ADDED_NOTIFY = "APP_ADDED_NOTIFY"
    const val APP_DELETED_NOTIFY = "APP_DELETED_NOTIFY"

    private val appMap = coroutinesMutableMapOf<Int, CoroutinesMutableList<App>>(true)

    // 存储所有 APP 实体
    private val appList by lazy {
        runBlocking { metaDatabase.appDao().loadAll() + getInstalledAppList() }.map { App(it) }
                .toCoroutinesMutableList(true).apply { AppReceiver().register() }
    }

    private fun getAppList(key: Int): CoroutinesMutableList<App> {
        return appMap.get(key, coroutinesMutableListOf(true))
    }

    /**
     * 以更新状态{@link Updater}为键值的字典
     * 在完成数据刷新{@link #renewApp}后，可以通过 App 类型{@link Constant}获取可以通过该字典来查看各个更新状态的 App 列表
     */
    fun getAppMap(appType: String? = null): Map<Int, List<App>> {
        return if (appType != null) {
            mutableMapOf<Int, List<App>>().apply {
                this@AppManager.appMap.forEach {
                    this[it.key] = it.value.filter { app ->
                        app.appId.containsKey(appType)
                    }
                }
            }
        } else appMap
    }

    /**
     * 按照更新状态{@link Updater}排序的列表
     * 在完成数据刷新{@link #renewApp}后，可以通过 App 类型{@link Constant}获取该类型的列表
     */
    fun getAppSortList(appType: String): List<App> {
        val list = getAppList(APP_OUTDATED) +
                getAppList(APP_NO_LOCAL) +
                getAppList(APP_LATEST) +
                getAppList(NETWORK_ERROR)
        return list.filter { it.appId.containsKey(appType) }
    }

    /**
     * 获取全部 App 实体列表
     */
    fun getAppList(): List<App> {
        return appList
    }

    fun getUserAppList(): List<App> {
        return appList.filter { it.appDatabase.isInit() }
    }

    fun getAutoAppList(): List<App> {
        return appList.filter { !it.appDatabase.isInit() }
    }

    fun getAppListWithoutKey(excludeAppType: String): List<App> {
        return appList.filter { !it.appId.containsKey(excludeAppType) }
    }

    fun getAppByUuid(uuid: String): App? {
        appList.forEach {
            if (uuid == it.appDatabase.cloudConfig?.uuid)
                return it
        }
        return null
    }

    /**
     * 获取全部 App 实体列表，按照 App 类型过滤
     */
    fun getAppList(appType: String): List<App> {
        return appList.filter { it.appId.containsKey(appType) }
    }

    /**
     * 刷新 App 的版本数据
     * @param renewStatusFun 每刷新一个 App 数据，回调一次，以返回正在刷新中的 App 数量
     */
    suspend fun renewApp(renewStatusFun: ((renewingAppNum: Int, totalAppNum: Int) -> Unit)? = null): Int {
        val count = CoroutinesCount(appList.size)
        coroutineScope {
            val totalAppNum = appList.size
            for (app in appList)
                launch {
                    renewApp(app)
                    count.down()
                    renewStatusFun?.run { this(count.count, totalAppNum) }
                }
        }
        return appList.size
    }

    suspend fun renewApp(app: App) {
        notifyChanged(DATA_UPDATING_NOTIFY, app)
        app.update()
        setAppMap(app)
        notifyChanged(DATA_UPDATED_NOTIFY, app)
    }

    private fun setAppMap(app: App) {
        val releaseStatus = app.getReleaseStatus()
        var changed = false
        // reset app map

        // retry add
        getAppList(releaseStatus).run {
            if (!contains(app)) {
                add(app)
                changed = true
            }
        }

        // retry delete
        appMap.forEach {
            if (it.key != releaseStatus) {
                it.value.remove(app)
                changed = true
            }
        }

        // check changed
        if (changed) {
            notifyChanged(DATA_UPDATE_CHANGED_NOTIFY, app)
        }
    }

    private fun getAppByDatabase(appEntity: AppEntity): App? {
        appList.forEach {
            if (it.appDatabase == appEntity)
                return it
        }
        return null
    }

    fun getAppById(appId: Map<String, String?>): App? {
        appList.forEach {
            if (appId == it.appDatabase.appId)
                return it
        }
        return null
    }

    /**
     * 用数据库数据修改数据库并更新 App 数据
     */
    suspend fun updateApp(appDatabase: AppEntity): App? {
        appDatabase.recheck()
        return addAppMap(addAppEntity(appDatabase) ?: return null)
    }

    private suspend fun addAppEntity(appEntity: AppEntity): AppEntity? {
        val appDao = metaDatabase.appDao()
        return try {
            if (appEntity.isInit())
                appDao.update(appEntity)
            else
                appDao.insert(appEntity)
            appEntity
            // TODO: 错误类型判断，并给出 null 返回值
        } catch (ignore: SQLiteConstraintException) {
            null
        }
    }

    private suspend fun addAppMap(appDatabase: AppEntity): App {
        val oldApp = if (appDatabase.isInit()) getAppByDatabase(appDatabase).apply {
        } else {
            getAppById(appDatabase.appId)?.apply {
                this.appDatabase.name = appDatabase.name
            }
        }
        val changedTag = if (oldApp != null)
            APP_DATABASE_CHANGED_NOTIFY
        else APP_ADDED_NOTIFY
        val app = oldApp ?: App(appDatabase).apply {
            appList.add(this)
        }
        renewApp(app)
        notifyChanged(changedTag, app)
        return app
    }

    /**
     * 删除这个 App，包括数据库
     */
    suspend fun removeApp(app: App) {
        metaDatabase.appDao().delete(app.appDatabase)
        appList.remove(app)
        appMap.forEach {
            it.value.remove(app)
        }
        notifyChanged(APP_DELETED_NOTIFY, app)
    }

    override val informerId: Int = Informer.getInformerId()
}