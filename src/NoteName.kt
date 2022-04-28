// todo: remove this enum.

enum class NoteName {
	C, D, E, F, G, A, B, REST;

	/**
	 * How many semitones this note is above the C of its octave.  Return -1 for
	 * a rest.
	 */
	fun semitonesAboveC(): Int {
		return when (this) {
			C -> 0
			D -> 2
			E -> 4
			F -> 5
			G -> 7
			A -> 9
			B -> 11
			REST -> -1
		}
	}
}

/**
 * Convert the input String to a NoteName.  If the String cannot be parsed as a
 * NoteName, print an error message and return REST.
 */
fun stringToNoteName(noteName: String): NoteName {
	return when (noteName) {
		"C" -> NoteName.C
		"D" -> NoteName.D
		"E" -> NoteName.E
		"F" -> NoteName.F
		"G" -> NoteName.G
		"A" -> NoteName.A
		"B" -> NoteName.B
		"0", "REST" -> NoteName.REST

		else -> {
			System.err.println("Unknown note name: $noteName")
			System.err.println("This note has been replaced by a rest.")
			NoteName.REST
		}
	}
}