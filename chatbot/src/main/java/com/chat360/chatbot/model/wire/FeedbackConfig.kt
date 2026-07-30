package com.chat360.chatbot.model.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ports types/feedbackConfig.ts - the bot-owner-authored, config-driven post-chat survey (rating
 * plus a form whose exact fields depend on which [TriggerRule] the rating matched). Arrives as
 * part of the same appearance json_info blob [BotAppearanceDetails] already decodes.
 */
@Serializable
data class FeedbackConfig(
    val rating: RatingConfig? = null,
    val forms: Map<String, FeedbackFormDefinition> = emptyMap(),
    val trigger_rules: List<TriggerRule> = emptyList(),
    val show_default_form: Boolean = false,
) {
    @Serializable
    data class RatingConfig(val style: String = "star", val scale: Int = 5)

    @Serializable
    data class FeedbackFormDefinition(
        val id: String? = null,
        val title: String? = null,
        val widgetTitle: String? = null,
        val twoColumnLayout: Boolean = false,
        val fields: List<FeedbackField> = emptyList(),
    )

    /**
     * [options]/[rows]/[columns] are heterogeneous on the wire (a bare `string[]` or an
     * `{id,label}[]`) - kept as raw [JsonElement] and normalized at the call site via
     * [normalizeFeedbackOptions]/[normalizeFeedbackMatrixItems], mirroring normalizeOptions()/
     * normalizeMatrixItems() in utils/feedbackConfig.ts rather than two parallel wire shapes here.
     */
    @Serializable
    data class FeedbackField(
        val id: String,
        val type: String,
        val label: String? = null,
        val required: Boolean = false,
        val placeholder: String? = null,
        val subtext: String? = null,
        val options: JsonElement? = null,
        val rows: JsonElement? = null,
        val columns: JsonElement? = null,
        val scaleMax: Int? = null,
    )

    @Serializable
    data class TriggerRule(
        val id: String? = null,
        val priority: Int = 0,
        val condition: TriggerCondition,
        val formId: String,
    )

    @Serializable
    data class TriggerCondition(
        val type: String = "rating",
        val op: String,
        val value: Double? = null,
        val min: Double? = null,
        val max: Double? = null,
    )
}

data class FeedbackOption(val value: String, val label: String)

/** Ports normalizeFieldType(): aliases/underscore-vs-hyphen variants collapse onto one canonical id. */
private val FIELD_TYPE_ALIASES = mapOf(
    "text" to "textbox",
    "input" to "textbox",
    "select" to "dropdown",
    "date-picker" to "date",
    "datepicker" to "date",
    "file-upload" to "file",
    "fileupload" to "file",
    "checkbox-matrix" to "checkbox-matrix",
    "radio-matrix" to "radio-matrix",
    "rank-scale" to "rank",
    "scale" to "rank",
)

fun normalizeFeedbackFieldType(type: String): String {
    val key = type.lowercase().trim().replace('_', '-').replace(Regex("\\s+"), "-")
    return FIELD_TYPE_ALIASES[key] ?: key
}

/** Ports normalizeOptions() for a field's flat choice list (dropdown/radio/checkbox). */
fun normalizeFeedbackOptions(options: JsonElement?): List<FeedbackOption> {
    val array = options as? JsonArray ?: return emptyList()
    return array.mapIndexedNotNull { index, element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.let { FeedbackOption(it, it) }
            is JsonObject -> {
                val id = element["id"]?.jsonPrimitive?.contentOrNull ?: "opt_$index"
                val label = element["label"]?.jsonPrimitive?.contentOrNull ?: id
                FeedbackOption(id, label)
            }
            else -> null
        }
    }
}

/** Ports normalizeMatrixItems() for a matrix field's rows/columns. */
fun normalizeFeedbackMatrixItems(items: JsonElement?): List<FeedbackOption> {
    val array = items as? JsonArray ?: return emptyList()
    return array.mapIndexed { index, element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.let { FeedbackOption("row_$index", it) } ?: FeedbackOption("row_$index", "Item ${index + 1}")
            is JsonObject -> {
                val id = element["id"]?.jsonPrimitive?.contentOrNull ?: "item_$index"
                val label = element["label"]?.jsonPrimitive?.contentOrNull ?: id
                FeedbackOption(id, label)
            }
            else -> FeedbackOption("row_$index", "Item ${index + 1}")
        }
    }
}

/** Ports resolveFormId(): first matching rule by ascending priority, or null (no form) if none match. */
fun resolveFeedbackFormId(rating: Int, rules: List<FeedbackConfig.TriggerRule>): String? {
    return rules.sortedBy { it.priority }.firstOrNull { rule ->
        val condition = rule.condition
        if (condition.type != "rating") return@firstOrNull false
        when (condition.op) {
            "<=" -> condition.value != null && rating <= condition.value
            "=" -> condition.value != null && rating.toDouble() == condition.value
            ">=" -> condition.value != null && rating >= condition.value
            "between" -> condition.min != null && condition.max != null && rating >= condition.min && rating <= condition.max
            else -> false
        }
    }?.formId
}

/** Ports getActiveForm(): 'none' resolves to an explicit empty form even if `forms.none` is absent, so a matched-but-form-less rule renders nothing rather than falling back to the default form. */
fun FeedbackConfig.activeForm(formId: String?): FeedbackConfig.FeedbackFormDefinition? {
    if (formId == null) return null
    if (formId == "none") return forms["none"] ?: FeedbackConfig.FeedbackFormDefinition(id = "none")
    return forms[formId]
}
