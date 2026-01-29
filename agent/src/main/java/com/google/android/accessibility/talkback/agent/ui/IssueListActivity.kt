package com.google.android.accessibility.talkback.agent.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.accessibility.talkback.agent.model.Issue
import com.google.android.accessibility.talkback.agent.model.IssueCategory
import com.google.android.accessibility.talkback.agent.model.IssueSeverity

/**
 * Activity that displays accessibility issues found by the agent.
 * Shows a list of issues with severity icons, and tapping an issue
 * shows a detail dialog with explanation and suggestion.
 */
class IssueListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build UI programmatically to avoid resource conflicts with TalkBack
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "Accessibility Issues"
            textSize = 24f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        emptyView = TextView(this).apply {
            text = "No issues found yet.\n\nNavigate your app with TalkBack enabled and the agent connected to find accessibility issues."
            textSize = 16f
            visibility = View.GONE
        }
        layout.addView(emptyView)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@IssueListActivity)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(recyclerView)

        setContentView(layout)

        // Load issues from IssueStore
        val issues = IssueStore.getIssues()
        if (issues.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = IssueAdapter(issues) { issue ->
                showIssueDetail(issue)
            }
        }
    }

    private fun showIssueDetail(issue: Issue) {
        val severityLabel = when (issue.severity) {
            IssueSeverity.ERROR -> "ERROR"
            IssueSeverity.WARNING -> "WARNING"
            IssueSeverity.SUGGESTION -> "SUGGESTION"
        }

        val categoryLabel = when (issue.category) {
            IssueCategory.LABEL_QUALITY -> "Label Quality"
            IssueCategory.STRUCTURE -> "Structure"
            IssueCategory.CONTEXT -> "Context"
            IssueCategory.NAVIGATION -> "Navigation"
        }

        val detail = buildString {
            appendLine("Severity: $severityLabel")
            appendLine("Category: $categoryLabel")
            appendLine()
            if (issue.utterance.isNotEmpty()) {
                appendLine("Utterance: \"${issue.utterance}\"")
                appendLine()
            }
            if (issue.explanation.isNotEmpty()) {
                appendLine(issue.explanation)
                appendLine()
            }
            if (issue.suggestion.isNotEmpty()) {
                appendLine("Suggestion:")
                appendLine(issue.suggestion)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("$severityLabel: ${issue.issue}")
            .setMessage(detail)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Issue", detail))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Dismiss", null)
            .show()
    }
}

/**
 * RecyclerView adapter for displaying issues.
 */
class IssueAdapter(
    private val issues: List<Issue>,
    private val onClick: (Issue) -> Unit
) : RecyclerView.Adapter<IssueAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val severityIcon: TextView = view.findViewById(android.R.id.text1) as? TextView
            ?: (view as android.widget.LinearLayout).getChildAt(0) as TextView
        val issueText: TextView = (view as android.widget.LinearLayout).getChildAt(1) as TextView
        val categoryText: TextView = (view as android.widget.LinearLayout).getChildAt(2) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val severityView = TextView(parent.context).apply {
            textSize = 12f
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(severityView)

        val issueView = TextView(parent.context).apply {
            textSize = 16f
            setPadding(0, 4, 0, 4)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(issueView)

        val categoryView = TextView(parent.context).apply {
            textSize = 12f
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(categoryView)

        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val issue = issues[position]

        holder.severityIcon.text = when (issue.severity) {
            IssueSeverity.ERROR -> "!! ERROR"
            IssueSeverity.WARNING -> "! WARNING"
            IssueSeverity.SUGGESTION -> "~ SUGGESTION"
        }
        holder.severityIcon.setTextColor(when (issue.severity) {
            IssueSeverity.ERROR -> 0xFFFF0000.toInt()
            IssueSeverity.WARNING -> 0xFFFF8800.toInt()
            IssueSeverity.SUGGESTION -> 0xFF0088FF.toInt()
        })

        holder.issueText.text = issue.issue

        holder.categoryText.text = when (issue.category) {
            IssueCategory.LABEL_QUALITY -> "Label Quality"
            IssueCategory.STRUCTURE -> "Structure"
            IssueCategory.CONTEXT -> "Context"
            IssueCategory.NAVIGATION -> "Navigation"
        }

        holder.itemView.setOnClickListener { onClick(issue) }
    }

    override fun getItemCount() = issues.size
}

/**
 * Simple in-memory store for issues.
 * In production, this would be backed by SQLite/Room.
 */
object IssueStore {
    private val issues = mutableListOf<Issue>()

    fun addIssues(newIssues: List<Issue>) {
        issues.addAll(newIssues)
        while (issues.size > MAX_ISSUES) {
            issues.removeAt(0)
        }
    }

    fun getIssues(): List<Issue> = issues.toList()

    fun clear() {
        issues.clear()
    }

    private const val MAX_ISSUES = 500
}
