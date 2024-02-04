package com.example.opencvdemo

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.HandlerCompat
import com.example.opencvdemo.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
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
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private lateinit var imageCapture: ImageCapture

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var outputDirectory: File

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV!");
        } else {
            Log.d(TAG, "OpenCV loaded successfully!");
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        outputDirectory = getOutputDirectory()

        // Verifica se todas as permissões necessárias foram concedidas
        if (allPermissionsGranted()) {
            startCamera() // Inicia a configuração da câmera se as permissões foram concedidas
            binding.take.setOnClickListener {
                takePhoto()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Usado para vincular o ciclo de vida dos casos de uso da câmera ao ciclo de vida do aplicativo
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Initialize o Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Initialize o ImageCapture
            imageCapture = ImageCapture.Builder().build()

            try {
                // Desvincula todos os casos de uso antes de rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Falha ao vincular casos de uso", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Criando o arquivo de saída da imagem
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg")

        // Criando um objeto de metadados de saída de imagem
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Configurando o callback de captura de imagem
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Foto capturada com sucesso: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()

                    // Carregar a imagem como um Mat no OpenCV
                    val imagePath = photoFile.absolutePath
                    val imageMat = Imgcodecs.imread(imagePath)

                    // Verificar se a imagem foi carregada corretamente
                    if (imageMat.empty()) {
                        Log.e(TAG, "Falha ao carregar a imagem com o OpenCV")
                        return
                    }

                    // Aqui você pode processar imageMat com o OpenCV como necessário
                    processImageWithOpenCV(imageMat)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Erro ao capturar imagem", exc)
                    Toast.makeText(baseContext, "Erro ao capturar imagem.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun processImageWithOpenCV(imageMat: Mat) {
        // Exemplo de processamento: conversão para escala de cinza
        //Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY)
        detectNumberPlate(imageMat)
        // Adicione aqui mais processamentos conforme necessário
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissões não concedidas pelo usuário.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun processImageProxy(image: ImageProxy) {
        try {
            detectNumberPlate(image)
        } catch (e: Exception) {
            Log.e("ERROR", "DETECT PLATE ERROR")
            image.close()
        } finally {
            image.close()
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

    private fun detectNumberPlate(imageMat: Mat) {
        val classifierPath = copyFileFromAssets("haarcascade_russian_plate_number.xml", "haarcascade_russian_plate_number.xml")

        // Converter ImageProxy para Mat
//        val imageMat = ImageProxyToMat(imageProxy)
//
        // Calcular o fator de escala para manter a proporção
//        val scaleFactor = Math.min(800.0 / imageMat.width(), 400.0 / imageMat.height())
        val scaleFactor = 1.05
        val newSize = Size(imageMat.width() * scaleFactor, imageMat.height() * scaleFactor)

        // Redimensionar a imagem mantendo a proporção
        val resizedImage = Mat()
        Imgproc.resize(imageMat, resizedImage, newSize)

        // Converter para escala de cinza
        val grayImage = Mat()
        Imgproc.cvtColor(resizedImage, grayImage, Imgproc.COLOR_BGR2GRAY)

        val nPlateDetector = CascadeClassifier(classifierPath)

        val detections = MatOfRect()
        nPlateDetector.detectMultiScale(grayImage, detections, scaleFactor, 7)

        if (detections.toArray().isNotEmpty()) {
            // Uma ou mais placas foram detectadas
            for (rect in detections.toArray()) {
                Imgproc.rectangle(resizedImage, rect, Scalar(0.0, 255.0, 255.0), 2)
                Imgproc.putText(resizedImage, "Number plate detected", Point(rect.x.toDouble() - 20, rect.y.toDouble() - 10),
                    Imgproc.FONT_HERSHEY_COMPLEX, 0.5, Scalar(0.0, 255.0, 255.0), 2)
            }

            // Converter Mat para Bitmap
            val bitmap = Bitmap.createBitmap(resizedImage.cols(), resizedImage.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resizedImage, bitmap)

            // Atualizar a UI no thread principal
            runOnUiThread {
                val imageView = findViewById<ImageView>(R.id.imageView)
                imageView.setImageBitmap(bitmap)
                binding.previewView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
            }
        } else {
            // Nenhuma placa detectada
            // Aqui você pode optar por mostrar uma mensagem ao usuário ou realizar outra ação conforme necessário
            runOnUiThread {
                // Exemplo: Limpar a ImageView ou mostrar uma mensagem
                val imageView = findViewById<ImageView>(R.id.imageView)
                imageView.setImageBitmap(null) // Ou imageView.setImageResource(R.drawable.imagem_padrao)
                imageView.visibility = View.GONE
            }
        }


        // Não esqueça de liberar o ImageProxy
//        imageProxy.close()
    }


    private fun detectNumberPlate(imageProxy: ImageProxy) {
        val classifierPath = copyFileFromAssets("haarcascade_russian_plate_number.xml", "haarcascade_russian_plate_number.xml")

        // Converter ImageProxy para Mat
        val imageMat = ImageProxyToMat(imageProxy)

        // Calcular o fator de escala para manter a proporção
//        val scaleFactor = Math.min(800.0 / imageMat.width(), 400.0 / imageMat.height())
        val scaleFactor = 1.05
        val newSize = Size(imageMat.width() * scaleFactor, imageMat.height() * scaleFactor)

        // Redimensionar a imagem mantendo a proporção
        val resizedImage = Mat()
        Imgproc.resize(imageMat, resizedImage, newSize)

        // Converter para escala de cinza
        val grayImage = Mat()
        Imgproc.cvtColor(resizedImage, grayImage, Imgproc.COLOR_BGR2GRAY)

        val nPlateDetector = CascadeClassifier(classifierPath)

        val detections = MatOfRect()
        nPlateDetector.detectMultiScale(grayImage, detections, scaleFactor, 7)

        if (detections.toArray().isNotEmpty()) {
            // Uma ou mais placas foram detectadas
            for (rect in detections.toArray()) {
                Imgproc.rectangle(resizedImage, rect, Scalar(0.0, 255.0, 255.0), 2)
                Imgproc.putText(resizedImage, "Number plate detected", Point(rect.x.toDouble() - 20, rect.y.toDouble() - 10),
                    Imgproc.FONT_HERSHEY_COMPLEX, 0.5, Scalar(0.0, 255.0, 255.0), 2)
            }

            // Converter Mat para Bitmap
            val bitmap = Bitmap.createBitmap(resizedImage.cols(), resizedImage.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resizedImage, bitmap)

            // Atualizar a UI no thread principal
            runOnUiThread {
                val imageView = findViewById<ImageView>(R.id.imageView)
                imageView.setImageBitmap(bitmap)
                binding.previewView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
            }
        } else {
            // Nenhuma placa detectada
            // Aqui você pode optar por mostrar uma mensagem ao usuário ou realizar outra ação conforme necessário
            runOnUiThread {
                // Exemplo: Limpar a ImageView ou mostrar uma mensagem
                val imageView = findViewById<ImageView>(R.id.imageView)
                imageView.setImageBitmap(null) // Ou imageView.setImageResource(R.drawable.imagem_padrao)
                imageView.visibility = View.GONE
            }
        }


        // Não esqueça de liberar o ImageProxy
        imageProxy.close()
    }

    private fun ImageProxyToMat(image: ImageProxy): Mat {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        val rgbMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2BGR_NV21)
        yuvMat.release()

        return rgbMat
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

    private companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PERMISSION_REQUEST_CODE = 10
    }
}