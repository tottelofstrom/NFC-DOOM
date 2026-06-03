package com.implantdoom.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.implantdoom.nfc.NdefCartridge
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.ScreenScaffold
import com.implantdoom.ui.SectionCard

/** Explains the concept, the safety/legal constraints and the technical notes. */
@Composable
fun AboutScreen(@Suppress("UNUSED_PARAMETER") viewModel: AppViewModel, navController: NavHostController) {
    ScreenScaffold(title = "About & safety", navController = navController) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SectionCard("What this is") {
                Text(
                    "NFC-DOOM is a Doom-like first-person raycaster game whose levels are " +
                        "loaded from a tiny binary \"cartridge\" stored on an NFC implant.\n\n" +
                        "The implant is NOT a computer. It is passive cartridge storage. The phone " +
                        "runs the entire game engine; the cartridge only initialises a level.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard("Graphics: Freedoom (not id Software)") {
                Text(
                    "The first-person graphics (wall textures, monster sprites, weapon and the " +
                        "status bar) come from FREEDOOM — a free, BSD-licensed replacement game-data " +
                        "project (freedoom.github.io). They are NOT id Software's copyrighted Doom " +
                        "assets: no id IWAD data, no original Doom sprites/textures/music/maps, and " +
                        "not the trademarked Doom logo.\n\n" +
                        "The engine itself is an original Doom-like raycaster written for this project. " +
                        "Freedoom's BSD license and contributor credits are bundled under " +
                        "assets/doomgfx (ATTRIBUTION.txt).\n\n" +
                        "A future version may also let you load your own legally-owned WAD locally.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard("Privacy & independence") {
                Text(
                    "• No network access — the app has no INTERNET permission.\n" +
                        "• No vendor app, API, server, SDK or cloud is used.\n" +
                        "• Tag data is never sent anywhere; everything stays on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard("The tag") {
                Text(
                    "Expected tag: NFC Forum Type 5 / ISO 15693 / NFC-V (e.g. an NXP ICODE DNA " +
                        "style implant). The reference implant exposed 256 blocks × 4 bytes = " +
                        "1024 bytes of raw memory, leaving roughly 1000 usable NDEF bytes.\n\n" +
                        "Cartridges are stored as a single custom MIME NDEF record:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    NdefCartridge.MIME_TYPE,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard("Capability Container note") {
                Text(
                    "For Android to detect NDEF on the reference implant, block 00 (the Type 5 " +
                        "Capability Container) had to be E1 40 80 09 (hex E1408009). An earlier " +
                        "broken value E140FF09 advertised too much memory and stopped Android / NXP " +
                        "apps from reading NDEF correctly.\n\n" +
                        "This app does NOT write block 00, lock bits, AFI, DSFID or passwords. The CC " +
                        "value is shown for reference only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
