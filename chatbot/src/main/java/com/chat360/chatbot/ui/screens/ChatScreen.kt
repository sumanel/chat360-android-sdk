package com.chat360.chatbot.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.ChatViewModel
import com.chat360.chatbot.ui.components.chrome.HeaderBar
import com.chat360.chatbot.ui.components.chrome.ChatHistorySidebar
import com.chat360.chatbot.ui.components.chrome.StatusBanner
import com.chat360.chatbot.ui.components.chrome.TypingIndicatorRow
import com.chat360.chatbot.ui.components.chrome.WelcomeSplash
import com.chat360.chatbot.ui.components.feedback.FeedbackFormDialog
import com.chat360.chatbot.ui.components.input.ChatInputBar
import com.chat360.chatbot.ui.components.input.EmojiPickerPanel
import com.chat360.chatbot.ui.components.input.SpeechToTextBar
import com.chat360.chatbot.ui.components.input.VoiceRecorderBar
import com.chat360.chatbot.ui.components.messages.BotMessageRow
import com.chat360.chatbot.ui.components.messages.UserMessageRow
import com.chat360.chatbot.ui.components.messages.content.BotContentActions
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.applyOverrides
import com.chat360.chatbot.ui.theme.LocalChat360ThemeController
import com.chat360.chatbot.config.LocalChat360UIConfig
import com.chat360.chatbot.ui.util.rememberAttachmentPicker
import com.chat360.chatbot.ui.util.rememberCameraCapture
import com.chat360.chatbot.ui.util.rememberSpeechToTextController
import com.chat360.chatbot.ui.util.rememberVoicePlaybackController
import com.chat360.chatbot.ui.util.rememberVoiceRecorderController
import android.util.Log


/**
 * Top-level chat screen: owns layout only. Every visual piece (header, splash, message rows,
 * input bar) lives in ui/components/ so this file stays readable as content types grow.
 *
 * Colors/branding are resolved in two layers: `Chat360Theme(...)` (set once, outside this
 * screen) picks the base preset/custom look, and this screen re-provides both locals with the
 * bot's server-side appearance config (fetched async, may arrive after first composition)
 * layered on top - the "other values fetched from the API as per customization" requirement.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val baseColors = LocalChat360Colors.current
    val baseBranding = LocalChat360Branding.current
//    val effectiveColors = baseColors.applyOverrides(state.colorOverrides)
    val effectiveBranding = baseBranding.copy(
        botTitle = state.botTitleOverride ?: baseBranding.botTitle,
        logo = state.logoOverride ?: baseBranding.logo,
    )

//    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.colorOverrides) {
        Log.d("Chat360Theme", "Color overrides = ${state.colorOverrides}")
    }

    LaunchedEffect(state.colorOverrides) {
        Log.d("Chat360Theme", "Base Colors      = $baseColors")
        Log.d("Chat360Theme", "Overrides       = ${state.colorOverrides}")
//        Log.d("Chat360Theme", "Effective Colors= $effectiveColors")
    }

    CompositionLocalProvider(
        LocalChat360Colors provides baseColors,
        LocalChat360Branding provides effectiveBranding,
    ) {
        val listState = rememberLazyListState()
        val pickAttachment = rememberAttachmentPicker { payload ->
            viewModel.sendFile(payload.bytes, payload.fileName, payload.mimeType)
        }
        val captureFromCamera = rememberCameraCapture { payload ->
            viewModel.sendFile(payload.bytes, payload.fileName, payload.mimeType)
        }
        val voiceRecorder = rememberVoiceRecorderController()
        val voicePreviewPlayback = rememberVoicePlaybackController()
        val speechToText = rememberSpeechToTextController()
        var showEmojiPicker by remember { mutableStateOf(false) }
        var showHistorySidebar by remember { mutableStateOf(false) }
        var isTrainingMode by remember { mutableStateOf(false) }
        val themeController = LocalChat360ThemeController.current
        val sdkConfig = LocalChat360UIConfig.current
        val features = sdkConfig.features
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(speechToText.transcript) {
            if (speechToText.isListening) viewModel.onInputChange(speechToText.transcript)
        }

        // Keyed on the tail message's id (not messages.size) so loadMoreHistory() prepending
        // older messages at the head - which also changes size - never yanks the view back
        // down to the bottom; LazyColumn's own key-based item tracking already keeps the
        // visible messages anchored in place when items are inserted above them.
        var lastTailMessageId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.messages.lastOrNull()?.id, state.isAgentTyping) {
            val newTailId = state.messages.lastOrNull()?.id
            if (state.messages.isNotEmpty() && newTailId != lastTailMessageId) {
                listState.animateScrollToItem(state.messages.size - 1 + if (state.isAgentTyping) 1 else 0)
            }
            lastTailMessageId = newTailId
        }

        // Scrolling near the top requests the next older page, gated by
        // hasMoreHistory/isLoadingMoreHistory to avoid firing overlapping requests.
        LaunchedEffect(listState, state.hasMoreHistory, state.isLoadingMoreHistory) {
            if (!state.hasMoreHistory || state.isLoadingMoreHistory) return@LaunchedEffect
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { index -> if (index <= 2) viewModel.loadMoreHistory() }
        }

        // A backgrounded app's socket can die silently (OS network suspension, doze, a dropped
        // mobile connection) with no local signal until the next send just goes nowhere -
        // reconnecting here means coming back to the app restores a live connection proactively
        // instead of the user only finding out by typing into a dead one. See
        // ChatViewModel.onAppForegrounded's own doc for why this reconnects rather than
        // starting a brand-new chat.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppForegrounded()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val context = LocalContext.current
        LaunchedEffect(state.pendingUrlToOpen) {
            state.pendingUrlToOpen?.let { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                viewModel.clearPendingUrl()
            }
        }

        // While a WELCOME_SCREEN message is the most-recent one, it renders fixed above the
        // scroll list instead of inside it: it stops being pinned automatically once anything
        // else arrives after it.
        val pinnedWelcomeMessage = state.messages.lastOrNull()?.takeIf { !it.fromUser && it.content is BotContent.WelcomeScreen }
        val listMessages = if (pinnedWelcomeMessage != null) state.messages.dropLast(1) else state.messages

        // imePadding() is the defensive half of the keyboard-overlap fix: it makes the input bar
        // react to the actual IME inset directly, so the layout still lands correctly even when
        // the surrounding window doesn't physically resize on keyboard show - e.g. when this
        // screen is embedded via ChatComposeFragment inside a host Activity that doesn't declare
        // adjustResize, or when edge-to-edge drawing is in play. When the window *does* resize
        // normally, the reported ime inset here is already 0, so this is a no-op in that case.
        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            Column(modifier = Modifier.fillMaxSize().background(baseColors.background)) {
                sdkConfig.ui.header?.invoke() ?: run {
                    if (features.showMenu || features.showNewChat) {
                        HeaderBar(
                            connected = state.isConnected,
                            assignedAgent = state.assignedAgent,
                            showMenu = features.showMenu && features.showHistorySidebar,
                            showNewChat = features.showNewChat,
                            newChatEnabled = !state.isAgentTyping,
                            onMenuClick = {
                                // Dismiss the IME first - otherwise it stays open behind the
                                // sidebar and keeps the window resized around it, squeezing the
                                // sidebar's height instead of letting it take the full screen.
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                sdkConfig.callbacks.onMenuClicked()
                                showHistorySidebar = true
                            },
                            onNewChatClick = {
                                sdkConfig.callbacks.onNewChatClicked()
                                viewModel.startNewChat()
                            },
                            shortcuts = shortcuts,
                            onShortcutSelected = { targetId, label -> viewModel.selectShortcut(targetId, label) },
                            onRefreshClick = { viewModel.refreshConnection() },
                        )
                    }
                }

            if (state.isSlowConnection) StatusBanner(text = "Slow connection…", emphasized = false)
            // Chat state is always live now (no local cache fallback - see ChatCacheRepository),
            // so a connection failure has nothing to silently fall back to and must be surfaced.
            if (!state.isConnected) state.error?.let { StatusBanner(text = it, emphasized = true) }

            pinnedWelcomeMessage?.let { pinned ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BotMessageItem(pinned, viewModel, pickAttachment, captureFromCamera, state.isLiveChat, state.assignedAgent)
                }
            }

            if (state.messages.isEmpty()) {
                WelcomeSplash(modifier = Modifier.weight(1f))
            } else if (listMessages.isNotEmpty() || state.isAgentTyping) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.isLoadingMoreHistory) {
                        item(key = "loading_more_history") {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = baseColors.accent)
                            }
                        }
                    }
                    items(listMessages, key = { it.id }) { message ->
                        if (message.fromUser) {
                            UserMessageRow(message)
                        } else {
                            BotMessageItem(message, viewModel, pickAttachment, captureFromCamera, state.isLiveChat, state.assignedAgent)
                        }
                    }
                    if (state.isAgentTyping && features.showTypingIndicator) {
                        item { TypingIndicatorRow() }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val voiceDraft = state.voiceDraft
            if (state.isArchived) {
                StatusBanner(text = "This conversation has been archived due to inactivity.", emphasized = false)
            } else if (voiceRecorder.isRecording || voiceDraft != null) {
                VoiceRecorderBar(
                    isRecording = voiceRecorder.isRecording,
                    liveAmplitudes = voiceRecorder.amplitudes,
                    elapsedMs = voiceRecorder.elapsedMs,
                    onStopRecording = {
                        voiceRecorder.stop()?.let {
                            viewModel.onVoiceRecordingCaptured(it.file.absolutePath, it.amplitudes, it.durationMs)
                        }
                    },
                    onCancelRecording = { voiceRecorder.cancel() },
                    draftAmplitudes = voiceDraft?.amplitudes.orEmpty(),
                    draftDurationMs = voiceDraft?.durationMs ?: 0L,
                    draftUploading = voiceDraft?.uploading ?: false,
                    draftUploadProgress = voiceDraft?.uploadProgress ?: 0,
                    draftError = voiceDraft?.error,
                    playbackController = voicePreviewPlayback,
                    draftLocalFilePath = voiceDraft?.filePath,
                    onSendDraft = viewModel::sendVoiceDraft,
                    onCancelDraft = {
                        voicePreviewPlayback.pause()
                        viewModel.cancelVoiceDraft()
                    },
                )
            } else if (speechToText.isListening) {
                SpeechToTextBar(
                    isListening = true,
                    error = speechToText.error,
                    onStop = { speechToText.stop() },
                )
            } else {
                if (showEmojiPicker) {
                    EmojiPickerPanel(onEmojiSelected = { emoji -> viewModel.onInputChange(state.inputText + emoji) })
                }
                sdkConfig.ui.footer?.invoke() ?: ChatInputBar(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage,
                    onAttachmentClick = pickAttachment,
                    onMicClick = { voiceRecorder.requestStart() },
                    showDictationIcon = features.showSpeechToText && speechToText.isSupported(),
                    onDictateClick = { speechToText.requestStart() },
                    onEmojiClick = { showEmojiPicker = !showEmojiPicker },
                    showAttachment = features.showAttachment,
                    showEmoji = features.showEmoji,
                    showVoiceInput = features.showVoiceInput,
                    showSend = features.showSend,
                    sendEnabled = !state.isAgentTyping,
                    enabled = state.isConnected,
                )
            }
            }
            if (showHistorySidebar && features.showHistorySidebar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { showHistorySidebar = false },
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clickable { },
                    ) {
                        ChatHistorySidebar(
                            onDismiss = { showHistorySidebar = false },
                            onNewChat = {
                                viewModel.startNewChat()
                                showHistorySidebar = false
                            },
                            isTrainingMode = isTrainingMode,
                            onAssistantModeChanged = { isTrainingMode = it },
                            isDarkTheme = themeController?.isDarkTheme == true,
                            onThemeChanged = { themeController?.selectDarkTheme(it) },
                            showAssistantMode = features.showAssistantMode,
                            trainingModeEnabled = features.enableTrainingMode,
                            showAppearanceSwitcher = features.showAppearanceSwitcher && sdkConfig.theme.allowThemeSwitch,
                            conversations = conversations,
                            activeConversationId = state.activeConversationId,
                            onConversationSelected = {
                                viewModel.openConversation(it)
                                showHistorySidebar = false
                            },
                            onConversationRenamed = viewModel::renameConversation,
                            onConversationDeleted = viewModel::deleteConversation,
                            languages = languages,
                            onLanguageSelected = { key ->
                                viewModel.switchLanguage(key)
                                showHistorySidebar = false
                            },
                        )
                    }
                }
            }
        }

        val feedbackConfig = state.feedbackConfig
        if (state.showFeedbackPrompt && feedbackConfig != null) {
            FeedbackFormDialog(
                feedbackConfig = feedbackConfig,
                onSubmit = { rating, feedbackText -> viewModel.submitFeedback(rating, feedbackText) },
                onDismiss = viewModel::dismissFeedbackPrompt,
            )
        }
    }
}

/** Shared by the normal scroll list and the pinned-WELCOME_SCREEN slot above it. */
@Composable
private fun BotMessageItem(
    message: ChatMessage,
    viewModel: ChatViewModel,
    pickAttachment: () -> Unit,
    captureFromCamera: () -> Unit,
    isLiveChat: Boolean,
    assignedAgent: AssignedAgent?,
) {
    BotMessageRow(
        message = message,
        isLiveChat = isLiveChat,
        assignedAgent = assignedAgent,
        actions = BotContentActions(
            onQuickReply = { option -> viewModel.selectQuickReply(message.id, option) },
            onAttachmentClick = pickAttachment,
            onCameraClick = captureFromCamera,
            onRatingSelected = { value -> viewModel.selectRating(message.id, value) },
            onFormFieldChange = { index, value -> viewModel.updateFormField(message.id, index, value) },
            onMediaFieldPicked = { index, bytes, fileName, mimeType -> viewModel.uploadFormField(message.id, index, bytes, fileName, mimeType) },
            onFormSubmit = { viewModel.submitForm(message.id) },
            onPromptValueChange = { primary, secondary -> viewModel.updatePromptValue(message.id, primary, secondary) },
            onEmailSubmit = { viewModel.submitEmail(message.id) },
            onPhoneSubmit = { viewModel.submitPhone(message.id) },
            onAutoSuggestionSelected = { index, text -> viewModel.selectAutoSuggestion(message.id, index, text) },
            onDateSelected = { date -> viewModel.selectDate(message.id, date) },
            onTimeSubmit = { time -> viewModel.submitTime(message.id, time) },
            onCheckboxToggle = { index -> viewModel.toggleCheckbox(message.id, index) },
            onCheckboxSubmit = { viewModel.submitCheckboxes(message.id) },
            onImageButtonClick = { card, button ->
                val submitType = (message.content as? BotContent.ImageButtons)?.submitType ?: "BUTTON"
                viewModel.selectImageButton(message.id, card, button, submitType)
            },
            onTextCarouselTap = { text, index, targetId -> viewModel.selectTextCarouselReply(message.id, text, index, targetId) },
            onWelcomeCardSelected = { card, index -> viewModel.selectWelcomeCard(message.id, card, index) },
            onIframeAdvance = { targetId -> viewModel.advanceFromIframe(targetId) },
        ),
    )
}
