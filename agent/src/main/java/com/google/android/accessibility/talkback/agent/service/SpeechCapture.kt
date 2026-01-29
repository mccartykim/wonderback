package com.google.android.accessibility.talkback.agent.service

import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.talkback.agent.model.*

/**
 * Captures TalkBack speech events and converts them to UtteranceEvents.
 *
 * This class is called from a hook in SpeechControllerImpl.speak() to intercept
 * all utterances. It extracts metadata from the current accessibility focus node.
 *
 * In TTS-suppressed mode, the capture still happens but TTS output is silenced,
 * making this useful for automated testing.
 */
class SpeechCapture {

    private var lastNavigationType: NavigationType = NavigationType.UNKNOWN
    private var lastScreenContext: ScreenContext = ScreenContext()
    private val listeners = mutableListOf<SpeechCaptureListener>()

    /**
     * Called from the speak() hook in SpeechControllerImpl.
     * Captures the utterance text along with current accessibility context.
     *
     * @param text The text being spoken by TalkBack
     * @param focusedNode The currently focused accessibility node (may be null)
     * @param queueMode The TTS queue mode
     * @param flags The feedback item flags
     */
    fun captureUtterance(
        text: CharSequence,
        focusedNode: AccessibilityNodeInfo?,
        queueMode: Int,
        flags: Int
    ) {
        val event = UtteranceEvent(
            text = text.toString(),
            timestamp = System.currentTimeMillis(),
            element = extractElementInfo(focusedNode),
            navigation = lastNavigationType,
            screen = lastScreenContext,
            queueMode = queueMode,
            flags = flags
        )

        Log.d(TAG, "Captured: \"${event.text}\" [${event.element.className}]")

        for (listener in listeners) {
            listener.onUtteranceCaptured(event)
        }
    }

    /**
     * Update the navigation type from gesture/event processors.
     */
    fun setNavigationType(type: NavigationType) {
        lastNavigationType = type
    }

    /**
     * Update the screen context when windows/activities change.
     */
    fun updateScreenContext(
        packageName: String? = null,
        activityName: String? = null,
        windowTitle: String? = null,
        windowId: Int = -1,
        isScrollable: Boolean = false
    ) {
        lastScreenContext = ScreenContext(
            packageName = packageName ?: lastScreenContext.packageName,
            activityName = activityName ?: lastScreenContext.activityName,
            windowTitle = windowTitle ?: lastScreenContext.windowTitle,
            windowId = if (windowId >= 0) windowId else lastScreenContext.windowId,
            isScrollable = isScrollable
        )
    }

    /**
     * Update screen context from an AccessibilityWindowInfo.
     */
    fun updateScreenContext(window: AccessibilityWindowInfo?) {
        if (window == null) return
        updateScreenContext(
            windowTitle = window.title?.toString(),
            windowId = window.id
        )
    }

    fun addListener(listener: SpeechCaptureListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SpeechCaptureListener) {
        listeners.remove(listener)
    }

    // -- Private helpers --

    private fun extractElementInfo(node: AccessibilityNodeInfo?): ElementInfo {
        if (node == null) return ElementInfo()

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return ElementInfo(
            bounds = bounds,
            className = node.className?.toString() ?: "",
            contentDescription = node.contentDescription?.toString(),
            text = node.text?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            isClickable = node.isClickable,
            isFocusable = node.isFocusable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            isEditable = node.isEditable,
            isScrollable = node.isScrollable,
            isEnabled = node.isEnabled,
            isSelected = node.isSelected,
            stateDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                node.stateDescription?.toString()
            } else null,
            roleDescription = node.extras?.getCharSequence("AccessibilityNodeInfo.roleDescription")?.toString(),
            childCount = node.childCount,
            drawingOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                node.drawingOrder
            } else -1,
            collectionInfo = node.collectionInfo?.let {
                CollectionData(
                    rowCount = it.rowCount,
                    columnCount = it.columnCount,
                    isHierarchical = it.isHierarchical
                )
            },
            collectionItemInfo = node.collectionItemInfo?.let {
                CollectionItemData(
                    rowIndex = it.rowIndex,
                    columnIndex = it.columnIndex,
                    rowSpan = it.rowSpan,
                    columnSpan = it.columnSpan,
                    isHeading = it.isHeading,
                    isSelected = it.isSelected
                )
            }
        )
    }

    interface SpeechCaptureListener {
        fun onUtteranceCaptured(event: UtteranceEvent)
    }

    companion object {
        private const val TAG = "SpeechCapture"

        @Volatile
        private var instance: SpeechCapture? = null

        fun getInstance(): SpeechCapture {
            return instance ?: synchronized(this) {
                instance ?: SpeechCapture().also { instance = it }
            }
        }
    }
}
