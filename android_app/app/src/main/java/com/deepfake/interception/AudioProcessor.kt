package com.deepfake.interception

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioProcessor(
    private val classifier: DeepfakeClassifier,
    private val onResult: (probFake: Float) -> Unit
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var processingJob: Job? = null

    private val sampleRate = 8000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val chunkSize = 8000 // 1 second at 8kHz

    @SuppressLint("MissingPermission")
    fun startListening() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, chunkSize * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        processingJob = CoroutineScope(Dispatchers.Default).launch {
            val shortBuffer = ShortArray(chunkSize)
            val floatChunk = FloatArray(chunkSize)

            while (isActive && isRecording) {
                var readSize = 0
                while (readSize < chunkSize && isRecording) {
                    val read = audioRecord?.read(shortBuffer, readSize, chunkSize - readSize) ?: 0
                    if (read > 0) readSize += read
                }

                if (readSize == chunkSize) {
                    for (i in 0 until chunkSize) {
                        floatChunk[i] = shortBuffer[i] / 32768.0f
                    }

                    val probFake = classifier.classifyAudioChunk(floatChunk)
                    onResult(probFake)
                }
            }
        }
    }

    fun stopListening() {
        isRecording = false
        processingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
