package com.vernai.ui.letter

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vernai.document.export.ExportFormat
import com.vernai.domain.model.letter.LetterType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterEditorScreen(
    viewModel: LetterEditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is LetterUiSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is LetterUiSideEffect.ShareExportedFile -> {
                    Toast.makeText(context, "పత్రం సిద్ధమైంది: ${effect.file.name}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("తెలుగు అధికారిక లేఖ (Letter Drafting)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Zero-Hallucination Local Civic AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.handleIntent(LetterUiIntent.SaveLetterDraft) },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save Draft")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            LetterBottomActionBar(
                activeTab = state.activeTab,
                isGenerating = state.isGenerating,
                isRegenerating = state.isRegenerating,
                isSaving = state.isSaving,
                isExporting = state.isExporting,
                onGenerate = { viewModel.handleIntent(LetterUiIntent.GenerateLetter) },
                onRegenerate = { viewModel.handleIntent(LetterUiIntent.RegenerateLetter) },
                onSave = { viewModel.handleIntent(LetterUiIntent.SaveLetterDraft) },
                onExportPdf = { viewModel.handleIntent(LetterUiIntent.ExportDocument(ExportFormat.PDF, context.cacheDir)) },
                onExportDocx = { viewModel.handleIntent(LetterUiIntent.ExportDocument(ExportFormat.DOCX, context.cacheDir)) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 3 Navigation Tabs
            TabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.handleIntent(LetterUiIntent.SwitchTab(0)) },
                    text = { Text("1. వివరాలు (Inputs)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.handleIntent(LetterUiIntent.SwitchTab(1)) },
                    text = { Text("2. తెలుగు లేఖ (Preview)", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = state.activeTab == 2,
                    onClick = { viewModel.handleIntent(LetterUiIntent.SwitchTab(2)) },
                    text = { Text("3. English Copy", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
            }

            // Tab Content
            when (state.activeTab) {
                0 -> LetterInputsTabContent(state = state, viewModel = viewModel)
                1 -> LetterTeluguPreviewTabContent(state = state, viewModel = viewModel)
                2 -> LetterEnglishCopyTabContent(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun LetterInputsTabContent(
    state: LetterUiState,
    viewModel: LetterEditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Privacy & Anti-Hallucination Guarantee Banner
        OutlinedCard(
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("100% ఆఫ్‌లైన్ & వాస్తవాల రక్షణ (Zero-Hallucination)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("మీరు అందించిన వాస్తవాలనే యథాతథంగా ఉపయోగిస్తుంది. పేర్లు, తేదీలు ఊహించబడవు.", fontSize = 11.sp)
                }
            }
        }

        // Quick Load Sample Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("లేఖ రకం (Letter Type)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedButton(
                onClick = { viewModel.handleIntent(LetterUiIntent.LoadSampleFacts) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("నమూనా లోడ్ చేయండి", fontSize = 12.sp)
            }
        }

        // Letter Type Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LetterType.entries.forEach { type ->
                FilterChip(
                    selected = state.letterType == type,
                    onClick = { viewModel.handleIntent(LetterUiIntent.UpdateLetterType(type)) },
                    label = { Text(type.teluguTitle, fontSize = 12.sp) }
                )
            }
        }

        // Voice Transcript / Spoken Issue
        OutlinedTextField(
            value = state.voiceTranscript,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateVoiceTranscript(it)) },
            label = { Text("వాయిస్ ట్రాన్స్‌క్రిప్ట్ / సమస్య వివరణ (Voice Transcript)") },
            leadingIcon = { Icon(imageVector = Icons.Default.Mic, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(12.dp)
        )

        // User Provided Facts Editor
        OutlinedTextField(
            value = state.userFactsText,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateUserFacts(it)) },
            label = { Text("వాస్తవాలు & ఆధారాలు (User-Provided Facts - Newline separated)") },
            supportingText = { Text("ప్రతి వాస్తవాన్ని ప్రత్యేక లైన్‌లో నమోదు చేయండి. మోడల్ ఈ వాస్తవాలను తప్పక భద్రపరుస్తుంది.") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        // Recipient Section
        Text("స్వీకర్త వివరాలు (Recipient Details)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value = state.recipientDesignation,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateRecipientDesignation(it)) },
            label = { Text("అధికారి హోదా (Recipient Designation)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.recipientDepartment,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateRecipientDepartment(it)) },
            label = { Text("శాఖ లేదా కార్యాలయం (Department / Office)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.recipientAddress,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateRecipientAddress(it)) },
            label = { Text("కార్యాలయ చిరునామా (Office Address - Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Metadata: Location & Date
        Text("ప్రదేశం మరియు తేదీ (Location & Date - Optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.location,
                onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateLocation(it)) },
                label = { Text("గ్రామం / పట్టణం (Location)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.date,
                onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateDate(it)) },
                label = { Text("తేదీ (Date: DD-MM-YYYY)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Applicant Name
        OutlinedTextField(
            value = state.applicantName,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateApplicantName(it)) },
            label = { Text("దరఖాస్తుదారుడి పేరు / గ్రామం (Applicant Name - Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Generate Action Button
        Button(
            onClick = { viewModel.handleIntent(LetterUiIntent.GenerateLetter) },
            enabled = !state.isGenerating,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("విశ్లేషిస్తూ లేఖ రచిస్తున్నది...")
            } else {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("వినతిపత్రం రూపొందించండి (Generate Formal Letter)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterTeluguPreviewTabContent(
    state: LetterUiState,
    viewModel: LetterEditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Preserved Facts Verification Card
        if (state.preservedFacts.isNotEmpty()) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ధృవీకరించిన వాస్తవాలు (Preserved User Facts)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.preservedFacts.forEach { fact ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(fact, fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        // Editable Subject
        OutlinedTextField(
            value = state.subject,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSubject(it)) },
            label = { Text("విషయము (Subject Line)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Editable Salutation
        OutlinedTextField(
            value = state.salutation,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSalutation(it)) },
            label = { Text("సంబోధన (Salutation)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Full Editable Formal Telugu Letter Body
        OutlinedTextField(
            value = state.vernacularBody,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateVernacularBody(it)) },
            label = { Text("తెలుగు అధికారిక లేఖ ముఖ్య భాగం (Full Editable Telugu Letter)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
        )

        // Editable Closing Statement
        OutlinedTextField(
            value = state.closing,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateClosing(it)) },
            label = { Text("ముగింపు (Formal Closing)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Editable Signature Placeholders
        OutlinedTextField(
            value = state.signaturePlaceholder,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSignaturePlaceholder(it)) },
            label = { Text("సంతకం మరియు దరఖాస్తుదారుడి వివరాలు (Signature Placeholders)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LetterEnglishCopyTabContent(
    state: LetterUiState,
    viewModel: LetterEditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "అధికారిక ఆంగ్ల అనువాదం (Formal English Translation)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        OutlinedTextField(
            value = state.englishTranslation,
            onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateEnglishTranslation(it)) },
            label = { Text("Formal English Translation (Editable)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LetterBottomActionBar(
    activeTab: Int,
    isGenerating: Boolean,
    isRegenerating: Boolean,
    isSaving: Boolean,
    isExporting: Boolean,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onExportPdf: () -> Unit,
    onExportDocx: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeTab == 0) {
                Button(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("రూపొందించండి (Generate)")
                }
            } else {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !isRegenerating,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRegenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("తిరిగి రాయండి (Regenerate)")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onSave,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("సేవ్")
                }

                FilledTonalButton(
                    onClick = onExportDocx,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("DOCX")
                }

                Button(
                    onClick = onExportPdf,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF")
                }
            }
        }
    }
}
