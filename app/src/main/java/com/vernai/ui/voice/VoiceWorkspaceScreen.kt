package com.vernai.ui.voice

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vernai.ui.theme.StatusGreen
import com.vernai.ui.theme.StatusRed
import com.vernai.ui.navigation.VernAiNavDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceWorkspaceScreen(
    viewModel: VoiceWorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDestination: (VernAiNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionState = com.vernai.ui.common.rememberAudioPermissionState {
        viewModel.handleIntent(VoiceUiIntent.ToggleRecording)
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is VoiceUiSideEffect.NavigateTo  -> onNavigateToDestination(effect.destination)
                is VoiceUiSideEffect.ShowToast   -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (state.activeLanguage) {
                        com.vernai.core.model.Language.TAMIL -> "குரல் உதவியாளர் (Voice Assistant)"
                        com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "वॉयस असिस्टेंट (Voice Assistant)"
                        com.vernai.core.model.Language.ENGLISH -> "Voice Workspace"
                        else -> "వాయిస్ వర్క్‌స్పేస్ (Voice Assistant)"
                    }
                    Text(
                        text = titleText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "వెనుకకు")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    com.vernai.core.model.Language.TELUGU,
                    com.vernai.core.model.Language.TAMIL,
                    com.vernai.core.model.Language.HINDI,
                    com.vernai.core.model.Language.ENGLISH
                ).forEach { lang ->
                    FilterChip(
                        selected = state.activeLanguage == lang,
                        onClick = { viewModel.handleIntent(VoiceUiIntent.ChangeLanguage(lang)) },
                        label = { Text("${lang.nativeName} (${lang.englishName})", fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Stage label ────────────────────────────────────────────
            StageLabel(stage = state.stage, language = state.activeLanguage)

            Spacer(modifier = Modifier.height(16.dp))

            // Helpful suggestion chip / tip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                val tipText = when (state.activeLanguage) {
                    com.vernai.core.model.Language.TAMIL -> "💡 உதாரணமாக: 'பஞ்சாயத்துக்கு மனு எழுத வேண்டும்' அல்லது '5 கிலோ தக்காளி 200 ரூபாய்'"
                    com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "💡 उदाहरण: 'सड़क मरम्मत के लिए पत्र लिखें' या '5 किलो टमाटर 200 रुपये'"
                    com.vernai.core.model.Language.ENGLISH -> "💡 Example: 'Draft a complaint about street lights' or '5 kg tomato 200 rupees'"
                    else -> "💡 ఉదాహరణ: 'పంచాయతీకి దరఖాస్తు రాయాలి' లేదా '5 కేజీల టమాటా 200 రూపాయలు'"
                }
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Voice Command Chips (for instant testing or fast dictation)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val voiceCommandSamples = when (state.activeLanguage) {
                    com.vernai.core.model.Language.TAMIL -> listOf(
                        "எனக்கு இரண்டு நாட்கள் விடுப்பு வேண்டும்" to "📝 விடுப்பு கடிதம்",
                        "இன்று 5 கிலோ தக்காளி 200 ரூபாய் விற்றேன்" to "🍅 தக்காளி விற்பனை",
                        "ஊராட்சி அலுவலருக்கு கிராம சாலை பழுது மனு எழுதவும்" to "📜 சாலை புகார் மனு",
                        "குடிநீர் தட்டுப்பாடு குறித்து புகார் மனு" to "💧 குடிநீர் மனு"
                    )
                    com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> listOf(
                        "मुझे दो दिन की छुट्टी चाहिए, आवेदन पत्र लिखें" to "📝 अवकाश पत्र",
                        "आज 5 किलो टमाटर 200 रुपये में बेचा" to "🍅 टमाटर बिक्री",
                        "सड़क मरम्मत के लिए पंचायत अधिकारी को शिकायत पत्र लिखें" to "📜 सड़क शिकायत",
                        "पीने के पानी की समस्या के लिए शिकायत पत्र" to "💧 पेयजल शिकायत"
                    )
                    com.vernai.core.model.Language.ENGLISH -> listOf(
                        "Write a leave letter for two days due to fever" to "📝 Leave Letter",
                        "Today sold 5 kg tomatoes for 200 rupees" to "🍅 Sales Record",
                        "Draft complaint letter to panchayat officer for road repair" to "📜 Road Repair",
                        "Formal petition regarding drinking water shortage" to "💧 Water Petition"
                    )
                    else -> listOf(
                        "నాకు రెండు రోజులు సెలవు కావాలి లేఖ రాయండి" to "📝 సెలవు లేఖ",
                        "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు అమ్మిన" to "🍅 టమాటా అమ్మకం",
                        "మా వీధిలో రోడ్ల మరమ్మత్తు కోసం పంచాయతీ అధికారికి లేఖ రాయాలి" to "📜 రోడ్ల ఫిర్యాదు",
                        "తాగునీటి సమస్యపై అధికారికి దరఖాస్తు" to "💧 తాగునీటి సమస్య"
                    )
                }
                voiceCommandSamples.forEach { (cmdText, cmdLabel) ->
                    SuggestionChip(
                        onClick = { viewModel.handleIntent(VoiceUiIntent.SimulateSpeech(cmdText)) },
                        label = { Text(cmdLabel, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Microphone button ──────────────────────────────────────
            MicButton(
                isRecording = state.isRecording,
                durationSec = state.recordingDurationSec,
                language = state.activeLanguage,
                onToggle = {
                    if (!state.isRecording && !permissionState.hasPermission) {
                        permissionState.requestPermission()
                    } else {
                        viewModel.handleIntent(VoiceUiIntent.ToggleRecording)
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Live transcript ────────────────────────────────────────
            TranscriptArea(transcript = state.liveTranscript, isRecording = state.isRecording, language = state.activeLanguage)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Error ─────────────────────────────────────────────────
            AnimatedVisibility(visible = state.errorMessage != null) {
                Text(
                    text = state.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Result / action ───────────────────────────────────────
            AnimatedVisibility(
                visible = state.stage == ProcessingStage.REASONING_LLM ||
                          state.stage == ProcessingStage.COMPLETED
            ) {
                ResultSection(
                    state = state,
                    onProceed = { viewModel.handleIntent(VoiceUiIntent.ProceedToIntentAction) },
                    onReset   = { viewModel.handleIntent(VoiceUiIntent.ResetState) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stage label — single concise status line
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StageLabel(stage: ProcessingStage, language: com.vernai.core.model.Language) {
    val (text, color) = when (stage) {
        ProcessingStage.IDLE -> when (language) {
            com.vernai.core.model.Language.TAMIL -> "பேச தயாராக உள்ளது"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "बोलने के लिए तैयार"
            com.vernai.core.model.Language.ENGLISH -> "Ready to listen"
            else -> "మాట్లాడటానికి సిద్ధంగా ఉంది"
        } to MaterialTheme.colorScheme.onSurfaceVariant
        ProcessingStage.RECORDING -> when (language) {
            com.vernai.core.model.Language.TAMIL -> "கேட்கிறது…"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "सुन रहा हूँ…"
            com.vernai.core.model.Language.ENGLISH -> "Listening…"
            else -> "వింటున్నది…"
        } to StatusRed
        ProcessingStage.TRANSCRIBING -> when (language) {
            com.vernai.core.model.Language.TAMIL -> "எழுத்தாக மாற்றுகிறது…"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "पहचान रहा हूँ…"
            com.vernai.core.model.Language.ENGLISH -> "Transcribing…"
            else -> "మాటలు గుర్తిస్తున్నది…"
        } to MaterialTheme.colorScheme.primary
        ProcessingStage.REASONING_LLM -> when (language) {
            com.vernai.core.model.Language.TAMIL -> "பகுப்பாய்வு செய்கிறது…"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "विश्लेषण कर रहा हूँ…"
            com.vernai.core.model.Language.ENGLISH -> "Reasoning…"
            else -> "విశ్లేషిస్తున్నది…"
        } to MaterialTheme.colorScheme.secondary
        ProcessingStage.COMPLETED -> when (language) {
            com.vernai.core.model.Language.TAMIL -> "முடிந்தது ✓"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "पूरा हुआ ✓"
            com.vernai.core.model.Language.ENGLISH -> "Completed ✓"
            else -> "పూర్తయింది ✓"
        } to StatusGreen
    }

    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "stageLabel"
    ) { label ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mic button — large, pulsing circle; clean and unmistakable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MicButton(
    isRecording: Boolean,
    durationSec: Int,
    language: com.vernai.core.model.Language,
    onToggle: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1.0f,
        targetValue  = if (isRecording) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring — only shown while recording
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(StatusRed.copy(alpha = 0.18f))
                )
            }

            // Main button
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) StatusRed
                        else MaterialTheme.colorScheme.primary
                    )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop" else "Speak",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Timer and label
        if (isRecording) {
            val m = durationSec / 60
            val s = durationSec % 60
            Text(
                text = "%02d:%02d".format(m, s),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = StatusRed
            )
            Spacer(modifier = Modifier.height(2.dp))
            val stopHint = when (language) {
                com.vernai.core.model.Language.TAMIL -> "ஆப் செய்ய மீண்டும் தட்டவும்"
                com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "रोकने के लिए दोबारा टैप करें"
                com.vernai.core.model.Language.ENGLISH -> "Tap again to finish"
                else -> "ఆపడానికి మళ్లీ నొక్కండి"
            }
            Text(
                text = stopHint,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = StatusRed
            )
        } else {
            val startHint = when (language) {
                com.vernai.core.model.Language.TAMIL -> "பேச தட்டவும்"
                com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "बोलने के लिए टैप करें"
                com.vernai.core.model.Language.ENGLISH -> "Tap to speak"
                else -> "నొక్కి మాట్లాడండి"
            }
            Text(
                text = startHint,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transcript area — minimal; shows placeholder until there's text
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TranscriptArea(
    transcript: String,
    isRecording: Boolean,
    language: com.vernai.core.model.Language
) {
    val emptyHint = when (language) {
        com.vernai.core.model.Language.TAMIL -> if (isRecording) "கேட்கிறது…" else "நீங்கள் பேசும் வார்த்தைகள் இங்கே தோன்றும்"
        com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> if (isRecording) "सुन रहा हूँ…" else "आपकी आवाज़ यहाँ दिखाई देगी"
        com.vernai.core.model.Language.ENGLISH -> if (isRecording) "Listening…" else "Your spoken words will appear here"
        else -> if (isRecording) "వింటున్నది…" else "మీరు మాట్లాడే మాటలు ఇక్కడ కనిపిస్తాయి"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = if (transcript.isBlank()) Alignment.Center else Alignment.TopStart
        ) {
            if (transcript.isBlank()) {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result section — shown after LLM reasoning; clean preview + action buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultSection(
    state: VoiceUiState,
    onProceed: () -> Unit,
    onReset: () -> Unit
) {
    val analyzingText = when (state.activeLanguage) {
        com.vernai.core.model.Language.TAMIL -> "பகுப்பாய்வு செய்கிறது…"
        com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "विश्लेषण कर रहा हूँ…"
        com.vernai.core.model.Language.ENGLISH -> "Reasoning & Analyzing…"
        else -> "విశ్లేషిస్తున్నది…"
    }

    val previewHeader = when (state.activeLanguage) {
        com.vernai.core.model.Language.TAMIL -> "AI கண்டறிந்த தகவல்"
        com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "AI द्वारा पहचानी गई जानकारी"
        com.vernai.core.model.Language.ENGLISH -> "AI Detected Result"
        else -> "AI గుర్తించిన విషయం"
    }

    val isLeave = state.liveTranscript.contains("leave", ignoreCase = true) ||
                  state.liveTranscript.contains("సెలవు") ||
                  state.liveTranscript.contains("விடுப்பு") ||
                  state.liveTranscript.contains("छुट्टी")

    val proceedText = if (state.detectedIntent == DetectedIntentType.SALES_RECORD) {
        when (state.activeLanguage) {
            com.vernai.core.model.Language.TAMIL -> "விற்பனை பதிவேட்டை திறக்கவும்"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "बिक्री खाता खोलें"
            com.vernai.core.model.Language.ENGLISH -> "Open Sales Ledger"
            else -> "అమ్మకాల లాగ్ తెరవండి"
        }
    } else if (isLeave) {
        when (state.activeLanguage) {
            com.vernai.core.model.Language.TAMIL -> "விடுப்பு கடிதத்தை திறக்கவும்"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "अवकाश पत्र खोलें"
            com.vernai.core.model.Language.ENGLISH -> "Open Leave Letter"
            else -> "సెలవు లేఖ తెరవండి"
        }
    } else {
        when (state.activeLanguage) {
            com.vernai.core.model.Language.TAMIL -> "மனுவை திருத்தவும்"
            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "शिकायत पत्र संपादित करें"
            com.vernai.core.model.Language.ENGLISH -> "Edit Complaint Letter"
            else -> "లేఖ సవరించండి"
        }
    }

    val resetText = when (state.activeLanguage) {
        com.vernai.core.model.Language.TAMIL -> "மீண்டும்"
        com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "फिर से"
        com.vernai.core.model.Language.ENGLISH -> "Reset"
        else -> "మళ్లీ"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.stage == ProcessingStage.REASONING_LLM) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = analyzingText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (state.stage == ProcessingStage.COMPLETED) {
            // Preview card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = previewHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.extractedResultPreview ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(resetText)
                }
                Button(
                    onClick = onProceed,
                    modifier = Modifier.weight(2f)
                ) {
                    Text(text = proceedText)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
