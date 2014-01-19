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

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.lang.reflect.Constructor;
import java.util.List;

public class Configuration extends PreferenceActivity {
    private final String LOG_TAG = "Configuration";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        Functions.configurationActivity = this;

        PreferenceFragment preferenceFragment = new PreferenceFragmentLoader();

        // add extra resource to load xml
        Bundle arguments = new Bundle(1);
        arguments.putCharSequence("resource", "preferences");
        preferenceFragment.setArguments(arguments);

        getFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).commit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.configuration, menu);
		return true;
	}
	
	@Override
	public void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		Functions.Events.activity_result(this, request, result, data);
	}

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference preference) {
        try {
            // create new instance of class
            Class<?> c = Class.forName(preference.getFragment());
            Constructor<?> cons = c.getConstructor();
            Object object = cons.newInstance();

            // add preference fragment
            if (object instanceof PreferenceFragment) {
                PreferenceFragment preferenceFragment = (PreferenceFragment)object;
                preferenceFragment.setArguments(preference.getExtras());

                getFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).addToBackStack((String) getActionBar().getTitle()).commit();
                getFragmentManager().executePendingTransactions();

                // update action bar
                updateHeaderTitle(preference.getExtras().getString("title"));
            } else
                Log_d(LOG_TAG, "onPreferenceStartFragment: given class is not a PreferenceFragment");
        } catch (Exception e) {
            Log_d(LOG_TAG, "onPreferenceStartFragment: exception occurred! " + e.getMessage());
        }

        return true; // the default implementation returns true. documentation is silent. FIXME
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        CharSequence title = getTitle();
        int idx;

        if ((idx = getFragmentManager().getBackStackEntryCount()) > 0) {
            title = getFragmentManager().getBackStackEntryAt(idx - 1).getName();
            getFragmentManager().popBackStackImmediate(); // TODO test back stack
        } else
            super.onBackPressed();

        updateHeaderTitle(title);
    }

    private void updateHeaderTitle(CharSequence title) {
        getActionBar().setTitle(title);
        getActionBar().setDisplayHomeAsUpEnabled((getFragmentManager().getBackStackEntryCount() > 0));
    }

    private void Log_d(String tag, String msg) {
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_dev_opts_debug", false))
            Log.d(tag, msg);
    }
}
