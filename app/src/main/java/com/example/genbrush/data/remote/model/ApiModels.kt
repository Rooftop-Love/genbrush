package com.example.genbrush.data.remote.model

// --- Sync request (qwen-image models) ---
data class GenerationRequest(
    val model: String,
    val input: Input,
    val parameters: Parameters
)

data class Input(
    val messages: List<Message>? = null,
    val prompt: String? = null
)

data class Message(
    val role: String,
    val content: List<ContentItem>
)

data class ContentItem(
    val text: String? = null,
    val image: String? = null
)

data class Parameters(
    val size: String,
    val n: Int = 1,
    val negative_prompt: String? = null,
    val prompt_extend: Boolean = true,
    val prompt_extend_mode: String? = null,
    val watermark: Boolean = false
)

// --- Sync response (qwen-image models) ---
data class GenerationResponse(
    val output: Output?,
    val request_id: String?,
    val code: String?,
    val message: String?,
    val usage: GenerationUsage?
)

data class Output(
    val choices: List<Choice>?,
    val task_id: String?,
    val task_status: String?,
    val results: List<TaskResult>?
)

data class Choice(
    val finish_reason: String?,
    val message: ResponseMessage?
)

data class ResponseMessage(
    val role: String?,
    val content: List<ContentItem>?
)

data class GenerationUsage(
    val output_width: Int?,
    val output_height: Int?,
    val input_image_count: Int?,
    val input_image_type: String?,
    val output_image_count: Int?,
    val output_image_type: String?,
    // qwen-image-2.0 uses these names in some response variants.
    val width: Int?,
    val height: Int?,
    val image_count: Int?
)

// --- Async task (wan models) ---
data class TaskSubmitRequest(
    val model: String,
    val input: TaskInput,
    val parameters: TaskParameters
)

data class TaskInput(
    val messages: List<Message>
)

data class TaskParameters(
    val size: String,
    val n: Int = 1,
    val seed: Int? = null,
    val watermark: Boolean = false
)

data class TaskSubmitResponse(
    val request_id: String?,
    val output: Output?,
    val code: String?,
    val message: String?
)

data class TaskStatusResponse(
    val request_id: String?,
    val output: Output?,
    val code: String?,
    val message: String?
)

data class TaskResult(
    val url: String?,
    val orig_url: String?,
    val b64_image: String?
)

fun isWanModel(model: String): Boolean = model.startsWith("wan")

fun isQwenImage3Model(model: String): Boolean =
    model == "qwen-image-3.0-pro" || model == "qwen-image-3.0"
