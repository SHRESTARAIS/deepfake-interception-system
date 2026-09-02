package com.deepfake.interception

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class AudioProcessor(
    private val classifier: DeepfakeClassifier,
    private val onResult: (result: DeepfakeClassifier.ClassificationResult?) -> Unit
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var processingJob: Job? = null

    private val sampleRate = 8000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val chunkSize = 8000 // 1 second at 8kHz

    private val historyBuffer = ArrayList<Float>()
    private val historySize = 2 // 2-second fast sliding window

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("DeepfakeInterceptor", "Background coroutine exception caught safely: ${throwable.message}")
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        historyBuffer.clear()
        isRecording = true
        initializeAudioRecord()

        processingJob = CoroutineScope(Dispatchers.Default + exceptionHandler).launch {
            val shortBuffer = ShortArray(chunkSize)
            val floatChunk = FloatArray(chunkSize)

            while (isActive && isRecording) {
                try {
                    if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        delay(150)
                        initializeAudioRecord()
                        continue
                    }

                    var readSize = 0
                    var errorCount = 0

                    while (readSize < chunkSize && isRecording && isActive) {
                        val read = try {
                            audioRecord?.read(shortBuffer, readSize, chunkSize - readSize) ?: 0
                        } catch (e: Exception) {
                            -1
                        }

                        if (read > 0) {
                            readSize += read
                        } else {
                            errorCount++
                            delay(30)
                            if (errorCount > 6) {
                                try {
                                    audioRecord?.stop()
                                    audioRecord?.release()
                                } catch (e: Exception) {}
                                audioRecord = null
                                break
                            }
                        }
                    }

                    if (readSize == chunkSize) {
                        var sum = 0.0f
                        for (i in 0 until chunkSize) {
                            val sample = shortBuffer[i] / 32768.0f
                            floatChunk[i] = sample
                            sum += sample
                        }

                        // 1. Remove DC Offset
                        val mean = sum / chunkSize
                        var maxAbs = 0.0f

                        for (i in 0 until chunkSize) {
                            val centered = floatChunk[i] - mean
                            floatChunk[i] = centered
                            if (abs(centered) > maxAbs) {
                                maxAbs = abs(centered)
                            }
                        }

                        // 2. Classify ALL audio frames with maxAbs >= 0.0001f (Instant detection on all call audio!)
                        if (maxAbs >= 0.0001f) {
                            // Peak normalization to standard 0.15f amplitude for optimal ONNX classification
                            if (maxAbs > 0.0f) {
                                val scaleFactor = 0.15f / maxAbs
                                for (i in 0 until chunkSize) {
                                    floatChunk[i] *= scaleFactor
                                }
                            }

                            val rawResult = classifier.classifyAudioChunk(floatChunk)
                            val rawFake = rawResult.probFake

                            historyBuffer.add(rawFake)
                            if (historyBuffer.size > historySize) {
                                historyBuffer.removeAt(0)
                            }

                            val avgFakeInWindow = historyBuffer.average().toFloat()

                            val windowResult = DeepfakeClassifier.ClassificationResult(
                                probFake = avgFakeInWindow,
                                realLogit = rawResult.realLogit,
                                fakeLogit = rawResult.fakeLogit
                            )

                            val verdict = if (avgFakeInWindow > 0.50f) "DEEPFAKE" else "REAL"
                            Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] status=$verdict, probFake=$avgFakeInWindow, maxAbs=$maxAbs")
                            Log.d("DeepfakeInterceptor", "Speech Frame -> maxAbs: $maxAbs | RawFake: $rawFake | AvgFake: $avgFakeInWindow")
                            onResult(windowResult)
                        } else {
                            // Complete Digital Zero Silence
                            onResult(null)
                        }
                    }
                } catch (e: Throwable) {
                    delay(150)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, chunkSize * 2)

        val audioSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intArrayOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )
        } else {
            intArrayOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )
        }

        for (source in audioSources) {
            try {
                val record = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord = record
                        isRecording = true
                        Log.i("DeepfakeInterceptor", "AudioRecord initialized successfully at 8kHz with source: $source")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("DeepfakeInterceptor", "Failed audio source $source: ${e.message}")
            }
        }
    }

    fun stopListening() {
        isRecording = false
        processingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
        historyBuffer.clear()
    }
}
