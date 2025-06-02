package com.example.ocrtestapp.uis

import android.annotation.SuppressLint
import android.os.Looper
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.layout.fillMaxSize
import android.os.Handler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.camera.core.resolutionselector.*
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.ExperimentalCamera2Interop


@OptIn(ExperimentalGetImage::class)
private fun analyzeImageProxy(
    imageProxy: ImageProxy,
    navController: NavController,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    hasNavigatedBack: MutableState<Boolean>,
    lastResult: MutableState<String>,
    stableCount: MutableState<Int>,
    threshold: Int = 3
) {
    imageProxy.image?.let { mediaImage ->
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val rawText = result.text.trim()

                val pattern = Regex("""\b([A-Z]{3}[A-Z0-9]{4})-([A-Z0-9]{6})-([A-Z0-9])\b""")
                val match = pattern.findAll(rawText).firstOrNull()
                val originalText = match?.value

                if (!originalText.isNullOrBlank()) {
                    // 補正ルール
                    val parts = originalText.split("-")
                    if (parts.size == 3) {
                        val part1 = parts[0]                   // 先頭 7文字
                        val part2 = parts[1]                   // 中間 6文字
                        val part3 = parts[2]                   // 最後 1文字

                        // 先頭3文字：0→O、それ以外そのまま
                        val correctedPart1 = part1.mapIndexed { index, c ->
                            when {
                                index < 3 && c == '0' -> 'O'
                                index >= 3 && c == 'O' -> '0'
                                index >= 3 && c == 'I' -> '1'
                                else -> c
                            }
                        }.joinToString("")

                        // 数字部（O→0, I→1）
                        val correctedPart2 = part2.replace('O', '0').replace('I', '1')
                        val correctedPart3 = part3.replace('O', '0').replace('I', '1')

                        val correctedText = "$correctedPart1-$correctedPart2-$correctedPart3"

                        if (correctedText == lastResult.value) {
                            stableCount.value++
                        } else {
                            lastResult.value = correctedText
                            stableCount.value = 1
                        }

                        Log.d("OCR_RESULT", "[$correctedText] x ${stableCount.value}")

                        if (stableCount.value >= threshold && !hasNavigatedBack.value) {
                            hasNavigatedBack.value = true
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("ocr_result", correctedText)

                            Handler(Looper.getMainLooper()).postDelayed({
                                navController.popBackStack()
                            }, 200)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCRCameraScreen", "OCR failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } ?: imageProxy.close()
}


@Composable
fun OCRCameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val executor = remember { Executors.newSingleThreadExecutor() }

    val hasNavigatedBack = remember { mutableStateOf(false) }
    val lastResult = remember { mutableStateOf("") }
    val stableCount = remember { mutableStateOf(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { contextView ->
                @SuppressLint("UnsafeOptInUsageError")
                @OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
                fun setupCamera(): PreviewView {
                    val previewView = PreviewView(contextView)

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(contextView)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val exposureCompensationValue = 4

                        val previewBuilder = Preview.Builder()
                        Camera2Interop.Extender(previewBuilder)
                            .setCaptureRequestOption(
                                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                                exposureCompensationValue
                            )
                        val preview = previewBuilder.build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analysisBuilder = ImageAnalysis.Builder()
                        Camera2Interop.Extender(analysisBuilder)
                            .setCaptureRequestOption(
                                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                                exposureCompensationValue
                            )

                        val analysis = analysisBuilder
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(1280, 720),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .setAspectRatioStrategy(
                                        AspectRatioStrategy(
                                            AspectRatio.RATIO_16_9,
                                            AspectRatioStrategy.FALLBACK_RULE_AUTO
                                        )
                                    )
                                    .build()
                            )
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(executor) { imageProxy ->
                            analyzeImageProxy(
                                imageProxy,
                                navController,
                                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
                                hasNavigatedBack,
                                lastResult,
                                stableCount
                            )
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)

                    }, ContextCompat.getMainExecutor(contextView))

                    return previewView
                }

                setupCamera()
            },
        )

        Button(
            onClick = {
                if (!hasNavigatedBack.value) {
                    hasNavigatedBack.value = true
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