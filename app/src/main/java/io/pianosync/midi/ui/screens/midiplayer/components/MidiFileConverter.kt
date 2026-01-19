package io.pianosync.midi.ui.screens.midiplayer.components

import android.content.Context
import android.net.Uri
import io.pianosync.midi.data.model.MidiFile as ProjectMidiFile
import io.pianosync.midi.sheetmusic.MidiFile as SheetMusicMidiFile
import io.pianosync.midi.sheetmusic.MidiFileException

/**
 * Converts the project's MidiFile model to the sheetmusic package's MidiFile
 * by reading the MIDI file bytes from the URI
 */
object MidiFileConverter {
    /**
     * Convert project MidiFile to sheetmusic MidiFile
     * @param context Android context for reading the file
     * @param projectMidiFile The project's MidiFile model
     * @return The sheetmusic MidiFile object, or null if conversion fails
     */
    fun convertToSheetMusicMidiFile(
        context: Context,
        projectMidiFile: ProjectMidiFile
    ): SheetMusicMidiFile? {
        return try {
            val uri = Uri.parse(projectMidiFile.path)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return null

            val bytes = inputStream.readBytes()
            inputStream.close()

            SheetMusicMidiFile(bytes, projectMidiFile.name)
        } catch (e: Exception) {
            android.util.Log.e("MidiFileConverter", "Error converting MidiFile", e)
            null
        }
    }
}
