package com.dmjobs.worker

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Supabase REST (PostgREST) client.
 * Mirrors the exact calls the web worker portal (jobs/index.html) makes,
 * using the anon key — same permission model as the website.
 */
object Supabase {

    // ── Same project the website uses ──
    private const val URL = "https://pzufqbgawgkrulvkrtbc.supabase.co"
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB6dWZxYmdhd2drcnVsdmtydGJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NDgxMTksImV4cCI6MjEwNDIyNDExOX0.vp_eGMWesGCEQkLaRoC4Q2WohiuSzCYrKlzXDvs5myE"

    const val ADMIN_PHONE = "2349110321143"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    private fun baseRequest(path: String, extraHeaders: Map<String, String> = emptyMap()): Request.Builder {
        val b = Request.Builder()
            .url("$URL/rest/v1/$path")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", "Bearer $ANON_KEY")
            .addHeader("Content-Type", "application/json")
        extraHeaders.forEach { (k, v) -> b.addHeader(k, v) }
        return b
    }

    /** SELECT — returns a JSONArray of rows. `query` is the raw PostgREST query string, e.g. "job_code=eq.ABC123&select=*" */
    fun select(table: String, query: String): JSONArray {
        val req = baseRequest("$table?$query").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "[]"
            if (!resp.isSuccessful) throw RuntimeException("Select failed (${resp.code}): $body")
            return JSONArray(body)
        }
    }

    /** INSERT — returns the inserted row(s) as JSONArray (uses Prefer: return=representation) */
    fun insert(table: String, jsonBody: JSONObject): JSONArray {
        val req = baseRequest(table, mapOf("Prefer" to "return=representation"))
            .post(jsonBody.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "[]"
            if (!resp.isSuccessful) throw RuntimeException("Insert failed (${resp.code}): $body")
            return JSONArray(body)
        }
    }

    /** UPDATE — `query` selects which rows (e.g. "id=eq.123&status=eq.pending").
     * Returns true if at least one row was updated (checked via return=representation).
     */
    fun update(table: String, query: String, jsonBody: JSONObject): JSONArray {
        val req = baseRequest("$table?$query", mapOf("Prefer" to "return=representation"))
            .patch(jsonBody.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "[]"
            if (!resp.isSuccessful) throw RuntimeException("Update failed (${resp.code}): $body")
            return JSONArray(body)
        }
    }

    /** Returns how many messages this worker has actually sent today for this job, per the server. */
    fun countSentToday(phone: String, jobId: String): Int {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val todayStart = sdf.format(java.util.Date())
        return select(
            "wdmj_message_logs",
            "sent_by=eq.$phone&job_id=eq.$jobId&created_at=gte.$todayStart&select=id"
        ).length()
    }
}
