import java.io.File

class MidiFileParser(verbose: Boolean = false): FileParser(verbose) {
	private var fileData = listOf<UByte>()
	/** Current position in the fileData list. */
	private var index = 0

	/**
	 * Parse the input audio file, and return a Map containing the file data.
	 */
	override fun parse(inputFile: String): Map<String, Any> {
		if (verbose) {
			println("+--------------------+")
			println("¦ FILE PARSER - MIDI ¦")
			println("+--------------------+")
		}

		index = 0
		var n32: UInt
		var n16: UShort

		fileData = File(inputFile)
			.readBytes()
			.map { it.toUByte() }

		// Read Midi header (fixed size).
		val fileID = read32()
		val headerLength = read32()
		val format = read16()
		val trackChunks = read16()
		val divisions = read16()
		if (verbose) {
			println("File ID: $fileID")
			println("Header Length: $headerLength")
			println("Format: $format")
			println("Track chunks: $trackChunks")
			println("Divisions: $divisions")
		}

		// Read track data.
		for (chunk in 0 until trackChunks.toInt()) {

		}

		return mapOf()
	}

	/**
	 * Read a byte from fileData, and advance the index.
	 */
	private fun readByte(): UByte {
		return fileData[index++]
	}

	/**
	 * Read 32 bits from fileData, and advance the index accordingly.
	 */
	private fun read32(): UInt {
		var returnValue: UInt = 0u
		for (ii in 0 until 4) {
			returnValue shl 8
			returnValue = returnValue or readByte().toUInt()
		}
		return returnValue
	}

	/**
	 * Read 16 bits from fileData, and advance the index accordingly.
	 */
	private fun read16(): UShort {
		var returnValue: UInt = 0u
		for (ii in 0 until 2) {
			returnValue shl 8
			returnValue = returnValue or readByte().toUInt()
		}
		return returnValue.toUShort()
	}

	/**
	 * Read the specified number of bytes from fileData, and convert it to a
	 * String.
	 */
	private fun readString(length: Int): String {
		// todo: check for unicode values outside the ascii set.
		var returnValue = ""
		for (ii in 0 until length) {
			returnValue += readByte().toInt().toChar()
		}
		return returnValue
	}

	/**
	 *  Read a variable length value from fileData, and advance the index
	 *  accordingly.
	 */
	private fun readValue(): UInt {
		// Read byte.
		var returnValue = readByte().toUInt()
		// Check if the most significant bit is set.
		if ((returnValue and 0x00_00_00_80u) == 0x00_00_00_80u) {
			// Extract the bottom 7 bits.
			returnValue = returnValue and 0x00_00_00_7Fu

			// Read bytes until we find a byte which has the most significant
			// bit not set.
			var tmp: UByte
			do {
				// Read the next byte.
				tmp = readByte()
				// Add the bottom 7 bits to the return value.
				returnValue shl 7
				returnValue = returnValue or (tmp.toUInt() and 0x00_00_00_7Fu)
			}
			while ((tmp.toUInt() and 0x00_00_00_80u) == 0x00_00_00_80u)
		}
		return returnValue
	}
}

// Events.
private const val VOICE_NOTE_OFF = 0x80
private const val VOICE_NOTE_ON = 0x90
private const val VOICE_AFTER_TOUCH = 0xA0
private const val VOICE_CONTROL_CHANGE = 0xB0
private const val VOICE_PROGRAM_CHANGE = 0xC0
private const val VOICE_CHANNEL_PRESSURE = 0xD0
private const val VOICE_PITCH_BEND = 0xE0
private const val SYSTEM_EXCLUSIVE = 0xF0

// Meta events.
private const val META_SEQUENCE = 0x00;
private const val META_TEXT = 0x01;
private const val META_COPYRIGHT = 0x02;
private const val META_TRACK_NAME = 0x03;
private const val META_INSTRUMENT_NAME = 0x04;
private const val META_LYRICS = 0x05;
private const val META_MARKER = 0x06;
private const val META_CUE_POINT = 0x07;
private const val META_CHANNEL_PREFIX = 0x20;
private const val META_END_OF_TRACK = 0x2F;
private const val META_SET_TEMPO = 0x51;
private const val META_SMPTE_OFFSET = 0x54;
private const val META_TIME_SIGNATURE = 0x58;
private const val META_KEY_SIGNATURE = 0x59;
private const val META_SEQUENCER_SPECIFIC = 0x7F;