import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// todo: fix this comment.
/**
 * Normalization constant necessary to have each Oscillator type have the
 * same volume.  It's calculated as follows:
 *
 * NORMALIZATION = √(I₀ / ∫ƒ(x)²dx),
 *
 * where ƒ(x) is the amplitude of the Oscillator waveform and I₀ is the
 * minimum value of ∫ƒ(x)²dx among every supported waveform.  As of right
 * now the minimum value is for Oscillator_Noise, with I₀ = 1/4.
 * This value is then to be multiplied by Short.MAX_VALUE, to get a value in
 * the correct range.
 */
sealed class Oscillator(val isPitched: Boolean, val normalization: Short) {
	/**
	 * Return the wave position for the specified phase.
	 */
	abstract fun getWavePosition(phase: Double): Short
}

// todo: check the normalization.
class OscillatorBhaskara: Oscillator(true, (0.707_544f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		// todo: write the equation.
		return if (phase < 0.5) {
			(normalization * 16 * phase * (0.5 - phase)
					/ (1.25 - 4 * phase * (0.5 - phase))).toInt().toShort()
		}
		else {
			(normalization * 16 * (phase - 0.5) * (phase - 1)
					/ (1.25 - 4 * (phase - 0.5) * (1 - phase))).toInt().toShort()
		}
	}

}

// todo: check the normalization.
class OscillatorCubic: Oscillator(true, (0.514_305f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		// 20.785 × (phase - 0) × (phase - 0.5) × (phase - 1)
		// Polynomial that has the same roots of sin(2π × phase) in [0, 1], with
		// a multiplicative constant in order to have the maximum and minimum
		// have a value of ±1.
		return (normalization * phase * (phase * (20.785 * phase - 31.1775) + 10.3925)).toInt().toShort()
	}

}

class OscillatorNoise(
	/**
	 * After how many samples the Oscillator will produce a new random wave
	 * position.
	 */
	private var stepDuration: Int = 1
): Oscillator(false, (1.000_000f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Last wave position that has been generated.
	 */
	private var wavePosition: Short = 0
	/**
	 * How many samples have been generated since the last time this Oscillator
	 * produced a new wave position.
	 */
	private var stepCounter: Int = 0

	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		stepCounter++
		if (stepCounter >= stepDuration) {
			stepCounter -= stepDuration
			wavePosition = (normalization * (2 * random.nextFloat() - 1)).toInt().toShort()
		}
		return wavePosition
	}

	/**
	 * Set the value of the step duration.  If the value is negative, print an
	 * error message, and keep the step duration unchanged.
	 */
	fun setStepDuration(stepDuration: Int) {
		if (stepDuration > 0) {
			this.stepDuration = stepDuration
		}
		else {
			System.err.println("Invalid value for the noise step duration: $stepDuration")
			System.err.println("The step duration has not been modified.")
		}
	}
}

class OscillatorPulse(private var dutyCycle: Float = 0.125f):
		Oscillator(true, (0.500_000f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		return if (phase < dutyCycle)
			normalization
		else
			(-normalization).toShort()
	}

	/**
	 * Set the value of the duty cycle.  If the value is outside the interval
	 * (0, 1), print an error message and clamp the value to be between 0 and 1.
	 */
	@JvmName("setDutyCycle1")  // todo: improve.
	fun setDutyCycle(dutyCycle: Float) {
		if (dutyCycle < 0.0f) {
			System.err.println("Invalid value for the duty cycle: $dutyCycle")
			System.err.println("The duty cycle has been clamped to 0.")
			this.dutyCycle = 0.0f
		}
		else if (dutyCycle > 1.0f) {
			System.err.println("Invalid value for the duty cycle: $dutyCycle")
			System.err.println("The duty cycle has been clamped to 1.")
			this.dutyCycle = 1.0f
		}
		else {
			this.dutyCycle = dutyCycle
		}
	}
}

class OscillatorSaw: Oscillator(true, (0.866_025f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		return (normalization * (1 - 2 * phase)).toInt().toShort()
	}
}

class OscillatorSine: Oscillator(true, (0.707_107f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		return (normalization * sin(2 * PI * phase)).toInt().toShort()
	}
}

class OscillatorSquare: Oscillator(true, (0.500_000f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		return if (phase < 0.5)
			normalization
		else
			(-normalization).toShort()
	}
}

class OscillatorTriangle: Oscillator(true, (0.866_025f * Short.MAX_VALUE).toInt().toShort()) {
	/**
	 * Return the wave position for the specified phase.
	 */
	override fun getWavePosition(phase: Double): Short {
		return (normalization * (4 * abs(Utils.mod(phase - 0.25, 1.0) - 0.5) - 1)).toInt().toShort()
	}
}

/**
 * Supported Oscillator types.
 */
enum class OscillatorType {
	BHASKARA,
	CUBIC,
	NOISE,
	PULSE,
	SAW,
	SINE,
	SQUARE,
	TRIANGLE
}

/**
 * Return the OscillatorType corresponding to the input String.  If no
 * OscillatorType match, print an error message and return SQUARE.
 */
fun stringToOscillatorType(oscillatorType: String): OscillatorType {
	return when (oscillatorType) {
		"BHASKARA" -> OscillatorType.BHASKARA
		"CUBIC" -> OscillatorType.CUBIC
		"NOISE" -> OscillatorType.NOISE
		"PULSE" -> OscillatorType.PULSE
		"SAW" -> OscillatorType.SAW
		"SINE" -> OscillatorType.SINE
		"SQUARE" -> OscillatorType.SQUARE
		"TRIANGLE" -> OscillatorType.TRIANGLE

		else -> {
			System.err.println("Unrecognise oscillator type: $oscillatorType")
			System.err.println("This oscillator has been replaced by a SQUARE oscillator.")
			OscillatorType.SQUARE
		}
	}
}

private val random = java.util.Random()