package com.dmjobs.worker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class JobDetailActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var detailTitle: TextView
    private lateinit var detailStatusChip: TextView
    private lateinit var detailCodeBadge: TextView
    private lateinit var detailAvailableContacts: TextView
    private lateinit var detailPayDm: TextView
    private lateinit var detailDailyLimit: TextView
    private lateinit var detailDailyEarning: TextView
    private lateinit var detailDelayText: TextView
    private lateinit var reqTimerTitle: TextView
    private lateinit var reqTimerDesc: TextView
    private lateinit var detailMessagePreview: TextView
    private lateinit var inpDetailPhone: EditText
    private lateinit var detailErrorText: TextView
    private lateinit var detailProgress: ProgressBar
    private lateinit var btnAcceptJob: Button

    private var job: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_detail)

        Session.init(this)

        val jobJsonStr = intent.getStringExtra("job_json")
        job = if (!jobJsonStr.isNullOrEmpty()) {
            try { JSONObject(jobJsonStr) } catch (e: Exception) { Session.job }
        } else {
            Session.job
        }

        if (job == null) {
            finish()
            return
        }

        initViews()
        populateJobDetails()
        fetchLiveAvailableContacts()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        detailTitle = findViewById(R.id.detail_title)
        detailStatusChip = findViewById(R.id.detail_status_chip)
        detailCodeBadge = findViewById(R.id.detail_code_badge)
        detailAvailableContacts = findViewById(R.id.detail_available_contacts)
        detailPayDm = findViewById(R.id.detail_pay_dm)
        detailDailyLimit = findViewById(R.id.detail_daily_limit)
        detailDailyEarning = findViewById(R.id.detail_daily_earning)
        detailDelayText = findViewById(R.id.detail_delay_text)
        reqTimerTitle = findViewById(R.id.req_timer_title)
        reqTimerDesc = findViewById(R.id.req_timer_desc)
        detailMessagePreview = findViewById(R.id.detail_message_preview)
        inpDetailPhone = findViewById(R.id.inp_detail_phone)
        detailErrorText = findViewById(R.id.detail_error_text)
        detailProgress = findViewById(R.id.detail_progress)
        btnAcceptJob = findViewById(R.id.btn_accept_job)

        btnBack.setOnClickListener {
            finish()
        }

        // Pre-fill phone if available
        if (Session.username.isNotEmpty()) {
            inpDetailPhone.setText(Session.username)
            inpDetailPhone.setSelection(Session.username.length)
        }

        btnAcceptJob.setOnClickListener {
            handleAcceptJob()
        }
    }

    private fun populateJobDetails() {
        val j = job ?: return

        val title = j.optString("title", "Outreach Campaign")
        val code = j.optString("job_code", "—")
        val pay = j.optDouble("pay_per_dm", 0.0)
        val maxPerDay = j.optInt("max_per_day", 30)
        val delay = j.optInt("rate_limit_seconds", 10)
        val message = j.optString("message", "(No message specified)")
        val totalContacts = j.optInt("total_contacts", 0)

        detailTitle.text = if (title.isNotBlank()) title else "Campaign $code"
        detailCodeBadge.text = "CODE: $code"
        detailAvailableContacts.text = if (totalContacts > 0) "$totalContacts total contacts" else "Available"

        val status = j.optString("display_status", "Available")
        detailStatusChip.text = status
        when (status) {
            "In Progress" -> {
                detailStatusChip.setBackgroundResource(R.drawable.badge_amber_bg)
                detailStatusChip.setTextColor(android.graphics.Color.parseColor("#B45309"))
                btnAcceptJob.text = "Continue Working This Job"
            }
            "Completed" -> {
                detailStatusChip.setBackgroundResource(R.drawable.badge_slate_bg)
                detailStatusChip.setTextColor(android.graphics.Color.parseColor("#64748B"))
                btnAcceptJob.text = "View Completed Campaign"
            }
            else -> {
                detailStatusChip.setBackgroundResource(R.drawable.pay_badge_bg)
                detailStatusChip.setTextColor(android.graphics.Color.parseColor("#15803D"))
                btnAcceptJob.text = "Accept & Start Campaign"
            }
        }

        detailPayDm.text = if (pay > 0) "₦${"%,.0f".format(pay)}" else "Paid"
        detailDailyLimit.text = "$maxPerDay DMs/day"

        val dailyPotential = pay * maxPerDay
        detailDailyEarning.text = if (dailyPotential > 0) {
            "Earn up to ₦${"%,.0f".format(dailyPotential)}/day"
        } else {
            "Daily limit: $maxPerDay DMs"
        }

        detailDelayText.text = "⏱ ${delay}s delay between sends"
        reqTimerTitle.text = "Follow Anti-Spam Rate Limit (${delay}s delay)"
        reqTimerDesc.text = "Strictly observe the mandatory ${delay}-second countdown timer before marking each message as sent."

        detailMessagePreview.text = message
    }

    private fun fetchLiveAvailableContacts() {
        val j = job ?: return
        val jobId = j.optString("id", "")
        if (jobId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val available = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_contacts",
                        "job_id=eq.$jobId&status=eq.pending&select=id"
                    ).length()
                }

                detailAvailableContacts.text = if (available > 0) {
                    "$available contacts available"
                } else {
                    "All contacts claimed"
                }
            } catch (_: Exception) {
                // Keep the default total contacts text if live count fails
            }
        }
    }

    private fun handleAcceptJob() {
        val j = job ?: return
        val phoneInput = inpDetailPhone.text.toString().trim()
        val cleanPhone = phoneInput.replace(" ", "")

        showError("")

        if (cleanPhone.isEmpty()) {
            showError("Please enter your WhatsApp phone number.")
            inpDetailPhone.requestFocus()
            return
        }

        if (!Regex("^\\d{11}$").matches(cleanPhone)) {
            showError("Enter a valid 11-digit phone number (e.g. 08012345678).")
            inpDetailPhone.requestFocus()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                Session.username = cleanPhone
                Session.job = j
                Session.persist()

                val jobId = j.getString("id")

                // 1. Check global ban
                val banCheck = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_worker_status",
                        "phone=eq.$cleanPhone&status=eq.banned&select=status&limit=1"
                    )
                }
                if (banCheck.length() > 0) {
                    startActivity(Intent(this@JobDetailActivity, StatusActivity::class.java).apply {
                        putExtra("mode", "banned")
                    })
                    finish()
                    return@launch
                }

                // 2. Check worker status for this specific job
                val statusRows = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_worker_status",
                        "phone=eq.$cleanPhone&job_id=eq.$jobId&select=status"
                    )
                }

                if (statusRows.length() > 0) {
                    val status = statusRows.getJSONObject(0).getString("status")
                    when (status) {
                        "pending" -> {
                            startActivity(Intent(this@JobDetailActivity, StatusActivity::class.java).apply {
                                putExtra("mode", "pending")
                            })
                            finish()
                            return@launch
                        }
                        "blocked" -> {
                            startActivity(Intent(this@JobDetailActivity, StatusActivity::class.java).apply {
                                putExtra("mode", "blocked")
                            })
                            finish()
                            return@launch
                        }
                    }
                } else {
                    // First time on this job — register as pending
                    withContext(Dispatchers.IO) {
                        val body = JSONObject().apply {
                            put("phone", cleanPhone)
                            put("job_id", jobId)
                            put("status", "pending")
                        }
                        Supabase.insert("wdmj_worker_status", body)
                    }
                    startActivity(Intent(this@JobDetailActivity, StatusActivity::class.java).apply {
                        putExtra("mode", "pending")
                    })
                    finish()
                    return@launch
                }

                // Active — go directly to SendActivity!
                startActivity(Intent(this@JobDetailActivity, SendActivity::class.java))
                finish()

            } catch (e: Exception) {
                setLoading(false)
                showError(e.message ?: "Failed to start job. Please try again.")
            }
        }
    }

    private fun showError(msg: String) {
        detailErrorText.text = msg
        detailErrorText.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        btnAcceptJob.isEnabled = !loading
        btnBack.isEnabled = !loading
        detailProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
