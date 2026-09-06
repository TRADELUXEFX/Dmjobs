package com.dmjobs.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var inpUsername: EditText
    private lateinit var inpJobcode: EditText
    private lateinit var errorText: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnNoJob: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        inpUsername = findViewById(R.id.inp_username)
        inpJobcode = findViewById(R.id.inp_jobcode)
        errorText = findViewById(R.id.login_error)
        btnLogin = findViewById(R.id.btn_login)
        btnNoJob = findViewById(R.id.btn_no_job)
        progress = findViewById(R.id.login_progress)

        btnLogin.setOnClickListener { doLogin() }
        btnNoJob.setOnClickListener {
            val msg = "Hi, I don't have a job code yet. Please assign me to a job."
            val uri = Uri.parse("https://wa.me/${Supabase.ADMIN_PHONE}?text=${Uri.encode(msg)}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = if (msg.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun setLoading(on: Boolean) {
        btnLogin.isEnabled = !on
        progress.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun doLogin() {
        val username = inpUsername.text.toString().trim()
        val code = inpJobcode.text.toString().trim().uppercase()
        showError("")

        if (username.isEmpty()) { showError("Please enter your phone number."); return }
        if (code.isEmpty()) { showError("Please enter a job code."); return }
        if (!Regex("^\\d{11}$").matches(username.replace(" ", ""))) {
            showError("Enter a valid 11-digit phone number (e.g. 08012345678).")
            return
        }
        if (code.length < 4) { showError("Job code looks too short. Check and try again."); return }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val cleanPhone = username.replace(" ", "")

                // 1. Look up job by code
                val jobs = withContext(Dispatchers.IO) {
                    Supabase.select("wdmj_jobs", "job_code=eq.$code&select=*")
                }
                if (jobs.length() == 0) {
                    showError("No job found for code \"$code\". Double-check and try again.")
                    setLoading(false)
                    return@launch
                }
                val job = jobs.getJSONObject(0)

                Session.username = cleanPhone
                Session.job = job

                // 2. Check if banned across ANY job
                val banCheck = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_worker_status",
                        "phone=eq.$cleanPhone&status=eq.banned&select=status&limit=1"
                    )
                }
                if (banCheck.length() > 0) {
                    startActivity(Intent(this@LoginActivity, StatusActivity::class.java).apply {
                        putExtra("mode", "banned")
                    })
                    setLoading(false)
                    return@launch
                }

                // 3. Check worker status for this specific job
                val jobId = job.getString("id")
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
                            startActivity(Intent(this@LoginActivity, StatusActivity::class.java).apply {
                                putExtra("mode", "pending")
                            })
                            setLoading(false)
                            return@launch
                        }
                        "blocked" -> {
                            startActivity(Intent(this@LoginActivity, StatusActivity::class.java).apply {
                                putExtra("mode", "blocked")
                            })
                            setLoading(false)
                            return@launch
                        }
                        // "active" — fall through to preview
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
                    startActivity(Intent(this@LoginActivity, StatusActivity::class.java).apply {
                        putExtra("mode", "pending")
                    })
                    setLoading(false)
                    return@launch
                }

                // Active — go to preview
                startActivity(Intent(this@LoginActivity, PreviewActivity::class.java))
                setLoading(false)

            } catch (e: Exception) {
                showError(e.message ?: "Something went wrong. Please try again.")
                setLoading(false)
            }
        }
    }
}
