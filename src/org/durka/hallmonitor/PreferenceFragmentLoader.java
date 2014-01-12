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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Toast;

public class PreferenceFragmentLoader extends PreferenceFragment  implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final String LOG_TAG = "PreferenceFragmentLoader";

    private boolean mDebug = DefaultActivity.isDebug();
    private int mAboutClicked = 0;
    private int mAboutClickCount = 7;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            final String resourceName = getArguments().getString("resource", "");

            Context context = getActivity().getApplicationContext();

            // debug
            if (mDebug)
                Toast.makeText(getActivity(), "debug is enabled!", Toast.LENGTH_LONG).show();

            final int resourceId = context.getResources().getIdentifier(resourceName, "xml", context.getPackageName());

            PreferenceManager.setDefaultValues(getActivity(), resourceId, false);
            addPreferencesFromResource(resourceId);
        } catch (Exception e) {
            Log_d(LOG_TAG, "onCreate: exception occurred! " + e.getMessage());
        }

        // setup about preference for debug
        Preference about = findPreference("pref_about");
        if (about != null) {
            // init onClick listener
            about.setEnabled(true);
            about.setSelectable(true);
            about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mAboutClicked += 1;
                    if (mAboutClicked == mAboutClickCount) {
                        mAboutClicked = 0;
                        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
                        prefs.edit().putBoolean("pref_dev_opts_debug", !mDebug).commit();
                        Toast.makeText(getActivity(), "debug is " + (prefs.getBoolean("pref_dev_opts_debug", false) ? "enabled" : "disabled") + " now!", Toast.LENGTH_LONG).show();
                    }

                    return true;
                }
            });

            // mask text as disabled
            about.setTitle(getTextDisabledFormatted(about.getTitle()));
            about.setSummary(getTextDisabledFormatted(about.getSummary()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log_d(LOG_TAG, "onResume: ");

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        prefs.edit()
            .putBoolean("pref_enabled", Functions.Is.service_running(getActivity()))
            .putBoolean("pref_default_widget_enabled", Functions.Is.widget_enabled(getActivity(), "default"))
            .putBoolean("pref_media_widget_enabled", Functions.Is.widget_enabled(getActivity(), "media"))
            .commit();

        prefs.registerOnSharedPreferenceChangeListener(this);

        // phone control
        enablePhoneScreen(prefs);
        updatePhoneControlTtsDelay(prefs);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log_d(LOG_TAG, "onPause: ");
        // don't unregister, because we still want to receive the notification when
        // pref_enabled is changed in onActivityResult
        // FIXME is it okay to just never unregister??
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log_d(LOG_TAG + "-oSPC", "changed key " + key);

        // update display
        if (findPreference(key) instanceof CheckBoxPreference) {
            ((CheckBoxPreference)findPreference(key)).setChecked(prefs.getBoolean(key, false));
        }

        // if the service is being enabled/disabled the key will be pref_enabled
        if (key.equals("pref_enabled")) {

            if (prefs.getBoolean(key, false)) {
                Functions.Actions.start_service(getActivity());
            } else {
                Functions.Actions.stop_service(getActivity());
            }

            // if the default screen widget is being enabled/disabled the key will be pref_default_widget
        } else if (key.equals("pref_default_widget")) {

            if (prefs.getBoolean(key, false)) {
                Functions.Actions.register_widget(getActivity(), "default");
            } else {
                Functions.Actions.unregister_widget(getActivity(), "default");
            }

            // if the media screen widget is being enabled/disabled the key will be pref_media_widget
        } else if (key.equals("pref_media_widget")) {

            if (prefs.getBoolean(key, false)) {
                Functions.Actions.register_widget(getActivity(), "media");
            } else {
                Functions.Actions.unregister_widget(getActivity(), "media");
            }

            // if the default screen widget is being enabled/disabled the key will be pref_widget
        } else if (key.equals("pref_runasroot")) {

            if (prefs.getBoolean(key, false)) {
                if (!Functions.Actions.run_commands_as_root(new String[]{"whoami"}).equals("root")) {
                    // if "whoami" doesn't work, refuse to set preference
                    Toast.makeText(getActivity(), "Root access not granted - cannot enable root features!", Toast.LENGTH_SHORT).show();
                    prefs.edit().putBoolean(key, false).commit();
                }
            }

        } else if (key.equals("pref_do_notifications")) {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            if (prefs.getBoolean(key, false)) {
                Toast.makeText(getActivity(), "check this box then", Toast.LENGTH_SHORT).show();
                //getActivity().startService(new Intent(getActivity(), NotificationService.class));
            } else {
                Toast.makeText(getActivity(), "okay uncheck the box", Toast.LENGTH_SHORT).show();
                //getActivity().startService(new Intent(getActivity(), NotificationService.class));
            }
            // if the flash controls are being enabled/disabled the key will be pref_widget
        } else if (key.equals("pref_flash_controls")) {

                if (prefs.getBoolean(key, false) ) {
                    try {
                        PackageManager packageManager = getActivity().getPackageManager();
                        packageManager.getApplicationLogo("net.cactii.flash2");
                    } catch (PackageManager.NameNotFoundException nfne) {
                        // if the app isn't installed, just refuse to set the preference
                        Toast.makeText(getActivity(), "Default torch application is not installed - cannot enable torch button!", Toast.LENGTH_SHORT).show();
                        prefs.edit().putBoolean(key, false).commit();
                    }
                }
            // preferences_phone
        } else if (key.equals("pref_phone_controls_tts_delay")) {
            updatePhoneControlTtsDelay(prefs);
        }

        // phone control
        enablePhoneScreen(prefs);
    }

    private void updatePhoneControlTtsDelay(SharedPreferences prefs) {
        Preference preference = findPreference("pref_phone_controls_tts_delay");

        if (preference != null && (preference instanceof ListPreference)) {
            preference.setSummary(((ListPreference)preference).getEntry());
        }
    }

    private void enablePhoneScreen(SharedPreferences prefs) {
        boolean phoneControlState = prefs.getBoolean("pref_enabled", false) && prefs.getBoolean("pref_runasroot", false);
        boolean phoneControlConfig = prefs.getBoolean("pref_phone_controls", false);
        Preference phoneControl = findPreference("pref_phone_controls_user");

        if (phoneControlConfig != phoneControlState && phoneControl != null)
            phoneControl.setEnabled(phoneControlState);
        if (phoneControlConfig != (phoneControlState && prefs.getBoolean("pref_phone_controls_user", false)))
            prefs.edit().putBoolean("pref_phone_controls", !phoneControlConfig).commit();
    }

    private void Log_d(String tag, String message) {
        if (mDebug)
            Log.d(tag, message);
    }

    private SpannableString getTextDisabledFormatted(CharSequence text) {
        // TODO: read default text color
        int defaultTextColor = Color.BLACK;

        int alpha = Color.argb((int)(0.5f * 255), Color.red(defaultTextColor), Color.green(defaultTextColor), Color.blue(defaultTextColor));

        SpannableString spannableString = new SpannableString(text);
        spannableString.setSpan(new ForegroundColorSpan(alpha), 0, text.length(), 0);

        return spannableString;
    }
}
