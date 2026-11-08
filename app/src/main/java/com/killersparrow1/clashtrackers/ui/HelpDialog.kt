package com.killersparrow1.clashtrackers.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import kotlinx.coroutines.launch

private data class HelpStep(
    @param:DrawableRes val image: Int?,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

private val helpSteps = listOf(
    HelpStep(R.drawable.help_1_settings, R.string.help_step1_title, R.string.help_step1_desc),
    HelpStep(R.drawable.help_2_copy, R.string.help_step2_title, R.string.help_step2_desc),
    HelpStep(R.drawable.help_3_sync, R.string.help_step3_title, R.string.help_step3_desc),
    HelpStep(null, R.string.help_step4_title, R.string.help_step4_desc),
)

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val pager = rememberPagerState(pageCount = { helpSteps.size })
    val scope = rememberCoroutineScope()
    val lastPage = helpSteps.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().height(330.dp),
                ) { page ->
                    val step = helpSteps[page]
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(180.dp).padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (step.image != null) {
                                    Image(
                                        painter = painterResource(step.image),
                                        contentDescription = stringResource(step.title),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.help_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(step.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.help_counter, pager.currentPage + 1, helpSteps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pager.currentPage < lastPage) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else {
                    onDismiss()
                }
            }) {
                Text(stringResource(if (pager.currentPage < lastPage) R.string.help_next else R.string.action_close))
            }
        },
        dismissButton = {
            if (pager.currentPage > 0) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(stringResource(R.string.help_previous))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
