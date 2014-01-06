package org.durka.hallmonitor.test;

import org.durka.hallmonitor.DefaultActivity;

import android.test.ActivityInstrumentationTestCase2;

public class DefaultActivityTest extends
		ActivityInstrumentationTestCase2<DefaultActivity> {
	
	private DefaultActivity mActivity;
	
	public DefaultActivityTest() {
		super(DefaultActivity.class);
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		
		setActivityInitialTouchMode(false);
		mActivity = getActivity();
	}

}
