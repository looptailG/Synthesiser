import java.io.IOException
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

abstract class Tuner(protected val verbose: Boolean) {
	/**
	 * Return a Channel array, containing the data in input, tuned to the
	 * corresponding tuning system.
	 */
	abstract fun tune(fileData: Map<String, Any>, bufferSize: Int): Array<Channel>
}

open class FifthBasedTuner(private val fifthSize: Double = 700.0, verbose: Boolean = false): Tuner(verbose) {
	// todo: companion object.
	/**
	 * Maximum deviation in cents of the perfect fifth from the 12EDO one before
	 * an alert is triggered.  The tuner works even in that case, but there
	 * could be unexpected results.
	 */
	private val maxFifthDeviation = 15.0

	init {
		if (abs(fifthSize - 700.0) > maxFifthDeviation) {
			System.err.println(
				"The perfect fifth deviates significantly from the standard one: " + "%.2f".format(fifthSize)
			)
			System.err.println("This might cause some unexpected results, but won't stop the tuner from working.")
		}
	}

	/**
	 * Return a Channel array, containing the data in input, tuned to the
	 * corresponding tuning system.
	 */
	override fun tune(fileData: Map<String, Any>, bufferSize: Int): Array<Channel> {
		if (verbose) {
			print("+")
			for (ii in 0 until (26 + "%.2f".format(fifthSize).length))
				print("-")
			println("+")

			println("¦ TUNER - FIFTH BASED - " + "%.2f".format(fifthSize).replace(",", ".") + " ¢ ¦")

			print("+")
			for (ii in 0 until (26 + "%.2f".format(fifthSize).length))
				print("-")
			println("+")

			println("Fifth size: $fifthSize")
		}

		val partNames = (fileData["PART_NAMES"] ?: throw IOException("Missing \"PART_NAMES\" key."))
			.toString()
			.split("|")
		val nParts = partNames.size

		val timeSteps: Int
		try {
			timeSteps = (fileData["N_STEPS"] ?: throw IOException("Missing \"N_STEPS\" key."))
				.toString().toInt()
		}
		catch (ee: NumberFormatException) {
			ee.printStackTrace()
			throw IOException("Wrongly formatted \"N_STEPS\" value: ${fileData["N_STEPS"]}")
		}

		val returnValue = initialiseChannels(partNames, timeSteps, bufferSize)

		val notesData = (fileData["NOTES_DATA"] ?: throw IOException("Missing \"NOTES_DATA\" key."))
			.toString()
			.split("<part>")

		for (part in 0 until nParts) {
			var timeStep = 0
			val notes = notesData[part].split("|")
			for (ii in notes.indices) {
				val currentNote = notes[ii]

				// Format the note data.
				val noteData = currentNote.split("_")
				var note: String
				var noteName: NoteName
				var octave: Byte
				var alteration: Byte
				var duration: Int
				var isGlissando = false
				var isVibrato = false
				try {
					note = noteData[0]
					octave = noteData[1].toByte()
					alteration = noteData[2].toByte()
				}
				catch (ee: Exception) {
					if ((ee is NumberFormatException) || (ee is ArrayIndexOutOfBoundsException)) {
						ee.printStackTrace()
						System.err.println("Wrongly formatted note: $currentNote")
						System.err.println("This note has been replaced by a rest.")
						note = "0"
						octave = 4
						alteration = 0
					}
					else {
						throw ee
					}
				}
				try {
					duration = noteData[3].toInt()
				}
				catch (ee: Exception) {
					ee.printStackTrace()
					throw IOException("Wrongly formatted duration in the note: $currentNote")
				}
				noteName = stringToNoteName(note)

				// Handle note events.
				val eventList = mutableListOf<NoteEvent>()
				if (noteData.size > 4) {
					for (jj in 4 until noteData.size) {
						val eventString = noteData[jj]
						when (eventString.substring(0, 3)) {
							"DCC" -> {
								// Duty cycle change.
								try {
									val dutyCycle = eventString.substring(3).toFloat() / 256.0f
									eventList.add(DutyCycleChange(dutyCycle))
								}
								catch (ee: NumberFormatException) {
									ee.printStackTrace()
									System.err.println("Wrongly formatted note event: $eventString")
									System.err.println("No action will be performed.")
								}
							}

							"NSD" -> {
								// Noise step duration change.
								try {
									val stepDuration = eventString.substring(3).toInt()
									eventList.add(NoisePeriodChange(stepDuration))
								}
								catch (ee: NumberFormatException) {
									ee.printStackTrace()
									System.err.println("Wrongly formatted note event: $eventString")
									System.err.println("No action will be performed.")
								}
							}

							"OCH" -> {
								// Oscillator change.
								eventList.add(
									OscillatorChange(stringToOscillatorType(eventString.substring(3)))
								)
							}

							"VOL" -> {
								// Volume change.
								try {
									val volume = eventString.substring(3).toFloat() / 256.0f
									eventList.add(VolumeChange(volume))
								}
								catch (ee: NumberFormatException) {
									ee.printStackTrace()
									System.err.println("Wrongly formatted note event: $eventString")
									System.err.println("No action will be performed.")
								}
							}

							"GLI" -> {
								// Glissando.
								isGlissando = true
							}

							"VIB" -> {
								// Vibrato
								isVibrato = true
							}

							else -> {
								System.err.println("Unrecognised note event: $eventString")
								System.err.println("No action will be performed.")
							}
						}
					}
				}

				// Add the note.
				for (jj in 0 until duration) {
					// Calculate the cent offset from the C of this octave.
					// todo: check if it's correct for notest like Cb.
					val intervalFromC = Utils.mod(
						(fifths[noteName] ?: throw ArrayIndexOutOfBoundsException("Missing note: $noteName")) * fifthSize
								+ 7 * alteration * fifthSize,
						1200.0
					)
					var frequency = (c4Frequency * 2.0.pow(octave - 4 + intervalFromC / 1200.0)).toFloat()

					if (isGlissando) {
						try {
							// todo: should be calculated on cents, not on frequency.
							val targetNote = notes[ii + 1]
							// Format the note data.
							val targetData = targetNote.split("_")
							var targetNoteString: String
							var targetNoteName: NoteName
							var targetOctave: Byte
							var targetAlteration: Byte
							try {
								targetNoteString = targetData[0]
								targetOctave = targetData[1].toByte()
								targetAlteration = targetData[2].toByte()
							}
							catch (ee: NumberFormatException) {
								ee.printStackTrace()
								System.err.println("Wrongly formatted note: $targetNote")
								System.err.println("This note has been replaced by a rest.")
								targetNoteString = "0"
								targetOctave = 4
								targetAlteration = 0
							}
							targetNoteName = stringToNoteName(targetNoteString)

							if (targetNoteName != NoteName.REST) {
								// todo: check if it's correct for notes like Cb.
								val targetIntervalFromC = Utils.mod(
									(fifths[targetNoteName] ?: throw ArrayIndexOutOfBoundsException("Missing note: $targetNoteName")) * fifthSize
											+ 7 * targetAlteration * fifthSize,
									1200.0
								)
								val targetFrequency = (
										c4Frequency * 2.0.pow(targetOctave - 4 + targetIntervalFromC / 1200.0)
								).toFloat()

								frequency += (targetFrequency - frequency) * (jj.toFloat() / duration)
							}
						}
						catch (ee: ArrayIndexOutOfBoundsException) {
							ee.printStackTrace()
							System.err.println("Glissando is not supported on the last note of the piece.")
							isGlissando = false
						}
					}

					if (isVibrato) {
						// todo
					}

					returnValue[part].setNote(
						timeStep,
						frequency, isGlissando, isVibrato,
						calculateMidiNumber(noteName, octave, alteration), (noteName == NoteName.REST)
					)
					if (jj == 0) {
						for (event in eventList) {
							returnValue[part].addNoteEvent(timeStep, event)
						}
					}
					timeStep++
				}
			}
		}

		return returnValue
	}

}

class EDOTuner(private val nSteps: Int = 12, verbose: Boolean = false):
		FifthBasedTuner(fifthSize(nSteps), verbose) {
	override fun tune(fileData: Map<String, Any>, bufferSize: Int): Array<Channel> {
		if (verbose) {
			print("+")
			for (ii in 0 until (13 + nSteps.toString().length))
				print("-")
			println("+")

			println("¦ TUNER - $nSteps EDO ¦")

			print("+")
			for (ii in 0 until (13 + nSteps.toString().length))
				print("-")
			println("+")
		}

		println("Number of divisions of the octave: $nSteps")

		return super.tune(fileData, bufferSize)
	}
}

/**
 * Initialise the Channel array with the input part names.  If a part name is
 * not recognised, the Channel is initialised as a SQUARE wave oscillator
 * instead.
 */
private fun initialiseChannels(partNames: List<String>, nSteps: Int, bufferSize: Int): Array<Channel> {
	val returnValue = mutableListOf<Channel>()

	for (ii in partNames.indices) {
		when (partNames[ii]) {
			"BHASKARA" -> returnValue.add(Channel(OscillatorType.BHASKARA, nSteps, bufferSize))
			"CUBIC" -> returnValue.add(Channel(OscillatorType.CUBIC, nSteps, bufferSize))
			"NOISE" -> returnValue.add(Channel(OscillatorType.NOISE, nSteps, bufferSize))
			"PULSE" -> returnValue.add(Channel(OscillatorType.PULSE, nSteps, bufferSize))
			"SAW" -> returnValue.add(Channel(OscillatorType.SAW, nSteps, bufferSize))
			"SINE" -> returnValue.add(Channel(OscillatorType.SINE, nSteps, bufferSize))
			"SQUARE" -> returnValue.add(Channel(OscillatorType.SQUARE, nSteps, bufferSize))
			"TRIANGLE" -> returnValue.add(Channel(OscillatorType.TRIANGLE, nSteps, bufferSize))

			else -> {
				System.err.println("Unrecognised oscillator name: ${partNames[ii]}")
				System.err.println("This oscillator has been replaced by a SQUARE wave oscillator.")
				returnValue.add(Channel(OscillatorType.SQUARE, nSteps, bufferSize))
			}
		}
	}

	return returnValue.toTypedArray()
}

// todo: companion object?
/**
 * How many fifths the note is above or below C, in the circle of fifths.
 * Return 0 for a REST.
 */
private val fifths = mapOf(
	NoteName.C to 0,
	NoteName.D to 2,
	NoteName.E to 4,
	NoteName.F to -1,
	NoteName.G to 1,
	NoteName.A to 3,
	NoteName.B to 5,
	NoteName.REST to 0
)

// todo: companion object?
/**
 * Calculate the perfect fifth size of an EDO system, given the number of
 * divisions of the octave.
 */
private fun fifthSize(nSteps: Int): Double {
	// Size of the smallest step in this EDO system.
	val stepSize = 1200.0 / nSteps
	// Number of steps in a perfect fifth.
	val stepsPerFifth = round(700.0/ stepSize).toInt()
	return stepSize * stepsPerFifth
}