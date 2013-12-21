/* Copyright 2013 Alex Burka

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.durka.hallmonitor;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.WindowManager;


public class ViewCoverService extends Service implements SensorEventListener, TextToSpeech.OnInitListener {

    private final static String LOGD = "VCS";

	private SensorManager       mSensorManager;

    /**
     *  Text-To-Speech
     */
    private TextToSpeech mTts;
    private boolean mTtsInitComplete = false;

    private boolean mWiredHeadSetPlugged = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {	// headset (un-)plugged
                mWiredHeadSetPlugged = ((intent.getIntExtra("state", -1) == 1));
            } else if (action.equals(getString(R.string.ACTION_SEND_TO_SPEECH_RECEIVE))) {
                String text = intent.getStringExtra("sendTextToSpeech");
                sendTextToSpeech(text);
            } else if (action.equals(getString(R.string.ACTION_STOP_TO_SPEECH_RECEIVE))) {
                stopTextToSpeech();
            }
        }
    };


    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(LOGD + ".onStartCommand", "View cover service started");

        //We don't want to do this - almost by defninition the cover can't be closed, and we don't actually want to do any open cover functionality
		//until the cover is closed and then opened again
		/*if (Functions.Is.cover_closed(this)) {
			Functions.Actions.close_cover(this);
		} else {
			Functions.Actions.open_cover(this);
		} */
		
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
		
//		Log.d(LOGD + "-oSC", "scanning keyboards...");
//		InputManager im = (InputManager) getSystemService(INPUT_SERVICE);
//		for (int id : im.getInputDeviceIds()) {
//			InputDevice dev = im.getInputDevice(id);
//			Log.d(LOGD + "-oSC", "\t" + dev.toString());
//		}

        // Text-To-Speech
        initTextToSpeech();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(getString(R.string.ACTION_SEND_TO_SPEECH_RECEIVE));
        filter.addAction(getString(R.string.ACTION_STOP_TO_SPEECH_RECEIVE));
        registerReceiver(receiver, filter);

        return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		Log.d(LOGD + ".onStartCommand", "View cover service stopped");
		
		mSensorManager.unregisterListener(this);

        // Text-To-Speech
        unregisterReceiver(receiver);
        destroyTextToSpeech();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// I don't care
		Log.d(LOGD + "onAccuracyChanged", "OnAccuracyChanged: Sensor=" + sensor.getName() + ", accuracy=" + accuracy);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {	
			Log.d(LOGD + ".onSensorChanged", "Proximity sensor changed, value=" + event.values[0]);
			Functions.Events.proximity(this, event.values[0]);
			
			//improve reliability by refiring the event 200ms afterwards
			final float val = event.values[0];
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {	
					Functions.Events.proximity(getApplicationContext(), val);
				}
			}, 200);
			
			timer.schedule(new TimerTask() {
				@Override
				public void run() {	
					Functions.Events.proximity(getApplicationContext(), val);
				}
			}, 500);
			
		}
	}

    /**
     * Text-To-Speech
     *
     * Executed when a new TTS is instantiated. We don't do anything here since
     * our speech is now determine by the button click
     * @param initStatus
     */

    public void onInit(int initStatus)
    {
        switch (initStatus) {
            case TextToSpeech.SUCCESS:
                Log.d(LOGD, "init Text To Speech successed");
                //mTts.setLanguage(Locale.GERMANY);
                mTtsInitComplete = true;
                break;
            case TextToSpeech.ERROR:
                Log.d(LOGD, "init Text To Speech failed");
                mTts = null;
                break;
            default:
                Log.d(LOGD, "onInit: " + initStatus);
                break;
        }
    }


    private void initTextToSpeech() {
        // init Text To Speech
        if (!mTtsInitComplete && mTts ==  null) {
            mTts = new TextToSpeech(this, this);
        }
    }

    private boolean sendTextToSpeech(String text) {
        boolean ttsEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_phone_controls_tts", false);
        boolean speakerEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_phone_controls_speaker", false);
        boolean result = false;

        Log.d(LOGD, "sendTextToSpeech: text = '" + text + "', " + mTtsInitComplete + ", " + (mTts != null) + ", " + ttsEnabled);
        if (mTtsInitComplete && mTts != null && ttsEnabled) {
            AudioManager audioManager = (AudioManager)this.getSystemService(AUDIO_SERVICE);

            Log.d(LOGD, "sendTextToSpeech: text = '" + text + "', " + mWiredHeadSetPlugged + ", " + audioManager.isBluetoothA2dpOn());

            if (audioManager.isBluetoothA2dpOn() || mWiredHeadSetPlugged || speakerEnabled) {
                HashMap <String, String> params = new HashMap<String, String>(1);
                params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(audioManager.STREAM_VOICE_CALL));

                mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
                result = mTts.isSpeaking();
            }
        }

        Log.d(LOGD, "sendTextToSpeech: text = '" + text + "' -> " + (result ? "ok" : "failed"));
        return result;
    }

    private void stopTextToSpeech() {
        if (mTtsInitComplete && mTts != null)
            mTts.stop();
    }

    private void destroyTextToSpeech() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }

    /**
     * Text-To-Speech (end)
     */
}
