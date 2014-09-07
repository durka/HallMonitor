REM keytool-importkeypair -k cm_platform.keystore -p android -pk8 platform.pk8 -cert platform.x509.pem -alias platform
SET JAVA_HOME=c:\Progra~1\Java\jdk1.8
SET PATH=%PATH%;c:\Progra~1\Java\jdk1.8\Bin;C:\Dev\android-sdk-windows\tools;C:\Dev\android-sdk-windows\platform-tools;C:\Dev\apache-ant\bin
SET ANDROID_SDK_TOOLS=C:\Dev\android-sdk-windows\build-tools\19.1.0
SET ANDROID_HOME=C:\\Dev\\android-sdk-windows
SET ANDROID_NDK=C:\Dev\android-ndk
SET APPNAME=HallMonitor
SET MODE=debug
ant clean
del bin\%APPNAME%-%MODE%-unaligned.apk
del bin\%APPNAME%.apk
del libs\armeabi\libGetEvent.so
del libs\armeabi-v7a\libGetEvent.so
del libs\mips\libGetEvent.so
del libs\x86\libGetEvent.so
%ANDROID_NDK%\ndk-build.cmd clean > build_ndkclean.log
%ANDROID_NDK%\ndk-build.cmd > build_ndk.log
ant %MODE% > build_ant.log
IF %MODE%==release java -jar cm_certs\signapk.jar cm_certs\platform.x509.pem cm_certs\platform.pk8 bin\%APPNAME%-%MODE%-unsigned.apk bin\%APPNAME%-%MODE%-unaligned.apk > build_sign.log
"%ANDROID_SDK_TOOLS%\zipalign.exe" -v 4 bin\%APPNAME%-%MODE%-unaligned.apk bin\%APPNAME%.apk > build_align.log
xcopy /y bin\%APPNAME%.apk cm_certs\zip\common
xcopy /s /y libs\* cm_certs\zip\lib
del cm_certs\zip\lib\android-support-v4.jar
echo Please create zip with cm_certs\zip contents
