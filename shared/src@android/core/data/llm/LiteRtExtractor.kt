package de.franzsw.ctx2cal.core.data.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.RawPromptResult
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Downloaded open-model backend (LiteRT-LM `.litertlm`, GPU with cached CPU fallback).
 * Uses chat-templated [Conversation] with thinking disabled, bounded token/context limits,
 * finite per-attempt timeout, and native [Conversation.cancelProcess] on timeout/cancel.
 */
class LiteRtExtractor(
    private val modelPathProvider: () -> String?,
    private val settings: SettingsRepository? = null,
    private val skipGpuHint: () -> Boolean = { false },
    private val attemptTimeoutMs: Long = DEFAULT_ATTEMPT_TIMEOUT_MS,
    override val backendName: String = "litertlm",
) : EventExtractor {

    override suspend fun extract(prompt: String): ExtractionResult = withContext(Dispatchers.IO) {
        val path = modelPathProvider()
            ?: return@withContext ExtractionResult.Failure(
                "No downloaded model found. Open Setup to download one.",
                isNoModel = true,
            )

        val modelFile = File(path)
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            return@withContext ExtractionResult.Failure(
                "Downloaded model file is missing. Open Setup to re-download it.",
                isNoModel = true,
            )
        }

        val engine = try {
            createEngineWithFallback(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            return@withContext ExtractionResult.Failure(
                if (detail != null) "Could not load the on-device model ($detail). Try re-downloading it in Setup."
                else "Could not load the on-device model. Try re-downloading it in Setup."
            )
        }

        try {
            coroutineContext.ensureActive()
            val parts = EventJsonParser.splitForConversation(prompt, settings?.customSystemPrompt())
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(parts.systemInstruction),
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 0.95,
                        temperature = 0.0,
                        seed = 0,
                    ),
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                )
            )
            try {
                val first = runConversationTurn(conversation, parts.userPrompt)
                when (val parsed = EventJsonParser.parse(first)) {
                    is EventJsonParser.Parsed.Event -> ExtractionResult.Success(parsed.draft)
                    is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                    is EventJsonParser.Parsed.Invalid -> {
                        val retryPrompt = EventJsonParser.buildRetryPrompt(
                            previousRaw = first,
                            reason = parsed.reason,
                            originalPrompt = null,
                        )
                        val second = runConversationTurn(conversation, retryPrompt)
                        when (val retry = EventJsonParser.parse(second)) {
                            is EventJsonParser.Parsed.Event -> ExtractionResult.Success(retry.draft)
                            is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                            is EventJsonParser.Parsed.Invalid -> ExtractionResult.Failure(
                                reason = "Couldn't extract a valid event from this text. Try simplifying the wording or adding a clearer date/time.",
                                rawOutput = second,
                            )
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                ExtractionResult.Failure(
                    "On-device extraction timed out after ${attemptTimeoutMs / 1000}s. Try shorter text or a smaller model."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() }
                ExtractionResult.Failure(
                    if (detail != null) "On-device inference failed ($detail). Please retry."
                    else "On-device inference failed. Please retry."
                )
            } finally {
                runCatching { conversation.close() }
            }
        } finally {
            runCatching { engine.close() }
        }
    }

    suspend fun generateRaw(
        modelPath: String,
        systemPrompt: String,
        userPrompt: String,
    ): RawPromptResult = withContext(Dispatchers.IO) {
        val modelFile = File(modelPath)
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            return@withContext RawPromptResult.Failure(
                "Downloaded model file is missing. Open Setup to re-download it."
            )
        }

        val engine = try {
            createEngineWithFallback(modelPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            return@withContext RawPromptResult.Failure(
                if (detail != null) "Could not load the on-device model ($detail). Try re-downloading it in Setup."
                else "Could not load the on-device model. Try re-downloading it in Setup."
            )
        }

        try {
            coroutineContext.ensureActive()
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = if (systemPrompt.isNotBlank()) Contents.of(systemPrompt) else null,
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 0.95,
                        temperature = 0.0,
                        seed = 0,
                    ),
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                )
            )
            try {
                val startMs = System.currentTimeMillis()
                val output = runConversationTurn(conversation, userPrompt)
                RawPromptResult.Success(
                    output = output,
                    durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                )
            } catch (_: TimeoutCancellationException) {
                RawPromptResult.Failure(
                    "On-device inference timed out after ${attemptTimeoutMs / 1000}s. Try shorter text or a smaller model."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() }
                RawPromptResult.Failure(
                    if (detail != null) "On-device inference failed ($detail). Please retry."
                    else "On-device inference failed. Please retry."
                )
            } finally {
                runCatching { conversation.close() }
            }
        } finally {
            runCatching { engine.close() }
        }
    }

    private suspend fun runConversationTurn(conversation: Conversation, userMessage: String): String {
        val sb = StringBuilder()
        try {
            withTimeout(attemptTimeoutMs) {
                conversation.sendMessageAsync(userMessage).collect { message ->
                    for (content in message.contents.contents) {
                        if (content is Content.Text) {
                            sb.append(content.text)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            runCatching { conversation.cancelProcess() }
            throw e
        } catch (e: CancellationException) {
            runCatching { conversation.cancelProcess() }
            throw e
        }
        return sb.toString()
    }

    /**
     * Prefers GPU backend unless cached [SettingsRepository.isGpuUnsupported] or hardware probe
     * marks GPU unsupported; records GPU init failure and falls back to 4-thread CPU execution.
     */
    private fun createEngineWithFallback(path: String): Engine {
        val skipGpu = settings?.isGpuUnsupported() == true || skipGpuHint()
        if (!skipGpu) {
            try {
                return Engine(
                    EngineConfig(
                        modelPath = path,
                        backend = Backend.GPU(),
                        maxNumTokens = MAX_NUM_TOKENS,
                    )
                ).also { it.initialize() }
            } catch (gpuError: Exception) {
                if (gpuError is CancellationException) throw gpuError
                settings?.setGpuUnsupported(true)
            }
        }
        return Engine(
            EngineConfig(
                modelPath = path,
                backend = Backend.CPU(threadCount = CPU_THREAD_COUNT),
                maxNumTokens = MAX_NUM_TOKENS,
            )
        ).also { it.initialize() }
    }

    companion object {
        const val MAX_NUM_TOKENS = 4096
        const val MAX_OUTPUT_TOKENS = 1024
        const val CPU_THREAD_COUNT = 4
        const val DEFAULT_ATTEMPT_TIMEOUT_MS = 60_000L
    }
}
