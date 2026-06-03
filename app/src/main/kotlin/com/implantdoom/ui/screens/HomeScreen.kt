package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.Routes
import com.implantdoom.ui.ScreenScaffold

/** Landing screen: title, NFC status and the main navigation actions. */
@Composable
fun HomeScreen(viewModel: AppViewModel, navController: NavHostController) {
    val supported by viewModel.nfcSupported.collectAsState()
    val enabled by viewModel.nfcEnabled.collectAsState()

    ScreenScaffold(title = "NFC-DOOM", navController = navController, showBack = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "NFC//DOOM",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "A Doom-like game loaded from an NFC implant cartridge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(16.dp))
            NfcStatusChip(supported, enabled)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { navController.navigate(Routes.SCAN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Nfc, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Scan implant cartridge")
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.loadDemoCartridge(navigate = false)
                    navController.navigate(Routes.PLAY)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Play built-in demo cartridge")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { navController.navigate(Routes.BUILDER) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Build, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Build / write a cartridge")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    viewModel.setDiagnosticsMode()
                    navController.navigate(Routes.DIAGNOSTICS)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("NFC diagnostics")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { navController.navigate(Routes.ABOUT) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("About & safety")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "The implant is passive cartridge storage. The phone runs the game. " +
                    "No network. No vendor app or cloud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NfcStatusChip(supported: Boolean, enabled: Boolean) {
    val (text, color) = when {
        !supported -> "No NFC hardware — demo & builder still work" to Color(0xFFE0A23A)
        !enabled -> "NFC is OFF — enable it in system settings to scan" to Color(0xFFE0A23A)
        else -> "NFC ready" to MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(10.dp).clip(CircleShape).background(color),
        )
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
