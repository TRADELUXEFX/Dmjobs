package com.dmjobs.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)

        val mode = intent.getStringExtra("mode") ?: "pending"
        val icon = findViewById<TextView>(R.id.status_icon)
        val title = findViewById<TextView>(R.id.status_title)
        val body = findViewById<TextView>(R.id.status_body)
        val btnWa = findViewById<Button>(R.id.btn_wa_action)
        val btnBack = findViewById<Button>(R.id.btn_back)

        val job = Session.job
        val jobCode = job?.optString("job_code", "???") ?: "???"
        val username = Session.username

        when (mode) {
            "pending" -> {
                icon.text = "⏳"
                title.text = "Awaiting Approval"
                body.text = "You're registered for this job but need admin approval before you can start sending."
                btnWa.text = "Message Admin to Get Approved"
                btnWa.setOnClickListener {
                    val msg = "Hi, I want to start job $jobCode. My WhatsApp number is $username. Please approve me."
                    openWhatsApp(msg)
                }
            }
            "blocked" -> {
                icon.text = "🚫"
                title.text = "Daily Limit Reached"
                body.text = "You've hit today's sending limit. Message admin to get re-approved for your next batch."
                btnWa.text = "Send Daily Proof / Request More"
                btnWa.setOnClickListener {
                    val msg = "Hi, I have completed my DM batch for job $jobCode. My number is $username. Please re-approve me to continue."
                    openWhatsApp(msg)
                }
            }
            "banned" -> {
                icon.text = "⛔"
                title.text = "Account Banned"
                body.text = "This phone number has been banned from all jobs. Contact admin if you believe this is a mistake."
                btnWa.text = "Contact Admin"
                btnWa.setOnClickListener {
                    val msg = "Hi, my number ($username) appears to be banned. Please review my account."
                    openWhatsApp(msg)
                }
            }
            "done" -> {
                icon.text = "🎉"
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
