package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.CartridgeSource
import com.implantdoom.ui.Routes
import com.implantdoom.ui.ScreenScaffold

/**
 * Prompts the user to tap their implant. Reading happens in [AppViewModel] when
 * the OS dispatches the tag to [com.implantdoom.MainActivity]; this screen just
 * reflects the latest result (success → offer details/play, or an error).
 */
@Composable
fun ScanScreen(viewModel: AppViewModel, navController: NavHostController) {
    val enabled by viewModel.nfcEnabled.collectAsState()
    val supported by viewModel.nfcSupported.collectAsState()
    val error by viewModel.readError.collectAsState()
    val source by viewModel.activeSource.collectAsState()
    val cartridge by viewModel.activeCartridge.collectAsState()
    val lastReadAt by viewModel.lastReadAt.collectAsState()

    // Enter read mode while this screen is shown; clear any stale error.
    LaunchedEffect(Unit) {
        viewModel.setReadMode()
        viewModel.clearReadError()
    }

    ScreenScaffold(title = "Scan cartridge", navController = navController) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Hold your implant to the back of the phone",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Keep it still over the NFC antenna until the cartridge loads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (!supported) {
                Spacer(Modifier.height(20.dp))
                WarnCard("This device has no NFC hardware. You can still play the demo and build cartridges.")
            } else if (!enabled) {
                Spacer(Modifier.height(20.dp))
                WarnCard("NFC is turned off. Enable it in Android settings, then tap your implant.")
            }

            if (error != null) {
                Spacer(Modifier.height(20.dp))
                WarnCard(error!!)
            }

            // Success: a cartridge was read from NFC.
            if (cartridge != null && source == CartridgeSource.NFC && lastReadAt > 0) {
                Spacer(Modifier.height(20.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Cartridge loaded from implant", color = MaterialTheme.colorScheme.primary)
                        Text(
                            cartridge!!.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = { navController.navigate(Routes.DETAILS) }) {
                                Text("Details")
                            }
                            Button(onClick = { navController.navigate(Routes.PLAY) }) {
                                Text("Play")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                viewModel.loadDemoCartridge(navigate = false)
                navController.navigate(Routes.DETAILS)
            }) {
                Text("No implant? Load the demo cartridge")
            }
        }
    }
}

@Composable
private fun WarnCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F12)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFFE0A23A),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
