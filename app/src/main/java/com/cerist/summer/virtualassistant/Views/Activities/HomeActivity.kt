package com.cerist.summer.virtualassistant.Views.Activities


import android.Manifest
import android.arch.lifecycle.Observer
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.widget.*
import com.cerist.summer.virtualassistant.Entities.BroadLinkProfile
import com.cerist.summer.virtualassistant.Entities.ChatBotProfile
import com.cerist.summer.virtualassistant.Entities.LampProfile
import com.cerist.summer.virtualassistant.Utils.Functions.getViewModel
import com.cerist.summer.virtualassistant.R
import com.cerist.summer.virtualassistant.Utils.BaseComponents.BaseRecognitionActivity
import com.cerist.summer.virtualassistant.Utils.Functions.getStringResourceByName
import com.cerist.summer.virtualassistant.Views.Fragments.DialogFragment
import com.cerist.summer.virtualassistant.Utils.Repositories
import com.cerist.summer.virtualassistant.ViewModels.AirConditionerViewModel
import com.cerist.summer.virtualassistant.ViewModels.DialogViewModel
import com.cerist.summer.virtualassistant.ViewModels.LampViewModel
import com.cerist.summer.virtualassistant.ViewModels.TvViewModel
import com.cerist.summer.virtualassistant.Views.Fragments.CameraFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.disposables.Disposable
import java.util.*
import android.view.MotionEvent



class HomeActivity: BaseRecognitionActivity(),
        DialogFragment.AudioRecordingButtonTouchListener,
        TextToSpeech.OnInitListener{


    companion object {
        val TAG = "HomeActivity"
        val TAG_BLE = "BLE_CHECK"

    }

    private lateinit var popupWindow: PopupWindow
    private lateinit var popupWindowDimmer: PopupWindow

    private lateinit var inflater: LayoutInflater
    private lateinit var layoutContainer: LinearLayout
    private lateinit var container: ViewGroup
    private lateinit var dimmerContainer: ViewGroup

    private lateinit var lampTextView: TextView
    private lateinit var seekBar: SeekBar

    private lateinit var textViewTV_Volume: TextView
    private lateinit var textViewTV_State: TextView
    private lateinit var seekBarTV: SeekBar
    private lateinit var switchTV_State : Switch

    private lateinit var mLampViewModel:LampViewModel
    private lateinit var mTvViewModel:TvViewModel
    private lateinit var mAirConditionerViewModel:AirConditionerViewModel
    private lateinit var mDialogViewModel: DialogViewModel

    private lateinit var mTextToSpeech:TextToSpeech
    private lateinit var mSpeechRecognizer: SpeechRecognizer
    private lateinit var mSpeechRecognizerIntent :Intent
    private lateinit var rxPermissions:RxPermissions

    private lateinit var disposable:Disposable

    private var allowLampSpeak = true
    private var allowTvSpeak = true

    private var CURRENT_TV_VOLUME: Int? = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        Log.d(TAG,"onCreate")
        setContentView(R.layout.home_activity)

        // Check for Bluetooth Support
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this, "Device does not support bluetooth", Toast.LENGTH_LONG).show()
            Log.d(TAG_BLE, "Device does not support bluetooth")

        } else {
            if (!mBluetoothAdapter.isEnabled) {
                // Enable Bluetooth
                mBluetoothAdapter.enable()
                Toast.makeText(this, "Bluetooth switched ON", Toast.LENGTH_LONG).show()
                Log.d(TAG_BLE, "Bluetooth switched ON")

            } else {
                Log.d(TAG_BLE, "Bluetooth already ON")
            }
        }

        //Check for LE support
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No Blutooth Low Energy Support", Toast.LENGTH_SHORT).show()
            Log.d(TAG_BLE, "Bluetooth Low Energy Not supported")

            finish()
        } else {
            Toast.makeText(this, "Blutooth Low Energy is Supported in the Device", Toast.LENGTH_SHORT).show()
            Log.d(TAG_BLE, "Blutooth Low Energy is Supported in the Device")
        }


        //Init popup window
        layoutContainer = findViewById<View>(R.id.fragment_camera) as LinearLayout

        inflater = applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        container = inflater.inflate(R.layout.popup_window, null) as ViewGroup
        dimmerContainer = inflater.inflate(R.layout.popup_window_dimmer, null) as ViewGroup

        popupWindow = PopupWindow(container, 600, 300, true)
        popupWindow.animationStyle = R.style.Animation

        popupWindowDimmer = PopupWindow(dimmerContainer,600,360,true)
        popupWindowDimmer.animationStyle = R.style.Animation

        container.setOnTouchListener { view, motionEvent ->
            popupWindow.dismiss()
            true
        }

        dimmerContainer.setOnTouchListener{ view, motionEvent ->
            popupWindowDimmer.dismiss()
            true
        }

        textViewTV_Volume = container.findViewById<View>(R.id.textViewTV_Volume) as TextView
        textViewTV_State = container.findViewById<View>(R.id.textViewTV_State) as TextView
        seekBarTV = container.findViewById(R.id.seekBarTV) as SeekBar
        switchTV_State = container.findViewById(R.id.SwichTV_State)

        seekBarTV.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                textViewTV_Volume.setText("The TV Volume is "+progress)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mTvViewModel.setTvVolumeLevel(seekBar!!.progress)

            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }
        })

        switchTV_State.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener{
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                if(isChecked){
                    seekBarTV.setEnabled(true)
                    switchTV_State.setText("ON")
                    mTvViewModel.setTvPowerState(BroadLinkProfile.TvProfile.State.ON)
                }else{
                    seekBarTV.setEnabled(false)
                    switchTV_State.setText("OFF")
                    mTvViewModel.setTvPowerState(BroadLinkProfile.TvProfile.State.OFF)
                }
            }

        })

        lampTextView = dimmerContainer.findViewById<View>(R.id.lampTextView) as TextView
        seekBar = dimmerContainer.findViewById(R.id.seekBar) as SeekBar

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                allowLampSpeak = false
                when(progress){
                    0 -> {
                        mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf("NON"))
                        lampTextView.setText("The lamp is set to NON 0%")
                    }
                    1 -> {
                        mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf("LOW"))
                        lampTextView.setText("The lamp is set to LOW 25%")
                    }
                    2 -> {
                        mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf("MEDIUM"))
                        lampTextView.setText("The lamp is set to MEDIUM 50%")
                    }
                    3 -> {
                        mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf("HIGH"))
                        lampTextView.setText("The lamp is set to HIGH 75%")
                    }
                    4 -> {
                        mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf("MAX"))
                        lampTextView.setText("The lamp is set to MAX 100%")
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        //val button = dimmerContainer.findViewById<View>(R.id.btn) as Button
        /*button.setOnClickListener(object: View.OnClickListener{
            override fun onClick(v: View?) {
                //onDimmerChangelistener()
                Toast.makeText(applicationContext,"button pressed",Toast.LENGTH_SHORT).show()
                allowLampSpeak = false
                mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf("HIGH"))
                lampTextView.setText("The lamp is set to NON 0%")

            }
        })*/




        mDialogViewModel = getViewModel(this,Repositories.DIALOG_REPOSITORY) as DialogViewModel
        mLampViewModel  = getViewModel(this,Repositories.LAMP_REPOSITORY) as LampViewModel
        mTvViewModel  = getViewModel(this,Repositories.TV_REPOSITORY) as TvViewModel
        mAirConditionerViewModel = getViewModel(this,Repositories.AIR_CONDITIONER_REPOSITORY) as AirConditionerViewModel
        rxPermissions  = RxPermissions(this)


        disposable = rxPermissions.requestEach(
                     Manifest.permission.ACCESS_COARSE_LOCATION,
                     Manifest.permission.ACCESS_FINE_LOCATION,
                     Manifest.permission.RECORD_AUDIO)
                      .subscribe {

                    }


        /**
         * Observing the lamp luminosity and power state characteristics
         */

        mLampViewModel.getLampPowerStateLiveData().observe(this, Observer{
            Log.d(TAG,"subscribing to the lamp power state changes")
            mTextToSpeech.speak("${getString(R.string.lamp_power_state_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())
        })

        mLampViewModel.getLampLuminosityLevelLiveData().observe(this, Observer{
            Log.d(TAG,"subscribing to the lamp luminosity change")
            if (allowLampSpeak)
                mTextToSpeech.speak("${getString(R.string.lamp_mode_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())
        })


        mLampViewModel.getLampConnectionErrorLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the lamp errors changes")
            if (allowLampSpeak)
                mTextToSpeech.speak(getStringResourceByName(it!!),TextToSpeech.QUEUE_ADD,null,it.hashCode().toString())
        })


        /**
         * Observing the TV volume and power state characteristics
         */

        mTvViewModel.getTvPowerStateLiveDate().observe(this, Observer {
            Log.d(TAG,"subscribing to the tv power state changes")
            mTextToSpeech.speak("${getString(R.string.tv_state_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())


        })

        mTvViewModel.getTvVolumeLevelLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the tv volume changes")
            CURRENT_TV_VOLUME = it

            if (allowTvSpeak)
                mTextToSpeech.speak("${getString(R.string.tv_volume_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())


        })

        mTvViewModel.getTvTimerLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the tv timer changes")
            mTextToSpeech.speak("${getString(R.string.tv_timer_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())

        })

        mTvViewModel.getTvConnectionErrorLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the tv errors changes")
            mTextToSpeech.speak(getStringResourceByName(it!!),TextToSpeech.QUEUE_ADD,null,it.hashCode().toString())

        })




        /**
         * Observing the Air conditioner temperature, mode and power state characteristics
         */

        mAirConditionerViewModel.getAirConditionerPowerStateLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the air conditioner power state changes")
            mTextToSpeech.speak("${getString(R.string.air_conditioner_state_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())
        })

        mAirConditionerViewModel.getAirConditionerModeLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the air conditioner  mode changes")
            mTextToSpeech.speak("${getString(R.string.air_conditioner_mode_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())
        })

        mAirConditionerViewModel.getAirConditionerTempLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the air conditioner  temperature changes")
            mTextToSpeech.speak("${getString(R.string.air_conditioner_temp_indicator)} $it",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())
        })

        mAirConditionerViewModel.getAirConditionerConnectionErrorLiveData().observe(this, Observer {
            Log.d(TAG,"subscribing to the tv errors changes")
            mTextToSpeech.speak(getStringResourceByName(it!!),TextToSpeech.QUEUE_ADD,null,it.hashCode().toString())
        })


        /**
         * Observing the BOT text responses
         */

        mDialogViewModel.getTextResponse().observe(this, Observer {
            Log.d(TAG,"subscribing to the text response changes")
            mTextToSpeech.speak(it,TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())
        })

        mDialogViewModel.getDialogErrorStatus().observe(this, Observer {
            Log.d(TAG,"subscribing to the text response changes")
            mTextToSpeech.speak(getStringResourceByName(it!!),TextToSpeech.QUEUE_ADD,null,it.hashCode().toString())
        })


        /**
         * Observing the BOT responses to the device power state actions
         */

        mDialogViewModel.getDevicePowerStateSetAction().observe(this, Observer {
            Log.d(TAG,"subscribing to the power dialog")

            val device = it?.device
            val state  = it?.powerState !!
            when(device){
                ChatBotProfile.Device.TV -> mTvViewModel.setTvPowerState(BroadLinkProfile.TvProfile.State.valueOf(state.name))
                ChatBotProfile.Device.AIR_CONDITIONER -> mAirConditionerViewModel.setAirConditionerPowerState(BroadLinkProfile.AirConditionerProfile.State.valueOf(state.name))
                ChatBotProfile.Device.LAMP -> {
                    allowLampSpeak = false
                    mLampViewModel.setLampPowerState(LampProfile.State.valueOf(state.name))
                }
            }
        })

        mDialogViewModel.getDevicePowerStateCheckAction().observe(this, Observer {
            Log.d(TAG,"subscribing to the  power state check action")
            val device = it?.device
            when(device){
                ChatBotProfile.Device.TV -> mTvViewModel.getTvPowerState()
                ChatBotProfile.Device.AIR_CONDITIONER -> mAirConditionerViewModel.getAirConditionerPowerState()
                ChatBotProfile.Device.LAMP -> {
                    allowLampSpeak = false
                    mLampViewModel.getLampPowerState()
                }
            }
        })


        /**
         * Observing the BOT responses to the device brightness actions
         */

        mDialogViewModel.getDeviceBrightnessCheckAction().observe(this, Observer {
            allowLampSpeak = false
            mLampViewModel.getLampLuminosityLevel()
        })

        mDialogViewModel.getDeviceBrightnessSetAction().observe(this, Observer {
            val level = it?.luminosity !!
            Log.d(TAG,"subscribing to the lamp luminosity set action: $level")
            allowLampSpeak = false
            mLampViewModel.setLampLuminosityLevel(LampProfile.Luminosity.valueOf(level.name))
        })

        /**
         * Observing the BOT responses to the device mode actions
         */

        mDialogViewModel.getDeviceModeSetAction().observe(this, Observer {
            val mode = it?.airMode !!
            Log.d(TAG,"subscribing to the air conditioner mode check action : $mode")
            mAirConditionerViewModel.setAirConditionerMode(BroadLinkProfile.AirConditionerProfile.Mode.valueOf(mode.name))

        })

        mDialogViewModel.getDeviceModeCheckAction().observe(this, Observer {
            mAirConditionerViewModel.getAirConditionerMode()
        })


        /**
         * Observing the BOT responses to the device volume actions
         */

        mDialogViewModel.getDeviceVolumeSetAction().observe(this, Observer {
            Log.d(TAG,"subscribing to the TV volume set action")

            val volume = it?.volume !!
            mTvViewModel.setTvVolumeLevel(volume)

        })

        mDialogViewModel.getDeviceVolumeCheckAction().observe(this, Observer {
            Log.d(TAG,"subscribing to the TV volume check action")

            allowTvSpeak =true
            mTvViewModel.getTvVolumeLevel()

            /*if (CameraFragment.currentRecognition.title == "television") {


            }else{
                mTextToSpeech.speak("Please turn your Camera to the TV",TextToSpeech.QUEUE_ADD,null,it?.hashCode().toString())


            }*/
        })


        /**
         * Observing the BOT responses to the device timer actions
         */

        mDialogViewModel.getDeviceTimerSetAction().observe(this, Observer {
            Log.d(TAG,"subscribing to the TV volume set action")
            val time = it?.timer !!
            mTvViewModel.setTvTimer(time)

        })


        //Attach the Chatbot Fragment
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container,DialogFragment())
                .commit()

    }


    override fun onResume() {
        super.onResume()
        fragmentManager
                .beginTransaction()
                .replace(R.id.fragment_camera, CameraFragment.newInstance())
                .commit()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG,"onStart")


        /**
         * setting up the (text to speech)/(speech to text)
         */

        mSpeechRecognizer =  SpeechRecognizer.createSpeechRecognizer(this)
        mSpeechRecognizerIntent =   Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        mTextToSpeech =  TextToSpeech(this,this)
        with(mSpeechRecognizerIntent){
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,500)
        }
        mSpeechRecognizer.setRecognitionListener(this)


    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        if (event.action == MotionEvent.ACTION_UP) {
                var detectedObject = CameraFragment.currentRecognition.title

            if (detectedObject.equals("television")){
                allowTvSpeak = false
                
                mTvViewModel.getTvVolumeLevel()

                seekBarTV.setProgress(CURRENT_TV_VOLUME!!)

                textViewTV_Volume.setText("The TV Volume is " + CURRENT_TV_VOLUME)
                popupWindow.showAtLocation(layoutContainer, Gravity.NO_GRAVITY, x, y)
                
                //allowTvSpeak = true
                return true
            }

            if (detectedObject.equals("lamp")){
                popupWindowDimmer.showAtLocation(layoutContainer, Gravity.NO_GRAVITY, x, y)

                return true
            }

            if (detectedObject.equals("air conditioner")) {

            }

        }
        return false
    }

    override fun onAudioRecordingButtonTouch() {
        Log.d(TAG,"onAudioRecordingButtonTouch")
          mSpeechRecognizer.startListening(mSpeechRecognizerIntent)
    }

    override fun onResults(results: Bundle?) {
        Log.d(TAG,"onAudioRecordingButtonTouch:")
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) !!
        mDialogViewModel.setDialogTextRequest(text)

    }


    override fun onInit(status: Int) {
        Log.d(TAG,"onInit")
        if(status == TextToSpeech.SUCCESS){
                 mTextToSpeech.language = Locale.US
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        mTextToSpeech.stop()
        mTextToSpeech.shutdown()
        mSpeechRecognizer.stopListening()
        mSpeechRecognizer.destroy()

        disposable.dispose()
    }

    private fun onDimmerChangelistener() {
        Log.d("TEST","suscribe inside onDimmerChange")

    }
}