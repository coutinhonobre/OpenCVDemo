package com.example.opencvdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.objects.DetectedObject

class BoundingBoxView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val detectedObjects = mutableListOf<DetectedObject>()

    fun setDetectedObjects(objects: List<DetectedObject>) {
        detectedObjects.clear()
        detectedObjects.addAll(objects)
        invalidate() // Chama o método onDraw para redesenhar a view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (obj in detectedObjects) {
            canvas.drawRect(obj.boundingBox, paint)
        }
    }
}