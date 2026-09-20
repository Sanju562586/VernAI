package com.vernai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vernai.core.model.Language
import com.vernai.ui.navigation.VernAiNavDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (VernAiNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VernAI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigate(VernAiNavDestination.Settings) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "అమరికలు",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Greeting ───────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val greetingTitle = when (state.activeLanguage) {
                    Language.TAMIL -> "வணக்கம் 👋"
                    Language.HINDI, Language.MARATHI -> "नमस्ते 👋"
                    Language.ENGLISH -> "Welcome 👋"
                    else -> "స్వాగతం 👋"
                }
                val greetingSubtitle = when (state.activeLanguage) {
                    Language.TAMIL -> "உங்கள் மொழியில் பேசுங்கள். வேலை எளிதாக முடியும்."
                    Language.HINDI, Language.MARATHI -> "अपनी भाषा में बोलें। काम आसानी से पूरा होगा।"
                    Language.ENGLISH -> "Speak in your language. Get work done completely offline."
                    else -> "మీ భాషలో మాట్లాడండి. పని పూర్తవుతుంది."
                }
                Text(
                    text = greetingTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = greetingSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Language chips ─────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Language.entries.forEach { lang ->
                        val selected = lang == state.activeLanguage
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.handleIntent(HomeUiIntent.SelectLanguage(lang)) },
                            label = {
                                Text(
                                    text = lang.nativeName,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // ── Section label ─────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val sectionTitle = when (state.activeLanguage) {
                    Language.TAMIL -> "என்ன செய்ய வேண்டும்?"
                    Language.HINDI, Language.MARATHI -> "क्या करना चाहते हैं?"
                    Language.ENGLISH -> "What would you like to do?"
                    else -> "ఏం చేయాలి?"
                }
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // ── Feature tiles ─────────────────────────────────────────
            items(state.features) { feature ->
                FeatureTile(
                    feature = feature,
                    onClick = { onNavigate(feature.destination) }
                )
            }

            // ── History counts ────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HistoryRow(state = state)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature tile — icon + title + one-line description; no technical jargon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FeatureTile(
    feature: FeatureCardItem,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (feature.id) {
        "voice"     -> Icons.Default.RecordVoiceOver
        "sales"     -> Icons.Default.PointOfSale
        "complaint" -> Icons.Default.Mic
        "doc"       -> Icons.Default.Description
        else        -> Icons.Default.Mic
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coloured icon bubble
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.nativeSubtitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History row — three compact counts at the bottom
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HistoryRow(state: HomeUiState) {
    val salesLabel = when (state.activeLanguage) {
        Language.TAMIL -> "விற்பனை"
        Language.HINDI, Language.MARATHI -> "बिक्री"
        Language.ENGLISH -> "Sales"
        else -> "అమ్మకాలు"
    }
    val complaintsLabel = when (state.activeLanguage) {
        Language.TAMIL -> "மனுக்கள்"
        Language.HINDI, Language.MARATHI -> "शिकायतें"
        Language.ENGLISH -> "Complaints"
        else -> "ఫిర్యాదులు"
    }
    val docsLabel = when (state.activeLanguage) {
        Language.TAMIL -> "ஆவணங்கள்"
        Language.HINDI, Language.MARATHI -> "दस्तावेज़"
        Language.ENGLISH -> "Documents"
        else -> "పత్రాలు"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        HistoryStat(count = state.totalSalesRecorded, label = salesLabel)
        VerticalDivider()
        HistoryStat(count = state.totalComplaintsDrafted, label = complaintsLabel)
        VerticalDivider()
        HistoryStat(count = state.totalDocumentsRead, label = docsLabel)
    }
}

@Composable
private fun HistoryStat(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
