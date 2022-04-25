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