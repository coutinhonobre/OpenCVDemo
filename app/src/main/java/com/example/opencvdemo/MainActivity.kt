package com.example.opencvdemo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (OpenCVLoader.initDebug().not()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully!")
//            loadImageAndProcess()
            detectNumberPlate()
        }
    }

    private fun loadImageAndProcess() {
        try {
            // Ler a imagem dos assets
            val inputStream = assets.open("carBR.jpg")
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Converter Bitmap para Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Processar a imagem (converter para escala de cinza)
            val matGray = Mat()
            Imgproc.cvtColor(mat, matGray, Imgproc.COLOR_BGR2GRAY)

            // Converter Mat de volta para Bitmap
            val bitmapGray = Bitmap.createBitmap(matGray.cols(), matGray.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(matGray, bitmapGray)

            // Exibir o Bitmap no ImageView
            findViewById<ImageView>(R.id.imageView).setImageBitmap(bitmapGray)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun detectNumberPlate() {
        val imageView = findViewById<ImageView>(R.id.imageView)

        // Carregar imagem e classificador Haar Cascade dos assets
        val imagePath = copyFileFromAssets("argo.jpg", "argo.jpg")
        val classifierPath = copyFileFromAssets("haarcascade_russian_plate_number.xml", "haarcascade_russian_plate_number.xml")

        val image = Imgcodecs.imread(imagePath)
        val resizedImage = Mat()
        Imgproc.resize(image, resizedImage, Size(800.0, 400.0))
        val grayImage = Mat()
        Imgproc.cvtColor(resizedImage, grayImage, Imgproc.COLOR_BGR2GRAY)

        val nPlateDetector = CascadeClassifier(classifierPath)
        val detections = MatOfRect()
        nPlateDetector.detectMultiScale(grayImage, detections, 1.05, 7)

        for (rect in detections.toArray()) {
            Imgproc.rectangle(resizedImage, rect, Scalar(0.0, 255.0, 255.0), 2)
            Imgproc.putText(resizedImage, "Number plate detected", Point(rect.x.toDouble() - 20, rect.y.toDouble() - 10),
                Imgproc.FONT_HERSHEY_COMPLEX, 0.5, Scalar(0.0, 255.0, 255.0), 2)
        }

        val bitmap = Bitmap.createBitmap(resizedImage.cols(), resizedImage.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resizedImage, bitmap)
        imageView.setImageBitmap(bitmap)
    }

    private fun copyFileFromAssets(assetFileName: String, outputFileName: String): String {
        val assetManager = this@MainActivity.assets
        val inputStream = assetManager.open(assetFileName)
        val outputFile = File(getExternalFilesDir(null), outputFileName)
        val outputStream = FileOutputStream(outputFile)

        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        return outputFile.absolutePath
    }
}