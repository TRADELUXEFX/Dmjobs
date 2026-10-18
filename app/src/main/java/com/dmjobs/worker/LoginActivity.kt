package com.dmjobs.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var inpUsername: EditText
    private lateinit var inpJobcode: EditText
    private lateinit var errorText: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnNoJob: Button
    private lateinit var progress: ProgressBar

    // Available jobs views
    private lateinit var recyclerJobs: RecyclerView
    private lateinit var jobsLoadingContainer: View
    private lateinit var jobsEmptyCard: View
    private lateinit var jobsCountBadge: TextView
    private lateinit var btnRefreshJobs: ImageButton
    private lateinit var btnRetryFetchJobs: Button

    private lateinit var jobsAdapter: JobsAdapter
    private val availableJobs = mutableListOf<JSONObject>()
    private var isFetchingJobs = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Session.init(this)

        inpUsername = findViewById(R.id.inp_username)
        inpJobcode = findViewById(R.id.inp_jobcode)
        errorText = findViewById(R.id.login_error)
        btnLogin = findViewById(R.id.btn_login)
        btnNoJob = findViewById(R.id.btn_no_job)
        progress = findViewById(R.id.login_progress)

        recyclerJobs = findViewById(R.id.recycler_jobs)
        jobsLoadingContainer = findViewById(R.id.jobs_loading_container)
        jobsEmptyCard = findViewById(R.id.jobs_empty_card)
        jobsCountBadge = findViewById(R.id.jobs_count_badge)
        btnRefreshJobs = findViewById(R.id.btn_refresh_jobs)
        btnRetryFetchJobs = findViewById(R.id.btn_retry_fetch_jobs)

        // Pre-fill phone number if worker has used the app before
        if (Session.username.isNotEmpty()) {
            inpUsername.setText(Session.username)
        }

        // Setup Jobs RecyclerView
        jobsAdapter = JobsAdapter(availableJobs) { selectedJob ->
            onJobClicked(selectedJob)
        }
        recyclerJobs.layoutManager = LinearLayoutManager(this)
        recyclerJobs.adapter = jobsAdapter

        // Button handlers
        btnLogin.setOnClickListener { doManualLogin() }
        btnRefreshJobs.setOnClickListener { fetchAvailableJobs() }
        btnRetryFetchJobs.setOnClickListener { fetchAvailableJobs() }
        btnNoJob.setOnClickListener {
            val phone = inpUsername.text.toString().trim()
            val msg = if (phone.isNotEmpty()) {
                "Hi, I'm worker $phone. Please assign me to an outreach job."
            } else {
                "Hi, I want to start working on DM Jobs. Please assign me to a job."
            }
            val uri = Uri.parse("https://wa.me/${Supabase.ADMIN_PHONE}?text=${Uri.encode(msg)}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        // Resume a saved session if active
        if (Session.hasSavedSession()) {
            setLoading(true)
            resumeSavedSession()
        } else {
            fetchAvailableJobs()
        }
    }

    override fun onResume() {
        super.onResume()
        // If not currently logged into an active job, refresh available jobs list
        if (!Session.hasSavedSession() && availableJobs.isEmpty()) {
            fetchAvailableJobs()
        }
    }

    private fun fetchAvailableJobs() {
        if (isFetchingJobs) return
        isFetchingJobs = true

        jobsLoadingContainer.visibility = View.VISIBLE
        jobsEmptyCard.visibility = View.GONE
        recyclerJobs.visibility = View.GONE
        jobsCountBadge.text = "Loading..."

        lifecycleScope.launch {
            try {
                val jobsArray = withContext(Dispatchers.IO) {
                    try {
                        // First attempt: fetch active jobs ordered by creation/id
                        val active = Supabase.select("wdmj_jobs", "status=eq.active&order=id.desc&select=*")
                        if (active.length() > 0) active
                        else Supabase.select("wdmj_jobs", "order=id.desc&select=*")
                    } catch (_: Exception) {
                        Supabase.select("wdmj_jobs", "select=*")
                    }
                }

                availableJobs.clear()
                for (i in 0 until jobsArray.length()) {
                    val job = jobsArray.getJSONObject(i)
                    // Only include active or open jobs
                    val status = job.optString("status", "active")
                    if (status != "inactive" && status != "archived") {
                        availableJobs.add(job)
                    }
                }

                jobsLoadingContainer.visibility = View.GONE
                if (availableJobs.isEmpty()) {
                    jobsEmptyCard.visibility = View.VISIBLE
                    recyclerJobs.visibility = View.GONE
                    jobsCountBadge.text = "0 open"
                } else {
                    jobsEmptyCard.visibility = View.GONE
                    recyclerJobs.visibility = View.VISIBLE
                    jobsAdapter.updateJobs(availableJobs)
                    jobsCountBadge.text = "${availableJobs.size} open"
                }
            } catch (e: Exception) {
                jobsLoadingContainer.visibility = View.GONE
                jobsEmptyCard.visibility = View.VISIBLE
                recyclerJobs.visibility = View.GONE
                jobsCountBadge.text = "Offline"
                showError("Could not refresh jobs: ${e.message ?: "Network error"}")
            } finally {
                isFetchingJobs = false
            }
        }
    }

    private fun onJobClicked(job: JSONObject) {
        val phoneInput = inpUsername.text.toString().trim()
        showError("")

        if (phoneInput.isEmpty()) {
            showError("Please enter your 11-digit phone number above to start this job.")
            inpUsername.requestFocus()
            return
        }

        val cleanPhone = phoneInput.replace(" ", "")
        if (!Regex("^\\d{11}$").matches(cleanPhone)) {
            showError("Enter a valid 11-digit phone number (e.g. 08012345678).")
            inpUsername.requestFocus()
            return
        }

        // Auto-fill the manual code field for reference
        val code = job.optString("job_code", "")
        if (code.isNotEmpty()) {
            inpJobcode.setText(code)
        }

        startJobWithPhone(cleanPhone, job)
    }

    private fun doManualLogin() {
        val username = inpUsername.text.toString().trim()
        val code = inpJobcode.text.toString().trim().uppercase()
        showError("")

        if (username.isEmpty()) { showError("Please enter your phone number."); return }
        if (code.isEmpty()) { showError("Please enter a job code or choose a job above."); return }
        val cleanPhone = username.replace(" ", "")
        if (!Regex("^\\d{11}$").matches(cleanPhone)) {
            showError("Enter a valid 11-digit phone number (e.g. 08012345678).")
            return
        }
        if (code.length < 4) { showError("Job code looks too short. Check and try again."); return }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val jobs = withContext(Dispatchers.IO) {
                    Supabase.select("wdmj_jobs", "job_code=eq.$code&select=*")
                }
                if (jobs.length() == 0) {
                    showError("No job found for code \"$code\". Double-check or select one above.")
                    setLoading(false)
                    return@launch
                }
                val job = jobs.getJSONObject(0)
                startJobWithPhone(cleanPhone, job)
            } catch (e: Exception) {
                showError(e.message ?: "Failed to find job. Please try again.")
                setLoading(false)
            }
        }
    }

    private fun startJobWithPhone(cleanPhone: String, job: JSONObject) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                Session.username = cleanPhone
                Session.job = job
                Session.persist()

                val jobId = job.getString("id")

                // 1. Check if banned across ANY job
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

                // Active — go directly to preview
                startActivity(Intent(this@LoginActivity, PreviewActivity::class.java))
                setLoading(false)

            } catch (e: Exception) {
                showError(e.message ?: "Something went wrong. Please try again.")
                setLoading(false)
            }
        }
    }

    private fun resumeSavedSession() {
        val job = Session.job ?: return
        val phone = Session.username
        val jobId = job.optString("id", "")
        if (jobId.isEmpty()) { setLoading(false); fetchAvailableJobs(); return }

        lifecycleScope.launch {
            try {
                val banCheck = withContext(Dispatchers.IO) {
                    Supabase.select(
                        "wdmj_worker_status",
                        "phone=eq.$phone&status=eq.banned&select=status&limit=1"
                    )
                }
                if (banCheck.length() > 0) {
                    startActivity(Intent(this@LoginActivity, StatusActivity::class.java).apply {
                        putExtra("mode", "banned")
                    })
                    setLoading(false)
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
                    }
                }

                // Active — go straight back to sending
                startActivity(Intent(this@LoginActivity, SendActivity::class.java))
                setLoading(false)

            } catch (e: Exception) {
                // Fall back to login screen with available jobs
                setLoading(false)
                fetchAvailableJobs()
            }
        }
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setLoading(on: Boolean) {
        btnLogin.isEnabled = !on
        progress.visibility = if (on) View.VISIBLE else View.GONE
    }
}
