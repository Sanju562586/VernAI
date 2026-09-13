package com.vernai.ui.sales

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesValidationStatus
import com.vernai.document.export.ExportFormat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesLogScreen(
    viewModel: SalesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<SalesItem?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is SalesUiSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is SalesUiSideEffect.ExportCompleted -> {
                    val formatLabel = when (effect.format) {
                        ExportFormat.CSV -> "CSV"
                        ExportFormat.XLSX -> "Excel (XLSX)"
                        ExportFormat.PDF -> "PDF"
                        ExportFormat.DOCX -> "Word (DOCX)"
                    }
                    Toast.makeText(context, "$formatLabel లోనికి ఎగుమతి పూర్తయింది: ${effect.exportedFile.name}", Toast.LENGTH_LONG).show()
                }
                is SalesUiSideEffect.RequestAudioPermission -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("అమ్మకాల లెడ్జర్ (Sales Ledger)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Deterministic Telugu Voice-to-Ledger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.handleIntent(SalesUiIntent.SaveLogToLedger) }) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save to Room DB")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            state.currentLog?.let { log ->
                Card(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "మొత్తం అమ్మకాలు (Grand Total)", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", log.grandTotal)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box {
                            Button(
                                onClick = { showExportMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ఎగుమతి (Export)")
                            }

                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("CSV ఎగుమతి (.csv - Excel/Sheets UTF-8)") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                    onClick = {
                                        showExportMenu = false
                                        val destination = File(context.cacheDir, "Sales_Ledger_${System.currentTimeMillis()}.csv")
                                        viewModel.handleIntent(SalesUiIntent.ExportLedger(destination, ExportFormat.CSV))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Excel ఎగుమతి (.xlsx / .xml - Native Formulas)") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                    onClick = {
                                        showExportMenu = false
                                        val destination = File(context.cacheDir, "Sales_Ledger_${System.currentTimeMillis()}.xlsx")
                                        viewModel.handleIntent(SalesUiIntent.ExportLedger(destination, ExportFormat.XLSX))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF పత్రం ఎగుమతి (.pdf)") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showExportMenu = false
                                        val destination = File(context.cacheDir, "Sales_Ledger_${System.currentTimeMillis()}.pdf")
                                        viewModel.handleIntent(SalesUiIntent.ExportLedger(destination, ExportFormat.PDF))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Voice Input & Live ASR bar
            item {
                Spacer(modifier = Modifier.height(4.dp))
                VoiceInputBar(
                    isRecording = state.isRecording,
                    isProcessing = state.isProcessing,
                    spokenTranscript = state.spokenTranscript,
                    onToggleRecord = { viewModel.handleIntent(SalesUiIntent.ToggleRecording) }
                )
            }

            // Sample Dictations for quick testing
            item {
                SampleDictationsRow(onSelectSample = { sample ->
                    viewModel.handleIntent(SalesUiIntent.SubmitManualTranscript(sample))
                })
            }

            // High-visibility Clarification Banner (when ambiguity or arithmetic discrepancy is found)
            state.activeClarificationItem?.let { ambiguousItem ->
                item {
                    ClarificationCard(
                        item = ambiguousItem,
                        onResolve = { qty, unitPrice, total, notes ->
                            viewModel.handleIntent(
                                SalesUiIntent.ResolveClarification(
                                    itemId = ambiguousItem.id,
                                    resolvedQuantity = qty,
                                    resolvedUnitPrice = unitPrice,
                                    resolvedTotal = total,
                                    resolvedNotes = notes
                                )
                            )
                        },
                        onDismiss = {
                            viewModel.handleIntent(SalesUiIntent.SelectClarificationItem(null))
                        }
                    )
                }
            }

            // Itemized Ledger Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "వస్తువుల జాబితా (Itemized Ledger)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "తేదీ, వస్తువు, కొలత, ధర మరియు గమనికలు",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showAddItemDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                    }
                }
            }

            // Ledger Rows
            val items = state.currentLog?.items ?: emptyList()
            if (items.isEmpty()) {
                item {
                    EmptySalesLedgerState()
                }
            } else {
                items(items, key = { it.id }) { item ->
                    SalesItemRowCard(
                        item = item,
                        onEdit = { itemToEdit = item },
                        onDelete = { viewModel.handleIntent(SalesUiIntent.DeleteItem(item.id)) },
                        onClarify = { viewModel.handleIntent(SalesUiIntent.SelectClarificationItem(item)) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialog for adding a new item manually
    if (showAddItemDialog) {
        EditSalesItemDialog(
            title = "కొత్త వస్తువును చేర్చండి (Add Item)",
            initialItem = SalesItem(
                id = UUID.randomUUID().toString(),
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                originalTerm = "",
                standardName = "",
                quantity = 1.0,
                unit = "కేజీ (kg)",
                unitPrice = 0.0,
                totalPrice = 0.0,
                notes = null
            ),
            onDismiss = { showAddItemDialog = false },
            onSave = { newItem ->
                viewModel.handleIntent(SalesUiIntent.AddNewItem(newItem))
                showAddItemDialog = false
            }
        )
    }

    // Dialog for editing an existing item
    itemToEdit?.let { existingItem ->
        EditSalesItemDialog(
            title = "వస్తువు వివరాలను సవరించండి (Edit Item)",
            initialItem = existingItem,
            onDismiss = { itemToEdit = null },
            onSave = { updatedItem ->
                viewModel.handleIntent(SalesUiIntent.UpdateItem(updatedItem))
                itemToEdit = null
            }
        )
    }
}

@Composable
fun ClarificationCard(
    item: SalesItem,
    onResolve: (Double?, Double?, Double?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFFFEF3C7) // Amber alert container
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "స్పష్టత అవసరం (Clarification Needed)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val promptText = item.clarificationPrompt
                ?: "${item.originalTerm} గురించి పూర్తి వివరాలు లేవు. దయచేసి స్పష్టం చేయండి."

            Text(
                text = promptText,
                color = Color(0xFF78350F),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Resolution Options depending on condition
            if (item.validationStatus == SalesValidationStatus.ARITHMETIC_MISMATCH) {
                val expected = item.quantity * item.unitPrice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onResolve(null, null, expected, null) },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFDE68A))
                    ) {
                        Text("₹${String.format(Locale.US, "%.2f", expected)} గా సరిచేయి")
                    }
                    OutlinedButton(
                        onClick = { onResolve(null, null, item.totalPrice, null) }
                    ) {
                        Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)} ఉంచు")
                    }
                }
            } else if (item.quantity <= 0.0) {
                // Ambiguous quantity chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1.0 to "1 కేజీ", 2.0 to "2 కేజీలు", 5.0 to "5 కేజీలు", 10.0 to "10 కేజీలు").forEach { (q, label) ->
                        SuggestionChip(
                            onClick = { onResolve(q, null, null, null) },
                            label = { Text(label) }
                        )
                    }
                }
            } else if (item.unitPrice <= 0.0 && item.totalPrice <= 0.0) {
                // Ambiguous price
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50.0 to "₹50", 100.0 to "₹100", 200.0 to "₹200", 500.0 to "₹500").forEach { (pr, label) ->
                        SuggestionChip(
                            onClick = { onResolve(null, null, pr, null) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("దాటవేయి (Dismiss)", color = Color(0xFF92400E))
                }
            }
        }
    }
}

@Composable
fun SampleDictationsRow(onSelectSample: (String) -> Unit) {
    Column {
        Text(
            text = "నమూనా వాక్యాలు (Quick Voice Samples):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { onSelectSample("ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు") },
                label = { Text("5 కేజీల టమాటా 200") }
            )
            SuggestionChip(
                onClick = { onSelectSample("2 నూనె ప్యాకెట్లు 260 రూపాయలు నగదు") },
                label = { Text("2 నూనె ప్యాకెట్లు 260 నగదు") }
            )
            SuggestionChip(
                onClick = { onSelectSample("10 కేజీల బియ్యం కేజీ 40 రూపాయలు మొత్తం 350") },
                label = { Text("బియ్యం లెక్క తేడా (Discrepancy)") }
            )
            SuggestionChip(
                onClick = { onSelectSample("టమాటా 150 రూపాయలు") },
                label = { Text("టమాటా (పరిమాణం అడగాలి)") }
            )
            SuggestionChip(
                onClick = { onSelectSample("1 డజన్ సబ్బులు 120 రూపాయలు రమేష్ కి అరువు") },
                label = { Text("సబ్బులు అరువు (Credit)") }
            )
        }
    }
}

@Composable
fun SalesItemRowCard(
    item: SalesItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClarify: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Date, Item Name, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.originalTerm,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (item.standardName.isNotBlank() && item.standardName != item.originalTerm) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${item.standardName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "తేదీ: ${item.date.ifBlank { "ఈరోజు" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                ValidationBadge(status = item.validationStatus, onClick = onClarify)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Middle Row: Quantity, Unit Price, Total Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${item.quantity} ${item.unit} @ ₹${String.format(Locale.US, "%.2f", item.unitPrice)}/${item.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    item.notes?.let { notes ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "గమనిక: $notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "₹${String.format(Locale.US, "%.2f", item.totalPrice)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Discrepancy or Clarification prompt text
            if (item.validationStatus == SalesValidationStatus.ARITHMETIC_MISMATCH ||
                item.validationStatus == SalesValidationStatus.CLARIFICATION_NEEDED ||
                item.validationStatus == SalesValidationStatus.DUPLICATE_WARNING) {
                item.clarificationPrompt?.let { prompt ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ $prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: Edit, Delete, Clarify
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.validationStatus != SalesValidationStatus.VERIFIED) {
                    TextButton(onClick = onClarify) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("సవరించండి")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Item",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ValidationBadge(status: SalesValidationStatus, onClick: () -> Unit) {
    val (bgColor, textColor, icon, label) = when (status) {
        SalesValidationStatus.VERIFIED -> {
            Tuple4(Color(0xFFDCFCE7), Color(0xFF166534), Icons.Default.CheckCircle, "ధృవీకరించబడింది")
        }
        SalesValidationStatus.ARITHMETIC_MISMATCH -> {
            Tuple4(Color(0xFFFEF3C7), Color(0xFF92400E), Icons.Default.WarningAmber, "లెక్క తేడా")
        }
        SalesValidationStatus.CLARIFICATION_NEEDED -> {
            Tuple4(Color(0xFFFFEDD5), Color(0xFFC2410C), Icons.Default.HelpOutline, "వివరణ అవసరం")
        }
        SalesValidationStatus.DUPLICATE_WARNING -> {
            Tuple4(Color(0xFFF3E8FF), Color(0xFF6B21A8), Icons.Default.ContentCopy, "డూప్లికేట్")
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun EditSalesItemDialog(
    title: String,
    initialItem: SalesItem,
    onDismiss: () -> Unit,
    onSave: (SalesItem) -> Unit
) {
    var date by remember { mutableStateOf(initialItem.date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }) }
    var name by remember { mutableStateOf(initialItem.originalTerm) }
    var qty by remember { mutableStateOf(if (initialItem.quantity > 0) initialItem.quantity.toString() else "1.0") }
    var unit by remember { mutableStateOf(initialItem.unit) }
    var unitPrice by remember { mutableStateOf(if (initialItem.unitPrice > 0) initialItem.unitPrice.toString() else "0.0") }
    var totalPrice by remember { mutableStateOf(if (initialItem.totalPrice > 0) initialItem.totalPrice.toString() else "0.0") }
    var notes by remember { mutableStateOf(initialItem.notes ?: "") }

    // Live auto-calculation indicator
    fun recalculateTotal(newQtyStr: String, newUnitPriceStr: String) {
        val q = newQtyStr.toDoubleOrNull() ?: 0.0
        val up = newUnitPriceStr.toDoubleOrNull() ?: 0.0
        if (q > 0 && up > 0) {
            totalPrice = String.format(Locale.US, "%.2f", q * up)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("తేదీ (Date: YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("వస్తువు పేరు (Item Name)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = {
                            qty = it
                            recalculateTotal(it, unitPrice)
                        },
                        label = { Text("పరిమాణం (Qty)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("కొలత (Unit)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitPrice,
                        onValueChange = {
                            unitPrice = it
                            recalculateTotal(qty, it)
                        },
                        label = { Text("ధర (Unit Price)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = totalPrice,
                        onValueChange = { totalPrice = it },
                        label = { Text("మొత్తం (Total Price)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("గమనికలు (Notes: ఉదా: నగదు, అరువు)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("రద్దు (Cancel)")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val parsedQty = qty.toDoubleOrNull() ?: 1.0
                        val parsedUnitPrice = unitPrice.toDoubleOrNull() ?: 0.0
                        val parsedTotal = totalPrice.toDoubleOrNull() ?: (parsedQty * parsedUnitPrice)

                        onSave(
                            initialItem.copy(
                                date = date,
                                originalTerm = name.ifBlank { "వస్తువు" },
                                standardName = name,
                                quantity = parsedQty,
                                unit = unit.ifBlank { "unit" },
                                unitPrice = parsedUnitPrice,
                                totalPrice = parsedTotal,
                                notes = notes.ifBlank { null }
                            )
                        )
                    }) {
                        Text("భద్రపరచు (Save)")
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceInputBar(
    isRecording: Boolean,
    isProcessing: Boolean,
    spokenTranscript: String,
    onToggleRecord: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleRecord,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("సమాచారాన్ని సంగ్రహిస్తోంది...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (spokenTranscript.isNotBlank()) {
                    Text(text = spokenTranscript, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                } else {
                    Text(
                        text = "అమ్మకాలను మాట్లాడండి (ఉదా: 5 కేజీల టమాటా 200)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySalesLedgerState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "ఇంకా అమ్మకాలు నమోదు కాలేదు",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "మైక్ నొక్కి రోజూవారీ అమ్మకాలను చెప్పండి లేదా పైన '+' నొక్కి జోడించండి.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
