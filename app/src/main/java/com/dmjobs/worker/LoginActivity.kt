package com.dmjobs.worker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    // Tab layout & containers
    private lateinit var tabLayout: TabLayout
    private lateinit var layoutBrowseSection: View
    private lateinit var layoutCodeSection: View

    // Global Error
    private lateinit var globalErrorText: TextView

    // Browse Jobs Tab Views
    private lateinit var inpSearchJobs: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var btnRefreshJobs: ImageButton

    // Filter Buttons (Available, In Progress, Completed)
    private lateinit var btnFilterAvailable: Button
    private lateinit var btnFilterInProgress: Button
    private lateinit var btnFilterCompleted: Button
    private var currentFilter: String = "Available"

    private lateinit var jobsCountBadge: TextView
    private lateinit var jobsLoadingContainer: View
    private lateinit var jobsEmptyCard: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDesc: TextView
    private lateinit var btnRetryFetchJobs: Button
    private lateinit var recyclerJobs: RecyclerView

    // Enter Code Tab Views
    private lateinit var inpManualJobcode: EditText
    private lateinit var codeErrorText: TextView
    private lateinit var codeProgress: ProgressBar
    private lateinit var btnVerifyCode: Button

    // Common Footer
    private lateinit var btnNoJob: Button

    // Data & Adapter
    private lateinit var jobsAdapter: JobsAdapter
    private val allJobs = mutableListOf<JSONObject>()
    private val displayedJobs = mutableListOf<JSONObject>()
    private var isFetchingJobs = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Session.init(this)

        initViews()
        setupTabs()
        setupSearch()
        setupFilters()
        setupRecyclerView()
        setupListeners()

        // Check if there is already an active session to resume
        if (Session.hasSavedSession()) {
            resumeSavedSession()
        } else {
            fetchAvailableJobs()
        }
    }

    override fun onResume() {
        super.onResume()
        // If returning without an active session and list is empty, refresh
        if (!Session.hasSavedSession() && allJobs.isEmpty()) {
            fetchAvailableJobs()
        }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tab_layout)
        layoutBrowseSection = findViewById(R.id.layout_browse_section)
        layoutCodeSection = findViewById(R.id.layout_code_section)
        globalErrorText = findViewById(R.id.login_error)

        inpSearchJobs = findViewById(R.id.inp_search_jobs)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        btnRefreshJobs = findViewById(R.id.btn_refresh_jobs)

        btnFilterAvailable = findViewById(R.id.btn_filter_available)
        btnFilterInProgress = findViewById(R.id.btn_filter_in_progress)
        btnFilterCompleted = findViewById(R.id.btn_filter_completed)

        jobsCountBadge = findViewById(R.id.jobs_count_badge)
        jobsLoadingContainer = findViewById(R.id.jobs_loading_container)
        jobsEmptyCard = findViewById(R.id.jobs_empty_card)
        emptyTitle = findViewById(R.id.empty_title)
        emptyDesc = findViewById(R.id.empty_desc)
        btnRetryFetchJobs = findViewById(R.id.btn_retry_fetch_jobs)
        recyclerJobs = findViewById(R.id.recycler_jobs)

        inpManualJobcode = findViewById(R.id.inp_manual_jobcode)
        codeErrorText = findViewById(R.id.code_error_text)
        codeProgress = findViewById(R.id.code_progress)
        btnVerifyCode = findViewById(R.id.btn_verify_code)

        btnNoJob = findViewById(R.id.btn_no_job)
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        layoutBrowseSection.visibility = View.VISIBLE
                        layoutCodeSection.visibility = View.GONE
                        showGlobalError("")
                    }
                    1 -> {
                        layoutBrowseSection.visibility = View.GONE
                        layoutCodeSection.visibility = View.VISIBLE
                        showGlobalError("")
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        inpSearchJobs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterJobs(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            inpSearchJobs.setText("")
        }
    }

    private fun setupFilters() {
        btnFilterAvailable.setOnClickListener {
            setFilter("Available")
        }
        btnFilterInProgress.setOnClickListener {
            setFilter("In Progress")
        }
        btnFilterCompleted.setOnClickListener {
            setFilter("Completed")
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        updateFilterButtonStyles()
        filterJobs(inpSearchJobs.text.toString().trim())
    }

    private fun updateFilterButtonStyles() {
        val mutedColor = resources.getColor(R.color.text_muted, theme)
        val whiteColor = Color.WHITE

        if (currentFilter == "Available") {
            btnFilterAvailable.setBackgroundResource(R.drawable.chip_filter_selected)
            btnFilterAvailable.setTextColor(whiteColor)
        } else {
            btnFilterAvailable.setBackgroundResource(R.drawable.chip_filter_unselected)
            btnFilterAvailable.setTextColor(mutedColor)
        }

        if (currentFilter == "In Progress") {
            btnFilterInProgress.setBackgroundResource(R.drawable.chip_filter_selected)
            btnFilterInProgress.setTextColor(whiteColor)
        } else {
            btnFilterInProgress.setBackgroundResource(R.drawable.chip_filter_unselected)
            btnFilterInProgress.setTextColor(mutedColor)
        }

        if (currentFilter == "Completed") {
            btnFilterCompleted.setBackgroundResource(R.drawable.chip_filter_selected)
            btnFilterCompleted.setTextColor(whiteColor)
        } else {
            btnFilterCompleted.setBackgroundResource(R.drawable.chip_filter_unselected)
            btnFilterCompleted.setTextColor(mutedColor)
        }
    }

    private fun setupRecyclerView() {
        jobsAdapter = JobsAdapter(displayedJobs) { selectedJob ->
            openJobDetail(selectedJob)
        }
        recyclerJobs.layoutManager = LinearLayoutManager(this)
        recyclerJobs.adapter = jobsAdapter
    }

    private fun setupListeners() {
        btnRefreshJobs.setOnClickListener { fetchAvailableJobs() }
        btnRetryFetchJobs.setOnClickListener { fetchAvailableJobs() }

        btnVerifyCode.setOnClickListener {
            verifyManualCode()
        }

        btnNoJob.setOnClickListener {
            val phone = Session.username
            val msg = if (phone.isNotEmpty()) {
                "Hi, I'm worker $phone. Please assign me to a WhatsApp outreach job."
            } else {
                "Hi, I would like to join DM Jobs as a worker. Please assign me to a job."
            }
            val uri = Uri.parse("https://wa.me/${Supabase.ADMIN_PHONE}?text=${Uri.encode(msg)}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun openJobDetail(job: JSONObject) {
        val intent = Intent(this, JobDetailActivity::class.java).apply {
            putExtra("job_json", job.toString())
        }
        startActivity(intent)
    }

    private fun fetchAvailableJobs() {
        if (isFetchingJobs) return
        isFetchingJobs = true

        jobsLoadingContainer.visibility = View.VISIBLE
        jobsEmptyCard.visibility = View.GONE
        recyclerJobs.visibility = View.GONE
        jobsCountBadge.text = "Loading campaigns..."
        showGlobalError("")

        lifecycleScope.launch {
            try {
                val jobsArray = withContext(Dispatchers.IO) {
                    try {
                        Supabase.select("wdmj_jobs", "order=id.desc&select=*")
                    } catch (_: Exception) {
                        Supabase.select("wdmj_jobs", "select=*")
                    }
                }

                allJobs.clear()
                for (i in 0 until jobsArray.length()) {
                    val job = jobsArray.getJSONObject(i)
                    val jobId = job.optString("id", "")
                    val jobStatus = job.optString("status", "active").lowercase()

                    val pendingCount = withContext(Dispatchers.IO) {
                        try {
                            Supabase.select("wdmj_contacts", "job_id=eq.$jobId&status=eq.pending&select=id&limit=1").length()
                        } catch (_: Exception) { 1 }
                    }
                    val lockedCount = withContext(Dispatchers.IO) {
                        try {
                            Supabase.select("wdmj_contacts", "job_id=eq.$jobId&status=eq.locked&select=id&limit=1").length()
                        } catch (_: Exception) { 0 }
                    }

                    // Categorize as Available, In Progress, or Completed
                    val displayStatus = when {
                        jobStatus == "completed" || jobStatus == "archived" -> "Completed"
                        pendingCount > 0 -> "Available"
                        lockedCount > 0 -> "In Progress"
                        else -> "Completed"
                    }

                    job.put("display_status", displayStatus)
                    job.put("pending_count", pendingCount)
                    job.put("locked_count", lockedCount)

                    allJobs.add(job)
                }

                // Update filter button labels with real counts
                val availableCount = allJobs.count { it.optString("display_status") == "Available" }
                val inProgressCount = allJobs.count { it.optString("display_status") == "In Progress" }
                val completedCount = allJobs.count { it.optString("display_status") == "Completed" }

                btnFilterAvailable.text = "Available ($availableCount)"
                btnFilterInProgress.text = "In Progress ($inProgressCount)"
                btnFilterCompleted.text = "Completed ($completedCount)"

                jobsLoadingContainer.visibility = View.GONE
                filterJobs(inpSearchJobs.text.toString().trim())

            } catch (e: Exception) {
                jobsLoadingContainer.visibility = View.GONE
                jobsEmptyCard.visibility = View.VISIBLE
                emptyTitle.text = "Could Not Load Campaigns"
                emptyDesc.text = e.message ?: "Please check your network connection and tap below to retry."
                recyclerJobs.visibility = View.GONE
                jobsCountBadge.text = "Offline"
            } finally {
                isFetchingJobs = false
            }
        }
    }

    private fun filterJobs(query: String) {
        displayedJobs.clear()
        val q = query.lowercase()

        for (job in allJobs) {
            val status = job.optString("display_status", "Available")
            val matchesFilter = (status == currentFilter)

            val title = job.optString("title", "").lowercase()
            val code = job.optString("job_code", "").lowercase()
            val msg = job.optString("message", "").lowercase()
            val matchesSearch = q.isEmpty() || title.contains(q) || code.contains(q) || msg.contains(q)

            if (matchesFilter && matchesSearch) {
                displayedJobs.add(job)
            }
        }

        jobsAdapter.updateJobs(displayedJobs)

        if (displayedJobs.isEmpty()) {
            jobsEmptyCard.visibility = View.VISIBLE
            recyclerJobs.visibility = View.GONE

            if (query.isNotEmpty()) {
                emptyTitle.text = "No Matching $currentFilter Jobs"
                emptyDesc.text = "No $currentFilter campaigns matched \"$query\". Try clearing or changing your search."
                jobsCountBadge.text = "0 matches in $currentFilter"
            } else {
                when (currentFilter) {
                    "Available" -> {
                        emptyTitle.text = "No Available Campaigns"
                        emptyDesc.text = "All active campaigns are currently in progress or completed. Check back soon for newly posted batches!"
                    }
                    "In Progress" -> {
                        emptyTitle.text = "No In-Progress Campaigns"
                        emptyDesc.text = "There are no campaigns undergoing active messaging right now."
                    }
                    "Completed" -> {
                        emptyTitle.text = "No Completed Campaigns"
                        emptyDesc.text = "Completed campaigns will be displayed here once all contacts have been delivered."
                    }
                }
                jobsCountBadge.text = "0 $currentFilter"
            }
        } else {
            jobsEmptyCard.visibility = View.GONE
            recyclerJobs.visibility = View.VISIBLE
            val s = if (displayedJobs.size == 1) "" else "s"
            jobsCountBadge.text = "${displayedJobs.size} $currentFilter campaign$s"
        }
    }

    private fun verifyManualCode() {
        val code = inpManualJobcode.text.toString().trim().uppercase()
        showCodeError("")

        if (code.isEmpty()) {
            showCodeError("Please enter a campaign job code.")
            return
        }
        if (code.length < 4) {
            showCodeError("Job code is too short. Please check and try again.")
            return
        }

        setCodeLoading(true)
        lifecycleScope.launch {
            try {
                val jobs = withContext(Dispatchers.IO) {
                    Supabase.select("wdmj_jobs", "job_code=eq.$code&select=*")
                }
                setCodeLoading(false)

                if (jobs.length() == 0) {
                    showCodeError("No campaign found for code \"$code\". Double-check with your admin.")
                    return@launch
                }

                val matchedJob = jobs.getJSONObject(0)
                openJobDetail(matchedJob)

            } catch (e: Exception) {
                setCodeLoading(false)
                showCodeError(e.message ?: "Failed to verify code. Try again.")
            }
        }
    }

    private fun resumeSavedSession() {
        val job = Session.job ?: return
        val phone = Session.username
        val jobId = job.optString("id", "")
        if (jobId.isEmpty()) { fetchAvailableJobs(); return }

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
                            return@launch
                        }
                        "blocked" -> {
                            startActivity(Intent(this@LoginActivity, StatusActivity::class.java).apply {
                                putExtra("mode", "blocked")
                            })
                            return@launch
                        }
                    }
                }

                // Active — go straight back to sending
                startActivity(Intent(this@LoginActivity, SendActivity::class.java))

            } catch (e: Exception) {
                fetchAvailableJobs()
            }
        }
    }

    private fun showGlobalError(msg: String) {
        globalErrorText.text = msg
        globalErrorText.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCodeError(msg: String) {
        codeErrorText.text = msg
        codeErrorText.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setCodeLoading(loading: Boolean) {
        btnVerifyCode.isEnabled = !loading
        codeProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
