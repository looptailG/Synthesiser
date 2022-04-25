interface NoteEvent

class DutyCycleChange(val dutyCycle: Float = 0.125f): NoteEvent

class NoisePeriodChange(val stepDuration: Int = 1): NoteEvent

class OscillatorChange(val oscillatorType: OscillatorType = OscillatorType.SQUARE): NoteEvent

class VolumeChange(val volume: Float = 1.0f): NoteEvent