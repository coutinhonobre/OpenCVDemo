package com.example.opencvdemo

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

internal class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully!")
        }
    }
}
