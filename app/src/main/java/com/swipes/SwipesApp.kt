package com.swipes

import android.app.Application
import com.swipes.capture.CaptureEngine

class SwipesApp : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CaptureEngine.requestFlush()
    }
}
