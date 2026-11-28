package de.franzsw.ctx2cal.core.domain.llm

/** Global timeout configuration for on-device LLM inference (Gemini Nano & LiteRT). */
object LlmTimeouts {
    /** Per-attempt inference timeout for Gemini Nano and LiteRT models (300 seconds). */
    const val MODEL_INFERENCE_TIMEOUT_MS = 300_000L

    /** Outer safety timeout for extraction (allows up to two attempts / fallback). */
    const val EXTRACTION_TIMEOUT_MS = MODEL_INFERENCE_TIMEOUT_MS * 2 + 10_000L
}
