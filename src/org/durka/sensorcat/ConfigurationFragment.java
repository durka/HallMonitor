package org.durka.sensorcat;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

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
	    //Toast.makeText(getActivity(), "resuming", Toast.LENGTH_SHORT).show();
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
	    getPreferenceManager().getSharedPreferences()
	            .unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		
		if (key.equals("pref_enabled")) {
			
			if (prefs.getBoolean(key, false)) {
				Functions.Actions.start_service(getActivity());
			} else {
				Functions.Actions.stop_service(getActivity());
			}
			
		}
	}

}
