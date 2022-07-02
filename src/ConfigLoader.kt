import org.json.JSONObject
import java.io.File

/**
 * Load the content of the input .json file into the returned Map.  If there are
 * duplicate keys, use only the first occurrence in the file.
 */
fun loadJsonFile(filePath: String): Map<String, Any> {
//	val fileLines = File(filePath).readText().lines()
//	val fileData = JSONObject(fileLines.joinToString(separator = ""))
	val fileData = JSONObject(File(filePath).readText())

	val returnValue = mutableMapOf<String, Any>()
	for (key in fileData.keys()) {
		if (key !in returnValue.keys) {
			returnValue[key] = fileData[key]
		}
		else {
			System.err.println("Duplicate key in the file $filePath: $key")
			System.err.println("Only the value \"${returnValue[key]}\" will be used.")
		}
	}
	return returnValue
}


/**
 * Load the content of the input .csv file into the returned map.  If there are
 * duplicate keys, use only the first occurrence in the file.
 * @param filePath The file to read.
 * @param keyColumn The column to use as key for the returned map.
 * @param valueColumn The column to use as value for the returned map.
 * @param separator The separator between columns.
 * @param hasHeader If set to true, the first row of the file will be ignored.
 * @param stringDelimiter The string delimiter used in the file.  This can be
 * escaped by repeating it twice, for example by writing "" for the default "
 * character.
 * @param trim Whether the content of the file is to be trimmed, after dividing
 * it into columns.
 * @param commentDelimiter Ignore every row that starts with this sequence of
 * characters.  Leave empty in case there are no comments in the file.
 * @throws IllegalArgumentException In case the input file doesn't have the .csv
 * extension, or if it's wrongly formatted.
 */
fun loadCsvFile(
	filePath: String, keyColumn: Int = 0, valueColumn: Int = 1, separator: Char = ',',
	hasHeader: Boolean = false, stringDelimiter: Char = '"', trim: Boolean = false,
	commentDelimiter: String = ""
): Map<String, String> {
	if (!filePath.endsWith(".csv")) {
		throw IllegalArgumentException(
			"Invalid file extension for the file: $filePath\n"
			+ "Expected a .csv file."
		)
	}

	if (keyColumn < 0)
		throw IllegalArgumentException("Invalid key column value: $keyColumn")
	if (valueColumn < 0)
		throw IllegalArgumentException("Invalid value column value: $valueColumn")

	val returnValue = mutableMapOf<String, String>()
	val fileData = File(filePath).readText().lines().toMutableList()  // todo: error checking.
	val hasComments = (commentDelimiter != "")

	if (fileData.size > 1) {
		if (hasHeader)
			fileData.removeFirst()

		if (hasComments)
			fileData.removeIf { it.startsWith(commentDelimiter) }

		fileData.forEachIndexed { lineNumber, line ->
			val characters = line.toCharArray()
			val values = mutableListOf<String>()
			var currentValue = ""
			var stringMode = false
			val key: String
			val value: String

			var ii = 0
			while (ii < line.length) {
				if (characters[ii] == stringDelimiter) {
					// Check if it's a string mode toggle, or if it's an escaped
					// string delimiter.
					if ((ii < line.length - 1) && (characters[ii + 1] == stringDelimiter) && stringMode) {
						currentValue += stringDelimiter
						ii++
					}
					else {
						stringMode = !stringMode
					}
				}
				else if ((characters[ii] == separator) && !stringMode) {
					values += if (trim) currentValue.trim() else currentValue
					currentValue = ""
				}
				else {
					currentValue += characters[ii]
				}

				ii++
			}
			// Add the last value to the list.  This wasn't added earlier
			// because the values are only added when we encounter the
			// separator.
			values += if (trim) currentValue.trim() else currentValue
			// If we're still in string mode at the end of the line, the file is
			// wrongly formatted.
			if (stringMode) {
				throw IllegalArgumentException(
					"Wrongly formatted line nº ${lineNumber + 1} in the file $filePath:\n"
					 + line
				)
			}

			if (keyColumn >= values.size) {
				throw IllegalArgumentException(
					"Not enough columns at line nº ${lineNumber + 1} for key column = $keyColumn:\n"
					+ line
				)
			}
			if (valueColumn >= values.size) {
				throw IllegalArgumentException(
					"Not enough columns at line nº ${lineNumber + 1} for value column = $valueColumn:\n"
					+ line
				)
			}
			key = values[keyColumn]
			value = values[valueColumn]
			if (key in returnValue.keys) {
				System.err.println("WARNING: Duplicate key in the file $filePath: $key")
				System.err.println("         Only the first occurrence in the file will be used.")
			}
			else {
				returnValue[key] = value
			}
		}
	}

	if (returnValue.isEmpty()) {
		System.err.println("WARNING: No data was loaded from the file $filePath.")
	}
	return returnValue
}