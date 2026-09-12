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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
                title = { Text("Letter Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.handleIntent(LetterUiIntent.SaveLetterDraft) }) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            LetterBottomActionBar(
                isRegenerating = state.isRegenerating,
                isExporting = state.isExporting,
                onRegenerate = { viewModel.handleIntent(LetterUiIntent.RegenerateLetter) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Department Input
            OutlinedTextField(
                value = state.department,
                onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateDepartment(it)) },
                label = { Text("చిరునామా / శాఖ (Recipient Department)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Subject Input
            OutlinedTextField(
                value = state.subject,
                onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateSubject(it)) },
                label = { Text("విషయం (Subject)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Tab Selector: Telugu vs English
            TabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.handleIntent(LetterUiIntent.SwitchTab(0)) },
                    text = { Text("తెలుగు లేఖ (Telugu Draft)", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.handleIntent(LetterUiIntent.SwitchTab(1)) },
                    text = { Text("English Copy", fontWeight = FontWeight.Bold) }
                )
            }

            // Editable Letter Body
            if (state.activeTab == 0) {
                OutlinedTextField(
                    value = state.vernacularBody,
                    onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateVernacularBody(it)) },
                    label = { Text("లేఖ ముఖ్య భాగం (Editable Telugu Body)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
                )
            } else {
                OutlinedTextField(
                    value = state.englishTranslation,
                    onValueChange = { viewModel.handleIntent(LetterUiIntent.UpdateEnglishTranslation(it)) },
                    label = { Text("Formal English Translation") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LetterBottomActionBar(
    isRegenerating: Boolean,
    isExporting: Boolean,
    onRegenerate: () -> Unit,
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
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRegenerate,
                enabled = !isRegenerating
            ) {
                if (isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("తిరిగి రాయండి")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onExportDocx,
                    enabled = !isExporting
                ) {
                    Text("DOCX")
                }
                Button(
                    onClick = onExportPdf,
                    enabled = !isExporting
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF Export")
                }
            }
        }
    }
}
