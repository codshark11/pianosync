package io.pianosync.midi.ui.screens.midiplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.pianosync.midi.R
import io.pianosync.midi.ui.theme.AccentRose
import io.pianosync.midi.ui.theme.WarmGold60
import io.pianosync.midi.ui.theme.WarmGold80
import io.pianosync.midi.ui.theme.successAccentColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ScoreDialog(
    showDialog: Boolean,
    score: Int,
    notesHit: Int,
    notesMissed: Int,
    totalNotes: Int,
    recordingDurationMs: Long,
    isSaving: Boolean,
    scope: CoroutineScope,
    onDismiss: suspend CoroutineScope.() -> Unit,
    onRetry: suspend CoroutineScope.() -> Unit,
    onBack: suspend CoroutineScope.() -> Unit
) {
    if (showDialog) {
        Dialog(
            onDismissRequest = {
                if (!isSaving) {
                    scope.launch { onDismiss() }
                }
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.performance_results),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (recordingDurationMs > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.recording),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.recording_saved_format, formatRecordingTime(recordingDurationMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Show loading indicator while saving
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.saving_progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Score display
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .padding(vertical = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    color = when(score) {
                                        in 90..100 -> successAccentColor()
                                        in 80..89 -> WarmGold60
                                        in 70..79 -> WarmGold80
                                        in 60..69 -> AccentRose.copy(alpha = 0.7f)
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$score%",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CompactStatisticItem(
                                label = stringResource(R.string.notes_hit),
                                value = "$notesHit",
                                modifier = Modifier.weight(1f)
                            )

                            CompactStatisticItem(
                                label = stringResource(R.string.notes_missed),
                                value = "$notesMissed",
                                modifier = Modifier.weight(1f)
                            )

                            CompactStatisticItem(
                                label = stringResource(R.string.total),
                                value = "$totalNotes",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grade display
                        val grade = when(score) {
                            in 95..100 -> "A+"
                            in 90..94 -> "A"
                            in 85..89 -> "B+"
                            in 80..84 -> "B"
                            in 75..79 -> "C+"
                            in 70..74 -> "C"
                            in 60..69 -> "D"
                            else -> "Keep practicing!"
                        }

                        Text(
                            text = if (score >= 60) "Grade: $grade" else grade,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when(score) {
                                in 90..100 -> Color(0xFF4CAF50)
                                in 80..89 -> Color(0xFF8BC34A)
                                in 70..79 -> Color(0xFFFFC107)
                                in 60..69 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { onRetry() } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.try_again),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.try_again),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { onBack() } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_library),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.back),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
