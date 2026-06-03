package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import kotlin.math.roundToInt

/**
 * Builder + writer screen: generate a seeded demo cartridge, tweak parameters,
 * preview the map and raw bytes, validate the size/CRC, then play or write it.
 */
@Composable
fun BuilderScreen(viewModel: AppViewModel, navController: NavHostController) {
    val state by viewModel.builder.collectAsState()

    ScreenScaffold(title = "Cartridge builder", navController = navController) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SectionCard("Generator") {
                Column {
                    var seedText by remember(state.seed) { mutableStateOf(state.seed.toString()) }
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = {
                            seedText = it
                            it.trim().toLongOrNull()?.let { v -> viewModel.updateBuilderSeed(v) }
                        },
                        label = { Text("Seed (decimal)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Seed 0x%08X".format(state.seed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.randomizeBuilderSeed() }) {
                        Icon(Icons.Filled.Casino, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Randomize map")
                    }

                    Spacer(Modifier.height(12.dp))
                    val maxEntities = CartridgeCodec.maxEntities(state.itemCount).coerceAtMost(48)
                    LabeledSlider(
                        label = "Entities: ${state.entityCount}",
                        value = state.entityCount.toFloat(),
                        range = 0f..maxEntities.toFloat().coerceAtLeast(1f),
                        onChange = { viewModel.setBuilderEntityCount(it.roundToInt()) },
                    )
                    LabeledSlider(
                        label = "Items: ${state.itemCount}",
                        value = state.itemCount.toFloat(),
                        range = 0f..16f,
                        onChange = { viewModel.setBuilderItemCount(it.roundToInt()) },
                    )
                    LabeledSlider(
                        label = "Wall density: ${"%.0f".format(state.wallDensity * 100)}%",
                        value = state.wallDensity.toFloat(),
                        range = 0f..0.45f,
                        onChange = { viewModel.setBuilderWallDensity(it.toDouble()) },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Theme", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val themes = listOf("Tech", "Inferno", "Cavern", "Cryo")
                        themes.forEachIndexed { i, name ->
                            FilterChip(
                                selected = state.themeId == i,
                                onClick = { viewModel.setBuilderTheme(i) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            state.cartridge?.let {
                SectionCard("Map preview") {
                    MapPreview(it, Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("Validation") {
                Column {
                    InfoRow(
                        "Size",
                        "${state.sizeBytes} / ${CartridgeCodec.MAX_SIZE_BYTES} bytes",
                        valueColor = if (state.underLimit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    InfoRow(
                        "CRC32",
                        if (state.crcValid) "valid" else "INVALID",
                        valueColor = if (state.crcValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    state.error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Error: $it", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.useBuilderCartridge(navigate = false)
                        navController.navigate(Routes.PLAY)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = state.cartridge != null,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = { viewModel.useBuilderCartridge(navigate = true) },
                    modifier = Modifier.weight(1f),
                    enabled = state.cartridge != null,
                ) {
                    Text("Details")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.loadDemoIntoBuilder() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load the built-in demo cartridge")
            }

            Spacer(Modifier.height(12.dp))
            state.bytes?.let { CartridgeWritePanel(viewModel, it) }

            Spacer(Modifier.height(12.dp))
            state.bytes?.let {
                SectionCard("Raw bytes (hex)") {
                    HexDump(it, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}
