package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.implantdoom.cartridge.CartridgeCodec
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.HexDump
import com.implantdoom.ui.InfoRow
import com.implantdoom.ui.MapPreview
import com.implantdoom.ui.Routes
import com.implantdoom.ui.ScreenScaffold
import com.implantdoom.ui.SectionCard

/** Shows the parsed header, a map preview, byte/CRC info, a hex dump and actions. */
@Composable
fun DetailsScreen(viewModel: AppViewModel, navController: NavHostController) {
    val cartridge by viewModel.activeCartridge.collectAsState()
    val bytes by viewModel.activeBytes.collectAsState()
    val source by viewModel.activeSource.collectAsState()

    ScreenScaffold(title = "Cartridge details", navController = navController) {
        val c = cartridge
        if (c == null) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("No cartridge loaded.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.loadDemoCartridge(navigate = false) }) {
                    Text("Load demo cartridge")
                }
            }
            return@ScreenScaffold
        }

        val crcValid = bytes?.let { CartridgeCodec.isValid(it) } == true

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SectionCard("Cartridge") {
                Column {
                    InfoRow("Name", c.displayName())
                    InfoRow("Source", source.label)
                    InfoRow("Format version", "v${c.version}")
                    InfoRow("Theme", "${c.themeName()} (#${c.textureThemeId})")
                    InfoRow("Seed", "0x%08X".format(c.seed))
                    InfoRow("Player start", "(${c.playerStartX}, ${c.playerStartY}) @ ${c.playerStartAngle}")
                    InfoRow("Map size", "${c.mapWidth} × ${c.mapHeight}")
                    InfoRow("Entities", "${c.entityCount}")
                    InfoRow("Items", "${c.itemCount}")
                    InfoRow(
                        "Payload size",
                        "${bytes?.size ?: c.encodedSize()} / ${CartridgeCodec.MAX_SIZE_BYTES} bytes",
                    )
                    InfoRow(
                        "CRC32",
                        if (crcValid) "valid" else "INVALID",
                        valueColor = if (crcValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("Map preview") {
                MapPreview(
                    cartridge = c,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { navController.navigate(Routes.PLAY) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Play this cartridge")
            }

            val data = bytes
            if (data != null) {
                Spacer(Modifier.height(12.dp))
                CartridgeWritePanel(viewModel, data)

                Spacer(Modifier.height(12.dp))
                SectionCard("Raw bytes (hex)") {
                    Column {
                        Text(
                            "Single MIME NDEF record payload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        HexDump(data, Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
