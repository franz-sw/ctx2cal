package com.example.ctx2cal.data.llm

import com.example.ctx2cal.domain.extraction.EventJsonParser
import com.example.ctx2cal.domain.model.ExtractionResult
import com.example.ctx2cal.domain.ports.EventExtractor
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloaded open-model backend (LiteRT-LM `.litertlm` on the GPU backend).
 * Inference is blocking/JNI — always dispatched to [Dispatchers.Default].
 */
class LiteRtExtractor(
    private val modelPathProvider: () -> String?,
    override val backendName: String = "litertlm",
) : EventExtractor {

    override suspend fun extract(prompt: String): ExtractionResult = withContext(Dispatchers.Default) {
        val path = modelPathProvider()
            ?: return@withContext ExtractionResult.Failure("No downloaded model found", isNoModel = true)
        val engine = try {
            Engine(EngineConfig(modelPath = path, backend = Backend.GPU())).also { it.initialize() }
        } catch (e: Exception) {
            return@withContext ExtractionResult.Failure("LiteRT failed to load the model: ${e.message}")
        }
        try {
            val session = engine.createSession(SessionConfig())
            try {
                val first = runSession(session, prompt)
                when (val parsed = EventJsonParser.parse(first)) {
                    is EventJsonParser.Parsed.Event -> ExtractionResult.Success(parsed.draft)
                    is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                    is EventJsonParser.Parsed.Invalid -> {
                        val second = runSession(session, EventJsonParser.buildRetryPrompt(first, parsed.reason))
                        when (val retry = EventJsonParser.parse(second)) {
                            is EventJsonParser.Parsed.Event -> ExtractionResult.Success(retry.draft)
                            is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                            is EventJsonParser.Parsed.Invalid -> ExtractionResult.Failure(
                                "Model output was invalid after retry: ${retry.reason}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                ExtractionResult.Failure("LiteRT inference failed: ${e.message}")
            } finally {
                session.close()
            }
        } finally {
            engine.close()
        }
    }

    private fun runSession(session: com.google.ai.edge.litertlm.Session, prompt: String): String =
        session.generateContent(listOf(InputData.Text(prompt)))
}
