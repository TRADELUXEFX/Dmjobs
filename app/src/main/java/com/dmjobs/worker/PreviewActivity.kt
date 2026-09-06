package com.dmjobs.worker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.ceil

class PreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val job = Session.job ?: run { finish(); return }

        val totalContacts = job.optInt("total_contacts", 0)
        val pay = job.optDouble("pay_per_dm", 0.0)
        val maxPerDay = job.optInt("max_per_day", 30)
        val rateLimitSeconds = job.optInt("rate_limit_seconds", 10)

        findViewById<TextView>(R.id.prev_code).text = job.optString("job_code", "")
        findViewById<TextView>(R.id.prev_title).text = job.optString("title", "Untitled Job")
        findViewById<TextView>(R.id.prev_total).text = totalContacts.toString()
        findViewById<TextView>(R.id.prev_pay).text = if (pay > 0) "₦${"%,.0f".format(pay)}" else "—"

        val maxEarn = if (pay > 0 && totalContacts > 0) "₦${"%,.0f".format(pay * totalContacts)}" else "—"
        findViewById<TextView>(R.id.prev_max).text = maxEarn
        findViewById<TextView>(R.id.prev_daily).text = "$maxPerDay DMs"
        findViewById<TextView>(R.id.prev_msg).text = job.optString("message", "(no message set)")

        val mins = ceil((maxPerDay * rateLimitSeconds) / 60.0).toInt()
        findViewById<TextView>(R.id.prev_time).text =
            "⏱ About $mins min/day · ${rateLimitSeconds}s delay between sends"

        findViewById<Button>(R.id.btn_accept).setOnClickListener {
            Session.sentToday = 0
            startActivity(Intent(this, SendActivity::class.java))
        }

        findViewById<Button>(R.id.btn_reject).setOnClickListener {
            Session.job = null
            finish()
        }
    }
}
