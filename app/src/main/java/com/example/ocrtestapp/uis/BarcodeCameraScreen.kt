package com.example.ocrtestapp.uis

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun BarcodeCameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val executor = remember { Executors.newSingleThreadExecutor() }
    val hasNavgatedBack = remember { mutableStateOf(false) }
    val lastResult = remember { mutableStateOf("")}
    val stableCount = remember { mutableStateOf(0) }
    val threhold = 3

    Box(modifier = Modifier.fillMaxSize()){
        AndroidView(
            factory = {
                val previewView = PreviewView(it)

                val cameraProviderFuture = ProcessCameraProvider.getInstance(it)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val scanner = BarcodeScanning.getClient()

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor, { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let { code ->
                                        Log.d("BARCODE_SCAN", "[$code]")

                                        if (code == lastResult.value) {
                                            stableCount.value++
                                        } else {
                                            lastResult.value = code
                                            stableCount.value = 1
                                        }

                                        Log.d("BARCODE_STABILITY", "Match count: ${stableCount.value}")

                                        if (stableCount.value >= threhold && !hasNavgatedBack.value){
                                            hasNavgatedBack.value = true
                                            navController.previousBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("barcode_result", code)

                                            Handler(Looper.getMainLooper()).postDelayed({
                                                navController.popBackStack()
                                            }, 200)
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("BarcodeCameraScreen", "Barcode scan failed", e)
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    })

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
                    } catch (e: Exception) {
                        Log.e("BarcodeCameraScreen", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(it))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                if (!hasNavgatedBack.value) {
                    hasNavgatedBack.value = true
                    navController.popBackStack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("← 戻る")
        }
    }

}
