class Channel(oscillatorType: OscillatorType, length: Int, private val bufferSize: Int) {
	private var phase = 0.0
	private var notes: Array<Note>
	private var oscillator: Oscillator
	private var volume = 1.0f
		/**
		 * Change the value of the volume.  If the volume is not in the interval
		 * (0, 1), print an error message and clamp the value to be between 0
		 * and 1.
		 */
		set(volume) {
			field = if (volume < 0.0f) {
				System.err.println("Invalid value for the volume: $volume")
				System.err.println("The volume has been clamped to 0.")
				0.0f
			}
			else if (volume > 1.0f) {
				System.err.println("Invalid value for the volume: $volume")
				System.err.println("The volume has been clamped to 1.")
				1.0f
			}
			else {
				volume
			}
		}

	private var glissandoCounter = 0

	init {
		notes = Array(length) { Note() }

		oscillator = when (oscillatorType) {
			OscillatorType.BHASKARA -> OscillatorBhaskara()
			OscillatorType.CUBIC -> OscillatorCubic()
			OscillatorType.NOISE -> OscillatorNoise()
			OscillatorType.PULSE -> OscillatorPulse()
			OscillatorType.SAW -> OscillatorSaw()
			OscillatorType.SINE -> OscillatorSine()
			OscillatorType.SQUARE -> OscillatorSquare()
			OscillatorType.TRIANGLE -> OscillatorTriangle()
		}
	}

	/**
	 * Advance the phase of this Channel according to the frequency of the Note
	 * at the specified time step, and return the next wave position.
	 */
	fun nextWavePosition(index: Int): Short {
		// todo: optimise.
		phase += notes[index].frequency / sampleRate
		if (notes[index].isGlissando) {
			phase += ((notes[index + 1].frequency - notes[index].frequency) / sampleRate
					* (glissandoCounter++ / bufferSize))
			glissandoCounter %= bufferSize
		}
		// todo: vibrato.
		if (phase > 1.0)
			phase %= 1.0

		return (oscillator.getWavePosition(phase) * volume).toInt().toShort()
	}

	/**
	 * Set the phase of this Channel to 0.
	 */
	fun resetPhase() {
		phase = 0.0
	}

	/**
	 * Initialise the Note at the specified position in the Note array.
	 */
	fun setNote(
		index: Int, frequency: Float, isGlissando: Boolean, isVibrato: Boolean,
		midiNumber: Byte, isRest: Boolean
	) {
		notes[index] = Note(
			frequency, isGlissando, isVibrato, midiNumber, isRest
		)
	}

	/**
	 * Return the Note at the specified time step.
	 */
	fun getNote(index: Int) = notes[index]

	/**
	 * Return true if the Oscillator is pitched.
	 */
	fun isPitched() = oscillator.isPitched

	/**
	 * Add the input NoteEvent to the Note at the specified time step.
	 */
	fun addNoteEvent(index: Int, event: NoteEvent) {
		notes[index].addNoteEvent(event)
	}

	/**
	 * Handle the input NoteEvent.
	 */
	fun handleNoteEvent(event: NoteEvent) {
		when (event) {
			is DutyCycleChange -> {
				if (oscillator is OscillatorPulse) {
					(oscillator as OscillatorPulse).setDutyCycle(event.dutyCycle)
				}
				else {
					System.err.println("Cannot change the duty cycle of a non PULSE oscillator.")
					System.err.println("No action was performed.")
				}
			}

			is NoisePeriodChange -> {
				if (oscillator is OscillatorNoise) {
					(oscillator as OscillatorNoise).setStepDuration(event.stepDuration)
				}
				else {
					System.err.println("Cannot change the noise step duration of a non NOISE oscillator.")
					System.err.println("No action was performed.")
				}
			}

			is OscillatorChange -> {
				oscillator = when (event.oscillatorType) {
					OscillatorType.BHASKARA -> OscillatorBhaskara()
					OscillatorType.CUBIC -> OscillatorCubic()
					OscillatorType.NOISE -> OscillatorNoise()
					OscillatorType.PULSE -> OscillatorPulse()
					OscillatorType.SAW -> OscillatorSaw()
					OscillatorType.SINE -> OscillatorSine()
					OscillatorType.SQUARE -> OscillatorSquare()
					OscillatorType.TRIANGLE -> OscillatorTriangle()
				}
			}

			is VolumeChange -> volume = event.volume
		}
	}
}