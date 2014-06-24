LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_LDLIBS := -llog
LOCAL_MODULE    := GetEvent
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := GetEvent.c
include $(BUILD_SHARED_LIBRARY)
