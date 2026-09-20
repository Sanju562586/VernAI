package com.vernai.ui.document

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vernai.document.processing.DocumentChunk
import com.vernai.document.processing.DocumentQualityReport
import com.vernai.document.processing.TestDocumentType

import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.DisposableEffect
import com.vernai.ai.tts.OnDeviceTtsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocReaderScreen(
    viewModel: DocReaderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAdvancedSections by remember { mutableStateOf(false) }

    val ttsManager = remember { OnDeviceTtsManager(context) }
    DisposableEffect(ttsManager) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Camera scanner for paper documents & forms
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            val fileName = "Paper_Scan_${System.currentTimeMillis()}.jpg"
            viewModel.handleIntent(DocReaderUiIntent.PickDocumentFile(fileName, "image/jpeg", bytes))
        }
    }

    // Local file picker for PDF and image documents
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/pdf"
            var fileName = "Document_${System.currentTimeMillis()}"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
            viewModel.handleIntent(DocReaderUiIntent.PickDocumentFile(fileName, mimeType, bytes))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is DocReaderUiSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is DocReaderUiSideEffect.OpenExportedFile -> Toast.makeText(context, "ఎగుమతి చేయబడింది: ${effect.file.name}", Toast.LENGTH_LONG).show()
                is DocReaderUiSideEffect.SpeakText -> {
                    val started = ttsManager.speak(effect.text, effect.language)
                    if (!started) {
                        viewModel.handleIntent(DocReaderUiIntent.SetSpeakingState(false))
                        Toast.makeText(context, "వాయిస్ చదవడం ప్రారంభించలేకపోయాము. డివైస్ TTS సెట్టింగ్స్ చూడండి", Toast.LENGTH_SHORT).show()
                    }
                }
                is DocReaderUiSideEffect.StopSpeaking -> {
                    ttsManager.stop()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val screenTitle = when (state.activeLanguage) {
                            com.vernai.core.model.Language.TAMIL -> "ஆவண விளக்கம் (Document Reader)"
                            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "दस्तावेज़ विवरण (Document Reader)"
                            com.vernai.core.model.Language.ENGLISH -> "Document Reader & Explainer"
                            else -> "పత్ర వివరణ (Document Reader)"
                        }
                        val screenSubtitle = when (state.activeLanguage) {
                            com.vernai.core.model.Language.TAMIL -> "எளிய விளக்கம் • 100% ஆஃப்லைன்"
                            com.vernai.core.model.Language.HINDI, com.vernai.core.model.Language.MARATHI -> "सरल विवरण • 100% ऑफ़लाइन"
                            com.vernai.core.model.Language.ENGLISH -> "Clear explanations • 100% Offline"
                            else -> "సులభమైన వివరణ • 100% ఆఫ్‌లైన్"
                        }
                        Text(screenTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(screenSubtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.handleIntent(DocReaderUiIntent.ToggleSpeech) }
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
                                contentDescription = "వివరణ చదువు (Read Aloud)",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            state.explanationReport?.let {
                Card(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Button(
                            onClick = { viewModel.handleIntent(DocReaderUiIntent.ExportExplanation(context.cacheDir)) },
                            enabled = !state.isExporting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("వివరణ PDF ఎగుమతి చేయండి (Export PDF)")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // File Picker Action Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("పత్రం స్కాన్ లేదా అప్‌లోడ్", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("పేపర్ ఫోటో లేదా PDF / ఇమేజ్", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { cameraLauncher.launch(null) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("స్కాన్")
                        }
                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ఫైల్")
                        }
                    }
                }
            }

            // Bundled Test Document Selector Row
            Column {
                Text("పరీక్షా పత్రాలు (Bundled Test Documents)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TestDocumentType.entries.forEach { docType ->
                        val isSelected = state.importedDocumentName.contains(docType.fileName)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.handleIntent(DocReaderUiIntent.ImportTestDocument(docType)) },
                            label = { Text(docType.titleTelugu, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Explanation Language Selector Row (Telugu, Tamil, Hindi, English, Marathi)
            Column {
                Text(
                    text = when (state.activeLanguage) {
                        com.vernai.core.model.Language.TAMIL -> "விளக்க மொழி (Explanation Language):"
                        com.vernai.core.model.Language.HINDI -> "विवरण भाषा (Explanation Language):"
                        com.vernai.core.model.Language.MARATHI -> "स्पष्टीकरण भाषा (Explanation Language):"
                        com.vernai.core.model.Language.ENGLISH -> "Explanation Language:"
                        else -> "వివరణ భాష (Explanation Language):"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
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
                        com.vernai.core.model.Language.ENGLISH,
                        com.vernai.core.model.Language.MARATHI
                    ).forEach { lang ->
                        FilterChip(
                            selected = state.activeLanguage == lang,
                            onClick = { viewModel.handleIntent(DocReaderUiIntent.ChangeLanguage(lang)) },
                            label = { Text("${lang.nativeName} (${lang.englishName})", fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Extraction Failure Banner (Clear Indication when Extraction Fails)
            if (state.extractionError != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "పత్రం సంగ్రహణ విఫలమైంది (Extraction Failed)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.extractionError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "పరిష్కారం: పత్రం పాస్‌వర్డ్ లేనిదని ధృవీకరించుకోండి లేదా మరింత స్పష్టమైన వెలుతురులో తీసిన ఫోటోను ఎంచుకోండి.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Document Header Card
            if (state.extractedDocument != null) {
                DocumentHeaderCard(state = state)
            }

            // Quality & Limitations Advisory Card (Telugu & English Document Limitations)
            state.qualityReport?.let { quality ->
                if (quality.requiresManualReview) {
                    DocumentLimitationsAdvisoryCard(quality = quality)
                }
            }

            // Structured Form Filling Guidance Card (Scholarship & Civic Applications)
            state.formFillingGuidance?.let { guidance ->
                FormFillingGuidanceCard(guidance = guidance)
            }

            // Full Vernacular Explanation Section
            if (state.isSummarizing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        val loadingText = when (state.activeLanguage) {
                            com.vernai.core.model.Language.TAMIL -> "உள்ளூர் AI எளிய தமிழில் விளக்குகிறது..."
                            com.vernai.core.model.Language.HINDI -> "स्थानीय AI सरल हिंदी में विवरण तैयार कर रहा है..."
                            com.vernai.core.model.Language.MARATHI -> "स्थानिक AI सोप्या मराठीत स्पष्टीकरण देत आहे..."
                            com.vernai.core.model.Language.ENGLISH -> "Local AI is analyzing and explaining the document..."
                            else -> "స్థానిక AI పత్రాన్ని సులభమైన తెలుగులో వివరిస్తోంది..."
                        }
                        Text(
                            text = loadingText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (state.explanationReport != null) {
                ExplanationCard(report = state.explanationReport!!)
            }

            // Optional Expandable Detail Section: Chunks & Raw Text
            if (state.chunks.isNotEmpty() || state.extractedDocument != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedSections = !showAdvancedSections },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "వివరణాత్మక విభాగాలు & మూల వచనం (Detailed Sections)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = { showAdvancedSections = !showAdvancedSections }) {
                                Icon(
                                    imageVector = if (showAdvancedSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle"
                                )
                            }
                        }
                        AnimatedVisibility(visible = showAdvancedSections) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (state.chunks.isNotEmpty()) {
                                    DocumentChunksViewerCard(
                                        state = state,
                                        onSelectChunk = { viewModel.handleIntent(DocReaderUiIntent.SelectChunk(it)) },
                                        onExplainChunk = { viewModel.handleIntent(DocReaderUiIntent.ExplainSelectedChunk(it)) }
                                    )
                                }
                                if (state.isExplainingSnippet || state.selectedSnippetExplanation != null) {
                                    SelectedSnippetExplanationCard(
                                        isLoading = state.isExplainingSnippet,
                                        explanation = state.selectedSnippetExplanation,
                                        selectedChunk = state.selectedChunk,
                                        onDismiss = { viewModel.handleIntent(DocReaderUiIntent.ClearSelectedSnippet) }
                                    )
                                }
                                if (state.extractedDocument != null) {
                                    RawOcrPreviewCard(
                                        state = state,
                                        onToggle = { viewModel.handleIntent(DocReaderUiIntent.ToggleRawText) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DocumentHeaderCard(state: DocReaderUiState) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.extractedDocument?.isScannedImage == true) Icons.AutoMirrored.Filled.Assignment else Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.importedDocumentName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                val typeDesc = if (state.extractedDocument?.isScannedImage == true) "స్కాన్ చేసిన OCR పత్రం" else "డిజిటల్ PDF"
                Text(
                    text = "$typeDesc • ${state.extractedDocument?.rawText?.length ?: 0} అక్షరాలు • ${state.chunks.size} భాగాలు",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentLimitationsAdvisoryCard(quality: DocumentQualityReport) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFFFBEB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "పత్రం గుర్తింపు పరిమితులు & విశ్లేషణ (Document Advisory)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF92400E)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = quality.advisoryTelugu,
                fontSize = 12.sp,
                color = Color(0xFF78350F)
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quality.detectedLimitations.forEach { limitation ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(limitation.titleTelugu, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentChunksViewerCard(
    state: DocReaderUiState,
    onSelectChunk: (DocumentChunk) -> Unit,
    onExplainChunk: (DocumentChunk) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "పత్రం విభాగాలు (Document Chunks / Context)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "${state.chunks.size} భాగాలు",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal row of chunk chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.chunks.forEach { chunk ->
                    val isSelected = state.selectedChunk?.id == chunk.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectChunk(chunk) },
                        label = { Text("భాగం ${chunk.index + 1}/${chunk.totalChunks}", fontSize = 12.sp) }
                    )
                }
            }

            // Selected chunk preview and explain button
            state.selectedChunk?.let { chunk ->
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedCard(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = chunk.headingPreview,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = chunk.text.take(180) + if (chunk.text.length > 180) "..." else "",
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onExplainChunk(chunk) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ఈ భాగాన్ని తెలుగులో వివరించండి (Explain This Section)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedSnippetExplanationCard(
    isLoading: Boolean,
    explanation: String?,
    selectedChunk: DocumentChunk?,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ఎంచుకున్న భాగం వివరణ (Section Explanation)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ఈ నిర్దిష్ట భాగాన్ని విశ్లేషిస్తున్నది...", fontSize = 12.sp)
                }
            } else if (explanation != null) {
                Text(
                    text = explanation,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun RawOcrPreviewCard(state: DocReaderUiState, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "గుర్తించిన మూల వచనం (Raw Extracted Text)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (state.showRawText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle"
                    )
                }
            }
            AnimatedVisibility(visible = state.showRawText) {
                Text(
                    text = state.extractedDocument?.rawText ?: "వచనం లేదు",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ExplanationCard(report: com.vernai.core.model.ExplanationReport) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Summary Block
            Text(
                text = "సులభమైన తెలుగు సారాంశం (Summary)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = report.summaryInVernacular,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp
            )

            // Key Actions Block
            Text(
                text = "ముఖ్యంగా చేయవలసిన పనులు (Action Items)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            report.keyActionPoints.forEach { point ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }

            // Deadlines & Warnings Block
            if (report.legalDeadlines.isNotEmpty()) {
                Text(
                    text = "ముఖ్య గడువులు & రుసుములు (Deadlines & Fees)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFD97706)
                )
                report.legalDeadlines.forEach { deadline ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = deadline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormFillingGuidanceCard(guidance: com.vernai.domain.usecase.FormFillingGuidance) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (guidance.targetLanguage) {
                            com.vernai.core.model.Language.TAMIL -> "படிவம் நிரப்பும் வழிகாட்டி (Form-Filling Guide)"
                            com.vernai.core.model.Language.HINDI -> "आवेदन पत्र भरने की मार्गदर्शिका (Form-Filling Guide)"
                            com.vernai.core.model.Language.MARATHI -> "अर्ज भरण्यासाठी मार्गदर्शन (Form-Filling Guide)"
                            com.vernai.core.model.Language.ENGLISH -> "Form-Filling Guidance (Step-by-Step Instructions)"
                            else -> "దరఖాస్తు పూరించే మార్గదర్శిని (Form-Filling Guide)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = guidance.formTitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Eligibility Criteria
            if (guidance.eligibilityCriteria.isNotEmpty()) {
                Text(
                    text = when (guidance.targetLanguage) {
                        com.vernai.core.model.Language.TAMIL -> "தகுதி வரம்புகள் (Eligibility Criteria):"
                        com.vernai.core.model.Language.HINDI -> "पात्रता मानदंड (Eligibility Criteria):"
                        com.vernai.core.model.Language.MARATHI -> "पात्रता निकष (Eligibility Criteria):"
                        com.vernai.core.model.Language.ENGLISH -> "Eligibility Criteria:"
                        else -> "అర్హత నిబంధనలు (Eligibility Criteria):"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                guidance.eligibilityCriteria.forEach { crit ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = crit, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // Mandatory Documents Checklist
            if (guidance.mandatoryDocuments.isNotEmpty()) {
                Text(
                    text = when (guidance.targetLanguage) {
                        com.vernai.core.model.Language.TAMIL -> "தேவையான சான்றிதழ்கள் (Mandatory Documents):"
                        com.vernai.core.model.Language.HINDI -> "आवश्यक प्रमाण पत्र (Mandatory Documents):"
                        com.vernai.core.model.Language.MARATHI -> "आवश्यक कागदपत्रे (Mandatory Documents):"
                        com.vernai.core.model.Language.ENGLISH -> "Mandatory Documents / Enclosures:"
                        else -> "అవసరమైన ధృవపత్రాలు (Mandatory Documents):"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    guidance.mandatoryDocuments.forEach { doc ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(doc, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Step-by-Step Instructions
            if (guidance.sectionWiseInstructions.isNotEmpty()) {
                Text(
                    text = when (guidance.targetLanguage) {
                        com.vernai.core.model.Language.TAMIL -> "படிவத்தின் பகுதிகள் (Step-by-Step Sections):"
                        com.vernai.core.model.Language.HINDI -> "आवेदन के चरण (Step-by-Step Sections):"
                        com.vernai.core.model.Language.MARATHI -> "अर्जाचे टप्पे (Step-by-Step Sections):"
                        com.vernai.core.model.Language.ENGLISH -> "Step-by-Step Sections:"
                        else -> "దరఖాస్తు దశలు (Step-by-Step Sections):"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                guidance.sectionWiseInstructions.forEachIndexed { idx, sec ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = sec, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // Deadline & Fee Highlight Box
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7).copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = guidance.submissionDeadline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                    }
                    Text(
                        text = guidance.applicationFee,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46)
                    )
                }
            }
        }
    }
}

