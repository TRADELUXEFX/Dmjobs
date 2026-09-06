package com.dmjobs.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SendActivity : AppCompatActivity() {

    private lateinit var sSent: TextView
    private lateinit var sLimit: TextView
    private lateinit var sProgress: ProgressBar
    private lateinit var sChip: TextView
    private lateinit var sPhone: TextView
    private lateinit var sMeta: TextView
    private lateinit var sCountdownRow: android.view.View
    private lateinit var sTimer: TextView
    private lateinit var sendError: TextView
    private lateinit var btnWa: Button
    private lateinit var btnMark: Button

    private var countdownTimer: CountDownTimer? = null
    private var waOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        sSent = findViewById(R.id.s_sent)
        sLimit = findViewById(R.id.s_limit)
        sProgress = findViewById(R.id.s_progress)
        sChip = findViewById(R.id.s_chip)
        sPhone = findViewById(R.id.s_phone)
        sMeta = findViewById(R.id.s_meta)
        sCountdownRow = findViewById(R.id.s_countdown)
        sTimer = findViewById(R.id.s_timer)
        sendError = findViewById(R.id.send_error)
        btnWa = findViewById(R.id.btn_wa)
        btnMark = findViewById(R.id.btn_mark)

        val job = Session.job
        sLimit.text = (job?.optInt("max_per_day", 30) ?: 30).toString()
        updateProgress()

        btnWa.setOnClickListener { openWhatsApp() }
        btnMark.setOnClickListener { markMessaged() }

        lockNextContact()
    }

    private fun showError(msg: String) {
        sendError.text = msg
        sendError.visibility = if (msg.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateProgress() {
        val limit = Session.job?.optInt("max_per_day", 30) ?: 30
        val pct = ((Session.sentToday.toDouble() / limit) * 100).toInt().coerceAtMost(100)
        sSent.text = Session.sentToday.toString()
        sProgress.progress = pct
    }

    private fun setChip(state: String) {
        when (state) {
            "pending" -> { sChip.setBackgroundResource(R.drawable.chip_pending); sChip.text = "Pending" }
            "opened" -> { sChip.setBackgroundResource(R.drawable.chip_opened); sChip.text = "Opened"; sChip.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_check_small, 0); sChip.compoundDrawablePadding = 6 }
            "ready" -> { sChip.setBackgroundResource(R.drawable.chip_ready); sChip.text = "Ready to Mark" }
        }
    }

    private fun maskPhone(num: String): String {
        if (num.length <= 6) return num
        return num.substring(0, 3) + "·".repeat(num.length - 6) + num.substring(num.length - 3)
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // ── Mirrors lockNextContact() in the web app ──
    private fun lockNextContact() {
        val job = Session.job ?: return
        val limit = job.optInt("max_per_day", 30)

        if (Session.sentToday >= limit) {
            blockWorker()
            return
        }

        lifecycleScope.launch {
            try {
                val jobId = job.getString("id")

                // Resume an already-locked contact if one exists (e.g. after app restart)
                val existing = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_contacts",
                        "job_id=eq.$jobId&status=eq.locked&locked_by=eq.${Session.username}&select=*&limit=1"
                    )
                }
                if (existing.length() > 0) {
                    Session.currentContact = existing.getJSONObject(0)
                    renderContact()
                    return@launch
                }

                // Get the next pending contact
                val next = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_contacts",
                        "job_id=eq.$jobId&status=eq.pending&order=id.asc&limit=1&select=*"
                    )
                }
                if (next.length() == 0) {
                    startActivity(Intent(this@SendActivity, StatusActivity::class.java).apply {
                        putExtra("mode", "done")
                    })
                    finish()
                    return@launch
                }

                val candidate = next.getJSONObject(0)
                val candidateId = candidate.getString("id")

                // Atomic lock attempt — only succeeds if still "pending" (race guard)
                val updated = withContext(Dispatchers.IO) {
                    val body = JSONObject().apply {
                        put("status", "locked")
                        put("locked_by", Session.username)
                        put("locked_at", isoNow())
                    }
                    Supabase.update(
                        "wdmj_contacts",
                        "id=eq.$candidateId&status=eq.pending",
                        body
                    )
                }

                if (updated.length() == 0) {
                    // Someone else grabbed it first — try again
                    lockNextContact()
                    return@launch
                }

                Session.currentContact = updated.getJSONObject(0)
                renderContact()

            } catch (e: Exception) {
                showError("Failed to load next contact: ${e.message}")
            }
        }
    }

    private fun renderContact() {
        val c = Session.currentContact
        if (c == null) {
            startActivity(Intent(this, StatusActivity::class.java).apply { putExtra("mode", "done") })
            finish()
            return
        }
        val phone = c.optString("phone_number", "").replace(Regex("\\D"), "")
        sPhone.text = maskPhone(phone)
        sMeta.text = "Locked to you — send this DM now"
        waOpened = false
        countdownTimer?.cancel()
        sCountdownRow.visibility = android.view.View.GONE
        btnMark.isEnabled = false
        btnWa.setBackgroundResource(R.drawable.button_wa_outline)
        btnWa.setTextColor(resources.getColor(R.color.green, theme))
        btnMark.setBackgroundResource(R.drawable.button_mark_outline_disabled)
        btnMark.setTextColor(android.graphics.Color.parseColor("#B4B2A9"))
        setChip("pending")
        showError("")
    }

    private fun toWhatsAppPhone(raw: String): String {
        var phone = raw.replace(Regex("\\D"), "")
        phone = if (phone.startsWith("0")) "234" + phone.substring(1)
        else if (!phone.startsWith("234")) "234$phone"
        else phone
        return phone
    }

    // ── Mirrors openWhatsApp() ──
    private fun openWhatsApp() {
        val c = Session.currentContact ?: return
        val phone = toWhatsAppPhone(c.optString("phone_number", ""))
        val msg = Session.job?.optString("message", "") ?: ""
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(msg)}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))

        waOpened = true
        setChip("opened")
        btnWa.setBackgroundResource(R.drawable.button_rounded_wa)
        btnWa.setTextColor(resources.getColor(R.color.white, theme))
        startCountdown(Session.job?.optInt("rate_limit_seconds", 10) ?: 10)
    }

    private fun startCountdown(secs: Int) {
        countdownTimer?.cancel()
        sCountdownRow.visibility = android.view.View.VISIBLE
        sTimer.text = secs.toString()

        countdownTimer = object : CountDownTimer((secs * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                sTimer.text = ((millisUntilFinished / 1000) + 1).toString()
            }
            override fun onFinish() {
                sCountdownRow.visibility = android.view.View.GONE
                btnMark.isEnabled = true
                btnMark.setBackgroundResource(R.drawable.button_rounded_secondary)
                btnMark.setTextColor(resources.getColor(R.color.green_deep, theme))
                setChip("ready")
            }
        }.start()
    }

    // ── Mirrors markMessaged() ──
    private fun markMessaged() {
        if (!waOpened) return
        btnMark.isEnabled = false
        btnMark.setBackgroundResource(R.drawable.button_rounded)
        btnMark.setTextColor(resources.getColor(R.color.white, theme))

        val c = Session.currentContact ?: return
        val contactId = c.getString("id")
        val job = Session.job ?: return

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val body = JSONObject().apply { put("status", "pending_approval") }
                    Supabase.update("wdmj_contacts", "id=eq.$contactId", body)
                }

                withContext(Dispatchers.IO) {
                    val body = JSONObject().apply {
                        put("job_id", job.getString("id"))
                        put("contact_id", contactId)
                        put("sent_by", Session.username)
                        put("success", true)
                    }
                    Supabase.insert("wdmj_message_logs", body)
                }

                Session.currentContact = null
                Session.sentToday++
                updateProgress()

                val limit = job.optInt("max_per_day", 30)
                if (Session.sentToday >= limit) {
                    blockWorker()
                    return@launch
                }

                lockNextContact()

            } catch (e: Exception) {
                showError("Failed to save. Try again. (${e.message})")
                btnMark.isEnabled = true
            }
        }
    }

    // ── Mirrors blockWorker() ──
    private fun blockWorker() {
        lifecycleScope.launch {
            try {
                val c = Session.currentContact
                if (c != null) {
                    withContext(Dispatchers.IO) {
                        val body = JSONObject().apply {
                            put("status", "pending")
                            put("locked_by", JSONObject.NULL)
                            put("locked_at", JSONObject.NULL)
                        }
                        Supabase.update(
                            "wdmj_contacts",
                            "id=eq.${c.getString("id")}&status=eq.locked",
                            body
                        )
                    }
                    Session.currentContact = null
                }

                val job = Session.job
                if (job != null) {
                    withContext(Dispatchers.IO) {
                        val body = JSONObject().apply { put("status", "blocked") }
                        Supabase.update(
                            "wdmj_worker_status",
                            "phone=eq.${Session.username}&job_id=eq.${job.getString("id")}",
                            body
                        )
                    }
                }
            } catch (_: Exception) {
                // best-effort — proceed to blocked screen regardless
            } finally {
                startActivity(Intent(this@SendActivity, StatusActivity::class.java).apply {
                    putExtra("mode", "blocked")
                })
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
