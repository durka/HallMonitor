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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

public class ConfigurationFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
	
	@Override
	public void onResume() {
	    super.onResume();
	    getPreferenceManager().getSharedPreferences()
	            .registerOnSharedPreferenceChangeListener(this);
	    
	    getPreferenceManager().getSharedPreferences()
	    		.edit()
	    		.putBoolean("pref_enabled", Functions.Is.service_running(getActivity()))
	    		.commit();
	}

	@Override
	public void onPause() {
	    super.onPause();
	    // don't unregister, because we still want to receive the notification when
	    // pref_enabled is changed in onActivityResult
	    // FIXME is it okay to just never unregister??
	    //getPreferenceManager().getSharedPreferences()
	    //        .unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		
		Log.d("CF-oSPC", "changed key " + key);
		
		// update display
		if (findPreference(key) instanceof CheckBoxPreference) {
			((CheckBoxPreference)findPreference(key)).setChecked(prefs.getBoolean(key, false));
		}
		
		// perform actions
		if (key.equals("pref_enabled")) {
			
			if (prefs.getBoolean(key, false)) {
				Functions.Actions.start_service(getActivity());
			} else {
				Functions.Actions.stop_service(getActivity());
			}
			
		}
	}

}
