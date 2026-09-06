package com.dmjobs.worker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Session state for the current worker — persisted to SharedPreferences so it survives app restarts. */
object Session {
    var username: String = ""          // worker's phone number
    var job: JSONObject? = null        // current job row
    var currentContact: JSONObject? = null
    var sentToday: Int = 0
    var waOpened: Boolean = false

    private const val PREFS = "dmjobs_session"
    private const val KEY_USERNAME = "username"
    private const val KEY_JOB = "job"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    /** Call once, e.g. from LoginActivity.onCreate, before reading/writing session state. */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        username = prefs.getString(KEY_USERNAME, "") ?: ""
        val jobStr = prefs.getString(KEY_JOB, null)
        job = if (jobStr != null) {
            try { JSONObject(jobStr) } catch (e: Exception) { null }
        } else null
        initialized = true
    }

    /** Persist username + job so a killed/reopened app can resume without re-login. */
    fun persist() {
        if (!initialized) return
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_JOB, job?.toString())
            .apply()
    }

    /** True if we have enough saved state to skip straight past login. */
    fun hasSavedSession(): Boolean = username.isNotEmpty() && job != null

    fun reset() {
        username = ""
        job = null
        currentContact = null
        sentToday = 0
        waOpened = false
        if (initialized) {
            prefs.edit().clear().apply()
        }
    }
}
