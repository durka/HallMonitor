/*
 * Android GetEvent
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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/inotify.h>
#include <sys/limits.h>
#include <sys/poll.h>
#include <linux/input.h>
#include <errno.h>
#include <jni.h>
#include <unistd.h>
#include <time.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <linux/fb.h>
#include <linux/kd.h>

#include <android/log.h>
#define TAG "GetEvent::JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , TAG, __VA_ARGS__) 
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#include "GetEvent.h"

static struct typedev {
	struct pollfd ufds;
	int device_version;
    char device_path[PATH_MAX];
    char device_name[80];
    char device_location[80];
    char device_idstr[80];
	struct input_id device_id;
	struct input_event event;
} *pDevs = NULL;

static struct pollfd *ufds;
static int nDevsCount = 0;
static int print_flags = (1U << 8) - 1;

const char *device_path = "/dev/input";

enum {
    PRINT_DEVICE_ERRORS     = 1U << 0,
    PRINT_DEVICE            = 1U << 1,
    PRINT_DEVICE_NAME       = 1U << 2,
    PRINT_DEVICE_INFO       = 1U << 3,
    PRINT_VERSION           = 1U << 4,
    PRINT_POSSIBLE_EVENTS   = 1U << 5,
    PRINT_INPUT_PROPS       = 1U << 6,
    PRINT_HID_DESCRIPTOR    = 1U << 7,

    PRINT_ALL_INFO          = (1U << 8) - 1,

    PRINT_LABELS            = 1U << 16,
};

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	LOGD("%s", "GetEvent native lib loaded.");
	return JNI_VERSION_1_2; //1_2 1_4
}

void JNI_OnUnload(JavaVM *vm, void *reserved)
{
	LOGD("%s", "GetEvent native lib unloaded.");
}

static const char *get_label(const struct label *labels, int value)
{
    while(labels->name && value != labels->value) {
        labels++;
    }
    return labels->name;
}

static int print_input_props(int fd)
{
    uint8_t bits[INPUT_PROP_CNT / 8];
    int i, j;
    int res;
    int count;
    const char *bit_label;

    LOGI("%s", "  input props:");
    res = ioctl(fd, EVIOCGPROP(sizeof(bits)), bits);
    if(res < 0) {
        LOGI("%s", "    <not available");
        return 1;
    }
    count = 0;
    for(i = 0; i < res; i++) {
        for(j = 0; j < 8; j++) {
            if (bits[i] & 1 << j) {
                bit_label = get_label(input_prop_labels, i * 8 + j);
                if(bit_label)
                    LOGI("    %s", bit_label);
                else
                	LOGI("    %04x", i * 8 + j);
                count++;
            }
        }
    }
    if (!count)
    	LOGI("%s", "    <none>");
    return 0;
}

static int print_possible_events(int fd)
{
    uint8_t *bits = NULL;
    ssize_t bits_size = 0;
    const char* label;
    int i, j, k;
    int res, res2;
    struct label* bit_labels;
    const char *bit_label;

    LOGI("%s", "  events:");
    for(i = EV_KEY; i <= EV_MAX; i++) { // skip EV_SYN since we cannot query its available codes
        int count = 0;
        while(1) {
            res = ioctl(fd, EVIOCGBIT(i, bits_size), bits);
            if(res < bits_size)
                break;
            bits_size = res + 16;
            bits = realloc(bits, bits_size * 2);
            if(bits == NULL) {
            	LOGE("failed to allocate buffer of size %d", (int)bits_size);
                return 1;
            }
        }
        res2 = 0;
        switch(i) {
            case EV_KEY:
                res2 = ioctl(fd, EVIOCGKEY(res), bits + bits_size);
                label = "KEY";
                bit_labels = key_labels;
                break;
            case EV_REL:
                label = "REL";
                bit_labels = rel_labels;
                break;
            case EV_ABS:
                label = "ABS";
                bit_labels = abs_labels;
                break;
            case EV_MSC:
                label = "MSC";
                bit_labels = msc_labels;
                break;
            case EV_LED:
                res2 = ioctl(fd, EVIOCGLED(res), bits + bits_size);
                label = "LED";
                bit_labels = led_labels;
                break;
            case EV_SND:
                res2 = ioctl(fd, EVIOCGSND(res), bits + bits_size);
                label = "SND";
                bit_labels = snd_labels;
                break;
            case EV_SW:
                res2 = ioctl(fd, EVIOCGSW(bits_size), bits + bits_size);
                label = "SW ";
                bit_labels = sw_labels;
                break;
            case EV_REP:
                label = "REP";
                bit_labels = rep_labels;
                break;
            case EV_FF:
                label = "FF ";
                bit_labels = ff_labels;
                break;
            case EV_PWR:
                label = "PWR";
                bit_labels = NULL;
                break;
            case EV_FF_STATUS:
                label = "FFS";
                bit_labels = ff_status_labels;
                break;
            default:
                res2 = 0;
                label = "???";
                bit_labels = NULL;
        }
        for(j = 0; j < res; j++) {
            for(k = 0; k < 8; k++)
                if(bits[j] & 1 << k) {
                    char down;
                    if(j < res2 && (bits[j + bits_size] & 1 << k))
                        down = '*';
                    else
                        down = ' ';
                    if(count == 0)
                        LOGI("    %s (%04x):", label, i);
                    else if((count & (print_flags & PRINT_LABELS ? 0x3 : 0x7)) == 0 || i == EV_ABS)
                    	LOGI("%s", "               ");
                    if(bit_labels && (print_flags & PRINT_LABELS)) {
                        bit_label = get_label(bit_labels, j * 8 + k);
                        if(bit_label)
                        	LOGI(" %.20s%c%*s", bit_label, down, (int) (20 - strlen(bit_label)), "");
                        else
                        	LOGI(" %04x%c                ", j * 8 + k, down);
                    } else {
                    	LOGI(" %04x%c", j * 8 + k, down);
                    }
                    if(i == EV_ABS) {
                        struct input_absinfo abs;
                        if(ioctl(fd, EVIOCGABS(j * 8 + k), &abs) == 0) {
                        	LOGI(" : value %d, min %d, max %d, fuzz %d, flat %d, resolution %d",
                                abs.value, abs.minimum, abs.maximum, abs.fuzz, abs.flat,
                                abs.resolution);
                        }
                    }
                    count++;
                }
        }
        if(count)
        	LOGI("%s", "");
    }
    free(bits);
    return 0;
}

static void print_event(int type, int code, int value)
{
    const char *type_label, *code_label, *value_label;

    if (print_flags & PRINT_LABELS) {
        type_label = get_label(ev_labels, type);
        code_label = NULL;
        value_label = NULL;

        switch(type) {
            case EV_SYN:
                code_label = get_label(syn_labels, code);
                break;
            case EV_KEY:
                code_label = get_label(key_labels, code);
                value_label = get_label(key_value_labels, value);
                break;
            case EV_REL:
                code_label = get_label(rel_labels, code);
                break;
            case EV_ABS:
                code_label = get_label(abs_labels, code);
                switch(code) {
                    case ABS_MT_TOOL_TYPE:
                        value_label = get_label(mt_tool_labels, value);
                }
                break;
            case EV_MSC:
                code_label = get_label(msc_labels, code);
                break;
            case EV_LED:
                code_label = get_label(led_labels, code);
                break;
            case EV_SND:
                code_label = get_label(snd_labels, code);
                break;
            case EV_SW:
                code_label = get_label(sw_labels, code);
                break;
            case EV_REP:
                code_label = get_label(rep_labels, code);
                break;
            case EV_FF:
                code_label = get_label(ff_labels, code);
                break;
            case EV_FF_STATUS:
                code_label = get_label(ff_status_labels, code);
                break;
        }

        if (type_label)
        	LOGI("%-12.12s", type_label);
        else
        	LOGI("%04x        ", type);
        if (code_label)
        	LOGI(" %-20.20s", code_label);
        else
        	LOGI(" %04x                ", code);
        if (value_label)
        	LOGI(" %-20.20s", value_label);
        else
        	LOGI(" %08x            ", value);
    } else {
    	LOGI("%04x %04x %08x", type, code, value);
    }
}

static void print_hid_descriptor(int bus, int vendor, int product)
{
    const char *dirname = "/sys/kernel/debug/hid";
    char prefix[16];
    DIR *dir;
    struct dirent *de;
    char filename[PATH_MAX];
    FILE *file;
    char line[2048];

    snprintf(prefix, sizeof(prefix), "%04X:%04X:%04X.", bus, vendor, product);

    dir = opendir(dirname);
    if(dir == NULL)
        return;
    while((de = readdir(dir))) {
        if (strstr(de->d_name, prefix) == de->d_name) {
            snprintf(filename, sizeof(filename), "%s/%s/rdesc", dirname, de->d_name);

            file = fopen(filename, "r");
            if (file) {
            	LOGI("  HID descriptor: %s\"", de->d_name);
                while (fgets(line, sizeof(line), file)) {
                    fputs("    ", stdout);
                    fputs(line, stdout);
                }
                fclose(file);
                puts("");
            }
        }
    }
    closedir(dir);
}

static int get_device_position(char devicepath[PATH_MAX]){
	if (pDevs == NULL) return -1;
	int index = 0;
	for(index = 0;index < nDevsCount;index++){
		if(strcmp(devicepath, pDevs[index].device_path) == 0){
			return index;
		}
	}
	return -1;
}

static int open_device(char devicepath[PATH_MAX])
{
	int index = get_device_position(devicepath);
	if(index < 0){
		return -1;
	}

	int version;
    int fd;
    struct pollfd *new_ufds;
    char **new_device_names;
    char name[80];
    char location[80];
    char idstr[80];
    struct input_id id;

	ufds[index].events = POLLIN;
	pDevs[index].ufds.events = POLLIN;

	fd = open(devicepath, O_RDWR);
    if(fd < 0) {
        if(print_flags & PRINT_DEVICE_ERRORS)
        	LOGE("could not open %s, %s", devicepath, strerror(errno));
        return -1;
    }
    
	pDevs[index].ufds.fd = fd;
	ufds[index].fd = fd;

	if(ioctl(fd, EVIOCGVERSION, &version)) {
        if(print_flags & PRINT_DEVICE_ERRORS)
        	LOGE("could not get driver version for %s, %s", devicepath, strerror(errno));
        return -1;
    }
	pDevs[index].device_version = version;
    if(ioctl(fd, EVIOCGID, &id)) {
        if(print_flags & PRINT_DEVICE_ERRORS)
        	LOGE("could not get driver id for %s, %s", devicepath, strerror(errno));
        return -1;
    }
	pDevs[index].device_id = id;
    name[sizeof(name) - 1] = '\0';
    if(ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
        if(print_flags & PRINT_DEVICE_ERRORS)
        	LOGE("could not get device name for %s, %s", devicepath, strerror(errno));
        name[0] = '\0';
    }
    strcpy(pDevs[index].device_name, name);
    location[sizeof(location) - 1] = '\0';
    if(ioctl(fd, EVIOCGPHYS(sizeof(location) - 1), &location) < 1) {
        if(print_flags & PRINT_DEVICE_ERRORS)
        	LOGE("could not get location for %s, %s", devicepath, strerror(errno));
        location[0] = '\0';
    }
    strcpy(pDevs[index].device_location, location);
    idstr[sizeof(idstr) - 1] = '\0';
    if(ioctl(fd, EVIOCGUNIQ(sizeof(idstr) - 1), &idstr) < 1) {
        if(print_flags & PRINT_DEVICE_ERRORS)
        	LOGE("could not get idstring for %s, %s", devicepath, strerror(errno));
        idstr[0] = '\0';
    }
    strcpy(pDevs[index].device_idstr, idstr);

    if(print_flags & PRINT_DEVICE)
    	LOGI("add device %d: %s\n", index, devicepath);
    if(print_flags & PRINT_DEVICE_INFO)
    	LOGI("  bus:      %04x\n"
               "  vendor    %04x\n"
               "  product   %04x\n"
               "  version   %04x\n",
               id.bustype, id.vendor, id.product, id.version);
    if(print_flags & PRINT_DEVICE_NAME)
    	LOGI("  name:     \"%s\"", name);
    if(print_flags & PRINT_DEVICE_INFO)
    	LOGI("  location: \"%s\""
               "  id:       \"%s\"", location, idstr);
    if(print_flags & PRINT_VERSION)
    	LOGI("  version:  %d.%d.%d",
               version >> 16, (version >> 8) & 0xff, version & 0xff);

    if(print_flags & PRINT_POSSIBLE_EVENTS) {
        print_possible_events(fd);
    }

    if(print_flags & PRINT_INPUT_PROPS) {
        print_input_props(fd);
    }
    if(print_flags & PRINT_HID_DESCRIPTOR) {
        print_hid_descriptor(id.bustype, id.vendor, id.product);
    }

    return 0;
}

int close_device(char devicepath[PATH_MAX])
{
	int index = get_device_position(devicepath);
	if(index < 0){
		return -1;
	}

	int count = nDevsCount - index - 1;
	LOGI("remove device %s / %d", devicepath, index);

	ufds[index].events = 0x0000;
	pDevs[index].ufds.events = 0x0000;

	memmove(&pDevs[index], &pDevs[index+1], sizeof(pDevs[0]) * count);
    memmove(ufds + index, ufds + index + 1, sizeof(ufds[0]) * count);
	nDevsCount--;

    return 0;
}

//static int read_notify(const char *dirname, int nfd)
//{
//    int res;
//    char devicepath[PATH_MAX];
//    char *filename;
//    char event_buf[512];
//    int event_size;
//    int event_pos = 0;
//    struct inotify_event *event;
//
//    res = read(nfd, event_buf, sizeof(event_buf));
//    if(res < (int)sizeof(*event)) {
//        if(errno == EINTR)
//            return 0;
//        LOGI("could not get event, %s", strerror(errno));
//        return 1;
//    }
//    LOGI("got %d bytes of event information", res);
//
//    strcpy(devicepath, dirname);
//    filename = devicepath + strlen(devicepath);
//    *filename++ = '/';
//
//    while(res >= (int)sizeof(*event)) {
//        event = (struct inotify_event *)(event_buf + event_pos);
//        LOGI("%d: %08x \"%s\"", event->wd, event->mask, event->len ? event->name : "");
//        if(event->len) {
//            strcpy(filename, event->name);
//            if(event->mask & IN_CREATE) {
//                open_device(devicepath, print_flags);
//            }
//            else {
//                close_device(devicepath, print_flags);
//            }
//        }
//        event_size = sizeof(*event) + event->len;
//        res -= event_size;
//        event_pos += event_size;
//    }
//    return 0;
//}

static int add_one_device(char devicepath[PATH_MAX])
{
    LOGD("Prepare to add :%s", devicepath);

	int index = get_device_position(devicepath);

	if(index >= 0){
	    LOGD("Already added:%s", devicepath);
		return 0;
	}

    struct typedev *new_pDevs = realloc(pDevs, sizeof(pDevs[0]) * (nDevsCount + 1));
	if(new_pDevs == NULL) {
		LOGD("%s", "out of memory");
		return -1;
	}
	pDevs = new_pDevs;

	struct pollfd *new_ufds = realloc(ufds, sizeof(ufds[0]) * (nDevsCount + 1));
	if(new_ufds == NULL) {
		LOGD("%s", "out of memory");
		return -1;
	}
	ufds = new_ufds;

	strcpy(pDevs[nDevsCount].device_path, devicepath);

	LOGD("Added %s", pDevs[nDevsCount].device_path);
	nDevsCount++;

    return 0;
}

static int scan_dir(const char *dirname)
{
    char devicepath[PATH_MAX];
    char *filename;
    DIR *dir;
    struct dirent *de;
    dir = opendir(dirname);
    if(dir == NULL)
        return -1;
    strcpy(devicepath, dirname);
    filename = devicepath + strlen(devicepath);
    *filename++ = '/';

    while((de = readdir(dir))) {
        if(de->d_name[0] == '.' &&
           (de->d_name[1] == '\0' ||
            (de->d_name[1] == '.' && de->d_name[2] == '\0')))
            continue;
        strcpy(filename, de->d_name);

        int res = add_one_device(devicepath);
        if( res < 0){
            closedir(dir);
        	return -1;
        }
    }
    closedir(dir);
    return 0;
}

//static void usage(int argc, char *argv[])
//{
//    LOGE("%s", "Usage: %s [-t] [-n] [-s switchmask] [-S] [-v [mask]] [-d] [-p] [-i] [-l] [-q] [-c count] [-r] [device]", argv[0]);
//    LOGE("%s", "    -t: show time stamps");
//    LOGE("%s", "    -s: print switch states for given bits");
//    LOGE("%s", "    -S: print all switch states");
//    LOGE("%s", "    -v: verbosity mask (errs=1, dev=2, name=4, info=8, vers=16, pos. events=32, props=64)");
//    LOGE("%s", "    -d: show HID descriptor, if available");
//    LOGE("%s", "    -p: show possible events (errs, dev, name, pos. events)");
//    LOGE("%s", "    -i: show all device info and possible events");
//    LOGE("%s", "    -l: label event types and names in plain text");
//    LOGE("%s", "    -q: quiet (clear verbosity mask)");
//    LOGE("%s", "    -c: print given number of events then exit");
//    LOGE("%s", "    -r: print rate events are received");
//}

//int command(int argc, char *argv[])
//{
//    int c;
//    int i;
//    int res;
//    int pollres;
//    int get_time = 0;
//    int print_device = 0;
//    uint16_t get_switch = 0;
//    struct input_event event;
//    int version;
//    int print_flags = 0;
//    int print_flags_set = 0;
//    int dont_block = -1;
//    int event_count = 0;
//    int sync_rate = 0;
//    int64_t last_sync_time = 0;
//    const char *device = NULL;
//    const char *device_path = "/dev/input";
//
//    opterr = 0;
//    do {
//        c = getopt(argc, argv, "tns:Sv::dpilqc:rh");
//        if (c == EOF)
//            break;
//        switch (c) {
//        case 't':
//            get_time = 1;
//            break;
//        case 's':
//            get_switch = strtoul(optarg, NULL, 0);
//            if(dont_block == -1)
//                dont_block = 1;
//            break;
//        case 'S':
//            get_switch = ~0;
//            if(dont_block == -1)
//                dont_block = 1;
//            break;
//        case 'v':
//            if(optarg)
//                print_flags |= strtoul(optarg, NULL, 0);
//            else
//                print_flags |= PRINT_DEVICE | PRINT_DEVICE_NAME | PRINT_DEVICE_INFO | PRINT_VERSION;
//            print_flags_set = 1;
//            break;
//        case 'd':
//            print_flags |= PRINT_HID_DESCRIPTOR;
//            break;
//        case 'p':
//            print_flags |= PRINT_DEVICE_ERRORS | PRINT_DEVICE
//                    | PRINT_DEVICE_NAME | PRINT_POSSIBLE_EVENTS | PRINT_INPUT_PROPS;
//            print_flags_set = 1;
//            if(dont_block == -1)
//                dont_block = 1;
//            break;
//        case 'i':
//            print_flags |= PRINT_ALL_INFO;
//            print_flags_set = 1;
//            if(dont_block == -1)
//                dont_block = 1;
//            break;
//        case 'l':
//            print_flags |= PRINT_LABELS;
//            break;
//        case 'q':
//            print_flags_set = 1;
//            break;
//        case 'c':
//            event_count = atoi(optarg);
//            dont_block = 0;
//            break;
//        case 'r':
//            sync_rate = 1;
//            break;
//        case '?':
//            LOGE("%s: invalid option -%c",
//                argv[0], optopt);
//        case 'h':
//            usage(argc, argv);
//            exit(1);
//        }
//    } while (1);
//    if(dont_block == -1)
//        dont_block = 0;
//
//    if (optind + 1 == argc) {
//        device = argv[optind];
//        optind++;
//    }
//    if (optind != argc) {
//        usage(argc, argv);
//        exit(1);
//    }
//    nfds = 1;
//    ufds = calloc(1, sizeof(ufds[0]));
//    ufds[0].fd = inotify_init();
//    ufds[0].events = POLLIN;
//    if(device) {
//        if(!print_flags_set)
//            print_flags |= PRINT_DEVICE_ERRORS;
//        res = open_device(device, print_flags);
//        if(res < 0) {
//            return 1;
//        }
//    } else {
//        if(!print_flags_set)
//            print_flags |= PRINT_DEVICE_ERRORS | PRINT_DEVICE | PRINT_DEVICE_NAME;
//        print_device = 1;
//		res = inotify_add_watch(ufds[0].fd, device_path, IN_DELETE | IN_CREATE);
//        if(res < 0) {
//            LOGE("could not add watch for %s, %s", device_path, strerror(errno));
//            return 1;
//        }
//        res = scan_dir(device_path, print_flags);
//        if(res < 0) {
//            LOGE("scan dir failed for %s", device_path);
//            return 1;
//        }
//    }
//
//    if(get_switch) {
//        for(i = 1; i < nfds; i++) {
//            uint16_t sw;
//            res = ioctl(ufds[i].fd, EVIOCGSW(1), &sw);
//            if(res < 0) {
//            	LOGE("could not get switch state, %s", strerror(errno));
//                return 1;
//            }
//            sw &= get_switch;
//            LOGI("%04x", sw);
//        }
//    }
//
//    if(dont_block)
//        return 0;
//
//    while(1) {
//        pollres = poll(ufds, nfds, -1);
//        //LOGI("poll %d, returned %d", nfds, pollres);
//        if(ufds[0].revents & POLLIN) {
//            read_notify(device_path, ufds[0].fd, print_flags);
//        }
//        for(i = 1; i < nfds; i++) {
//            if(ufds[i].revents) {
//                if(ufds[i].revents & POLLIN) {
//                    res = read(ufds[i].fd, &event, sizeof(event));
//                    if(res < (int)sizeof(event)) {
//                    	LOGE("%s", "could not get event");
//                        return 1;
//                    }
//                    if(get_time) {
//                    	LOGI("[%8ld.%06ld] ", event.time.tv_sec, event.time.tv_usec);
//                    }
//                    if(print_device)
//                        LOGI("%s: ", device_names[i]);
//                    print_event(event.type, event.code, event.value, print_flags);
//                    if(sync_rate && event.type == 0 && event.code == 0) {
//                        int64_t now = event.time.tv_sec * 1000000LL + event.time.tv_usec;
//                        if(last_sync_time)
//                            LOGI(" rate %lld", 1000000LL / (now - last_sync_time));
//                        last_sync_time = now;
//                    }
//                    LOGI("%s", "");
//                    if(event_count && --event_count == 0)
//                        return 0;
//                }
//            }
//        }
//    }
//
//    return 0;
//}

jint Java_com_manusfreedom_android_Events_getDevicesCount( JNIEnv* env, jobject thiz ) {
	return nDevsCount;
}

jint Java_com_manusfreedom_android_Events_AddOneDevice( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);

	int res = add_one_device(fullpath);
	if(res < 0)  {
		LOGE("Failed add device from path: %s", fullpath);
		return -1;
	}

	return nDevsCount;
}

jint Java_com_manusfreedom_android_Events_ScanDir( JNIEnv* env, jobject thiz ) {
	int res = scan_dir(device_path);
	if(res < 0) {
		LOGE("Scan device path failed: %s", device_path);
		return -1;
	}

	return nDevsCount;
}

jstring Java_com_manusfreedom_android_Events_getDeviceAtPosition( JNIEnv* env, jobject thiz, jint index) {
	if (pDevs == NULL || index >= nDevsCount) return (*env)->NewStringUTF(env, "-1");
	return (*env)->NewStringUTF(env, pDevs[index].device_path);
}

jint Java_com_manusfreedom_android_Events_getDeviceVersion( JNIEnv* env, jobject thiz, jstring jfullpath) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return -1;
	}
	else{
		return pDevs[index].device_version;
	}
}
jstring Java_com_manusfreedom_android_Events_getDevicePath( JNIEnv* env, jobject thiz, jstring jfullpath) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return (*env)->NewStringUTF(env, "-1");
	}
	else{
		return (*env)->NewStringUTF(env, pDevs[index].device_path);
	}
}
jstring Java_com_manusfreedom_android_Events_getDeviceName( JNIEnv* env, jobject thiz, jstring jfullpath) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return (*env)->NewStringUTF(env, "-1");
	}
	else{
		return (*env)->NewStringUTF(env, pDevs[index].device_name);
	}
}
jstring Java_com_manusfreedom_android_Events_getDeviceLocation( JNIEnv* env, jobject thiz, jstring jfullpath) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return (*env)->NewStringUTF(env, "-1");
	}
	else{
		return (*env)->NewStringUTF(env, pDevs[index].device_location);
	}
}
jstring Java_com_manusfreedom_android_Events_getDeviceIdStr( JNIEnv* env, jobject thiz, jstring jfullpath) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return (*env)->NewStringUTF(env, "-1");
	}
	else{
		return (*env)->NewStringUTF(env, pDevs[index].device_idstr);
	}
}

jint Java_com_manusfreedom_android_Events_OpenDevice( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	return open_device(fullpath);
}

jint Java_com_manusfreedom_android_Events_CloseDevice( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	return close_device(fullpath);
}

jint Java_com_manusfreedom_android_Events_PollDevice( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return -1;
	}
	else{
		if (index >= nDevsCount || pDevs[index].ufds.fd == -1) return -1;
		LOGD("Poll: %s", pDevs[index].device_path);
		int pollres = poll(&pDevs[index].ufds, 1, -1);
		if (pollres > 0) {
			if(pDevs[index].ufds.revents & POLLIN) {
				int res = read(pDevs[index].ufds.fd, &pDevs[index].event, sizeof(pDevs[index].event));
				if(res < (int)sizeof(pDevs[index].event)) {
					LOGE("Could not get event: %s", pDevs[index].device_path);
					return 1;
				}
				else return 0;
			}
		}
	}
	return -1;
}

jint Java_com_manusfreedom_android_Events_PollAllDevices( JNIEnv* env, jobject thiz ) {
	LOGD("%s", "Poll all device");
	int pollres = poll(ufds, nDevsCount, -1);
	int res = -1;
	int index = 0;
	if (pollres > 0) {
		for(index = 0; index < nDevsCount; index++){
			if(ufds[index].revents & POLLIN) {
				int res = read(ufds[index].fd, &pDevs[index].event, sizeof(pDevs[index].event));
				if(res < (int)sizeof(pDevs[index].event)) {
					LOGE("Could not get event: %s", pDevs[index].device_path);
				}
				res = 0;
			}
		}
	}
	return res;
}

jint Java_com_manusfreedom_android_Events_getType( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return -1;
	}
	return pDevs[index].event.type;
}

jint Java_com_manusfreedom_android_Events_getCode( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return -1;
	}
	return pDevs[index].event.code;
}

jint Java_com_manusfreedom_android_Events_getValue( JNIEnv* env, jobject thiz, jstring jfullpath ) {
	char fullpath[PATH_MAX];
	const char *strTmp = (*env)->GetStringUTFChars(env, jfullpath, 0);
	strcpy(fullpath, strTmp);
	(*env)->ReleaseStringUTFChars(env, jfullpath, strTmp);
	int index = get_device_position(fullpath);
	if(index < 0){
		return -1;
	}
	return pDevs[index].event.value;
}

jint Java_com_manusfreedom_android_Events_getPrintFlags( JNIEnv* env, jobject thiz ) {
	return print_flags;
}

jint Java_com_manusfreedom_android_Events_setPrintFlags( JNIEnv* env, jobject thiz, jint jprint_flags ) {
	print_flags = jprint_flags;
	return print_flags;
}
