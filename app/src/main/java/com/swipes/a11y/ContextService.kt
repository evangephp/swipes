package com.swipes.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.swipes.capture.Bounds
import com.swipes.capture.CaptureEngine
import com.swipes.capture.ImeState
import com.swipes.capture.WindowSnap

/**
 * Feeds screen context (foreground app, keyboard bounds) to [CaptureEngine] and keeps the app
 * process alive while enabled. Never reads text.
 */
class ContextService : AccessibilityService() {

    override fun onServiceConnected() {
        CaptureEngine.start(this)
        val now = SystemClock.uptimeMillis()
        CaptureEngine.screen.ime.set(now, currentIme())
        CaptureEngine.screen.windows.set(now, snapshot())
        CaptureEngine.screen.foregroundPackage.set(now, rootInActiveWindow?.packageName?.toString())
        CaptureEngine.setAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val time = event.eventTime.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                CaptureEngine.screen.ime.set(time, currentIme())
                CaptureEngine.screen.windows.set(time, snapshot())
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Fallback attribution only: System UI pop-ups (heads-up, volume) and the keyboard
                // aren't the app being used.
                val window = windows.firstOrNull { it.id == event.windowId }
                if (window?.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    CaptureEngine.screen.foregroundPackage.set(time, event.packageName?.toString())
                }
            }
        }
    }

    private fun snapshot(): List<WindowSnap> = windows.map { w ->
        val r = Rect().also(w::getBoundsInScreen)
        WindowSnap(w.layer, Bounds(r.left, r.top, r.right, r.bottom), w.type, w.root?.packageName?.toString())
    }

    private fun currentIme(): ImeState {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return ImeState.Hidden
        val r = Rect().also(ime::getBoundsInScreen)
        return ImeState.Visible(Bounds(r.left, r.top, r.right, r.bottom))
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        CaptureEngine.screen.ime.set(SystemClock.uptimeMillis(), ImeState.Unknown)
        CaptureEngine.setAccessibilityConnected(false)
        super.onDestroy()
    }
}
