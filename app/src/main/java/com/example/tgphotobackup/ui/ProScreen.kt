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
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
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
    val isPro by vm.isPro.collectAsState()

    var keyInput      by remember { mutableStateOf("") }
    var activateState by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WorkspacePremium, null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("TG Backup Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
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
            // ── Hero ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isPro) Icons.Default.VerifiedUser else Icons.Default.WorkspacePremium,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (isPro) "Pro Active" else "Upgrade to Pro",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (isPro) "Thank you for your support!"
                        else "One-time payment · No subscription",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Features ──────────────────────────────────────────
            ProFeaturesCard(isPro)

            if (!isPro) {
                // ── Price ──────────────────────────────────────────
                PriceCard()

                // ── How to pay ─────────────────────────────────────
                PaymentCard()

                // ── ABA QR ─────────────────────────────────────────
                QrCard()
            }

            // ── Key entry ─────────────────────────────────────────
            LicenseKeyCard(
                isPro        = isPro,
                keyInput     = keyInput,
                onKeyChange  = { keyInput = it; activateState = null },
                activateState = activateState,
                onActivate   = { activateState = vm.unlockPro(keyInput.trim()) }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Features list ─────────────────────────────────────────────────────────────

@Composable
private fun ProFeaturesCard(isPro: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("WHAT YOU GET", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            FeatureRow(Icons.Default.CloudUpload,  "Manual photo backup",    "Back up photos on demand",                  pro = false, unlocked = true)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            FeatureRow(Icons.Default.PhotoLibrary, "Gallery & photo viewer", "Browse and restore backed-up photos",       pro = false, unlocked = true)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            FeatureRow(Icons.Default.History,      "History & stats",        "Backup runs and storage usage",             pro = false, unlocked = true)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            FeatureRow(Icons.Default.Schedule,     "Auto-backup scheduling", "Automatic backups every 6–24 h",            pro = true,  unlocked = isPro)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            FeatureRow(Icons.Default.Videocam,     "Video backup",           "Include videos alongside photos",           pro = true,  unlocked = isPro)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            FeatureRow(Icons.Default.AutoDelete,   "Auto-delete after backup","Free up local storage automatically",      pro = true,  unlocked = isPro)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            FeatureRow(Icons.Default.Folder,       "Additional folders",     "Back up custom folders via file picker",    pro = true,  unlocked = isPro)
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    label: String,
    desc: String,
    pro: Boolean,
    unlocked: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp),
            tint = if (unlocked) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        Column(Modifier.weight(1f)) {
            Text(label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            Text(desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (unlocked) 0.8f else 0.4f))
        }
        if (pro && !unlocked) {
            Surface(shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Text("PRO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        } else {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp),
                tint = if (unlocked) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        }
    }
}

// ── Price card ────────────────────────────────────────────────────────────────

@Composable
private fun PriceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("$",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Column {
                Text("5 USD",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("One-time payment · Lifetime Pro access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

// ── Payment instructions ──────────────────────────────────────────────────────

@Composable
private fun PaymentCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("HOW TO PAY", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            val steps = listOf(
                "1" to "Open ABA Mobile app",
                "2" to "Scan the QR below, or transfer to:",
                "3" to "Amount: \$5 USD",
                "4" to "Memo / note: \"TG Pro\"",
                "5" to "Email receipt to:\nmenghong@aeu.edu.kh",
                "6" to "Receive your license key within 24 hours"
            )
            steps.forEach { (num, step) ->
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(num, style = MaterialTheme.typography.labelSmall,
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
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── QR code ───────────────────────────────────────────────────────────────────

@Composable
private fun QrCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ABA KHQR", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            AsyncImage(
                model = R.drawable.aba_qr,
                contentDescription = "ABA Bank QR Code",
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
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
private fun LicenseKeyCard(
    isPro: Boolean,
    keyInput: String,
    onKeyChange: (String) -> Unit,
    activateState: Boolean?,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isPro) "YOUR LICENSE" else "ENTER LICENSE KEY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            if (isPro) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Column {
                        Text("Pro is active on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("All Pro features are unlocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = onKeyChange,
                    label = { Text("License key") },
                    placeholder = { Text("TGPRO-XXXX-XXXX-XXXX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                activateState?.let { success ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null, Modifier.size(16.dp),
                            tint = if (success) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                        Text(
                            if (success) "Pro unlocked — enjoy all features!"
                            else "Invalid key. Double-check and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Button(
                    onClick = onActivate,
                    enabled = keyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.WorkspacePremium, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activate Pro", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
