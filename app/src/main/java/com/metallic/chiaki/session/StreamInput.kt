package com.metallic.chiaki.session

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.*
import android.os.Build
import android.view.*
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.metallic.chiaki.R
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.touchcontrols.ButtonHaptics
import com.metallic.chiaki.touchcontrols.TouchpadView
import com.metallic.chiaki.touchcontrols.Vector
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlin.math.max

class StreamInput(val context: Context, val preferences: Preferences)
{
	var controllerStateChangedCallback: ((ControllerState) -> Unit)? = null

	val controllerState: ControllerState get()
	{
		val controllerState = sensorControllerState or keyControllerState or motionControllerState

		val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		@Suppress("DEPRECATION")
		when(windowManager.defaultDisplay.rotation)
		{
			Surface.ROTATION_90 -> {
				controllerState.accelX *= -1.0f
				controllerState.accelZ *= -1.0f
				controllerState.gyroX *= -1.0f
				controllerState.gyroZ *= -1.0f
				controllerState.orientX *= -1.0f
				controllerState.orientZ *= -1.0f
			}
			else -> {}
		}

		// prioritize motion controller's l2 and r2 over key
		// (some controllers send only key, others both but key earlier than full press)
		if(motionControllerState.l2State > 0U)
			controllerState.l2State = motionControllerState.l2State
		if(motionControllerState.r2State > 0U)
			controllerState.r2State = motionControllerState.r2State

		return controllerState or touchControllerState
	}

	private val sensorControllerState = ControllerState() // from Motion Sensors
	private val keyControllerState = ControllerState() // from KeyEvents
	private val motionControllerState = ControllerState() // from MotionEvents
	var touchControllerState = ControllerState()
		set(value)
		{
			field = value
			controllerStateUpdated()
		}

	private val swapCrossMoon = preferences.swapCrossMoon

	private val sensorEventListener = object: SensorEventListener {
		override fun onSensorChanged(event: SensorEvent)
		{
			when(event.sensor.type)
			{
				Sensor.TYPE_ACCELEROMETER -> {
					sensorControllerState.accelX = event.values[1] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelY = event.values[2] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelZ = event.values[0] / SensorManager.GRAVITY_EARTH
				}
				Sensor.TYPE_GYROSCOPE -> {
					sensorControllerState.gyroX = event.values[1]
					sensorControllerState.gyroY = event.values[2]
					sensorControllerState.gyroZ = event.values[0]
				}
				Sensor.TYPE_ROTATION_VECTOR -> {
					val q = floatArrayOf(0f, 0f, 0f, 0f)
					SensorManager.getQuaternionFromVector(q, event.values)
					sensorControllerState.orientX = q[2]
					sensorControllerState.orientY = q[3]
					sensorControllerState.orientZ = q[1]
					sensorControllerState.orientW = q[0]
				}
				else -> return
			}
			controllerStateUpdated()
		}

		override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
	}

	private val motionLifecycleObserver = object: LifecycleObserver {
		@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
		fun onResume()
		{
			val samplingPeriodUs = 4000
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			listOfNotNull(
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
			).forEach {
				sensorManager.registerListener(sensorEventListener, it, samplingPeriodUs)
			}
		}

		@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		fun onPause()
		{
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			sensorManager.unregisterListener(sensorEventListener)
		}
	}

	fun observe(lifecycleOwner: LifecycleOwner)
	{
		if(preferences.motionEnabled)
			lifecycleOwner.lifecycle.addObserver(motionLifecycleObserver)
	}

	private fun controllerStateUpdated()
	{
		controllerStateChangedCallback?.let { it(controllerState) }
	}

	fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		//Log.i("StreamSession", "key event $event")
		if(event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP)
			return false

		when(event.keyCode)
		{
			KeyEvent.KEYCODE_BUTTON_L2 -> {
				keyControllerState.l2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
				return true
			}
			KeyEvent.KEYCODE_BUTTON_R2 -> {
				keyControllerState.r2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
				return true
			}
		}

		val buttonMask: UInt = when(event.keyCode)
		{
			// dpad handled by MotionEvents
			//KeyEvent.KEYCODE_DPAD_LEFT -> ControllerState.BUTTON_DPAD_LEFT
			//KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
			//KeyEvent.KEYCODE_DPAD_UP -> ControllerState.BUTTON_DPAD_UP
			//KeyEvent.KEYCODE_DPAD_DOWN -> ControllerState.BUTTON_DPAD_DOWN
			KeyEvent.KEYCODE_BUTTON_A -> if(swapCrossMoon) ControllerState.BUTTON_MOON else ControllerState.BUTTON_CROSS
			KeyEvent.KEYCODE_BUTTON_B -> if(swapCrossMoon) ControllerState.BUTTON_CROSS else ControllerState.BUTTON_MOON
			KeyEvent.KEYCODE_BUTTON_X -> if(swapCrossMoon) ControllerState.BUTTON_PYRAMID else ControllerState.BUTTON_BOX
			KeyEvent.KEYCODE_BUTTON_Y -> if(swapCrossMoon) ControllerState.BUTTON_BOX else ControllerState.BUTTON_PYRAMID
			KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
			KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
			KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
			KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
			KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerState.BUTTON_SHARE
			KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
			KeyEvent.KEYCODE_BUTTON_C -> ControllerState.BUTTON_PS
			KeyEvent.KEYCODE_BUTTON_MODE -> ControllerState.BUTTON_PS
			else -> return false
		}

		keyControllerState.buttons = keyControllerState.buttons.run {
			when(event.action)
			{
				KeyEvent.ACTION_DOWN -> this or buttonMask
				KeyEvent.ACTION_UP -> this and buttonMask.inv()
				else -> this
			}
		}

		controllerStateUpdated()
		return true
	}

	fun onCapturedPointerEvent(event: MotionEvent?): Boolean{
		return true
	}

	fun onGenericMotionEvent(event: MotionEvent): Boolean
	{
		if (!event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK) && (event.action == MotionEvent.ACTION_BUTTON_PRESS || event.action == MotionEvent.ACTION_BUTTON_RELEASE || event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN)) {
			when (event.actionMasked) {

				MotionEvent.ACTION_BUTTON_PRESS -> {
					motionControllerState.buttons = motionControllerState.buttons or ControllerState.BUTTON_TOUCHPAD
					return true
				}

				MotionEvent.ACTION_BUTTON_RELEASE -> {
					motionControllerState.buttons = motionControllerState.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
					pointerTouches.remove(event.getPointerId(event.actionIndex))?.let {
						motionControllerState.stopTouch(it.stateId)
					}
					return true
				}

				MotionEvent.ACTION_DOWN -> {
					motionControllerState.startTouch(
						touchX(event, event.actionIndex),
						touchY(event, event.actionIndex)
					)?.let {
						val touch =
							Touch(it, event.getX(event.actionIndex), event.getY(event.actionIndex))
						pointerTouches[event.getPointerId(event.actionIndex)] = touch
					}
					return true
				}
				MotionEvent.ACTION_UP -> {
					pointerTouches.remove(event.getPointerId(event.actionIndex))?.let {
						motionControllerState.stopTouch(it.stateId)
					}
				}
				MotionEvent.ACTION_MOVE -> {
					val changed = pointerTouches.entries.fold(false) { acc, it ->
						val index = event.findPointerIndex(it.key)
						if (index < 0)
							acc
						else {
							it.value.onMove(
								event.getX(event.actionIndex),
								event.getY(event.actionIndex)
							)
							acc || motionControllerState.setTouchPos(
								it.value.stateId,
								touchX(event, index),
								touchY(event, index)
							)
						}
					}
				}
			}
		}
		else if (event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)){
			fun Float.signedAxis() = (this * Short.MAX_VALUE).toInt().toShort()
			fun Float.unsignedAxis() = (this * UByte.MAX_VALUE.toFloat()).toUInt().toUByte()

			motionControllerState.leftX = event.getAxisValue(MotionEvent.AXIS_X).signedAxis()
			motionControllerState.leftY = event.getAxisValue(MotionEvent.AXIS_Y).signedAxis()
			motionControllerState.rightX = event.getAxisValue(MotionEvent.AXIS_Z).signedAxis()
			motionControllerState.rightY = event.getAxisValue(MotionEvent.AXIS_RZ).signedAxis()
			motionControllerState.l2State = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).unsignedAxis()
			motionControllerState.r2State = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).unsignedAxis()
			motionControllerState.buttons = motionControllerState.buttons.let {
				val dpadX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
				val dpadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
				val dpadButtons =
					(if(dpadX > 0.5f) ControllerState.BUTTON_DPAD_RIGHT else 0U) or
							(if(dpadX < -0.5f) ControllerState.BUTTON_DPAD_LEFT else 0U) or
							(if(dpadY > 0.5f) ControllerState.BUTTON_DPAD_DOWN else 0U) or
							(if(dpadY < -0.5f) ControllerState.BUTTON_DPAD_UP else 0U)
				it and (ControllerState.BUTTON_DPAD_RIGHT or
						ControllerState.BUTTON_DPAD_LEFT or
						ControllerState.BUTTON_DPAD_DOWN or
						ControllerState.BUTTON_DPAD_UP).inv() or
						dpadButtons
			}
			//Log.i("StreamSession", "motionEvent => $motionControllerState")
			controllerStateUpdated()
		} else {
			return false
		}
		return true
	}


	//Touchpad handling modificato da me DIOCANE b
	companion object
	{
		private const val BUTTON_PRESS_MAX_MOVE_DIST_DP = 32.0f
		private const val SHORT_BUTTON_PRESS_DURATION_MS = 200L
		private const val BUTTON_HOLD_DELAY_MS = 500L
	}


	inner class Touch(
		val stateId: UByte,
		private val startX: Float,
		private val startY: Float)
	{
		var lifted = false // will be true but touch still in list when only relevant for short touch
		private var maxDist: Float = 0.0f
		val moveInsignificant: Boolean get() = maxDist < BUTTON_PRESS_MAX_MOVE_DIST_DP

		fun onMove(x: Float, y: Float)
		{
			val d = (Vector(x, y) - Vector(startX, startY)).length / context.resources.displayMetrics.density
			maxDist = max(d, maxDist)
		}

		val startButtonHoldRunnable = Runnable {
			if(!moveInsignificant || buttonHeld)
				return@Runnable
			motionControllerState.buttons = motionControllerState.buttons or ControllerState.BUTTON_TOUCHPAD
			buttonHeld = true
		}
	}
	private val pointerTouches = mutableMapOf<Int, Touch>()


	private var shortPressingTouches = listOf<Touch>()
	private val shortButtonPressLiftRunnable = Runnable {
		motionControllerState.buttons = motionControllerState.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
		shortPressingTouches.forEach {
			motionControllerState.stopTouch(it.stateId)
		}
		shortPressingTouches = listOf()
	}

	private var buttonHeld = false


	private fun touchX(event: MotionEvent, index: Int): UShort =
		maxOf(0U.toUShort(), minOf((ControllerState.TOUCHPAD_WIDTH - 1u).toUShort(),
			(ControllerState.TOUCHPAD_WIDTH.toFloat() * event.getX(index) / (context.resources.displayMetrics.widthPixels).toFloat()).toUInt().toUShort()))

	private fun touchY(event: MotionEvent, index: Int): UShort =
		maxOf(0U.toUShort(), minOf((ControllerState.TOUCHPAD_HEIGHT - 1u).toUShort(),
			(ControllerState.TOUCHPAD_HEIGHT.toFloat() * event.getY(index) / (context.resources.displayMetrics.heightPixels).toFloat()).toUInt().toUShort()))

	private fun triggerShortButtonPress(touch: Touch)
	{
		shortPressingTouches = shortPressingTouches + listOf(touch)
		motionControllerState.buttons = motionControllerState.buttons or ControllerState.BUTTON_TOUCHPAD
	}
}