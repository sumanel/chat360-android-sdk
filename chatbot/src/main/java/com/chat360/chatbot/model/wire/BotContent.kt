package com.chat360.chatbot.model.wire

/** A bot flow node received over the socket, with just enough context to answer it. */
data class BotNode(
    val nodeId: String?,
    val nodeType: String?,
    val targetId: String?,
    val variable: String?,
    val text: String?,
    val content: BotContent,
    /** END-node side effects (open externally / close the session) - not about rendering at all. */
    val endUrlMessage: String? = null,
    val endSessionRequested: Boolean = false,
    /** Set only for a chatgpt_message streaming chunk - chunks sharing an id get concatenated onto one bubble. */
    val streamId: String? = null,
    val streamEnded: Boolean = true,
    /** BOT for a `user:'bot'` frame, AGENT for `user:'admin'|'operator'` - same content shapes either way. */
    val author: MessageAuthor = MessageAuthor.BOT,
    /** The frame's real server send time (epoch ms), from [RawSocketEnvelope.time] - null only
     * when that field was absent/unparseable, in which case the UI falls back to "now". */
    val timestampMs: Long? = null,
) {
    enum class MessageAuthor { BOT, AGENT }
}

/**
 * Renderable content variants. Node types without a dedicated variant fall through to
 * [Unsupported] so the UI's `when` stays exhaustive and new variants can be added without
 * breaking existing code.
 */
sealed interface BotContent {
    data object PlainText : BotContent

    data class MultiChoice(
        val variant: String?,
        val options: List<Option>,
    ) : BotContent {
        data class Option(val index: Int, val text: String, val targetId: String?)
    }

    enum class MediaKind { IMAGE, VIDEO, AUDIO, OTHER }

    data class Media(
        val url: String,
        val kind: MediaKind,
        val title: String?,
        val downloadDisabled: Boolean,
        /** Quick-reply pills rendered below the media bubble, each answering with the same
         * `carousel-text-reply` wire shape as a TextCarousel card tap - reuses that variant's
         * [TextCarousel.DynamicButton] shape rather than a second copy of the same three fields. */
        val dynamicButtons: List<TextCarousel.DynamicButton> = emptyList(),
    ) : BotContent

    data class Carousel(val cards: List<Card>) : BotContent {
        data class Card(val imageUrl: String, val heading: String?, val caption: String?, val link: String?)
    }

    data class FileUploadPrompt(val promptText: String?, val allowedExtensions: List<String>, val allowCamera: Boolean = false) : BotContent

    data class DownloadMedia(val fileUrl: String, val fileName: String) : BotContent

    /** style: "star" | "emoji"; scale is the star count. */
    data class Rating(val style: String, val scale: Int) : BotContent

    data class Form(
        val fields: List<Field>,
        val submitButtonText: String,
    ) : BotContent {
        enum class FieldType { TEXT, NUMBER, EMAIL, PHONE, SELECT, DATE, MEDIA, OTHER }

        data class Field(
            val index: Int,
            val type: FieldType,
            val label: String?,
            val placeholder: String?,
            val isRequired: Boolean,
            val options: List<String>,
            val variable: String?,
            val validation: FieldValidation? = null,
        )

        /**
         * A form element's `validation` object - the exact rule set
         * `domain/validation/FormFieldValidator` enforces. All-nullable/false defaults so a
         * field with no `validation` key at all simply has no constraints.
         */
        data class FieldValidation(
            val isRequired: Boolean = false,
            val errorMessage: String? = null,
            val userInputType: String? = null,
            val maxCharacters: Int? = null,
            val minCharacters: Int? = null,
            val email: Boolean = false,
            val maxCount: Double? = null,
            val minCount: Double? = null,
            val phone: Boolean = false,
            val allowInternationalNumber: Boolean = false,
            val numberFormat: String? = null,
            val dateRules: DateRules? = null,
        )
    }

    /**
     * A WINDOW_EVENT node - renders nothing visually, it only triggers the host-app bridge.
     * See domain/windowevent/WindowEventBridge.kt.
     */
    data class WindowEvent(
        val shouldSend: Boolean,
        val sendData: Map<String, String>,
        val shouldReceive: Boolean,
    ) : BotContent

    /** An EMAIL node - always required, validated with InputValidators.validateEmail/validateTest. */
    data object EmailPrompt : BotContent

    /**
     * A PHONE node. [allowInternational] false (the common case) means no dedicated input or
     * validation at all - answered through the plain free-text input.
     * [splitVariable]/[countryCodeVar] only matter when international.
     */
    data class PhonePrompt(
        val allowInternational: Boolean,
        val splitVariable: Boolean,
        val countryCodeVar: String?,
    ) : BotContent

    /** The AUTOSUGGESTION sub-variant of CUSTOMINPUT: pick one choice from a searchable list. */
    data class AutoSuggestion(val choices: List<String>, val description: String?) : BotContent

    /** A standalone DATE node - full rule engine, see [DateRules]. */
    data class DatePrompt(val rules: DateRules) : BotContent

    /** A standalone TIME node. disabledSlots maps hour (1-12) -> the disabled minutes within it. */
    data class TimePrompt(val disabledSlots: Map<Int, List<Int>>, val disablePrevious: Boolean) : BotContent

    /** A MULTIPLE_CHECK_BOX node - unlike MultiChoice, more than one option can be selected. */
    data class MultiOption(val description: String?, val options: List<Option>) : BotContent {
        data class Option(val index: Int, val text: String)
    }

    /**
     * An IMAGE_BUTTON node - visually a carousel (reuses the same card shape as [Carousel]) but
     * each card carries its own reply/web_url buttons instead of a single tap-through link.
     */
    data class ImageButtons(val cards: List<Card>, val submitType: String) : BotContent {
        data class Card(val imageUrl: String, val description: String?, val buttons: List<Button>)
        data class Button(val text: String, val type: String, val value: String?, val url: String?, val targetId: String?)
    }

    /**
     * A TEXT_CAROUSEL node. Both wire generations (`type1`/`type2`) map onto one card shape
     * rather than two distinct layouts - a deliberate simplification; per-button custom styling
     * (`ctaButtons[].style`) is not modeled, cards use the theme's own button styling instead.
     */
    data class TextCarousel(val cards: List<Card>, val dynamicButtons: List<DynamicButton>) : BotContent {
        data class Card(
            val name: String?,
            val content: String?,
            val bgColor: String?,
            val bgImage: String?,
            val footerText: String?,
            val iconText: String?,
            val iconUrl: String?,
            /** Whole-card tap target: type "link" opens [redirectLink] externally, "component_uuid" submits it as a targetId. */
            val redirectType: String?,
            val redirectLink: String?,
            val ctaButtons: List<CtaButton>,
            val buttons: List<CardButton>,
        )

        /** type "link" opens [link] externally; "component_uuid" submits [link] as a targetId. */
        data class CtaButton(val name: String, val type: String, val link: String)
        data class CardButton(val label: String, val link: String?)
        data class DynamicButton(val title: String, val targetId: String?, val componentUuid: String?)
    }

    /** A LINK node that also has its own separate question text - see toBotNode()'s LINK handling. */
    data class LinkCard(val url: String) : BotContent

    /** AGENT_TRANSFER/TEAM_TRANSFER - the bot-flow-to-human-agent handoff signal. */
    data object AgentTransferNotice : BotContent

    /**
     * A WELCOME_SCREEN node. On a card tap: [Card.ctaEnabled] + `ctaType=="external_link"`
     * opens [Card.ctaLink] externally (and still submits); `ctaType=="component"` submits with
     * [Card.ctaLink] as the targetId instead of the node's own; the outgoing text is
     * [Card.name] trimmed, falling back to "Card {n}" (1-based).
     */
    data class WelcomeScreen(
        val iconUrl: String?,
        val title: String?,
        val description: String?,
        val cards: List<Card>,
    ) : BotContent {
        data class Card(
            val name: String?,
            val title: String?,
            val bgColor: String?,
            val bgImageUrl: String?,
            val ctaEnabled: Boolean,
            val ctaType: String?,
            val ctaLink: String?,
        )
    }

    /**
     * An IFRAME node. `postMessage` auto-advance: when [moveForEvent] is set, a `message` event
     * from the embedded page whose `data.type === moveForEvent` (origin-checked against [url])
     * jumps the bot flow to [targetId], via a native WebView+JavascriptInterface bridge.
     */
    data class IframeContent(
        val url: String,
        val heightDp: Int?,
        val moveForEvent: String?,
        val targetId: String?,
    ) : BotContent

    data class Unsupported(val nodeType: String?) : BotContent
}
