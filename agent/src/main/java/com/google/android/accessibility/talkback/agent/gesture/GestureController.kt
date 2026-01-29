package com.google.android.accessibility.talkback.agent.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Programmatic gesture injection via AccessibilityService.dispatchGesture().
 *
 * This is the key mechanism for tablet automation - since standard accessibility
 * gestures (e.g., via `input` command or Instrumentation) may not work on all
 * devices, we use the AccessibilityService's own gesture dispatch API which
 * bypasses those restrictions.
 *
 * Requires android:canPerformGestures="true" in the accessibility service config.
 */
class GestureController(
    private val service: AccessibilityService
) {
    private val handler = Handler(Looper.getMainLooper())
    private val displayMetrics: DisplayMetrics
        get() = service.resources.displayMetrics

    private val screenWidth: Int get() = displayMetrics.widthPixels
    private val screenHeight: Int get() = displayMetrics.heightPixels

    // -- High-level TalkBack-style gestures --

    /**
     * Swipe right: TalkBack "next element" gesture.
     * Swipes from center-left to center-right of screen.
     */
    suspend fun swipeRight(): Boolean = performSwipe(
        startX = screenWidth * 0.3f,
        startY = screenHeight * 0.5f,
        endX = screenWidth * 0.7f,
        endY = screenHeight * 0.5f
    )

    /**
     * Swipe left: TalkBack "previous element" gesture.
     */
    suspend fun swipeLeft(): Boolean = performSwipe(
        startX = screenWidth * 0.7f,
        startY = screenHeight * 0.5f,
        endX = screenWidth * 0.3f,
        endY = screenHeight * 0.5f
    )

    /**
     * Swipe up: TalkBack "change granularity / previous" gesture.
     */
    suspend fun swipeUp(): Boolean = performSwipe(
        startX = screenWidth * 0.5f,
        startY = screenHeight * 0.7f,
        endX = screenWidth * 0.5f,
        endY = screenHeight * 0.3f
    )

    /**
     * Swipe down: TalkBack "change granularity / next" gesture.
     */
    suspend fun swipeDown(): Boolean = performSwipe(
        startX = screenWidth * 0.5f,
        startY = screenHeight * 0.3f,
        endX = screenWidth * 0.5f,
        endY = screenHeight * 0.7f
    )

    /**
     * Tap at a specific screen coordinate.
     */
    suspend fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        return dispatchGesture(gesture)
    }

    /**
     * Tap at the center of an accessibility node's bounds.
     */
    suspend fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /**
     * Double tap at a specific coordinate (TalkBack "activate" gesture).
     */
    suspend fun doubleTap(x: Float, y: Float): Boolean {
        val firstTap = tap(x, y)
        if (!firstTap) return false
        kotlinx.coroutines.delay(DOUBLE_TAP_DELAY_MS)
        return tap(x, y)
    }

    /**
     * Double tap at the center of screen (activate current TalkBack focus).
     */
    suspend fun doubleTapCenter(): Boolean {
        return doubleTap(screenWidth / 2f, screenHeight / 2f)
    }

    /**
     * Long press at a specific coordinate.
     */
    suspend fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, LONG_PRESS_DURATION_MS))
            .build()

        return dispatchGesture(gesture)
    }

    /**
     * Scroll down gesture (two-finger swipe up for TalkBack).
     */
    suspend fun scrollDown(): Boolean = performSwipe(
        startX = screenWidth * 0.5f,
        startY = screenHeight * 0.7f,
        endX = screenWidth * 0.5f,
        endY = screenHeight * 0.3f,
        durationMs = SCROLL_DURATION_MS
    )

    /**
     * Scroll up gesture (two-finger swipe down for TalkBack).
     */
    suspend fun scrollUp(): Boolean = performSwipe(
        startX = screenWidth * 0.5f,
        startY = screenHeight * 0.3f,
        endX = screenWidth * 0.5f,
        endY = screenHeight * 0.7f,
        durationMs = SCROLL_DURATION_MS
    )

    // -- TalkBack-specific compound gestures --

    /**
     * TalkBack "explore by touch" - move focus to a specific element.
     * Simulates touching the screen at the node's location.
     */
    suspend fun exploreToNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /**
     * Navigate forward through N elements using swipe right.
     */
    suspend fun navigateForward(steps: Int): Boolean {
        for (i in 0 until steps) {
            if (!swipeRight()) return false
            kotlinx.coroutines.delay(NAVIGATION_PAUSE_MS)
        }
        return true
    }

    /**
     * Navigate backward through N elements using swipe left.
     */
    suspend fun navigateBackward(steps: Int): Boolean {
        for (i in 0 until steps) {
            if (!swipeLeft()) return false
            kotlinx.coroutines.delay(NAVIGATION_PAUSE_MS)
        }
        return true
    }

    /**
     * Perform an angular gesture (swipe in a specific direction at a given angle).
     * Useful for custom TalkBack gestures that use diagonal swipes.
     */
    suspend fun angularSwipe(angleDegrees: Float, distance: Float = 0.4f): Boolean {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = (distance * screenWidth * Math.cos(radians)).toFloat()
        val dy = (distance * screenHeight * Math.sin(radians)).toFloat()

        return performSwipe(cx - dx / 2, cy - dy / 2, cx + dx / 2, cy + dy / 2)
    }

    /**
     * Perform a back gesture (swipe from left edge).
     */
    suspend fun back(): Boolean = performSwipe(
        startX = 10f,
        startY = screenHeight * 0.5f,
        endX = screenWidth * 0.4f,
        endY = screenHeight * 0.5f
    )

    // -- Low-level gesture primitives --

    /**
     * Perform a swipe gesture between two points.
     */
    suspend fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = SWIPE_DURATION_MS
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return dispatchGesture(gesture)
    }

    /**
     * Perform a pinch gesture (zoom in/out).
     */
    suspend fun pinch(
        centerX: Float = screenWidth / 2f,
        centerY: Float = screenHeight / 2f,
        startDistance: Float,
        endDistance: Float,
        durationMs: Long = PINCH_DURATION_MS
    ): Boolean {
        val path1 = Path().apply {
            moveTo(centerX - startDistance, centerY)
            lineTo(centerX - endDistance, centerY)
        }
        val path2 = Path().apply {
            moveTo(centerX + startDistance, centerY)
            lineTo(centerX + endDistance, centerY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, durationMs))
            .addStroke(GestureDescription.StrokeDescription(path2, 0, durationMs))
            .build()

        return dispatchGesture(gesture)
    }

    /**
     * Dispatch a raw GestureDescription via the AccessibilityService.
     * Returns true if the gesture was dispatched successfully.
     */
    suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Gesture cancelled")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }

            val dispatched = service.dispatchGesture(gesture, callback, handler)
            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch gesture")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

    companion object {
        private const val TAG = "GestureController"
        private const val TAP_DURATION_MS = 50L
        private const val DOUBLE_TAP_DELAY_MS = 100L
        private const val LONG_PRESS_DURATION_MS = 1000L
        private const val SWIPE_DURATION_MS = 300L
        private const val SCROLL_DURATION_MS = 500L
        private const val PINCH_DURATION_MS = 500L
        private const val NAVIGATION_PAUSE_MS = 500L
    }
}
