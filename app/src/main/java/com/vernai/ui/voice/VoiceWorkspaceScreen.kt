package com.vernai.ui.voice

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
                is VoiceUiSideEffect.NavigateTo -> onNavigateToDestination(effect.destination)
                is VoiceUiSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Workspace", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = state.activeLanguage.nativeName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Header
            StatusChipBar(state = state)

            // Error Message Banner (if any)
            AnimatedVisibility(visible = state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Mic Recording Core
            MicRecordingZone(
                isRecording = state.isRecording,
                durationSec = state.recordingDurationSec,
                decibels = state.audioDecibels,
                onToggle = {
                    if (!state.isRecording && !permissionState.hasPermission) {
                        permissionState.requestPermission()
                    } else {
                        viewModel.handleIntent(VoiceUiIntent.ToggleRecording)
                    }
                }
            )

            // Live Transcription Card
            LiveTranscriptCard(state = state)

            // LLM Reasoning & Result Card
            AnimatedVisibility(visible = state.stage == ProcessingStage.REASONING_LLM || state.stage == ProcessingStage.COMPLETED) {
                ResultPreviewCard(
                    state = state,
                    onProceed = { viewModel.handleIntent(VoiceUiIntent.ProceedToIntentAction) },
                    onReset = { viewModel.handleIntent(VoiceUiIntent.ResetState) }
                )
            }
        }
    }
}

@Composable
fun StatusChipBar(state: VoiceUiState) {
    val (statusText, statusColor) = when (state.stage) {
        ProcessingStage.IDLE -> "సిద్ధంగా ఉంది (Ready)" to MaterialTheme.colorScheme.outline
        ProcessingStage.RECORDING -> "రికార్డింగ్ జరుగుతోంది (Listening 16kHz)..." to Color(0xFFDC2626)
        ProcessingStage.TRANSCRIBING -> "అక్షరీకరణ (Transcribing)..." to MaterialTheme.colorScheme.primary
        ProcessingStage.REASONING_LLM -> "స్థానిక AI విశ్లేషణ (Qwen2.5 Reasoning)..." to Color(0xFF0F766E)
        ProcessingStage.COMPLETED -> "విశ్లేషణ పూర్తయింది (Complete)" to Color(0xFF059669)
    }

    Surface(
        color = statusColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }
    }
}

@Composable
fun MicRecordingZone(
    isRecording: Boolean,
    durationSec: Int,
    decibels: Float,
    onToggle: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626).copy(alpha = 0.2f))
                )
            }

            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop" else "Record",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isRecording) "మాట్లాడడం ఆపడానికి నొక్కండి (Tap to Stop)" else "రికార్డ్ చేయడానికి మైక్ నొక్కండి (Tap to Speak)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LiveTranscriptCard(state: VoiceUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "లైవ్ మాటలు (Live Transcript)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (state.isRecording) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(12.dp)
            ) {
                if (state.liveTranscript.isBlank()) {
                    Text(
                        text = "మీరు మాట్లాడే మాటలు ఇక్కడ నేరుగా తెలుగులో కనిపిస్తాయి... (Your spoken words will appear here)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = state.liveTranscript,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ResultPreviewCard(
    state: VoiceUiState,
    onProceed: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.stage == ProcessingStage.REASONING_LLM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "స్థానిక AI సమాచారాన్ని క్రమబద్ధీకరిస్తోంది...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (state.stage == ProcessingStage.COMPLETED) {
                Text(
                    text = "AI గుర్తించిన సమాచారం (Extracted Insight)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.extractedResultPreview ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onReset) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("మళ్లీ చేయండి")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onProceed) {
                        Text(
                            text = if (state.detectedIntent == DetectedIntentType.SALES_RECORD) "లెడ్జర్‌లోకి తెరవండి" else "పత్రం ఎడిట్ చేయండి"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
