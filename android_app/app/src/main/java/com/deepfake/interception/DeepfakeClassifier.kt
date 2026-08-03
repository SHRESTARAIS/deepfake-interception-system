package com.deepfake.interception

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class DeepfakeClassifier(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession: OrtSession

    init {
        // Load deepfake_detector.onnx model from assets folder
        val modelBytes = context.assets.open("deepfake_detector.onnx").readBytes()
        ortSession = ortEnv.createSession(modelBytes)
    }

    /**
     * Run ONNX inference on 8000 float audio samples (1 second of 8kHz audio)
     * Returns: Probability of DEEPFAKE (0.0 to 1.0)
     */
    fun classifyAudioChunk(audioChunk: FloatArray): Float {
        if (audioChunk.size != 8000) return 0.0f

        val shape = longArrayOf(1, 1, 8000)
        val floatBuffer = FloatBuffer.wrap(audioChunk)

        val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, shape)
        val inputs = mapOf("input" to inputTensor)

        val results = ortSession.run(inputs)
        val outputTensor = results[0] as OnnxTensor
        val rawOutputs = (outputTensor.value as Array<FloatArray>)[0] // [real_logit, fake_logit]

        inputTensor.close()
        results.close()

        // Apply Softmax to convert logits to probabilities
        val realLogit = rawOutputs[0]
        val fakeLogit = rawOutputs[1]
        
        val maxLogit = maxOf(realLogit, fakeLogit)
        val expReal = Math.exp((realLogit - maxLogit).toDouble())
        val expFake = Math.exp((fakeLogit - maxLogit).toDouble())
        
        val probFake = (expFake / (expReal + expFake)).toFloat()
        return probFake
    }

    fun close() {
        ortSession.close()
        ortEnv.close()
    }
}
