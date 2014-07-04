#!/bin/sh

# keytool-importkeypair -k cm_platform.keystore -p android -pk8 platform.pk8 -cert platform.x509.pem -alias platform
APPNAME=HallMonitor
MODE=debug
ant clean
ndk-build clean
ndk-build
ant $MODE
if [ $MODE == release ]; then
    java -jar cm_certs/{signapk.jar,platform.{x509.pem,pk8}} bin/$APPNAME-$MODE-un{s,al}igned.apk
    zipalign -v 4 bin/$APPNAME-$MODE{-unaligned,}.apk
fi

