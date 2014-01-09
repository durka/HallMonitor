Hall Monitor
============

About
-----
This is an Android app that reimplements some of the behaviors specific to the Samsung Galaxy S4, so that they can be used with alternative ROMs, such as CyanogenMod, where the proprietary Samsung components are not available. At this time, these behaviors are:

1. Support the [S View Cover](http://www.theverge.com/2013/3/14/4104134/samsung-announces-galaxy-s4-s-view-cover). There is a magnet in the cover, and a [hall effect sensor](https://en.wikipedia.org/wiki/Hall_effect_sensor) built in to the S4, so that when you close the cover, we can automatically display the lock screen for a few seconds before putting the phone to sleep, and when you open the cover the phone wakes up.

Why use Hall Monitor instead of one of the other apps that locks the screen when you close the cover? As far as I can tell, they all use the proximity sensor to detect when the cover closes. Well, the S4 has a hall effect sensor, which is a much more accurate way to detect when the cover closes, and is what this app uses (but see below).

This app is a "clean room" implementation. I'm not extracting Samsung's binaries from the stock ROM, or anything like that, just attempting to reimplement the functionality that I like.

Screenshots:

![Configuration screen (okay, it's the only screen)](https://raw.github.com/durka/HallMonitor/master/screenshot.png "Configuration screen (okay, it's the only screen)") ![GIF of opening and closing the cover](https://raw.github.com/durka/HallMonitor/master/animation_trimmed.gif "GIF of opening and closing the cover")

**Important**: The preponderance of the evidence so far indicates that there should be no problem running this app on any Galaxy S4, and even on the S4 mini/mega. However, I have only _personally_ tested it under the following configuration(s):

- Samsung Galaxy S4 T-Mobile (jfltetmo / SGH-M919), CyanogenMod 10.1.3 (based on Android 4.2.2)
- Samsung Galaxy S4 T-Mobile (jfltetmo / SGH-M919), CyanogenMod 10.2 (based on Android 4.3)

Others have tested this under the following configuration(s):

- Samsung Galaxy S4 Sprint (jfltespr / SPH-L720), CyanogenMod 10.2
- Samsung Galaxy S4 international (jfltexx / GT-I9505), CyanogenMod 10.2
- _[Mostly working]_ S4 Exynos octacore international (GT-I9500), unofficial CyanogenMod 10.2
- _[Mostly working]_ S4 mini international (serranoltexx / GT-I9195), CyanogenMod 10.2
- S4 mega, CyanogenMod (unknown carrier/version)

If you test this on something other than the above (including another carrier's S4) and it works, please let me know! You can contact me at [android@alexburka.com](mailto:android@alexburka.com). If it doesn't work, please file an issue here. But no promises, because I can't really do tech support for devices I don't own.

Installation / Usage
--------------------
The current release is: [0.0.2a](https://github.com/durka/HallMonitor/tree/0.0.2a) (see [changelog](https://github.com/durka/HallMonitor/blob/master/CHANGELOG.md))

- The easiest way to install the latest stable version is through [F-Droid](https://f-droid.org/), an "app store" for open source. Here is [Hall Monitor's entry](https://f-droid.org/repository/browse/?fdid=org.durka.hallmonitor).
- You can download [`bin/HallMonitor.apk`](https://github.com/durka/HallMonitor/blob/0.0.2a/bin/HallMonitor.apk?raw=true) from one of the tagged versions, and install it on your phone assuming you have sideloading turned on.
- You can clone the repository at a tagged version and build from source. It's configured as an Eclipse project, but I haven't even used any external libraries yet, so it "should" be "simple" to build with another system such as Android Studio or ant.

Limitations / Known Issues
--------------------------
- Little Things
    - I don't know how to make the lockscreen come up without turning the screen off and back on. So, currently, I turn the screen off and back on.
    - If you don't have any security turned on (i.e. pattern or PIN), then there is no lockscreen and so my only choice is to turn off the screen as soon as you close the cover (by the way, you can configure this behavior even if you do have a lockscreen set up).
- Bigger Things
    - I have figured out how to _read_ the state of the hall effect sensor, i.e. whether the cover is opened or closed. But I have not yet figured out how to receive _events_ from the sensor. Constantly polling its state is not an option, due to battery life concerns. So I am using the proximity sensor events, and the hall effect sensor for confirmation (so if you put your hand over the screen, the proximity sensor will fire, but the hall effect sensor will report that the cover is still open, so the phone will not lock). This is kind of a hack, and also it is possible to fool the system by closing or opening the cover while holding your finger over the proximity sensor (it's near the top edge of the screen). In my testing this has never happened accidentally, but it would still be nice not to need the proximity sensor hack.

Future Plans
------------
1. Comment the code more
2. Remove the above limitations
3. Enhance the view cover lockscreen:
    - (done) Either modify cLock, or make a new lockscreen widget, that crams more information into the space you can see through the view cover
    - (done) Make the lockscreen, or just a fullscreen activity, come up immediately when you close the cover instead of turning the screen off and back on (this could supersede the previous point)
    - (in progress) More enhancements to the fullscreen activity; see the issues/wiki for a roadmap
4. Add options to support other S4-specific things:
    - Keep the screen on while the camera can see your eyes
    - Air gestures? I never used them myself and don't know exactly how they work, but maybe people want them?
    - Smart scroll/pause? (see above point)
    - Other cool features that Samsung HASN'T thought of! If you have a good idea, file a feature request or, better yet, a pull request.

Hacking
-------
This should serve as quick guide if you're trying to find your way around my code.

- All the real functionality is in [`Functions.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/Functions.java). The various activities and intent receivers forward everything to functions in there, along with whatever references to Contexts or Activities are necessary. I am experimenting this design, as it seems cleaner to not have the logic scattered among all the Android and Java boilerplate.
- Activities:
    - Configuration: this is the configuration screen. It's defined in [`Configuration.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/Configuration.java) and [`activity_configuration.xml`](https://github.com/durka/HallMonitor/blob/master/res/layout/activity_configuration.xml). It contains only a preferences screen, using [`ConfigurationFragment.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/ConfigurationFragment.java) and [`preferences.xml`](https://github.com/durka/HallMonitor/blob/master/res/xml/preferences.xml).
        - I used one custom preference widget, not written by me (but slightly enhanced by me), in [`SeekBarPreference.java`](https://github.com/durka/HallMonitor/blob/master/src/com/hlidskialf/android/preference/SeekBarPreference.java).
- Intent receivers:
    - BootReceiver: the service needs to be started at boot; this is implemented by [`BootReceiver.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/BootReceiver.java) (though again, that just delegates to a function in [`Functions.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/Functions.java)).
    - DeviceAdminReceiver: the service needs device admin permissions in order to lock the screen, and we can't really start the service until that happens, so we need a receiver for the event. This is implemented in [`AdminReceiver.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/AdminReceiver.java) and configured in [`device_admin.xml`](https://github.com/durka/HallMonitor/blob/master/res/xml/device_admin.xml).
- Services:
    - ViewCoverService: the most important file, really, outside of [`Functions.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/Functions.java), is [`ViewCoverService.java`](https://github.com/durka/HallMonitor/blob/master/src/org/durka/hallmonitor/ViewCoverService.java). This is the service that runs all the time and receives events from the proximity sensor, so it can check the hall effect sensor state, and react to view cover events.

Happy hacking! File an issue or contact me at [android@alexburka.com](mailto:android@alexburka.com) with any questions.
