import org.lwjgl.openal.AL
import org.lwjgl.openal.ALC

import javax.swing.*
import java.awt.*
import java.io.File
import java.io.IOException

import org.lwjgl.openal.AL10.*
import org.lwjgl.openal.ALC10.*
import kotlin.math.round

val sampleRate = 44_100
val c4Frequency = 256.0f

class Synthesiser(configFilePath: String) {
	private var parser: FileParser
	private var tuner: Tuner

	private var title: String
	private var channels: Array<Channel>

	private var stepTime: Float  // Milliseconds per time step.
	private var nSteps: Int  // Number of time steps.
	private var timeStep = 0  // Current position in the Note array.
	// todo: repeats

	private var bufferSize: Int  // Number of samples per buffer.
	private val bufferCount = 8
	private val buffers = IntArray(bufferCount) { 0 }
	private val device = alcOpenDevice(alcGetString(0, ALC_DEFAULT_DEVICE_SPECIFIER))
	private val context = alcCreateContext(device, IntArray(1) { 0 })
	private var source: Int
	private var bufferIndex: Int

	private var isRunning = false

	private val verbose: Boolean

	init {
		// todo: read config data.

		verbose = true
		parser = MusicXmlParser()
		tuner = FifthBasedTuner()
		title = ""
		channels = arrayOf()
		stepTime = 0.0f
		nSteps = 0
		bufferSize = 0
		source = 0
		bufferIndex = 0
	}

	fun testStart() {
//		val filePath = "music/2022-03-05_FrontierBrain.json"
		val filePath = "music/2021-12-08_PassacagliaInCMinor_Loop.musicxml"
		parser = MusicXmlParser(verbose)
		tuner = WellTemperamentTuner(verbose = verbose)

		val fileData = parser.parse(filePath)
		stepTime = fileData["STEP_TIME"]!!.toString().toFloat()
		nSteps = fileData["N_STEPS"]!!.toString().toInt()

		bufferSize = round(sampleRate * stepTime / 1000).toInt()
		channels = tuner.tune(fileData, bufferSize)

		alcMakeContextCurrent(context)
		AL.createCapabilities(ALC.createCapabilities(device))
		source = alGenSources()
		for (ii in 0 until bufferCount) {
			val currentIndex = buffers[ii]
			alBufferData(currentIndex, AL_FORMAT_MONO16, ShortArray(0) { 0 }, sampleRate)
			alSourceQueueBuffers(source, currentIndex)
		}
		alSourcePlay(source)

		while (timeStep < nSteps) {
			val processedBuffers = alGetSourcei(source, AL_BUFFERS_PROCESSED)
			for (buffer in 0 until processedBuffers) {
				for (channel in channels) {
					val nEvents = channel.getNote(timeStep).getNumberOfEvents()
					if (nEvents > 0) {
						for (ii in 0 until nEvents) {
							channel.handleNoteEvent(channel.getNote(timeStep).getNoteEvent(ii))
						}
					}
				}

				val samples = ShortArray(bufferSize)
				for (ii in samples.indices) {
					samples[ii] = 0
					for (channel in channels) {
						if (!channel.getNote(timeStep).isRest) {
							samples[ii] = (samples[ii] + (channel.nextWavePosition(timeStep) / channels.size)).toShort()
						}
						else if (channel.isPitched()) {
							channel.resetPhase()
						}
					}
				}

				alDeleteBuffers(alSourceUnqueueBuffers(source))
				buffers[bufferIndex] = alGenBuffers()
				val currentIndex = buffers[bufferIndex++]
				alBufferData(currentIndex, AL_FORMAT_MONO16, samples, sampleRate)
				alSourceQueueBuffers(source, currentIndex)
				bufferIndex %= bufferCount

				val output = Array<Char>(128) { ' ' }
				for (jj in channels.indices) {
					if (channels[jj].isPitched()) {
						if (!channels[jj].getNote(timeStep).isRest) {
//							output[channels[jj].getNote(timeStep).midiNumber.toInt()] = jj.digitToChar()
							output[channels[jj].getNote(timeStep).midiNumber.toInt()] = '#'
						}
					}
				}
				println(output.joinToString(separator = ""))

				timeStep++
				if (timeStep >= nSteps)
					break
			}

			if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
				alSourcePlay(source)
			}
		}

		alDeleteSources(source)
		alDeleteBuffers(buffers)
		alcDestroyContext(context)
		alcCloseDevice(device)
	}
}