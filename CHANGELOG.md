- 2.0.2 beta
	Major code update:
		- Boost performance

	Fixes:
		- Fix possible crash turning on/off torch
		- Fix camera log
		- Fix not all but main security warning in log
		- Fix screen off during alert/activity

	Know issues:
		- Alternative layout broken

	Next big beta (2.1.0):
		- Gesture/Swipe over the cover (bottom&left to: open camera, hang up call, snooze alarm / bottom right to: toggle torch, pickup call, dismiss alarm / bottom top to: pop-up menu)
		- Pop-up menu like habeIchVergessen propose (using over cover touch): https://github.com/durka/HallMonitor/issues/18
		- Network connectivity indicator (WIFI/2G/3G/4G): https://github.com/durka/HallMonitor/issues/24
		- Add landscape layout to avoid rotate animation & rotate background app

- 2.0.1 beta
	Major code update:
		- Clean/Reorder preferences
		- Clean/Reorder strings
		- Better preferences change/update
		- Better preferences dependencies
	
	Feature:
		- Support 3 modes:
			Lock (require admin)
			OS PowerManagement
			Internal/HallMonitor Powermanagement (require system app)

	Fixes:
		- Fix CM 11 LID support
		- Fix widget crash (to confirm)

	Know issues:
		- Alternative layout broken

	Next big beta (2.1.0):
		- Gesture/Swipe over the cover (bottom&left to: open camera, hang up call, snooze alarm / bottom right to: toggle torch, pickup call, dismiss alarm / bottom top to: pop-up menu)
		- Pop-up menu like habeIchVergessen propose (using over cover touch): https://github.com/durka/HallMonitor/issues/18
		- Network connectivity indicator (WIFI/2G/3G/4G): https://github.com/durka/HallMonitor/issues/24
		- Add landscape layout to avoid rotate animation & rotate background app

- 2.0.0 beta
	Major code update:
		- Rewrite many code to exclude much as possible from UI thread
		- Add share value between application parts
	
	Feature:
		- Normally support last CM 11 LID action (to test): turn off Use internal service
		- Support 4x1 widget (from my test)
		- Add lock mode option when system app (fixed to lock mode if not system app)
		- New Input Controls menu under Optional Features
		- Pick Up & Hang Up a call without use command line

	Fixes:
		- https://github.com/durka/HallMonitor/issues/33 (to test)
		- Fix swipe

	Know issues:
		- Alternative layout broken
		- Must force restart application (through Configuration activity) to get widget content updated after the first attachment

	Next beta (2.1.0):
		- Gesture/Swipe over the cover (bottom&left to: open camera, hang up call, snooze alarm / bottom right to: toggle torch, pickup call, dismiss alarm / bottom top to: pop-up menu)
		- Pop-up menu like habeIchVergessen propose (using over cover touch): https://github.com/durka/HallMonitor/issues/18
		- Network connectivity indicator (WIFI/2G/3G/4G): https://github.com/durka/HallMonitor/issues/24
		- Add landscape layout to avoid rotate animation & rotate background app

- 0.5.1
	Features
		- Add serrano3gxx support (thank habeIchVergessen)

- 0.5.0
	Features
		- Add Swipe for Torch
		- Add Swipe for Alarm
		- Add Swipe for Phone (fix)
		- Re-add Real Fullscreen (only for software navigation bar)

	Fixes
		- Try re-fix (#37) Last used app shows up randomly
		- Remove duplicate torch button
		- At init unable to turn on directly Torch Control
		- Torch icon on CM display correctly (is on/off too when activated outside HallMonitor)
		- Clean Alarm code
		- Cover mode sensitivity after displaying not before
		- Better debug tag

- 0.4.2
	Features
		- Added a new alternative layout
		- Answer/Reject call with "swipe"
	Fixes
		- If a torch button is enabled, refuse to enable the second button at the same time
		- Stopped TimerTask when receiving a call
	Partial Fixes
		- Incoming call screen now works 99% times in both layouts when the screen is off and the cover closed. Does not work if the screen is on and the cover closed (need to open and close the cover manually)
		- Alternative and media widgets works for 4x1 widgets and for some 4x2 widget, but only on alternative layout
- 0.4.1
	- Fixes
		- Touch screen sensitivity now works on S4 Mini Dual Sim
		- JNI build issues resolved

- 0.4.0
	- 0.3.x were internal, unreleased versions
	- Features (summary)
		- New contributors
		- Option to use the real Hall effect sensor for everything!
		  Proximity sensor no longer required.
		- Option to install as system app
		- Italian translation
		- See 0.3.x changelogs for details
	- Fixes
		- Many
		- See 0.3.x changelogs for details

- 0.3.9
	- Features
		- Added Italian translation
		- Added alternative torch button (tested on i9505, need test on other devices)
	- Fixes
		- Removed HallMonitor from "recent app list"
		- Fix 5 seconds delay on start activity after home button pression (System App only)
		- Minor changes

- 0.3.8
	- Fixes
		- Fix System Bar appear under cover when we touch the screen (only for people using navigation bar instead of harware buttons)

- 0.3.7
	- Features
		- Real HallMonitor support for SERRANO device
	- Fixes
		- Remove submenu on Real HallMonitor on/off

- 0.3.6
	- Features
		- Data & Time feature
		- Fix HallMonitor as System App on CM (gotosleep working): read special Readme
		- Try better touch when cover is closed (nothing change but more often call but more often call to enble it)
		- Build for more system
		- Fix licenses

- 0.3.5
	- Features
		- Possibility to choose between real Hall or simule with Proximity sensor (under Root menu)
		- Change licencing to Apache
		- Use eu.chainfire.libsuperuser in place of net.pocketmagic.android.eventinjector
		- All su access through eu.chainfire.libsuperuser.Shell
		- Removed unused library import
		- Suppress navigation bar (Softkey)
		- Remove su call from main thread
		- To test: gotosleep instead of lock if installed as SystemApp

- 0.3.0
	- Features
		- Add real Hall Event
		- Root is now a requirement
	- Remove
		- Proximity sensor use
	- Notes
		- Next version add settings to choose between Proximity sensor or Hall Event?

- 0.2.2
	- Fixes
		- (#37) Last used app no longer shows up randomly
- 0.2.1
	- Fixes
		- Root features could not be enabled on some phones
			- No need to update if Hall Monitor is working for you
	- Notes
		- Root features (still) may not work with XPrivacy enabled
- 0.2
	- Features
		- Option to stay on as Device Administrator when disabled
			- This may be useful in conjunction with e.g. Tasker
		- Added instructional text at top of main screen
		- When cover is closed, disable longpress-back (can't disable longpress-menu)
	- Fixes
		- Only display "First run" dialog box if old_version < 0.1 && new_version >= 0.1
		- Removed unnecessary CALL_PHONE permission
		- Widget settings screen now has the right title
		- Widget settings now persist across reboot
		- Confusing settings button is gone from main screen
- 0.1
	- Features
		- Replacement lock screen!
			- Configurable display with clock or widget
			- Shows notifications on lock screen
			- Touchscreen works through cover
			- Alarm clock, camera, flashlight controls
			- Phone answering (TTS coming soon)
		- Redesigned and reorganized preference screen
	- Fixes
		- Too many to count
- 0.0.2a
    - Features
        - Release checklist
    - Fixes
        - Forgot to update the strings for 0.0.2
    - I'm new at this "shipping software" thing...
- 0.0.2
    - Features
        - Icon (I made it myself, so it's libre)
        - App credits (on the preference screen)
        - Users report that it works on jfltespr, jfltexx
    - Fixes
        - Don't ask for admin privileges as "SensorCat" (#2)
        - Wait for admin privileges before starting the service (#3)
- 0.0.1
    - It's alive!