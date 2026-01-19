package io.pianosync.midi.ui.screens.midiplayer.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.pianosync.midi.data.model.MidiFile
import io.pianosync.midi.R
import androidx.compose.ui.res.stringResource

@Composable
fun BpmDialog(
    showDialog: Boolean,
    currentBpm: Int?,
    midiFile: MidiFile,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (showDialog) {
        var tempBpm by remember { mutableStateOf(currentBpm?.toString() ?: "") }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.set_bpm)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.original_bpm, midiFile.originalBpm?.toString() ?: stringResource(R.string.bmp_unknown)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = tempBpm,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                tempBpm = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.current_bpm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tempBpm.toIntOrNull()?.let { newBpm ->
                            onConfirm(newBpm)
                        }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
