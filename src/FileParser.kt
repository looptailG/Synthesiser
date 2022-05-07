import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The implementations of this class contain the methods necessaries to parse a
 * specific type of file, to return the following information into a Map:
 *
 * • TITLE
 *
 * • STEP_TIME
 *
 * • N_STEPS
 *
 * • TODO: REPEATS
 *
 * • PART_NAMES
 *
 * • NOTES_DATA
 */
abstract class FileParser(protected val verbose: Boolean = false) {
	/**
	 * Parse the input audio file, and return a Map containing the file data.
	 */
	abstract fun parse(inputFile: String): Map<String, Any>
}

class MusicXmlParser(verbose: Boolean = false): FileParser(verbose) {
	/**
	 * Parse the input audio file, and return a Map containing the file data.
	 */
	override fun parse(inputFile: String): Map<String, Any> {
		if (verbose) {
			println("+-------------------------+")
			println("¦ FILE PARSER - MUSIC XML ¦")
			println("+-------------------------+")
		}

		val returnValue = mutableMapOf<String, Any>()

		if (verbose)
			println("Input file: $inputFile")
		val fileData = File(inputFile)
			.readText()
			.lines()
			.map { it.trim() }

		// TITLE
		var title = ""
		for (line in fileData) {
			if ("<work-title>" in line) {
				title = line.substring(
					"<work-title>".length,
					line.length - "</work-title>".length
				)
				break
			}
		}
		if (title.isBlank())
			System.err.println("Missing <work-title> attribute.")
		returnValue["TITLE"] = title
		if (verbose)
			println("Title: $title")

		var nChannels = 0
		for (line in fileData) {
			if ("<score-part " in line) {
				nChannels++
			}
		}
		if (verbose)
			println("Number of channels: $nChannels")

		// PART_NAMES
		val partNames = mutableListOf<String>()
		if (verbose)
			println("Part names:")
		for (line in fileData) {
			if ("<part-name>" in line) {
				val partName = line.substring(
					"<part-name>".length,
					line.length - "</part-name>".length
				)
				if (verbose)
					println("\t$partName")

				if (partName in supportedPartNames) {
					partNames.add(partName)
				}
				else if (false) {
					// todo: check for assignment based on midi name.
				}
				else {
					System.err.println("Unsupported part name at line:\n$line")
					System.err.println("This part has been replaced with a SQUARE oscillator.")
					partNames.add("SQUARE")
				}
			}
		}
		returnValue["PART_NAMES"] = partNames.joinToString(separator = "|")

		// The tempo and time signature are read from the first occurrence in
		// the file only, and assumed to be the same for every part.
		var scaleFactor = 0.0f
		for (line in fileData) {
			if ("<beat-unit>" in line) {
				val beatUnit = line.substring(
					"<beat-unit>".length,
					line.length - "</beat-unit>".length
				)
				when (beatUnit) {
					"quarter" -> scaleFactor = 1.0f

					// todo: add more cases.

					else -> throw IOException("Unrecognised beat unit: $beatUnit")
				}
				break
			}
		}
		if (scaleFactor == 0.0f)
			throw IOException("Missing <beat-unit> attribute.")

		var beatsPerMinute = 0
		for (line in fileData) {
			if ("<per-minute>" in line) {
				try {
					beatsPerMinute = line.substring(
						"<per-minute>".length,
						line.length - "</per-minute>".length
					).toInt()
					if (verbose)
						println("Beats per minute: $beatsPerMinute")
					break
				}
				catch (ee: NumberFormatException) {
					ee.printStackTrace()
					throw IOException("Wrongly formatted <per-minute> attribute at line:\n$line")
				}
			}
		}
		if (beatsPerMinute == 0)
			throw IOException("Missing <per-minute> attribute.")

		var quarterNoteDivisions = 0
		for (line in fileData) {
			if ("<divisions>" in line) {
				try {
					quarterNoteDivisions = line.substring(
						"<divisions>".length,
						line.length - "</divisions>".length
					).toInt()
					if (verbose)
						println("Quarter note divisions: $quarterNoteDivisions")
					break
				}
				catch (ee: NumberFormatException) {
					ee.printStackTrace()
					throw IOException("Wrongly formatted <divisions> attribute at line:\n$line")
				}
			}
		}
		if (quarterNoteDivisions == 0)
			throw IOException("Missing <divisions> attribute.")

		// STEP_TIME
		val stepTime = 60_000.0f * scaleFactor / beatsPerMinute / quarterNoteDivisions
		if (verbose)
			println("Time step duration: $stepTime")
		returnValue["STEP_TIME"] = stepTime

		var beatsPerMeasure = 0
		for (line in fileData) {
			if ("<beats>" in line) {
				try {
					beatsPerMeasure = line.substring(
						"<beats>".length,
						line.length - "</beats>".length
					).toInt()
					if (verbose)
						println("Beats per measure: $beatsPerMeasure")
					break
				}
				catch (ee: NumberFormatException) {
					ee.printStackTrace()
					throw IOException("Wrongly formatted <beats> attribute at line:\n$line")
				}
			}
		}
		if (beatsPerMeasure == 0)
			throw IOException("Missing <beat> attribute.")

		var beatType = 0
		for (line in fileData) {
			if ("<beat-type>" in line) {
				try {
					beatType = line.substring(
						"<beat-type>".length,
						line.length - "</beat-type>".length
					).toInt()
					if (verbose)
						println("Beat type: $beatType")
					break
				}
				catch (ee: NumberFormatException) {
					ee.printStackTrace()
					throw IOException("Wrongly formatted <beat-type> attribute at line:\n$line")
				}
			}
		}
		if (beatType == 0)
			throw IOException("Missing <beat-type> attribute.")

		var nMeasures = 0
		for (line in fileData) {
			if ("<measure " in line) {
				nMeasures++
			}
		}
		if ((nMeasures % nChannels) != 0)
			throw IOException("Number of <measure> attributes not divisible by the number of channels.")
		nMeasures /= nChannels
		if (verbose)
			println("Number of measures: $nMeasures")

		// N_STEPS
		val stepsPerMeasure = beatsPerMeasure / (beatType / 4) * quarterNoteDivisions
		val nSteps = nMeasures * stepsPerMeasure
		returnValue["N_STEPS"] = nSteps
		if (verbose)
			println("Number of time steps: $nSteps")

		// todo: repeats.

		// NOTE_DATA
		var ii = 0
		var currentPart = -1
		var noteData = ""
		// Last note read, necessary for the automatic noise step duration note
		// event.
		var lastNote = ""
		var noteEvent = ""
		while (ii < fileData.size) {
			// Change of channel.
			if ("<part id=\"P" in fileData[ii]) {
				noteData += "<part>"
				currentPart++
				lastNote = ""
				ii++
			}

			// Read the text to look for note events.
			if ("<words " in fileData[ii]) {
				noteEvent = fileData[ii].substring(
					fileData[ii].indexOf('>') + 1,  // todo: might not work if there is a > in the text.
					fileData[ii].length - "</words>".length
				)
				// Check if it's a note event, or some other kind of text to
				// ignore.
				if (!noteEvent.startsWith("_"))
					noteEvent = ""
				// todo: check if I can do ii++ here.
			}

			// Read note data.
			if ("<note" in fileData[ii]) {
				ii++

				var noteName = "0"
				var octave = "4"
				var alteration = "0"
				var duration = ""
				while ("</note>" !in fileData[ii]) {
					// todo: probably I can do ii++ in some places.

					if ("rest" in fileData[ii])
						noteName = "0"
					else if ("<step>" in fileData[ii]) {
						// Pitched staff.
						noteName = fileData[ii].substring(
							"<step>".length,
							fileData[ii].length - "</step>".length
						)
					}
					else if ("<display-step>" in fileData[ii]) {
						// Un-pitched staff.
						noteName = fileData[ii].substring(
							"<display-step>".length,
							fileData[ii].length - "</display-step>".length
						)
					}

					if ("<octave>" in fileData[ii]) {
						octave = fileData[ii].substring(
							"<octave>".length,
							fileData[ii].length - "</octave>".length
						)
					}

					if ("<alter>" in fileData[ii]) {
						alteration = fileData[ii].substring(
							"<alter>".length,
							fileData[ii].length - "</alter>".length
						)
					}

					if ("<duration>" in fileData[ii]) {
						duration = fileData[ii].substring(
							"<duration>".length,
							fileData[ii].length - "</duration>".length
						)
					}

					ii++
				}

				// If the oscillator is a NOISE oscillator, check if it's
				// necessary to add a noise step duration change event in case
				// the current note is different from the last one that was
				// inserted.  The step duration is chosen based on the note name
				// only, regardless of alteration and octave.
				// todo: check for oscillator change?
				if (
					partNames[currentPart] == "NOISE"
					&& noteName != lastNote
					&& noteName != "0"
				) {
					when (noteName) {
						"E" -> noteEvent += "_NSD64"  // Bass drum 2.
						"F" -> noteEvent += "_NSD32"  // Bass drum.
						"A" -> noteEvent += "_NSD16"  // Tom.
						"C" -> noteEvent += "_NSD4"  // Snare.
						"G" -> noteEvent += "_NSD1"  // Hi-hat.

						else -> {
							if (verbose)
								println("No event will be performed for the note $noteName for the NOISE oscillator.")
						}
					}
				}

				// Add the note.
				noteData += "|${noteName}_${octave}_${alteration}_$duration$noteEvent"
				if (noteName != "0")
					lastNote = noteName
				noteEvent = ""
			}

			ii++
		}
		returnValue["NOTES_DATA"] = noteData
			// Remove the | before the first note of each part.
			.replace("<part>|", "<part>")
			// Remove the <part> at the beginning of the String.
			.substring("<part>".length)
		// todo: check that the total duration of every part sums up to the correct amount.

		return returnValue
	}
}

class JsonParser(verbose: Boolean = false): FileParser(verbose) {
	/**
	 * Parse the input audio file, and return a Map containing the file data.
	 */
	override fun parse(inputFile: String): Map<String, Any> {
		if (verbose) {
			println("+--------------------+")
			println("¦ FILE PARSER - JSON ¦")
			println("+--------------------+")
		}

		val returnValue = mutableMapOf<String, Any>()

		if (verbose) {
			println("Input file: $inputFile")
		}
		val fileData = JSONObject(File(inputFile).readText())
		for (key in fileData.keySet()) {
			// Don't print the note data, usually it's very long, and can create
			// lag in the output console.
			if (verbose && (key != "NOTES_DATA")) {
				println("$key: ${fileData[key]}")
			}
			returnValue[key] = fileData[key]
		}

		// Check that the parts have the correct number of steps.
		val nSteps: Int
		try {
			nSteps = returnValue["N_STEPS"].toString().toInt()
		}
		catch (ee: NumberFormatException) {
			ee.printStackTrace()
			throw IOException("Wrongly formatted \"N_STEPS\" value: ${returnValue["N_STEPS"]}")
		}
		returnValue["NOTES_DATA"].toString()
			.split("<part>")
			.forEachIndexed { partCounter, notesData ->
				var stepCounter = 0
				for (note in notesData.split("|")) {
					try {
						stepCounter += note.split("_")[3].toInt()
					}
					catch (ee: NumberFormatException) {
						throw IOException("Wrongly formatted note in the part Nº $partCounter: $note")
					}
				}
				if (stepCounter != nSteps) {
					throw IOException(
						"Incorrect number of steps in the part Nº $partCounter:\n"
						+ "N_STEPS: $nSteps\n"
						+ "Part Nº $partCounter: $stepCounter"
					)
				}
			}

		return returnValue
	}

}

val supportedPartNames = setOf(
	"BHASKARA",
	"CUBIC",
	"NOISE",
	"PULSE",
	"SAW",
	"SINE",
	"SQUARE",
	"TRIANGLE"
)