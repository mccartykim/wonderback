package com.google.android.accessibility.talkback.agent.model

import android.graphics.Rect

/**
 * Represents a single TalkBack utterance event captured during screen reader navigation.
 * Contains the spoken text, element metadata, navigation context, and screen info.
 */
data class UtteranceEvent(
    val text: String,
    val timestamp: Long,
    val element: ElementInfo,
    val navigation: NavigationType,
    val screen: ScreenContext,
    val queueMode: Int = 0,
    val flags: Int = 0
)

/**
 * Metadata about the accessibility node that generated the utterance.
 */
data class ElementInfo(
    val bounds: Rect = Rect(),
    val className: String = "",
    val contentDescription: String? = null,
    val text: String? = null,
    val viewIdResourceName: String? = null,
    val isClickable: Boolean = false,
    val isFocusable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val isSelected: Boolean = false,
    val stateDescription: String? = null,
    val roleDescription: String? = null,
    val childCount: Int = 0,
    val drawingOrder: Int = -1,
    val collectionInfo: CollectionData? = null,
    val collectionItemInfo: CollectionItemData? = null
)

/**
 * Simplified collection info for lists/grids.
 */
data class CollectionData(
    val rowCount: Int,
    val columnCount: Int,
    val isHierarchical: Boolean
)

/**
 * Simplified collection item info for list/grid items.
 */
data class CollectionItemData(
    val rowIndex: Int,
    val columnIndex: Int,
    val rowSpan: Int,
    val columnSpan: Int,
    val isHeading: Boolean,
    val isSelected: Boolean
)

/**
 * Screen/window context at the time of utterance.
 */
data class ScreenContext(
    val packageName: String = "",
    val activityName: String = "",
    val windowTitle: String? = null,
    val windowId: Int = -1,
    val isScrollable: Boolean = false,
    val displayId: Int = 0
)

/**
 * Type of navigation action that triggered the utterance.
 */
enum class NavigationType {
    SWIPE_RIGHT,
    SWIPE_LEFT,
    SWIPE_UP,
    SWIPE_DOWN,
    TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    SCREEN_CHANGE,
    SCROLL,
    FOCUS_CHANGE,
    WINDOW_CHANGE,
    ANNOUNCEMENT,
    KEY_EVENT,
    UNKNOWN
}
