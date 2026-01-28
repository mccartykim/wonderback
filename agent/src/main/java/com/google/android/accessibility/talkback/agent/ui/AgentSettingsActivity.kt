package com.google.android.accessibility.talkback.agent.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.accessibility.talkback.agent.model.AgentConfig
import com.google.android.accessibility.talkback.agent.model.IssueSeverity
import com.google.android.accessibility.talkback.agent.service.AgentClientService

/**
 * Settings activity for the TalkBack Agent.
 * Provides controls for connection, analysis, and display settings.
 *
 * Built programmatically to avoid resource conflicts with the TalkBack host app.
 */
class AgentSettingsActivity : AppCompatActivity() {

    private lateinit var config: AgentConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = AgentConfig.getInstance(this)

        val scrollView = ScrollView(this).apply {
            setPadding(32, 32, 32, 32)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(layout)

        // -- Title --
        layout.addView(sectionTitle("TalkBack Agent Settings"))

        // -- Agent Enable/Disable --
        layout.addView(sectionHeader("General"))
        val enableSwitch = addSwitch(layout, "Agent Enabled", config.isEnabled)
        val ttsSwitch = addSwitch(layout, "Suppress TTS (Silent Capture)", config.isTtsSuppressed)
        val gestureSwitch = addSwitch(layout, "Gesture Injection", config.gestureInjectionEnabled)
        val debugSwitch = addSwitch(layout, "Debug Logging", config.debugLogging)

        // -- Connection --
        layout.addView(sectionHeader("Connection"))
        val connectionSpinner = addSpinner(
            layout,
            "Connection Method",
            AgentConfig.ConnectionMethod.values().map { it.name },
            config.connectionMethod.ordinal
        )

        val addressEdit = addEditText(layout, "Server Address", config.manualServerAddress)
        val portEdit = addEditText(layout, "Server Port", config.serverPort.toString())
        val tokenEdit = addEditText(layout, "Auth Token (auto-filled after approval)", config.authToken)

        // -- Connection Actions --
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        val connectBtn = Button(this).apply {
            text = "Connect"
            setOnClickListener {
                val intent = Intent(this@AgentSettingsActivity, AgentClientService::class.java)
                intent.action = AgentClientService.ACTION_RECONNECT
                startService(intent)
                Toast.makeText(this@AgentSettingsActivity, "Reconnecting...", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(connectBtn)

        val disconnectBtn = Button(this).apply {
            text = "Disconnect"
            setOnClickListener {
                val intent = Intent(this@AgentSettingsActivity, AgentClientService::class.java)
                intent.action = AgentClientService.ACTION_DISCONNECT
                startService(intent)
                Toast.makeText(this@AgentSettingsActivity, "Disconnected", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(disconnectBtn)
        layout.addView(buttonRow)

        // -- Analysis --
        layout.addView(sectionHeader("Analysis"))
        val triggerSpinner = addSpinner(
            layout,
            "Trigger Mode",
            AgentConfig.TriggerMode.values().map { it.name },
            config.triggerMode.ordinal
        )

        val bufferEdit = addEditText(layout, "Buffer Size", config.bufferSize.toString())

        val severitySpinner = addSpinner(
            layout,
            "Severity Filter",
            IssueSeverity.values().map { it.name },
            config.severityFilter.ordinal
        )

        val notificationSwitch = addSwitch(layout, "Show Notifications", config.showNotifications)
        val metadataSwitch = addSwitch(layout, "Capture Full Metadata", config.captureFullMetadata)

        // -- Save Button --
        layout.addView(Button(this).apply {
            text = "Save Settings"
            setPadding(0, 32, 0, 0)
            setOnClickListener {
                config.isEnabled = enableSwitch.isChecked
                config.isTtsSuppressed = ttsSwitch.isChecked
                config.gestureInjectionEnabled = gestureSwitch.isChecked
                config.debugLogging = debugSwitch.isChecked

                config.connectionMethod = AgentConfig.ConnectionMethod.values()[connectionSpinner.selectedItemPosition]
                config.manualServerAddress = addressEdit.text.toString()
                config.serverPort = portEdit.text.toString().toIntOrNull() ?: AgentConfig.DEFAULT_PORT

                config.triggerMode = AgentConfig.TriggerMode.values()[triggerSpinner.selectedItemPosition]
                config.bufferSize = bufferEdit.text.toString().toIntOrNull() ?: 20
                config.severityFilter = IssueSeverity.values()[severitySpinner.selectedItemPosition]
                config.showNotifications = notificationSwitch.isChecked
                config.captureFullMetadata = metadataSwitch.isChecked
                config.authToken = tokenEdit.text.toString()

                Toast.makeText(this@AgentSettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()

                // Restart service if enabled
                if (config.isEnabled) {
                    AgentClientService.start(this@AgentSettingsActivity)
                }
            }
        })

        // -- View Issues Button --
        layout.addView(Button(this).apply {
            text = "View Issues"
            setOnClickListener {
                startActivity(Intent(this@AgentSettingsActivity, IssueListActivity::class.java))
            }
        })

        // -- Start/Stop Service --
        layout.addView(sectionHeader("Service"))
        val serviceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        serviceRow.addView(Button(this).apply {
            text = "Start Service"
            setOnClickListener { AgentClientService.start(this@AgentSettingsActivity) }
        })
        serviceRow.addView(Button(this).apply {
            text = "Stop Service"
            setOnClickListener { AgentClientService.stop(this@AgentSettingsActivity) }
        })
        layout.addView(serviceRow)

        setContentView(scrollView)
    }

    // -- UI helpers --

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 24f
        setPadding(0, 0, 0, 24)
    }

    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 24, 0, 8)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun addSwitch(parent: LinearLayout, label: String, checked: Boolean): Switch {
        val switch = Switch(this).apply {
            text = label
            isChecked = checked
            setPadding(0, 8, 0, 8)
        }
        parent.addView(switch)
        return switch
    }

    private fun addSpinner(
        parent: LinearLayout,
        label: String,
        options: List<String>,
        selectedIndex: Int
    ): Spinner {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setPadding(0, 8, 0, 4)
        })

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AgentSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
            setSelection(selectedIndex)
        }
        parent.addView(spinner)
        return spinner
    }

    private fun addEditText(parent: LinearLayout, label: String, value: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setPadding(0, 8, 0, 4)
        })

        val editText = EditText(this).apply {
            setText(value)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        parent.addView(editText)
        return editText
    }
}
