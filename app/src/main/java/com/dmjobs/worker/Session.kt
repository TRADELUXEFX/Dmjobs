package com.dmjobs.worker

import org.json.JSONObject

/** In-memory session state for the current worker — mirrors the `S` object in the web app. */
object Session {
    var username: String = ""          // worker's phone number
    var job: JSONObject? = null        // current job row
    var currentContact: JSONObject? = null
    var sentToday: Int = 0
    var waOpened: Boolean = false

    fun reset() {
        username = ""
        job = null
        currentContact = null
        sentToday = 0
        waOpened = false
    }
}
