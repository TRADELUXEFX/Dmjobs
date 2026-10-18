package com.dmjobs.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class JobsAdapter(
    private var jobs: List<JSONObject>,
    private val onJobClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<JobsAdapter.JobViewHolder>() {

    fun updateJobs(newJobs: List<JSONObject>) {
        this.jobs = newJobs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(jobs[position])
    }

    override fun getItemCount(): Int = jobs.size

    inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardClickable: View = itemView.findViewById(R.id.job_card_clickable)
        private val txtTitle: TextView = itemView.findViewById(R.id.job_item_title)
        private val txtPay: TextView = itemView.findViewById(R.id.job_item_pay)
        private val txtStatus: TextView = itemView.findViewById(R.id.job_item_status)
        private val txtCode: TextView = itemView.findViewById(R.id.job_item_code)
        private val txtCooldown: TextView = itemView.findViewById(R.id.job_item_cooldown)
        private val txtContacts: TextView = itemView.findViewById(R.id.job_item_contacts)
        private val txtDaily: TextView = itemView.findViewById(R.id.job_item_daily_potential)
        private val btnSelect: Button = itemView.findViewById(R.id.btn_select_job)

        fun bind(job: JSONObject) {
            val title = job.optString("title", "Outreach Campaign")
            val code = job.optString("job_code", "")
            val pay = job.optDouble("pay_per_dm", 0.0)
            val maxPerDay = job.optInt("max_per_day", 30)
            val delay = job.optInt("rate_limit_seconds", 10)
            val contacts = job.optInt("total_contacts", 0)

            txtTitle.text = if (title.isNotBlank()) title else "Campaign $code"
            txtPay.text = if (pay > 0) "₦${"%,.0f".format(pay)} / DM" else "Paid DM"
            txtCode.text = "CODE: $code"
            txtCooldown.text = "⏱ ${delay}s delay"
            txtContacts.text = if (contacts > 0) "$contacts contacts" else "Available"

            val displayStatus = job.optString("display_status", "Available")
            txtStatus.text = displayStatus
            when (displayStatus) {
                "In Progress" -> {
                    txtStatus.setBackgroundResource(R.drawable.badge_amber_bg)
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#B45309"))
                    btnSelect.text = "View In-Progress Job →"
                }
                "Completed" -> {
                    txtStatus.setBackgroundResource(R.drawable.badge_slate_bg)
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#64748B"))
                    btnSelect.text = "View Completed Job →"
                }
                else -> {
                    txtStatus.setBackgroundResource(R.drawable.pay_badge_bg)
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#15803D"))
                    btnSelect.text = "Select This Job →"
                }
            }

            val dailyPotential = pay * maxPerDay
            txtDaily.text = if (dailyPotential > 0) {
                "Daily limit: $maxPerDay DMs/day (Earn up to ₦${"%,.0f".format(dailyPotential)}/day)"
            } else {
                "Daily limit: $maxPerDay DMs/day"
            }

            val clickListener = View.OnClickListener {
                onJobClick(job)
            }

            cardClickable.setOnClickListener(clickListener)
            btnSelect.setOnClickListener(clickListener)
        }
    }
}
