package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/** Small pill for the header: the installed game's own icon plus "Open game". Falls back to a plain arrow icon. */
@Composable
fun OpenGameButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The icon comes from the installed game (nothing is bundled); no game installed means the fallback arrow.
    val gameIcon = remember {
        runCatching { context.packageManager.getApplicationIcon(GAME_PACKAGE).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Surface(
        modifier = modifier.padding(end = 8.dp).heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (gameIcon != null) {
                Image(gameIcon, contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(20.dp))
            }
            Text(stringResource(R.string.open_game_short), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
