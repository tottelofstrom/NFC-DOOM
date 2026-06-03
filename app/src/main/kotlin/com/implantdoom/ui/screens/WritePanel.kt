package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.implantdoom.cartridge.CartridgeCodec
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.SectionCard

/**
 * Reusable NFC-write panel. Arms a write of [bytes]; the next implant tap (while
 * [com.implantdoom.MainActivity] is in WRITE mode) performs it. Shows live status
 * and a cancel action. The actual write is safe: NDEF only, never locks the tag.
 */
@Composable
fun CartridgeWritePanel(viewModel: AppViewModel, bytes: ByteArray) {
    val armed by viewModel.writeArmed.collectAsState()
    val status by viewModel.writeStatus.collectAsState()

    SectionCard("Write to implant") {
        Column {
            Text(
                "Writes the cartridge as one MIME NDEF record. The app never locks the tag, " +
                    "sets passwords, or changes AFI/DSFID/CC/lock bits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val fits = bytes.size <= CartridgeCodec.MAX_SIZE_BYTES
            Text(
                "Payload: ${bytes.size} bytes " +
                    if (fits) "(fits the ~1000-byte budget)" else "(TOO LARGE for a ~1000-byte tag!)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (fits) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))

            if (!armed) {
                Button(onClick = { viewModel.armWrite(bytes) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Nfc, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Arm write — then tap implant")
                }
            } else {
                Text(
                    status ?: "Ready — hold your implant to the phone…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.cancelWrite() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel write")
                }
            }

            // Show the last write result even after disarming.
            if (!armed && status != null) {
                Spacer(Modifier.height(8.dp))
                Text(status!!, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
