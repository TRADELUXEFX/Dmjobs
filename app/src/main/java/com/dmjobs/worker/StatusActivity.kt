package com.dmjobs.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusActivity : AppCompatActivity() {

    private var currentMode: String = "pending"

    private var isFetchingCount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)

        currentMode = intent.getStringExtra("mode") ?: "pending"
        render(currentMode, fetchCount = false)
    }

    override fun onResume() {
        super.onResume()
        // Re-check status every time this screen becomes visible (e.g. after
        // admin re-approves and worker returns to the app) so the user never
        // has to log out and back in to continue sending.
        if (currentMode == "pending" || currentMode == "blocked") {
            recheckStatus()
        }
    }

    private fun recheckStatus() {
        val job = Session.job ?: return
        val jobId = job.optString("id", "")
        val phone = Session.username ?: return
        if (jobId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val banCheck = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_worker_status",
                        "phone=eq.$phone&status=eq.banned&select=status&limit=1"
                    )
                }
                if (banCheck.length() > 0) {
                    currentMode = "banned"
                    render(currentMode)
                    return@launch
                }

                val statusRows = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_worker_status",
                        "phone=eq.$phone&job_id=eq.$jobId&select=status"
                    )
                }
                if (statusRows.length() > 0) {
                    val status = statusRows.getJSONObject(0).getString("status")
                    when (status) {
                        "active" -> {
                            // Re-approved — go straight to sending, no relogin needed.
                            startActivity(Intent(this@StatusActivity, PreviewActivity::class.java))
                            finish()
                        }
                        "pending" -> {
                            currentMode = "pending"
                            render(currentMode)
                        }
                        "blocked" -> {
                            currentMode = "blocked"
                            render(currentMode)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent — keep showing current status, will retry next onResume.
            }
        }
    }

    private fun render(mode: String, fetchCount: Boolean = true) {
        val title = findViewById<TextView>(R.id.status_title)
        val body = findViewById<TextView>(R.id.status_body)
        val btnWa = findViewById<Button>(R.id.btn_wa_action)
        val btnBack = findViewById<Button>(R.id.btn_back)
        val earnedView = findViewById<TextView>(R.id.status_earned)
        val sentCountView = findViewById<TextView>(R.id.status_sent_count)

        val job = Session.job
        val jobCode = job?.optString("job_code", "???") ?: "???"
        val username = Session.username

        val pay = job?.optDouble("pay_per_dm", 0.0) ?: 0.0
        val earnedNow = pay * Session.sentToday
        earnedView.text = if (earnedNow > 0) "₦${"%,.0f".format(earnedNow)}" else "₦0"
        sentCountView.text = Session.sentToday.toString()

        val jobId = job?.optString("id", "") ?: ""
        if (fetchCount && !isFetchingCount && jobId.isNotEmpty() && username.isNotEmpty()) {
            isFetchingCount = true
            lifecycleScope.launch {
                try {
                    val realSentToday = withContext(Dispatchers.IO) {
                        Supabase.countSentToday(username, jobId)
                    }
                    Session.sentToday = realSentToday
                    val earnedToday = pay * realSentToday
                    earnedView.text = if (earnedToday > 0) "₦${"%,.0f".format(earnedToday)}" else "₦0"
                    sentCountView.text = realSentToday.toString()
                } catch (e: Exception) {
                    // Keep showing the last-known local value if the server check fails
                } finally {
                    isFetchingCount = false
                }
            }
        }

        when (mode) {
            "pending" -> {
                title.text = "Awaiting Approval"
                body.text = "You're registered for this job but need admin approval before you can start sending."
                btnWa.text = "Message Admin to Get Approved"
                btnWa.setOnClickListener {
                    val msg = "Hi, I want to start job $jobCode. My WhatsApp number is $username. Please approve me."
                    openWhatsApp(msg)
                }
            }
            "blocked" -> {
                title.text = "Daily Limit Reached"
                body.text = "You've hit today's sending limit. Message admin to get re-approved for your next batch."
                btnWa.text = "Send proof"
                btnWa.setOnClickListener {
                    val msg = "Hi, I have completed my DM batch for job $jobCode. My number is $username. Please re-approve me to continue."
                    openWhatsApp(msg)
                }
            }
            "banned" -> {
                title.text = "Account Banned"
                body.text = "This phone number has been banned from all jobs. Contact admin if you believe this is a mistake."
                btnWa.text = "Contact Admin"
                btnWa.setOnClickListener {
                    val msg = "Hi, my number ($username) appears to be banned. Please review my account."
                    openWhatsApp(msg)
                }
            }
            "done" -> {
                title.text = "Job Complete!"
                body.text = "All contacts for this job have been messaged. Contact admin for a new job code."
                btnWa.text = "Request New Job"
                btnWa.setOnClickListener {
                    val msg = "Hi, I'm $username. Job $jobCode is complete. Please send me a new job code."
                    openWhatsApp(msg)
                }
            }
        }

        btnBack.setOnClickListener {
            Session.reset()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun openWhatsApp(message: String) {
        val uri = Uri.parse("https://wa.me/${Supabase.ADMIN_PHONE}?text=${Uri.encode(message)}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
