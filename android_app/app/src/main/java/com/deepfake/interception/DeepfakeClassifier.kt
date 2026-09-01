package com.deepfake.interception

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DeepfakeClassifier(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession: OrtSession

    data class ClassificationResult(
        val probFake: Float,
        val realLogit: Float,
        val fakeLogit: Float
    )

    init {
        val modelBytes = context.assets.open("deepfake_detector.onnx").readBytes()
        ortSession = ortEnv.createSession(modelBytes)
    }

    /**
     * Run ONNX inference on 8000 float audio samples (1 second of 8kHz audio)
     */
    fun classifyAudioChunk(audioChunk: FloatArray): ClassificationResult {
        if (audioChunk.size != 8000) return ClassificationResult(0.0f, 0.0f, 0.0f)

        val shape = longArrayOf(1, 1, 8000)
        
        val directBuffer = ByteBuffer.allocateDirect(8000 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        directBuffer.put(audioChunk)
        directBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(ortEnv, directBuffer, shape)
        val inputs = mapOf("input" to inputTensor)

        val results = ortSession.run(inputs)
        val outputTensor = results[0] as OnnxTensor
        
        val fb = outputTensor.floatBuffer
        val raw0 = fb.get(0)
        val raw1 = fb.get(1)

        inputTensor.close()
        results.close()
        
        Log.d("DeepfakeClassifier", "Raw ONNX Logits -> [0]=$raw0 | [1]=$raw1")

        // Exact ONNX logit mapping: raw0 = REAL HUMAN VOICE, raw1 = AI FAKE VOICE
        val realLogit = raw0
        val fakeLogit = raw1

        val maxLogit = maxOf(realLogit, fakeLogit)
        val expReal = Math.exp((realLogit - maxLogit).toDouble())
        val expFake = Math.exp((fakeLogit - maxLogit).toDouble())
        
        val probFake = (expFake / (expReal + expFake)).toFloat()
        return ClassificationResult(probFake, realLogit, fakeLogit)
    }

    fun close() {
        ortSession.close()
        ortEnv.close()
    }
}
