package com.focusguard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.*
import com.focusguard.app.engine.BlockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainVm(app: Application) : AndroidViewModel(app) {
    private val c = (app as FocusApp).container
    val db = c.db
    val prefs = c.prefs

    private fun <T> Flow<T>.keep(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val profiles = db.profileDao().all().keep(emptyList())
    val tasks = db.taskDao().all().keep(emptyList())
    val plans = db.planDao().all().keep(emptyList())
    val limits = db.limitDao().all().map { l -> l.associate { it.pkg to it.limitMs } }.keep(emptyMap())
    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val focusTask = MutableStateFlow(0L)

    init { loadApps() }

    fun loadApps() { viewModelScope.launch(Dispatchers.IO) { apps.value = Apps.load(getApplication()) } }
    fun syncUsage() { viewModelScope.launch(Dispatchers.IO) { UsageSync.sync(getApplication(), 8) } }

    fun upsertProfile(p: Profile) { viewModelScope.launch(Dispatchers.IO) { db.profileDao().upsert(p) } }
    fun deleteProfile(p: Profile) { viewModelScope.launch(Dispatchers.IO) { db.profileDao().delete(p) } }

    fun upsertTask(t: TaskRec) { viewModelScope.launch(Dispatchers.IO) { db.taskDao().upsert(t) } }
    fun deleteTask(t: TaskRec) { viewModelScope.launch(Dispatchers.IO) { db.taskDao().delete(t) } }
    fun toggleTask(t: TaskRec) = upsertTask(t.copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else 0))

    fun upsertPlan(b: PlanBlock) {
        viewModelScope.launch(Dispatchers.IO) { db.planDao().upsert(b); Scheduler.rescheduleAll(getApplication()) }
    }
    fun deletePlan(b: PlanBlock) {
        viewModelScope.launch(Dispatchers.IO) { db.planDao().delete(b); Scheduler.rescheduleAll(getApplication()) }
    }

    fun setLimit(pkg: String, ms: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ms <= 0) db.limitDao().delete(pkg) else db.limitDao().upsert(LimitRec(pkg, ms))
            BlockEngine.refreshLimits(getApplication())
        }
    }

    fun usage(from: LocalDate, to: LocalDate) = db.usageDao().between(from.toString(), to.toString())
    fun sessions(from: Long, to: Long) = db.sessionDao().between(from, to)
    fun attempts(from: Long, to: Long) = db.attemptDao().between(from, to)
}
