package com.example.tgphotobackup.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tgphotobackup.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(vm: MainViewModel, onBack: () -> Unit) {
    val isPro       by vm.isPro.collectAsState()
    val isProMax    by vm.isProMax.collectAsState()
    val proExpiresAt by vm.proExpiresAt.collectAsState()

    var proKeyInput      by remember { mutableStateOf("") }
    var proActivate      by remember { mutableStateOf<Boolean?>(null) }
    var maxKeyInput      by remember { mutableStateOf("") }
    var maxActivate      by remember { mutableStateOf<Boolean?>(null) }
    val lockoutSeconds   by vm.licenseLockoutSeconds.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (isProMax) Icons.Default.AutoAwesome
                            else Icons.Default.WorkspacePremium,
                            null,
                            tint = if (isProMax) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (isProMax) "TG Backup Pro Max" else "TG Backup Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Status banner ───────────────────────────────────────────────
            HeroBanner(isPro, isProMax, proExpiresAt)

            // ── Feature comparison ──────────────────────────────────────────
            FeaturesCard(isPro, isProMax)

            // ── Pro monthly section ─────────────────────────────────────────
            if (!isProMax) {
                SectionHeader("PRO — \$5 USD / month")
                ProPriceCard()
                PaymentCard(
                    amount = "\$5 USD",
                    memo   = "TG Pro",
                    steps  = listOf(
                        "Open ABA Mobile app",
                        "Scan the QR below or transfer to account",
                        "Amount: \$5 USD",
                        "Memo / note: \"TG Pro\"",
                        "Email receipt to: menghong@aeu.edu.kh",
                        "Receive license key within 24 hours"
                    )
                )
                QrCard()
                KeyEntryCard(
                    title          = if (isPro) "PRO LICENSE ACTIVE" else "ENTER PRO KEY",
                    placeholder    = "PROM-YYYYMM-XXXX-CCCC-CCCC",
                    isActive       = isPro,
                    activeLabel    = "Pro is active · $proExpiresAt",
                    keyInput       = proKeyInput,
                    onKeyChange    = { proKeyInput = it; proActivate = null },
                    activateState  = proActivate,
                    onActivate     = { proActivate = vm.unlockPro(proKeyInput.trim()) },
                    buttonLabel    = "Activate Pro",
                    lockoutSeconds = lockoutSeconds
                )
            }

            // ── Pro Max lifetime section ────────────────────────────────────
            SectionHeader("PRO MAX — \$15 USD · Lifetime")
            MaxPriceCard()
            PaymentCard(
                amount = "\$15 USD",
                memo   = "TG Max",
                steps  = listOf(
                    "Open ABA Mobile app",
                    "Scan the QR below or transfer to account",
                    "Amount: \$15 USD",
                    "Memo / note: \"TG Max\"",
                    "Email receipt to: menghong@aeu.edu.kh",
                    "Receive lifetime license key within 24 hours"
                )
            )
            if (!isProMax) QrCard()
            KeyEntryCard(
                title          = if (isProMax) "PRO MAX LICENSE ACTIVE" else "ENTER PRO MAX KEY",
                placeholder    = "PMAX-XXXX-XXXX-CCCC-CCCC",
                isActive       = isProMax,
                activeLabel    = "Pro Max is active · Lifetime",
                keyInput       = maxKeyInput,
                onKeyChange    = { maxKeyInput = it; maxActivate = null },
                activateState  = maxActivate,
                onActivate     = { maxActivate = vm.unlockProMax(maxKeyInput.trim()) },
                buttonLabel    = "Activate Pro Max",
                buttonColor    = Color(0xFFFFB300),
                lockoutSeconds = lockoutSeconds
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroBanner(isPro: Boolean, isProMax: Boolean, proExpiresAt: String) {
    val (icon, headline, sub, brush) = when {
        isProMax -> Quadruple(
            Icons.Default.AutoAwesome,
            "Pro Max Active",
            "All features unlocked · Lifetime",
            Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00)))
        )
        isPro -> Quadruple(
            Icons.Default.VerifiedUser,
            "Pro Active",
            proExpiresAt.ifBlank { "Pro features unlocked" },
            Brush.linearGradient(listOf(
                Color(0xFF1565C0), Color(0xFF0288D1)
            ))
        )
        else -> Quadruple(
            Icons.Default.WorkspacePremium,
            "Upgrade Your Backup",
            "Choose Pro or Pro Max below",
            Brush.linearGradient(listOf(
                Color(0xFF4A148C), Color(0xFF7B1FA2)
            ))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(56.dp), tint = Color.White)
            Text(headline, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

// ── Features table ──────────────────────────────────────────────────────────

private enum class Tier { FREE, PRO, MAX }

private data class Feature(
    val icon: ImageVector,
    val label: String,
    val desc: String,
    val tier: Tier
)

private val FEATURES = listOf(
    Feature(Icons.Default.CloudUpload,   "Manual photo backup",       "Back up photos on demand",                  Tier.FREE),
    Feature(Icons.Default.PhotoLibrary,  "Gallery & photo viewer",    "Browse and restore backed-up photos",       Tier.FREE),
    Feature(Icons.Default.History,       "History & stats",           "Backup runs and storage usage",             Tier.FREE),
    Feature(Icons.Default.Schedule,      "Auto-backup scheduling",    "Automatic backups every 6–24 h",            Tier.PRO),
    Feature(Icons.Default.Videocam,      "Video backup",              "Include videos alongside photos",           Tier.PRO),
    Feature(Icons.Default.AutoDelete,    "Auto-delete after backup",  "Free up local storage automatically",       Tier.PRO),
    Feature(Icons.Default.Folder,        "Additional folders",        "Back up custom folders via file picker",    Tier.PRO),
    Feature(Icons.Default.AllInbox,      "Bulk restore all photos",   "Restore your entire backup at once",        Tier.MAX),
    Feature(Icons.Default.Security,      "Encrypted backup (AES-256)","Files encrypted before upload to Telegram", Tier.MAX),
    Feature(Icons.Default.SyncAlt,       "Auto daily index backup",   "Upload backup index to Telegram every day", Tier.MAX),
    Feature(Icons.Default.Share,         "Export backup report",      "Share a full log of all backed-up files",   Tier.MAX),
)

@Composable
private fun FeaturesCard(isPro: Boolean, isProMax: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("FEATURES", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            FEATURES.forEachIndexed { i, f ->
                val unlocked = when (f.tier) {
                    Tier.FREE -> true
                    Tier.PRO  -> isPro || isProMax
                    Tier.MAX  -> isProMax
                }
                FeatureRow(f, unlocked)
                if (i < FEATURES.size - 1) HorizontalDivider(Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow(f: Feature, unlocked: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(f.icon, null, Modifier.size(20.dp),
            tint = if (unlocked) when (f.tier) {
                Tier.MAX  -> Color(0xFFFFB300)
                Tier.PRO  -> MaterialTheme.colorScheme.primary
                else      -> MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        Column(Modifier.weight(1f)) {
            Text(f.label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text(f.desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (unlocked) 0.8f else 0.35f))
        }
        when {
            unlocked -> Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp),
                tint = if (f.tier == Tier.MAX) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary)
            f.tier == Tier.MAX -> TierBadge("MAX", Color(0xFFFFB300))
            f.tier == Tier.PRO -> TierBadge("PRO", MaterialTheme.colorScheme.primary)
            else -> {}
        }
    }
}

@Composable
private fun TierBadge(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ── Section header ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp))
}

// ── Price cards ─────────────────────────────────────────────────────────────

@Composable
private fun ProPriceCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("$", style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Column {
                Text("5 USD / month", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Renew every month · 4 Pro features",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun MaxPriceCard() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00))))
        .padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("$", style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, color = Color.White)
            Column {
                Text("15 USD · Lifetime", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text("Pay once, use forever · 8 Pro Max features",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

// ── Payment instructions ─────────────────────────────────────────────────────

@Composable
private fun PaymentCard(amount: String, memo: String, steps: List<String>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("HOW TO PAY", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            steps.forEachIndexed { i, step ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(step, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("ABA BANK ACCOUNT", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            AccountRow("Account name", "MENGHONG TANN")
            AccountRow("USD account",  "500 090 709")
            AccountRow("KHR account",  "006 662 997")
        }
    }
}

@Composable
private fun AccountRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── QR code ──────────────────────────────────────────────────────────────────

@Composable
private fun QrCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ABA KHQR", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            AsyncImage(
                model = R.drawable.aba_qr,
                contentDescription = "ABA Bank QR Code",
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
            Text("Scan with ABA Mobile to pay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── License key entry ─────────────────────────────────────────────────────────

@Composable
private fun KeyEntryCard(
    title: String,
    placeholder: String,
    isActive: Boolean,
    activeLabel: String,
    keyInput: String,
    onKeyChange: (String) -> Unit,
    activateState: Boolean?,
    onActivate: () -> Unit,
    buttonLabel: String,
    buttonColor: Color = Color.Unspecified,
    lockoutSeconds: Long = 0L
) {
    val isLockedOut = lockoutSeconds > 0L

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Column {
                        Text(activeLabel, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("All features in this tier are unlocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = onKeyChange,
                    label = { Text("License key") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    enabled = !isLockedOut,
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    isLockedOut -> Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text("Too many failed attempts. Try again in ${lockoutSeconds}s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    activateState != null -> Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (activateState) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null, Modifier.size(16.dp),
                            tint = if (activateState) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                        Text(
                            if (activateState) "Activated! Enjoy your features."
                            else "Invalid or expired key. Double-check and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activateState) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }

                Button(
                    onClick = onActivate,
                    enabled = keyInput.isNotBlank() && !isLockedOut,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (buttonColor != Color.Unspecified)
                        ButtonDefaults.buttonColors(containerColor = buttonColor)
                    else ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.WorkspacePremium, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
