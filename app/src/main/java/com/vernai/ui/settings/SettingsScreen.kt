package com.vernai.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.vernai.ai.llm.benchmark.ExecutionBackend
import com.vernai.core.model.Language
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAdvancedHardware by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is SettingsUiSideEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SettingsUiSideEffect.ShareReport -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "VernAI Snapdragon LLM Benchmark Report")
                        putExtra(Intent.EXTRA_TEXT, effect.markdown)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Benchmark Report"))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val screenTitle = when (state.selectedLanguage) {
                        Language.TAMIL -> "அமைப்புகள் & தனியுரிமை (Settings)"
                        Language.HINDI, Language.MARATHI -> "सेटिंग्स और गोपनीयता (Settings)"
                        Language.ENGLISH -> "Settings & Privacy"
                        else -> "అమరికలు & భద్రత (Settings)"
                    }
                    Text(screenTitle, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Language Preference
            item {
                Spacer(modifier = Modifier.height(4.dp))
                val langHeader = when (state.selectedLanguage) {
                    Language.TAMIL -> "முதன்மை மொழி (Choose Language)"
                    Language.HINDI, Language.MARATHI -> "प्राथमिक भाषा (Choose Language)"
                    Language.ENGLISH -> "Choose App Language"
                    else -> "భాష ఎంచుకోండి (Choose Language)"
                }
                Text(
                    text = langHeader,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Language.TELUGU, Language.TAMIL, Language.HINDI, Language.ENGLISH).forEach { lang ->
                        val isSelected = lang == state.selectedLanguage
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.handleIntent(SettingsUiIntent.ChangeLanguage(lang)) },
                            label = {
                                Text("${lang.nativeName} (${lang.englishName})")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // 2. 100% Offline & Privacy Assurance Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16A34A).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "100% ఆఫ్‌లైన్ & పూర్తి గోప్యత (Zero Cloud)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF166534)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "మీ డేటా, మాట్లాడిన మాటలు మరియు పత్రాలు మీ ఫోన్‌లోనే ఉంటాయి. ఇంటర్నెట్ అవసరం లేదు.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF15803D),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 3. On-Device AI Models
            item {
                Text(
                    text = "స్థానిక AI ఇంజిన్లు (On-Device AI)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.models) { model ->
                ModelStatusCard(model = model)
            }

            // 4. Advanced Hardware & Benchmark (Collapsed by default)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedHardware = !showAdvancedHardware },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "హార్డ్‌వేర్ & బెంచ్‌మార్క్ (Advanced)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Snapdragon మెమరీ బడ్జెట్ మరియు పనితీరు",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { showAdvancedHardware = !showAdvancedHardware }) {
                                Icon(
                                    imageVector = if (showAdvancedHardware) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle"
                                )
                            }
                        }

                        AnimatedVisibility(visible = showAdvancedHardware) {
                            Column(
                                modifier = Modifier.padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                MemoryBudgetCard(state = state)
                                HardwareSettingsCard(state = state, viewModel = viewModel)
                                SnapdragonBenchmarkCard(state = state, viewModel = viewModel)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MemoryBudgetCard(state: SettingsUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Snapdragon Memory Budget",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val progress = (state.maxModelRamBudgetMb.toFloat() / state.availableRamMb.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Model Budget: ${state.maxModelRamBudgetMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Available RAM: ${state.availableRamMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HardwareSettingsCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Compute & Hardware Acceleration",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Adreno GPU / Vulkan", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Accelerate GGUF tensor ops via Vulkan backend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.useVulkanAcceleration,
                    onCheckedChange = { viewModel.handleIntent(SettingsUiIntent.ToggleVulkan(it)) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Kryo Performance Threads", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Dedicated NDK worker threads: ${state.threadCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ModelStatusCard(model: ModelStatusItem) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF059669).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = model.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Disk: ${model.sizeOnDisk} • RAM: ${model.memoryFootprint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnapdragonBenchmarkCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Snapdragon LLM Benchmark",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Empirically measure TTFT, decode throughput (tokens/sec), peak RAM, and thermals on target hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Execution Backend:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExecutionBackend.entries.forEach { backend ->
                    val isSelected = state.selectedBackend == backend
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.handleIntent(SettingsUiIntent.SelectBackend(backend)) },
                        label = {
                            Text(
                                text = when (backend) {
                                    ExecutionBackend.CPU_NEON -> "CPU (NEON)"
                                    ExecutionBackend.GPU_VULKAN -> "GPU (Vulkan)"
                                    ExecutionBackend.NPU_QUALCOMM_QNN -> "NPU (QNN)"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }

            if (state.selectedBackend == ExecutionBackend.NPU_QUALCOMM_QNN) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚠️ Qualcomm Hexagon HTP requires pre-compiled QNN serialized context binaries. Direct GGUF execution will use CPU NEON fallback.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { viewModel.handleIntent(SettingsUiIntent.RunLlmBenchmark) },
                enabled = !state.isBenchmarking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.isBenchmarking) {
                    Text(text = state.benchmarkStatus.ifEmpty { "Benchmarking..." })
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Run Snapdragon Benchmark")
                }
            }

            state.benchmarkResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Decode Throughput:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", result.tokensPerSecond)} tokens/sec",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Time to First Token (TTFT):", style = MaterialTheme.typography.bodySmall)
                            Text(text = "${result.timeToFirstTokenMs} ms", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Peak RAM Footprint:", style = MaterialTheme.typography.bodySmall)
                            Text(text = "${result.peakRamMb} MB", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Thermal Behavior:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${result.startTelemetry.batteryTempCelsius}°C -> ${result.endTelemetry.batteryTempCelsius}°C (${if (result.thermalDeltaCelsius >= 0) "+${result.thermalDeltaCelsius}" else "${result.thermalDeltaCelsius}"}°C)",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.handleIntent(SettingsUiIntent.ExportBenchmarkReport) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Export Benchmark Report (.md)")
                        }
                    }
                }
            }
        }
    }
}

