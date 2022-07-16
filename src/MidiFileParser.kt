import java.io.File

class MidiFileParser(verbose: Boolean = false): FileParser(verbose) {
	private var fileData = listOf<UByte>()
	/** Current position in the fileData list. */
	private var index = 0

	private var tempo = 0u
	private var bpm = 0

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
			println("Header length: $headerLength")
			println("Format: $format")
			println("Track chunks: $trackChunks")
			println("Divisions: $divisions")
		}

		// Read track data.
		for (chunk in 0 until trackChunks.toInt()) {
			if (verbose)
				println("-- Track $chunk --")

			// Read track header.
			val trackID = read32()
			val trackLength = read32()
			if (verbose) {
				println("Track ID: $trackID")
				println("Track length: $trackLength")
			}

			var endOfTrack = false
			var previousStatus: UByte = 0u
			while ((index < fileData.size) && !endOfTrack) {
				// Read timecode.
				var statusTimeDelta = readValue()
				var status = readByte()

				// If the most significant bit is not set, we use the previous
				// status.
				if (status < 0x80u) {
					status = previousStatus
					// With this implementation we read a byte that we shouldn't
					// have, go back one step in the fileData list.
					index--
				}

				when (status and 0x80u) {
					VOICE_NOTE_OFF -> {
						previousStatus = status
						println("NOTE OFF")

						val channel = status and 0x0Fu
						val noteID = readByte()
						val noteVelocity = readByte()

						println("Channel: $channel")
						println("Note ID: $noteID")
						println("Velocity: $noteVelocity")
						println("Time delta: $statusTimeDelta")
					}

					VOICE_NOTE_ON -> {
						previousStatus = status
						println("NOTE ON")

						val channel = status and 0x0Fu
						val noteID = readByte()
						val noteVelocity = readByte()

						println("Channel: $channel")
						println("Note ID: $noteID")
						println("Velocity: $noteVelocity")
						println("Time delta: $statusTimeDelta")
					}

					VOICE_AFTER_TOUCH -> {
						previousStatus = status
						println("VOICE AFTER TOUCH")

						val channel = status and 0x0Fu
						val noteID = readByte()
						val noteVelocity = readByte()
					}

					VOICE_CONTROL_CHANGE -> {
						previousStatus = status
						println("VOICE CONTROL CHANGE")

						val channel = status and 0x0Fu
						val controlID = readByte()
						val controlValue = readByte()
					}

					VOICE_PROGRAM_CHANGE -> {
						previousStatus = status
						println("VOICE PROGRAM CHANGE")

						val channel = status and 0x0Fu
						val programID = readByte()
					}

					VOICE_CHANNEL_PRESSURE -> {
						previousStatus = status
						println("VOICE CHANNEL PRESSURE")

						val channel = status and 0x0Fu
						val channelPressure = readByte()
					}

					VOICE_PITCH_BEND -> {
						previousStatus = status
						println("VOICE PITCH BEND")

						val channel = status and 0x0Fu
						val LS7B = readByte()
						val MS7B = readByte()
					}

					SYSTEM_EXCLUSIVE -> {
						previousStatus = 0u

						if (status.toUInt() == 0xFFu) {
							// Meta message.
							val type = readByte()
							val length = readValue()

							when (type) {
								META_SEQUENCE -> {
									println("Sequence number: ${readByte()}${readByte()}")
								}

								META_TEXT -> {
									println("Text: ${readString(length)}")
								}

								META_COPYRIGHT -> {
									println("Copyright: ${readString(length)}")
								}

								META_TRACK_NAME -> {
									println("Track name: ${readString(length)}")
								}

								META_INSTRUMENT_NAME -> {
									println("Instrument name: ${readString(length)}")
								}

								META_LYRICS -> {
									println("Lyrics: ${readString(length)}")
								}

								META_MARKER -> {
									println("Marker: ${readString(length)}")
								}

								META_CUE_POINT -> {
									println("Cue: ${readString(length)}")
								}

								META_CHANNEL_PREFIX -> {
									println("Prefix: ${readByte()}")
								}

								META_END_OF_TRACK -> {
									endOfTrack = true
								}

								META_SET_TEMPO -> {
									// Tempo is in μs per quarter note.
									if (tempo == 0u) {
										tempo = (readByte().toUInt() and 0x00_00_00_FFu) shl 16
										tempo = tempo or ((readByte().toUInt() and 0x00_00_00_FFu) shl 8)
										tempo = tempo or (readByte().toUInt() and 0x00_00_00_FFu)
										bpm = 60_000_000 / tempo.toInt()
										println("Tempo: $tempo - BPM: $bpm")
									}
								}

								META_SMPTE_OFFSET -> {
									println("SMPTE: H: ${readByte()} - M: ${readByte()} - S: ${readByte()} - FR: ${readByte()}")
								}

								META_TIME_SIGNATURE -> {
									println("Time signature: ${readByte()}/${1 shl readByte().toInt()}")
									println("Clocks per tick: ${readByte()}")
									println("32per24Clocks: ${readByte()}")
								}

								META_KEY_SIGNATURE -> {
									println("Key signature: ${readByte()}")
									println("Minor key: ${readByte()}")
								}

								META_SEQUENCER_SPECIFIC -> {
									println("Sequencer specific: ${readString(length)}")
								}

								else -> {
									System.err.println("Unrecognised meta event: $type")
								}
							}
						}
						else if (status.toUInt() == 0xF0u) {
							// System exclusive message begins.
							println("System exclusive begin: ${readString(readValue())}")
						}
						else if (status.toUInt() == 0xF7u) {
							// System exclusive message ends.
							println("System exclusive end: ${readString(readValue())}")
						}
					}

					else -> {
						System.err.println("Unrecognised status byte: $status")
					}
				}
			}
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
	private fun readString(length: UInt): String {
		// todo: check for unicode values outside the ascii set.
		var returnValue = ""
		for (ii in 0u until length) {
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
private const val VOICE_NOTE_OFF: UByte = 0x80u
private const val VOICE_NOTE_ON: UByte = 0x90u
private const val VOICE_AFTER_TOUCH: UByte = 0xA0u
private const val VOICE_CONTROL_CHANGE: UByte = 0xB0u
private const val VOICE_PROGRAM_CHANGE: UByte = 0xC0u
private const val VOICE_CHANNEL_PRESSURE: UByte = 0xD0u
private const val VOICE_PITCH_BEND: UByte = 0xE0u
private const val SYSTEM_EXCLUSIVE: UByte = 0xF0u

// Meta events.
private const val META_SEQUENCE: UByte = 0x00u
private const val META_TEXT: UByte = 0x01u
private const val META_COPYRIGHT: UByte = 0x02u
private const val META_TRACK_NAME: UByte = 0x03u
private const val META_INSTRUMENT_NAME: UByte = 0x04u
private const val META_LYRICS: UByte = 0x05u
private const val META_MARKER: UByte = 0x06u
private const val META_CUE_POINT: UByte = 0x07u
private const val META_CHANNEL_PREFIX: UByte = 0x20u
private const val META_END_OF_TRACK: UByte = 0x2Fu
private const val META_SET_TEMPO: UByte = 0x51u
private const val META_SMPTE_OFFSET: UByte = 0x54u
private const val META_TIME_SIGNATURE: UByte = 0x58u
private const val META_KEY_SIGNATURE: UByte = 0x59u
private const val META_SEQUENCER_SPECIFIC: UByte = 0x7Fu