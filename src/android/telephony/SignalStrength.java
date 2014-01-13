/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telephony;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Contains phone signal strength related information.
 */
public class SignalStrength implements Parcelable {

    /**
     * Get signal level as an int from 0..4
     *
     * @hide
     */
    public int getLevel() {
        throw new RuntimeException("fake proxy class");
    }
    
    /**
     * Get the signal strength as dBm
     *
     * @hide
     */
    public int getDbm() {
    	throw new RuntimeException("fake proxy class");
    }

    /**
     * @return true if this is for GSM
     */
    public boolean isGsm() {
        throw new RuntimeException("fake proxy class");
    }

	@Override
	public int describeContents() {
		throw new RuntimeException("fake proxy class");
	}

	@Override
	public void writeToParcel(Parcel arg0, int arg1) {
		throw new RuntimeException("fake proxy class");
	}
}
