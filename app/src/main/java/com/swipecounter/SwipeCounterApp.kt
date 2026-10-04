package com.swipecounter

import android.app.Application
import com.swipecounter.capture.CaptureEngine

class SwipeCounterApp : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CaptureEngine.requestFlush()
    }
}
