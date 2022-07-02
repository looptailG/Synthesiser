fun main() {
//	println(0b11111111.toUByte())
//	println(0u or 0b11111111.toUByte().toUInt())
//	Synthesiser("").testStart()

	println(loadCsvFile("config/test.csv", commentDelimiter = "#", trim = true))
}