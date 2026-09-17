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
    private val historySize = 3 // 3-frame median window for instant responsiveness

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
                        delay(100)
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
                            delay(20)
                            if (errorCount > 5) {
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

                        // 2. Classify audio frames when speech audio is present
                        if (maxAbs > 0.0001f) {
                            // Peak normalization to standard 0.15f amplitude
                            val scaleFactor = if (maxAbs > 0.0f) 0.15f / maxAbs else 1.0f
                            for (i in 0 until chunkSize) {
                                floatChunk[i] *= scaleFactor
                            }

                            val rawResult = classifier.classifyAudioChunk(floatChunk)
                            val rawFake = rawResult.probFake

                            historyBuffer.add(rawFake)
                            if (historyBuffer.size > historySize) {
                                historyBuffer.removeAt(0)
                            }

                            // Calculate 3-frame Median value for noise-free stability
                            val sortedList = historyBuffer.sorted()
                            val medianFake = sortedList[sortedList.size / 2]

                            val windowResult = DeepfakeClassifier.ClassificationResult(
                                probFake = medianFake,
                                realLogit = rawResult.realLogit,
                                fakeLogit = rawResult.fakeLogit
                            )

                            val verdict = if (medianFake > 0.50f) "DEEPFAKE" else "REAL"
                            Log.i("DeepfakeInterceptor", "[USB_CABLE_STREAM] status=$verdict, probFake=$medianFake, maxAbs=$maxAbs")
                            Log.d("DeepfakeInterceptor", "Speech Frame -> maxAbs: $maxAbs | Raw: $rawFake | Median: $medianFake")
                            onResult(windowResult)
                        } else {
                            // Silence Frame
                            Log.d("DeepfakeInterceptor", "Silence Frame -> maxAbs: $maxAbs")
                        }
                    }
                } catch (e: Throwable) {
                    delay(100)
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

        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.CAMCORDER
        )

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
