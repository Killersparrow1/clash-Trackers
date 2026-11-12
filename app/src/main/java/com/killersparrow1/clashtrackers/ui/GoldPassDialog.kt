@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.GoldPassReduction
import com.killersparrow1.clashtrackers.sync.GoldPassStore

/**
 * "Which pass this month?" Gold Pass with its two reductions, or Silver (free). Asked once a month and
 * available any time from Settings. [onLater] closes without saving, so the monthly prompt comes back.
 */
@Composable
fun GoldPassDialog(
    initial: GoldPassStore.Answer,
    onSave: (gold: Boolean, builder: Int, research: Int) -> Unit,
    onLater: () -> Unit,
    laterLabel: String = stringResource(R.string.gold_pass_later),
) {
    var gold by remember { mutableStateOf(initial.gold) }
    var builder by remember { mutableStateOf(initial.builder) }
    var research by remember { mutableStateOf(initial.research) }

    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.gold_pass_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.gold_pass_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PassCard(R.drawable.gold_pass, stringResource(R.string.gold_pass_gold), gold, Modifier.weight(1f)) { gold = true }
                    PassCard(R.drawable.silver_pass, stringResource(R.string.gold_pass_silver), !gold, Modifier.weight(1f)) { gold = false }
                }
                if (gold) {
                    PercentRow(stringResource(R.string.gold_pass_builder), builder) { builder = it }
                    PercentRow(stringResource(R.string.gold_pass_research), research) { research = it }
                    Text(stringResource(R.string.gold_pass_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(gold, builder, research) }) { Text(stringResource(R.string.gold_pass_save)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(laterLabel) } },
    )
}

@Composable
private fun PassCard(image: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(image), contentDescription = null, modifier = Modifier.size(64.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun PercentRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GoldPassReduction.CHOICES.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = value == p,
                    onClick = { onChange(p) },
                    shape = SegmentedButtonDefaults.itemShape(i, GoldPassReduction.CHOICES.size),
                    label = { Text(if (p == 0) stringResource(R.string.gold_pass_off) else "$p%") },
                )
            }
        }
    }
}
