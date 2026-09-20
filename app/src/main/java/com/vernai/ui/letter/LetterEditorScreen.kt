package com.vernai.ui.letter

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vernai.document.export.ExportFormat
import com.vernai.domain.model.letter.LetterType
import com.vernai.ui.theme.StatusGreen
import com.vernai.ui.theme.StatusRed

import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.DisposableEffect
import com.vernai.ai.tts.OnDeviceTtsManager
import com.vernai.document.export.PanchayatPrintManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterEditorScreen(
    viewModel: LetterEditorViewModel,
    initialTranscript: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val ttsManager = remember { OnDeviceTtsManager(context) }

    DisposableEffect(ttsManager) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    LaunchedEffect(initialTranscript) {
        if (!initialTranscript.isNullOrBlank()) {
            viewModel.handleIntent(LetterUiIntent.InitializeWithTranscript(initialTranscript))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is LetterUiSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is LetterUiSideEffect.ShareExportedFile -> {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", effect.file
                        )
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = effect.mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, state.subject.ifBlank { "అధికారిక వినతిపత్రం" })
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "లేఖ పంచుకోండి"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "లోపం: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                is LetterUiSideEffect.PrintDocument -> {
                    val jobName = if (state.subject.isNotBlank()) state.subject else "వినతిపత్రం_లేఖ"
                    val success = PanchayatPrintManager.printPdf(context, effect.file, jobName)
                    if (!success) {
                        Toast.makeText(context, "ప్రింటింగ్ విఫలమైంది. ప్రింటర్‌ను తనిఖీ చేయండి", Toast.LENGTH_SHORT).show()
                    }
                }
                is LetterUiSideEffect.SpeakText -> {
                    val started = ttsManager.speak(effect.text, effect.language)
                    if (!started) {
                        viewModel.handleIntent(LetterUiIntent.SetSpeakingState(false))
                        Toast.makeText(context, "వాయిస్ చదవడం ప్రారంభించలేకపోయాము. డివైస్ TTS సెట్టింగ్స్ చూడండి", Toast.LENGTH_SHORT).show()
                    }
                }
                is LetterUiSideEffect.StopSpeaking -> {
                    ttsManager.stop()
                }
            }
        }
    }

    if (state.showReviewDialog) {
        ReviewDialog(
            state    = state,
            onConfirm = { viewModel.handleIntent(LetterUiIntent.ConfirmReviewAndExport) },
            onDismiss = { viewModel.handleIntent(LetterUiIntent.DismissReviewDialog) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("అధికారిక లేఖ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "వెనుకకు")
                    }
                },
                actions = {
                    // Read Aloud / Stop Voice
                    IconButton(
                        onClick = { viewModel.handleIntent(LetterUiIntent.ToggleSpeech) }
                    ) {
                        if (state.isSpeaking) {
                            Icon(
                                Icons.Default.VolumeOff,
                                contentDescription = "వాయిస్ ఆపు (Stop)",
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "చదివి వినిపించు (Read Aloud)",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Direct Panchayat Print
                    IconButton(
                        onClick = { viewModel.handleIntent(LetterUiIntent.PrintLetter(context.cacheDir)) },
                        enabled = !state.isExporting
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "పంచాయతీ ప్రింట్ (Print)")
                    }

                    // Save to Room DB
                    IconButton(
                        onClick = { viewModel.handleIntent(LetterUiIntent.SaveLetterDraft) },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "సేవ్")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            LetterActionBar(
                activeTab    = state.activeTab,
                isGenerating = state.isGenerating,
                isRegenerating = state.isRegenerating,
                isSaving     = state.isSaving,
                isExporting  = state.isExporting,
                isSpeaking   = state.isSpeaking,
                onGenerate   = { viewModel.handleIntent(LetterUiIntent.GenerateLetter) },
                onRegenerate = { viewModel.handleIntent(LetterUiIntent.RegenerateLetter) },
                onSave       = { viewModel.handleIntent(LetterUiIntent.SaveLetterDraft) },
                onToggleSpeech = { viewModel.handleIntent(LetterUiIntent.ToggleSpeech) },
                onPrint      = { viewModel.handleIntent(LetterUiIntent.PrintLetter(context.cacheDir)) },
                onExportPdf  = { viewModel.handleIntent(LetterUiIntent.RequestExport(ExportFormat.PDF, context.cacheDir)) },
                onExportDocx = { viewModel.handleIntent(LetterUiIntent.RequestExport(ExportFormat.DOCX, context.cacheDir)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Generating banner
            if (state.isGenerating || state.isRegenerating) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("లేఖ రూపొందిస్తున్నది…", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.handleIntent(LetterUiIntent.CancelGeneration) }) {
                            Text("రద్దు")
                        }
                    }
                }
            }

            // Error banner
            if (state.errorMessage != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Tabs
            TabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick  = { viewModel.handleIntent(LetterUiIntent.SwitchTab(0)) },
                    text     = { Text("వివరాలు", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick  = { viewModel.handleIntent(LetterUiIntent.SwitchTab(1)) },
                    text     = { Text("తెలుగు లేఖ", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = state.activeTab == 2,
                    onClick  = { viewModel.handleIntent(LetterUiIntent.SwitchTab(2)) },
                    text     = { Text("English", fontWeight = FontWeight.SemiBold) }
                )
            }

            when (state.activeTab) {
                0 -> InputsTab(state = state, viewModel = viewModel)
                1 -> TeluguPreviewTab(state = state, viewModel = viewModel)
                2 -> EnglishTab(state = state, viewModel = viewModel)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Inputs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InputsTab(state: LetterUiState, viewModel: LetterEditorViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Letter type selector
        SectionLabel(text = "1. లేఖ రకం (Letter Type)")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LetterType.entries.forEach { type ->
                val selected = state.letterType == type
                FilledTonalButton(
                    onClick = { viewModel.handleIntent(LetterUiIntent.UpdateLetterType(type)) },
                    shape   = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = type.teluguTitle,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Spoken issue
        SectionLabel(text = "2. సమస్య లేదా విన్నపం (Describe your issue)")
        OutlinedTextField(
            value = state.voiceTranscript,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateVoiceTranscript(it)) },
            placeholder = { Text("ఉదా: మా వీధిలో వీధి దీపాలు వెలగడం లేదు, రాత్రి వేళల్లో రాకపోకలకు ఇబ్బందిగా ఉంది...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        // Recipient
        SectionLabel(text = "3. ఎవరికి పంపాలి? (Whom to send)")
        OutlinedTextField(
            value = state.recipientDesignation,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateRecipientDesignation(it)) },
            placeholder = { Text("అధికారి హోదా (ఉదా: జిల్లా కలెక్టర్, గ్రామ సర్పంచ్)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.recipientDepartment,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateRecipientDepartment(it)) },
            placeholder = { Text("కార్యాలయం లేదా శాఖ (ఉదా: కలెక్టరేట్, గ్రామ పంచాయతీ)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Location & date row
        SectionLabel(text = "4. స్థలం & తేదీ (Place & Date)")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.location,
                onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateLocation(it)) },
                placeholder = { Text("గ్రామం / పట్టణం") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.date,
                onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateDate(it)) },
                placeholder = { Text("DD-MM-YYYY") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Applicant name
        SectionLabel(text = "5. మీ పేరు (Your Name)")
        OutlinedTextField(
            value = state.applicantName,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateApplicantName(it)) },
            placeholder = { Text("దరఖాస్తుదారుడి పేరు (ఉదా: రాము)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Generate button
        Button(
            onClick = { viewModel.handleIntent(LetterUiIntent.GenerateLetter) },
            enabled = !state.isGenerating,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("లేఖ రూపొందిస్తున్నది…")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("లేఖను సిద్ధం చేయండి (Draft Letter) ✨")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — Telugu preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TeluguPreviewTab(state: LetterUiState, viewModel: LetterEditorViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Review banner — compact, colour-coded
        ReviewBanner(
            isReviewed = state.isUserReviewed,
            onToggle   = { viewModel.handleIntent(LetterUiIntent.SetUserReviewed(!state.isUserReviewed)) }
        )

        // Editable fields
        OutlinedTextField(
            value = state.subject,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSubject(it)) },
            placeholder = { Text("విషయము") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.salutation,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSalutation(it)) },
            placeholder = { Text("సంబోధన") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.vernacularBody,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateVernacularBody(it)) },
            placeholder = { Text("లేఖ మొత్తం…") },
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
        )
        OutlinedTextField(
            value = state.closing,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateClosing(it)) },
            placeholder = { Text("ముగింపు") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.signaturePlaceholder,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSignaturePlaceholder(it)) },
            placeholder = { Text("సంతకం / దరఖాస్తుదారుడి వివరాలు") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 3 — English copy
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EnglishTab(state: LetterUiState, viewModel: LetterEditorViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.englishTranslation,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateEnglishTranslation(it)) },
            placeholder = { Text("Formal English translation…") },
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom action bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LetterActionBar(
    activeTab: Int,
    isGenerating: Boolean,
    isRegenerating: Boolean,
    isSaving: Boolean,
    isExporting: Boolean,
    isSpeaking: Boolean,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onToggleSpeech: () -> Unit,
    onPrint: () -> Unit,
    onExportPdf: () -> Unit,
    onExportDocx: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left action — Generate or Regenerate
            if (activeTab == 0) {
                OutlinedButton(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("రూపొందించు", maxLines = 1)
                }
            } else {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !isRegenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRegenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("తిరిగి రాయి", maxLines = 1)
                }
            }

            // Read Aloud / Stop
            FilledTonalButton(
                onClick = onToggleSpeech
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "చదివి వినిపించు",
                    modifier = Modifier.size(16.dp),
                    tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Print Directly at Panchayat
            FilledTonalButton(
                onClick = onPrint,
                enabled = !isExporting
            ) {
                Icon(Icons.Default.Print, contentDescription = "పంచాయతీ ప్రింట్", modifier = Modifier.size(16.dp))
            }

            // Save
            FilledTonalButton(onClick = onSave, enabled = !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            // PDF
            Button(onClick = onExportPdf, enabled = !isExporting) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF", fontSize = 12.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Review banner — replaces the old verbose OutlinedCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReviewBanner(isReviewed: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (isReviewed) StatusGreen.copy(alpha = 0.12f) else StatusRed.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isReviewed) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (isReviewed) StatusGreen else StatusRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isReviewed) "సరిచూడబడింది — ఎగుమతికి సిద్ధం" else "ఎగుమతికి ముందు లేఖ సరిచూడండి",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isReviewed) StatusGreen else StatusRed,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Checkbox(checked = isReviewed, onCheckedChange = { onToggle() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Review/export confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReviewDialog(
    state: LetterUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ఎగుమతి చేయడానికి ముందు ధృవీకరించండి", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "దరఖాస్తుదారు: ${state.applicantName.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "అధికారి: ${state.recipientDesignation}, ${state.recipientDepartment}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "తేదీ & స్థలం: ${state.date} | ${state.location}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "లేఖలోని అన్ని వాస్తవాలు సరైనవని ధృవీకరిస్తున్నాను.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmed) {
                Text("ఎగుమతి చేయి")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("సవరించు")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
