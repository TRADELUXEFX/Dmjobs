package com.dmjobs.worker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        findViewById<TextView>(R.id.prev_title).text = job.optString("title", "Untitled Job")
        findViewById<TextView>(R.id.prev_total).text = totalContacts.toString()
        findViewById<TextView>(R.id.prev_pay).text = if (pay > 0) "₦${"%,.0f".format(pay)}" else "—"
        findViewById<TextView>(R.id.prev_daily).text = "$maxPerDay DMs"
        findViewById<TextView>(R.id.prev_msg).text = job.optString("message", "(no message set)")

        val mins = ceil((maxPerDay * rateLimitSeconds) / 60.0).toInt()
        findViewById<TextView>(R.id.prev_time).text =
            "⏱ About $mins min/day · ${rateLimitSeconds}s delay between sends"

        // Daily Earning Total = pay per DM × daily limit (how much a worker can make in one day)
        val dailyEarning = pay * maxPerDay
        findViewById<TextView>(R.id.prev_daily_earning).text =
            if (dailyEarning > 0) "₦${"%,.0f".format(dailyEarning)}" else "—"
        findViewById<TextView>(R.id.prev_daily_earning_breakdown).text =
            "$maxPerDay DMs/day · ₦${"%,.0f".format(pay)} each"

        val prevAvailable = findViewById<TextView>(R.id.prev_available)
        val prevMax = findViewById<TextView>(R.id.prev_max)
        val soldOutNotice = findViewById<TextView>(R.id.prev_sold_out)
        val btnAccept = findViewById<Button>(R.id.btn_accept)

        prevAvailable.text = ""
        prevAvailable.minHeight = (18 * resources.displayMetrics.density).toInt()
        prevAvailable.setBackgroundResource(R.drawable.skeleton_bar)
        prevMax.text = ""
        prevMax.minHeight = (18 * resources.displayMetrics.density).toInt()
        prevMax.setBackgroundResource(R.drawable.skeleton_bar)
        btnAccept.isEnabled = false

        lifecycleScope.launch {
            val available = try {
                val jobId = job.getString("id")
                withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_contacts",
                        "job_id=eq.$jobId&status=eq.pending&select=id"
                    ).length()
                }
            } catch (e: Exception) {
                totalContacts // fall back to total if the count fails, rather than blocking the worker
            }

            prevAvailable.background = null
            prevAvailable.text = available.toString()
            prevAvailable.setTextColor(
                resources.getColor(if (available > 0) R.color.green_deep else R.color.red, theme)
            )

            // Max Earning reflects what's actually claimable right now, not the job's original total
            val maxEarn = if (pay > 0 && available > 0) "₦${"%,.0f".format(pay * available)}" else "—"
            prevMax.background = null
            prevMax.text = maxEarn

            if (available <= 0) {
                soldOutNotice.visibility = android.view.View.VISIBLE
                btnAccept.isEnabled = false
                btnAccept.setBackgroundResource(R.drawable.button_rounded_disabled)
                btnAccept.setTextColor(android.graphics.Color.parseColor("#888780"))
            } else {
                soldOutNotice.visibility = android.view.View.GONE
                btnAccept.isEnabled = true
                btnAccept.setBackgroundResource(R.drawable.button_rounded)
                btnAccept.setTextColor(resources.getColor(R.color.white, theme))
            }
        }

        btnAccept.setOnClickListener {
            Session.sentToday = 0
            startActivity(Intent(this, SendActivity::class.java))
        }

        findViewById<Button>(R.id.btn_reject).setOnClickListener {
            Session.job = null
            Session.persist()
            finish()
        }
    }
}
