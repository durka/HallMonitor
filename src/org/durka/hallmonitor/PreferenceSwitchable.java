package org.durka.hallmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Created by ladmin on 07.01.14.
 */
public class PreferenceSwitchable extends Preference {
    private final String LOG_TAG = "PreferenceSwitchable";

    private TextView mTitle;
    private TextView mSummary;
    private ImageView mIcon;
    private Switch mSwitch;
    private boolean mSwitchState = false;

    public PreferenceSwitchable(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        //Log.d(LOG_TAG, "onCreateView: " + getKey());

        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.preference_switchable_layout, parent, false);

        mTitle = (TextView)view.findViewById(android.R.id.title);
        mSummary = (TextView)view.findViewById(android.R.id.summary);
        mIcon = (ImageView)view.findViewById(android.R.id.icon);
        mSwitch = (Switch)view.findViewById(R.id.prefEnableSwitch);

        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setChecked(b);
                persistBoolean(b);
            }
        });

        setChecked(mSwitchState);

        return view;
    }

    @Override
    public void setTitle(int titleResId) {
        mTitle.setText(getContext().getResources().getString(titleResId));
    }

    @Override
    public void setTitle(CharSequence title) {
        mSummary.setText(title);
    }

    @Override
    public void setSummary(int summaryResId) {
        mSummary.setText(getContext().getResources().getString(summaryResId));
    }

    @Override
    public void setSummary(CharSequence summary) {
        mSummary.setText(summary);
    }

    @Override
    public void setIcon(int iconResId) {
        mIcon.setImageDrawable(getContext().getResources().getDrawable(iconResId));
    }

    @Override
    public void setIcon(Drawable icon) {
        mIcon.setImageDrawable(icon);
    }

    @Override
    public void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        //Log.d(LOG_TAG, "onSetInitialValue: " + restorePersistedValue + ", " + defaultValue);
        boolean value = false;

        if (!restorePersistedValue && (defaultValue instanceof Boolean)) {
            value = ((Boolean)defaultValue).booleanValue();
        }

        if (restorePersistedValue)
            value = getSharedPreferences().getBoolean(getKey(), false);

        mSwitchState = value;
        //Log.d(LOG_TAG, "onSetInitialValue: value = " + value);
    }

    @Override
    protected Object onGetDefaultValue (TypedArray a, int index) {
        //Log.d(LOG_TAG, "onGetDefaultValue: " + index + ", " + a);
        return a.getBoolean(index, false);
    }

    protected void setChecked(boolean checked) {
        //Log.d(LOG_TAG, "setChecked: " + checked);
        mSwitchState = checked;
        setSelectable(mSwitchState);
        mSwitch.setChecked(mSwitchState);
    }

}
