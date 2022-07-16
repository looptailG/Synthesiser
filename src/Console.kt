import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.io.OutputStream
import java.io.PrintStream
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.Document
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class Console(
	consoleName: String = "Output Console", width: Int = 800, height: Int = 300,
	redirectOutput: Boolean = true, redirectError: Boolean = true,
	foregroundColor: Color = Color.WHITE, backgroundColor: Color = Color.BLACK, errorColor: Color = Color.RED,
	font: Font = Font(Font.MONOSPACED, Font.PLAIN, 12)
): JFrame(consoleName) {
	init {
		defaultCloseOperation = DISPOSE_ON_CLOSE
		isResizable = false

		val textPane = JTextPane()
		textPane.preferredSize = Dimension(width, height)
		textPane.isEditable = false
		textPane.background = backgroundColor
		textPane.font = font

		if (redirectOutput)
			System.setOut(PrintStream(CustomOutputStream(textPane, foregroundColor), true))
		if (redirectError)
			System.setErr(PrintStream(CustomOutputStream(textPane, errorColor), true))

		val scrollPane = JScrollPane(textPane)
		add(scrollPane)
		pack()
		setLocationRelativeTo(null)
		isVisible = true
	}
}

private class CustomOutputStream(
	private val textPane: JTextPane, color: Color
): OutputStream() {
	private val document: Document
	private val attributeSet: SimpleAttributeSet

	/**
	 * Temporary buffer for Unicode characters that are composed by more than
	 * one byte.
	 */
	private var buffer = 0

	/**
	 * How many bytes are still to be read to get the complete Unicode value.
	 */
	private var remainingBytes = 0

	init {
		document = textPane.styledDocument
		attributeSet = SimpleAttributeSet()
		StyleConstants.setForeground(attributeSet, color)
	}

	/**
	 * Writes the specified byte to this output stream.  The general contract
	 * for `write` is that one byte is written to the output stream.  The byte
	 * to be written is the eight low-order bits of the argument `bb`.  The 24
	 * high-order bits of `bb` are ignored.  If the input byte is the beginning
	 * of a group of bytes that constitute a non ASCII character in UTF8, wait
	 * for the next bytes and concatenate them together before printing the
	 * character.
	 */
	override fun write(bb: Int) {
		if ((bb and 0b1000_0000) == 0b0000_0000) {
			// ASCII character.
			buffer = bb
			remainingBytes = 0
		}
		else if ((bb and 0b1100_0000) == 0b1100_0000) {
			// Beginning of a UTF8 packet.
			if ((bb and 0b1110_0000) == 0b1100_0000) {
				// 2 bytes packet.
				buffer = bb and 0b0001_1111
				remainingBytes = 1
			}
			else if ((bb and 0b1111_0000) == 0b1110_0000) {
				// 3 bytes packet.
				buffer = bb and 0b0000_1111
				remainingBytes = 2
			}
			else {
				// 4 bytes packet.  Assuming that the data stream is properly
				// formatted, and not checking that the input is of the form
				// 0b1111_0xxxx.
				buffer = bb and 0b0000_0111
				remainingBytes = 3
			}
		}
		else {
			// Continuation of a UTF8 packet.  Assuming that the data stream is
			// properly formatted, and not checking that the input is of the
			// form 0b10xx_xxxx.
			buffer = buffer shl 6
			buffer = buffer or (bb and 0b0011_1111)
			remainingBytes--
		}

		if (remainingBytes == 0) {
			// No more bytes are necessary for this character, print it.
			try {
				document.insertString(document.length, Char(buffer).toString(), attributeSet)
				textPane.caretPosition = textPane.document.length
			}
			catch (ee: Exception) {
				JOptionPane.showMessageDialog(
					null, ee.message, "Console Error", JOptionPane.ERROR_MESSAGE
				)
			}
		}
	}

}