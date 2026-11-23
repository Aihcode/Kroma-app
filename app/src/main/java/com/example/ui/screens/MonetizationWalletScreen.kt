package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PayoutRecord
import com.example.data.model.PayoutStatus
import com.example.data.model.RevenueSourceType
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.formatCentsUsd
import com.example.ui.components.ExpressiveOrganicLoader
import com.example.ui.components.ExpressiveSurfaceCard
import com.example.ui.components.MorphingPillButton
import com.example.ui.theme.LocalKromaAccents
import com.example.ui.theme.OrganicHeroShape
import com.example.ui.theme.PillShape
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MonetizationWalletScreen(
    viewModel: StudioViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalKromaAccents.current
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val txState by viewModel.transactions.collectAsStateWithLifecycle()
    val payoutsState by viewModel.payouts.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var showPayoutSheet by remember { mutableStateOf(false) }
    var showEditPayPal by remember { mutableStateOf(false) }

    val profile = (profileState as? UiState.Success)?.data
    val transactions = (txState as? UiState.Success)?.data.orEmpty()
    val payouts = (payoutsState as? UiState.Success)?.data.orEmpty()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Creator Profile & Sign Out Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (!profile?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile?.avatarUrl,
                                contentDescription = profile?.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_creator_avatar),
                                contentDescription = "Avatar del creador",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape),
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = profile?.displayName ?: "Creador Kroma",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                if (profile?.verifiedCreator == true) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Creador verificado",
                                        tint = accents.cobalt,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = profile?.handle ?: "@creator",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = PillShape,
                        modifier = Modifier.testTag("sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.sign_out),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }

            // Hero Expressive Balance Card
            item {
                val availableCents = profile?.availableBalanceCents ?: 0L
                val pendingCents = profile?.pendingBalanceCents ?: 0L
                val totalCents = profile?.totalEarnedCents ?: 0L
                val currency = profile?.preferredPayoutCurrency ?: "USD"
                val thresholdProgress = (availableCents / 1000f).coerceIn(0f, 1f)

                Surface(
                    shape = OrganicHeroShape,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF3E1F9E),
                                        Color(0xFF1A3A8A),
                                        Color(0xFF421849),
                                    )
                                )
                            )
                            .padding(22.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.available_balance_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.82f),
                                )
                                Surface(
                                    shape = PillShape,
                                    color = Color.White.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = currency,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Text(
                                text = formatCentsUsd(availableCents, currency),
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                modifier = Modifier.testTag("available_balance_text"),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = stringResource(R.string.pending_balance_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.72f),
                                        )
                                        Text(
                                            text = formatCentsUsd(pendingCents, currency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                        )
                                    }
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = stringResource(R.string.total_earned_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.72f),
                                        )
                                        Text(
                                            text = formatCentsUsd(totalCents, currency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = accents.emerald,
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(R.string.min_payout_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Text(
                                        text = "${(thresholdProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.emerald,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { thresholdProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(PillShape),
                                    color = accents.emerald,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                )
                            }

                            MorphingPillButton(
                                text = stringResource(R.string.withdraw_paypal_cta),
                                icon = Icons.Filled.Payments,
                                enabled = availableCents >= 1000L && !profile?.paypalEmail.isNullOrBlank(),
                                isLoading = feedback.isBusy,
                                containerColor = Color.White,
                                contentColor = Color(0xFF1D0061),
                                testTag = "open_payout_modal_button",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showPayoutSheet = true },
                            )
                        }
                    }
                }
            }

            // Linked PayPal Account Card
            item {
                PayPalSettingsCard(
                    profile = profile,
                    isEditing = showEditPayPal,
                    onToggleEdit = { showEditPayPal = !showEditPayPal },
                    onSave = { name, handle, email, currency ->
                        viewModel.updatePayPalAccount(name, handle, email, currency)
                        showEditPayPal = false
                    },
                )
            }

            // Revenue Breakdown Chart (Real Firestore Transactions)
            item {
                RevenueBreakdownChartCard(transactions = transactions)
            }

            // PayPal Payouts History
            item {
                Text(
                    text = "Historial de Retiros PayPal",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (payouts.isEmpty()) {
                item {
                    ExpressiveSurfaceCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = accents.cobalt,
                            )
                            Text(
                                text = "Aún no has solicitado retiros. Al superar $10.00 USD disponibles podrás transferir directo a tu PayPal.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(payouts, key = { it.id }) { payout ->
                    PayoutReceiptCard(payout = payout)
                }
            }

            // Recent Ledger Transactions
            item {
                Text(
                    text = "Movimientos de Ingresos",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (transactions.isEmpty()) {
                item {
                    ExpressiveSurfaceCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = accents.violet,
                            )
                            Text(
                                text = "Los ingresos verificados por reproducciones, desbloqueos PPV y regalos en vivo aparecerán aquí en tiempo real.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    ExpressiveSurfaceCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${tx.parsedSourceType.name} · Comisión 30%: ${formatCentsUsd(tx.platformFeeCents)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "+${formatCentsUsd(tx.netAmountCents)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.parsedSourceType == RevenueSourceType.PAYOUT_DEBIT) {
                                    accents.coral
                                } else {
                                    accents.emerald
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPayoutSheet && profile != null) {
        PayPalPayoutBottomSheet(
            profile = profile,
            isBusy = feedback.isBusy,
            onDismiss = { showPayoutSheet = false },
            onConfirmPayout = { amountDollars, email ->
                viewModel.requestPayPalPayout(
                    amountDollarsInput = amountDollars,
                    paypalEmail = email,
                    currency = profile.preferredPayoutCurrency,
                )
                showPayoutSheet = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayPalSettingsCard(
    profile: UserProfile?,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    val accents = LocalKromaAccents.current
    var name by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "") }
    var handle by remember(profile?.handle) { mutableStateOf(profile?.handle ?: "@creator") }
    var email by remember(profile?.paypalEmail) { mutableStateOf(profile?.paypalEmail ?: "") }
    var currency by remember(profile?.preferredPayoutCurrency) {
        mutableStateOf(profile?.preferredPayoutCurrency ?: "USD")
    }

    ExpressiveSurfaceCard(
        borderColor = accents.cobalt.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuenta PayPal Vinculada",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = profile?.paypalEmail ?: "Sin correo PayPal configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (profile?.paypalEmail.isNullOrBlank()) {
                            accents.coral
                        } else {
                            accents.emerald
                        },
                    )
                }
                IconButton(
                    onClick = onToggleEdit,
                    modifier = Modifier.testTag("edit_paypal_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar PayPal",
                        tint = accents.cobalt,
                    )
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del creador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Handle (@usuario)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico de PayPal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paypal_email_input"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("USD", "EUR", "MXN").forEach { code ->
                        FilterChip(
                            selected = currency == code,
                            onClick = { currency = code },
                            label = { Text(code) },
                        )
                    }
                }
                MorphingPillButton(
                    text = "Guardar Configuración",
                    icon = Icons.Filled.CheckCircle,
                    containerColor = accents.cobalt,
                    contentColor = Color.White,
                    testTag = "save_paypal_button",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSave(name, handle, email, currency) },
                )
            }
        }
    }
}

@Composable
private fun RevenueBreakdownChartCard(transactions: List<TransactionRecord>) {
    val accents = LocalKromaAccents.current
    val videoTotal = transactions
        .filter { it.parsedSourceType == RevenueSourceType.VIDEO_VIEWS }
        .sumOf { it.netAmountCents }
    val unlockTotal = transactions
        .filter { it.parsedSourceType == RevenueSourceType.EXCLUSIVE_UNLOCK }
        .sumOf { it.netAmountCents }
    val liveTotal = transactions
        .filter { it.parsedSourceType == RevenueSourceType.LIVE_GIFT }
        .sumOf { it.netAmountCents }

    val maxVal = maxOf(videoTotal, unlockTotal, liveTotal, 1L).toFloat()

    ExpressiveSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Desglose de Ingresos por Fuente",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Custom Canvas expressive pill bars
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            ) {
                val barCount = 3
                val spacing = 24.dp.toPx()
                val barWidth = (size.width - spacing * (barCount - 1)) / barCount
                val values = listOf(
                    videoTotal to accents.violet,
                    unlockTotal to accents.cobalt,
                    liveTotal to accents.coral,
                )
                values.forEachIndexed { index, (cents, color) ->
                    val ratio = if (cents == 0L) 0.12f else (cents / maxVal).coerceIn(0.14f, 1f)
                    val barHeight = size.height * ratio
                    val x = index * (barWidth + spacing)
                    val y = size.height - barHeight
                    // Track
                    drawRoundRect(
                        color = color.copy(alpha = 0.12f),
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(24f, 24f),
                    )
                    // Active bar
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(24f, 24f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChartLegendMetric("Videos", formatCentsUsd(videoTotal), accents.violet)
                ChartLegendMetric("Exclusivos", formatCentsUsd(unlockTotal), accents.cobalt)
                ChartLegendMetric("Lives", formatCentsUsd(liveTotal), accents.coral)
            }
        }
    }
}

@Composable
private fun ChartLegendMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PayoutReceiptCard(payout: PayoutRecord) {
    val accents = LocalKromaAccents.current
    val statusColor = when (payout.parsedStatus) {
        PayoutStatus.SUCCESS -> accents.emerald
        PayoutStatus.PENDING, PayoutStatus.REQUESTED -> accents.amber
        PayoutStatus.FAILED -> accents.coral
    }
    ExpressiveSurfaceCard(
        borderColor = statusColor.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatCentsUsd(payout.amountCents, payout.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "PayPal: ${payout.paypalEmail}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Batch: ${payout.payoutBatchId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = PillShape,
                color = statusColor.copy(alpha = 0.16f),
            ) {
                Text(
                    text = payout.parsedStatus.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayPalPayoutBottomSheet(
    profile: UserProfile,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmPayout: (amountDollars: String, email: String) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val accents = LocalKromaAccents.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxDollars = "%.2f".format(profile.availableBalanceCents / 100.0)
    var amountInput by remember { mutableStateOf(maxDollars) }
    var emailInput by remember { mutableStateOf(profile.paypalEmail ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Solicitar Retiro PayPal",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "La Cloud Function verificará tu saldo mediante transacción atómica en Firestore y ejecutará la transferencia mediante PayPal Payouts API.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Correo PayPal receptor") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Monto a retirar (${profile.preferredPayoutCurrency})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payout_amount_input"),
            )

            MorphingPillButton(
                text = "Confirmar Retiro",
                icon = Icons.Filled.Payments,
                isLoading = isBusy,
                containerColor = accents.emerald,
                contentColor = Color(0xFF002417),
                testTag = "confirm_payout_button",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onConfirmPayout(amountInput, emailInput) },
            )
        }
    }
}
