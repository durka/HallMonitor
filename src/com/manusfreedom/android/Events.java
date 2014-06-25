/*
 * Android get Event from drivers directly
 *
 * Copyright (c) 2014 by Manus Freedom , manus@manusfreedom.com
 * Based on https://github.com/android/platform_system_core/blob/master/toolbox/getevent.c
 * Inspired by EventInjector
 * 
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

package com.manusfreedom.android;

import java.util.ArrayList;
import android.util.Log;
import eu.chainfire.libsuperuser.Shell;


public class Events
{
	
	private final static String LT = "Events";
	
	public class InputDevice {
		
		private int m_nVersion;
		private String m_szPath, m_szName, m_szLocation, m_szIdStr;
		private boolean m_bOpen;
		
		InputDevice(String path) {
			m_szPath = path; 
		}
		
		public int getPollingEvent() {
			return PollDevice(m_szPath);
		}

		public int getAllDevicesPollingEvent() {
			return PollAllDevices();
		}
		public int getSuccessfulPollingType() {
			return getType(m_szPath);
		}
		public int getSuccessfulPollingCode() {
			return getCode(m_szPath);
		}
		public int getSuccessfulPollingValue() {
			return getValue(m_szPath);
		}
		
		public boolean getOpen() {
			return m_bOpen;
		}
		public int getVersion() {
			return m_nVersion;
		}
		public String getPath() {
			return m_szPath;
		}
		public String getName() {
			return m_szName;
		}
		public String getLocation() {
			return m_szLocation;
		}
		public String getIdStr() {
			return m_szIdStr;
		}
		
		public void Close() {
			m_bOpen  = false;
			CloseDevice(m_szPath);
		}
		
		/**
		 * function Open : opens an input event node
		 * @param forceOpen will try to set permissions and then reopen if first open attempt fails
		 * @return true if input event node has been opened
		 */
		public boolean Open(boolean forceOpen) {
			int res = OpenDevice(m_szPath);
	   		// if opening fails, we might not have the correct permissions, try changing 660 to 666
	   		if (res != 0) {
	   			// possible only if we have root
	   			if(forceOpen && Shell.SU.available()) { 
	   				// set new permissions
	   				Shell.SU.run("chmod 666 "+ m_szPath);
	   				// reopen
	   			    res = OpenDevice(m_szPath);
	   			}
	   		}
	   		m_nVersion = getDeviceVersion(m_szPath);
	   		m_szName = getDeviceName(m_szPath);
	   		m_szLocation = getDeviceLocation(m_szPath);
	   		m_szIdStr = getDeviceIdStr(m_szPath);
	   		m_bOpen = (res == 0);
	   		// debug
	   		Log.d(LT,  "Open:" + m_szPath + " Name:" + m_szName + " Version:" + m_nVersion+" Location:" + m_szLocation + " IdStr:" + m_szIdStr + " Result:" + m_bOpen);
	   		// done, return
	   		return m_bOpen;
	   	}
	}
	
	// top level structures
	public ArrayList<InputDevice> m_Devs = new ArrayList<InputDevice>(); 

	public int Refresh() {
   		Log.d(LT,  "Refreshing");
		m_Devs.clear();
		int n = getDevicesCount();
		String devicePath = "";
		for (int i=0; i < n; i++) {
			devicePath = getDeviceAtPosition(i);
	   		Log.d(LT,  "Add position:" + i + " Path:" + devicePath);
			m_Devs.add(new InputDevice(getDevicePath(getDeviceAtPosition(i))));
		}
	   	return n;
	}
	
	public int AddAllDevices() {
		Release();
		int n = ScanDir(); // return number of devs
		if(n > 0)
		{
			return Refresh();
		}
		else
			return -1;
	}

	public int AddDevice(String fullpath) {
		int n = AddOneDevice(fullpath); // return number of devs
		if(n > 0)
		{
			m_Devs.add(new InputDevice(getDevicePath(getDeviceAtPosition(n-1))));
			return Refresh();
		}
		else
			return -1;
	}
	
	public int Release() {
		for (InputDevice idev: m_Devs)
			idev.Close();
	   	return Refresh();
	}
	   	 
	private native static int getDevicesCount(); // return number of devs
	private native static int AddOneDevice(String fullpath); // return number of devs
	private native static int ScanDir(); // return number of devs
	private native static String getDeviceAtPosition(int index); //
	private native static int getDeviceVersion(String fullpath);
	private native static String getDevicePath(String fullpath);
	private native static String getDeviceName(String fullpath);
	private native static String getDeviceLocation(String fullpath);
	private native static String getDeviceIdStr(String fullpath);
	private native static int OpenDevice(String fullpath);
	private native static int CloseDevice(String fullpath);
	private native static int PollDevice(String fullpath);
	private native static int PollAllDevices();
	private native static int getType(String fullpath);
	private native static int getCode(String fullpath);
	private native static int getValue(String fullpath);
	private native static int getPrintFlags();
	private native static int setPrintFlags();


    static {
    	//Shell.SU.available();
    	try {
            System.loadLibrary("GetEvent");
        } catch (UnsatisfiedLinkError eGeneric) {
        	try {
                System.load("/data/data/org.durka.hallmonitor/lib/libGetEvent.so");
            } catch (UnsatisfiedLinkError eApkLib) {
            	try {
                    System.load("/data/app-lib/HallMonitor/libGetEvent.so");
                } catch (UnsatisfiedLinkError eAppLib) {
                    System.load("/system/lib/libGetEvent.so");
                }
            }
        }
    }

}

