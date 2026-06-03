package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.InfoRow
import com.implantdoom.ui.ScreenScaffold
import com.implantdoom.ui.SectionCard

/**
 * Read-only NFC diagnostics. Shows UID (both byte orders), tech list, NDEF
 * availability/size/records, MIME-cartridge detection, and NFC-V (ISO 15693)
 * details. An advanced, clearly-labelled toggle additionally issues read-only
 * ISO 15693 Get-System-Information + Read-Single-Block commands.
 */
@Composable
fun DiagnosticsScreen(viewModel: AppViewModel, navController: NavHostController) {
    val diag by viewModel.diagnostics.collectAsState()
    val advanced by viewModel.advancedNfcVEnabled.collectAsState()
    val supported by viewModel.nfcSupported.collectAsState()
    val enabled by viewModel.nfcEnabled.collectAsState()

    LaunchedEffect(Unit) { viewModel.setDiagnosticsMode() }

    ScreenScaffold(title = "NFC diagnostics", navController = navController) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SectionCard("Scan") {
                Column {
                    Text(
                        when {
                            !supported -> "No NFC hardware on this device."
                            !enabled -> "NFC is OFF. Enable it, then tap a tag."
                            else -> "Tap any NFC tag/implant to inspect it (read-only)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = advanced, onCheckedChange = { viewModel.toggleAdvancedNfcV() })
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Advanced: read ISO 15693 blocks", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Developer option. Read-only Get System Info + Read Single Block. Never writes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val d = diag
            if (d == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No tag scanned yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                SectionCard("Identity") {
                    Column {
                        InfoRow("UID (Android order)", d.uidColonHex.ifEmpty { "—" })
                        InfoRow("UID (no separators)", d.uidHex.ifEmpty { "—" })
                        InfoRow("UID (reversed)", d.uidReversedHex.ifEmpty { "—" })
                        InfoRow("Tech list", if (d.techList.isEmpty()) "—" else d.techList.joinToString(", "))
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionCard("NDEF") {
                    Column {
                        InfoRow("NDEF available", if (d.ndefAvailable) "yes" else "no")
                        InfoRow("Type", d.ndefType ?: "—")
                        InfoRow("Max size", d.ndefMaxSizeBytes?.let { "$it bytes" } ?: "—")
                        InfoRow("Writable", d.ndefWritable?.let { if (it) "yes" else "no" } ?: "—")
                        InfoRow("Can make read-only", d.ndefCanMakeReadOnly?.let { if (it) "yes" else "no" } ?: "—")
                        InfoRow(
                            "Cartridge record",
                            if (d.cartridgeFound) "FOUND" else "not present",
                            valueColor = if (d.cartridgeFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (d.records.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Records:", style = MaterialTheme.typography.bodyMedium)
                            d.records.forEach { r ->
                                Text(
                                    "#${r.index} ${r.tnfName} • ${r.typeDescription} • ${r.payloadLength} B" +
                                        if (r.isCartridge) "  ◄ cartridge" else "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (r.isCartridge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                                )
                            }
                        }
                    }
                }

                d.nfcV?.let { v ->
                    Spacer(Modifier.height(12.dp))
                    SectionCard("NFC-V / ISO 15693") {
                        Column {
                            InfoRow("DSFID", v.dsfid?.let { "0x%02X".format(it) } ?: "—")
                            InfoRow("Response flags", v.responseFlags?.let { "0x%02X".format(it) } ?: "—")
                            InfoRow("Max transceive", v.maxTransceiveLength?.let { "$it bytes" } ?: "—")
                            v.systemInfo?.let { si ->
                                Spacer(Modifier.height(6.dp))
                                Text("System information:", style = MaterialTheme.typography.bodyMedium)
                                InfoRow("  DSFID", si.dsfid?.let { "0x%02X".format(it) } ?: "—")
                                InfoRow("  AFI", si.afi?.let { "0x%02X".format(it) } ?: "—")
                                InfoRow("  Block count", si.blockCount?.toString() ?: "—")
                                InfoRow("  Block size", si.blockSizeBytes?.let { "$it bytes" } ?: "—")
                                InfoRow("  IC reference", si.icReference?.let { "0x%02X".format(it) } ?: "—")
                            }
                            if (v.blocks.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Blocks (read-only):", style = MaterialTheme.typography.bodyMedium)
                                v.blocks.forEach { b ->
                                    Text(
                                        "blk %02d: %s".format(b.index, b.hexOrError()),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (d.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionCard("Notes") {
                        Column { d.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("Reference: Capability Container") {
                Text(
                    "This implant needed CC block 00 = E1 40 80 09 (E1408009) for Android to detect " +
                        "NDEF. E140FF09 advertised too much memory and broke detection. This app does " +
                        "not write block 00 or any lock/security settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
