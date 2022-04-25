class Note(
	val frequency: Float = 0.0f, val isGlissando: Boolean = false, val isVibrato: Boolean = false,
	val midiNumber: Byte = 0, val isRest: Boolean = false
) {
	private val eventList = mutableListOf<NoteEvent>()

	/**
	 * Add the input NoteEvent to the events list of this Note.
	 */
	fun addNoteEvent(event: NoteEvent) = eventList.add(event)

	/**
	 * Return the NoteEvent at the specified index.
	 */
	fun getNoteEvent(index: Int) = eventList[index]

	/**
	 * Return the number of events in the NoteEvent list.
	 */
	fun getNumberOfEvents() = eventList.size
}

/**
 * Calculate the midi number of a given note.
 */
fun calculateMidiNumber(noteName: NoteName, octave: Byte, alteration: Byte): Byte
{
	return (12 * (octave + 1) + noteName.semitonesAboveC() + alteration).toByte()
}