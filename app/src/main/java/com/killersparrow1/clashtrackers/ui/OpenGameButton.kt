package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.killersparrow1.clashtrackers.R

private const val GAME_PACKAGE = "com.supercell.clashofclans"

/** Minimal header button: just the installed game's own icon. Falls back to a plain arrow icon. */
@Composable
fun OpenGameButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The icon comes from the installed game (nothing is bundled); no game installed means the fallback arrow.
    val gameIcon = remember {
        runCatching { context.packageManager.getApplicationIcon(GAME_PACKAGE).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    val label = stringResource(R.string.open_game_short)
    // Icon only (no pill, no text): the game's own icon is recognisable, and the label stays for screen readers.
    Box(
        modifier = modifier.padding(end = 8.dp).size(48.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (gameIcon != null) {
            Image(gameIcon, contentDescription = label, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
        } else {
            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}
