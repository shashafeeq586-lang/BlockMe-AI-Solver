
package com.blockme.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

enum class GestureResult {
    COMPLETED,
    CANCELLED,
    REJECTED
}

object GameTouchController {

    @Volatile
    private var service: GameAccessibilityService? = null

    internal fun connect(accessibilityService: GameAccessibilityService) {
        service = accessibilityService
    }

    internal fun disconnect(accessibilityService: GameAccessibilityService) {
        if (service === accessibilityService) {
            service = null
        }
    }

    fun isConnected(): Boolean = service != null

    fun tap(x: Float, y: Float): Boolean {
        return service?.performTap(x, y) ?: false
    }

    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 350L
    ): Boolean {
        return service?.performSwipe(
            startX, startY, endX, endY, duration
        ) ?: false
    }

    fun swipeWithCallback(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 650L,
        onResult: (GestureResult) -> Unit
    ): Boolean {
        return service?.performSwipeWithCallback(
            startX,
            startY,
            endX,
            endY,
            duration,
            onResult
        ) ?: run {
            onResult(GestureResult.REJECTED)
            false
        }
    }
}

class GameAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        GameTouchController.connect(this)
        Log.d("BlockMeAI", "Touch service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Game screen events can be handled later.
    }

    override fun onInterrupt() {
        Log.d("BlockMeAI", "Touch service interrupted")
    }

    override fun onDestroy() {
        GameTouchController.disconnect(this)
        super.onDestroy()
    }

    fun performTap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0, 100)
            )
            .build()

        return dispatchGesture(gesture, null, null)
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ): Boolean {
        return performSwipeWithCallback(
            startX,
            startY,
            endX,
            endY,
            duration
        ) { result ->
            Log.d("BlockMeAI", "Swipe result: $result")
        }
    }

    fun performSwipeWithCallback(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long,
        onResult: (GestureResult) -> Unit
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onResult(GestureResult.REJECTED)
            return false
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration.coerceIn(100L, 2000L)
                )
            )
            .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    super.onCompleted(gestureDescription)
                    Log.d("BlockMeAI", "Swipe completed")
                    onResult(GestureResult.COMPLETED)
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    super.onCancelled(gestureDescription)
                    Log.w("BlockMeAI", "Swipe cancelled")
                    onResult(GestureResult.CANCELLED)
                }
            },
            null
        )
    }
}
