DOCKER ?= docker
IMAGE ?= local/ftc-toolchain:sdk12
HOME_VOLUME ?= ftc-toolchain-home
PROJECT ?= .
UDEV_RULE := 99-android-adb.rules

# Rebuild the toolchain image only when toolchain/ changes; forget the stamp if
# the image itself was removed from Docker.
IMAGE_STAMP := .toolchain-image
ifneq ($(shell $(DOCKER) image inspect $(IMAGE) >/dev/null 2>&1 && echo present),present)
$(shell rm -f $(IMAGE_STAMP))
endif

# Group granted USB access by the udev rule: the owner of this checkout, which is
# the account `make build/deploy` (and so the toolchain containers) run as.
ADB_GROUP ?= $(shell stat -c %G $(CURDIR))

# Root on the host (host networking makes localhost the laptop), used for
# host setup and Wi-Fi switching from inside the VS Code container.
ROOT_SSH ?= ssh root@localhost

# Robot Wi-Fi (a saved NetworkManager profile on the host) and the hub's adb address.
HUB_SSID ?= 31744-RC
HUB_ADDR ?= 192.168.43.1:5555
# The laptop's timezone, so backup folder names use local time (containers default to UTC).
HOST_TZ ?= $(shell $(ROOT_SSH) timedatectl show -p Timezone --value 2>/dev/null || echo UTC)
APK := TeamCode/build/outputs/apk/debug/TeamCode-debug.apk
# adb install doesn't start the app (Android Studio's Run does), so launch it explicitly.
LAUNCH_RC := am start -n com.qualcomm.ftcrobotcontroller/org.firstinspires.ftc.robotcontroller.internal.PermissionValidatorWrapper

# Run as the invoking user so build outputs in the bind mount stay owned by you.
# Host networking lets adb reach the hub over Wi-Fi as well as USB.
# /dev/bus/usb is bind-mounted (not --device) so the hub can be plugged in or
# replugged while the container runs; 189 is the Linux USB device major number.
RUN := $(DOCKER) run --rm \
	--user "$$(id -u):$$(id -g)" \
	--network host \
	--env TZ=$(HOST_TZ) \
	--volume $(HOME_VOLUME):/home/builder \
	--volume "$(CURDIR):/ftc" \
	--workdir /ftc/$(PROJECT) \
	--volume /dev/bus/usb:/dev/bus/usb \
	--device-cgroup-rule "c 189:* rmw"

.DEFAULT_GOAL := help

.PHONY: help udev image build deploy deploy-wifi deploy-wifi-fresh hub-config hub-backup usb-backup driver-station blocks devices logcat shell clean
help:
	@printf '%s\n' \
	  'make udev     One-time: install the hub USB access rule on the host (via $(ROOT_SSH))' \
	  'make image    Force a rebuild of the toolchain image (otherwise automatic)' \
	  'make build    Compile TeamCode into an APK (needs internet the first time)' \
	  'make deploy   Build and install onto the Control Hub over USB' \
	  'make deploy-wifi  Build, hop onto $(HUB_SSID), install, then return to your Wi-Fi' \
	  'make deploy-wifi-fresh  Same, but uninstall a differently-signed Robot Controller first' \
	  'make hub-config  Push robot-config/*.xml to the hub over Wi-Fi (then Activate on the Driver Hub)' \
	  'make hub-backup  Copy the hub'"'"'s /sdcard/FIRST (Blocks, configs) to hub-backup/ over Wi-Fi' \
	  'make usb-backup  Same backup from a USB-connected device (e.g. the Driver Hub)' \
	  'make driver-station  Update the USB-connected Driver Hub to Driver Station $(DS_VERSION)' \
	  'make blocks   Blocks/OnBot Java editor at http://localhost:$(BLOCKS_PORT) via the Control Hub on USB' \
	  'make devices  List adb devices (check the hub is visible)' \
	  'make logcat   Stream robot logs (Ctrl-C to stop)' \
	  'make shell    Interactive shell in the toolchain container' \
	  'make clean    Remove build outputs and the Gradle cache volume'

$(IMAGE_STAMP): toolchain/Dockerfile
	$(DOCKER) buildx build --load --tag $(IMAGE) toolchain
	touch $@

image:
	rm -f $(IMAGE_STAMP)
	$(MAKE) $(IMAGE_STAMP)

# Host setup over SSH as root, so it works from inside the VS Code container.
udev:
	sed 's/@ADB_GROUP@/$(ADB_GROUP)/' toolchain/$(UDEV_RULE) | $(ROOT_SSH) '\
	  install --mode 0644 /dev/stdin /etc/udev/rules.d/$(UDEV_RULE) && \
	  { ! command -v restorecon >/dev/null || restorecon /etc/udev/rules.d/$(UDEV_RULE); } && \
	  udevadm control --reload-rules && \
	  udevadm trigger --subsystem-match=usb --action=change'
	@echo "Installed /etc/udev/rules.d/$(UDEV_RULE) via $(ROOT_SSH) (group $(ADB_GROUP)); replug the hub"

build: $(IMAGE_STAMP)
	$(RUN) $(IMAGE) ./gradlew :TeamCode:assembleDebug

deploy: $(IMAGE_STAMP)
	$(RUN) $(IMAGE) sh -c 'adb wait-for-usb-device && ./gradlew :TeamCode:installDebug && adb shell $(LAUNCH_RC)'

# Run a command on the robot's Wi-Fi, switching back afterwards (see toolchain/hub-wifi.sh).
ON_HUB = ROOT_SSH='$(ROOT_SSH)' HUB_SSID='$(HUB_SSID)' HUB_ADDR='$(HUB_ADDR)' \
	RUN='$(RUN)' IMAGE='$(IMAGE)' toolchain/hub-wifi.sh

# Build while still online, so only the install happens on the robot's network.
deploy-wifi: build
	$(ON_HUB) 'adb -s $$HUB install -r $(APK) && adb -s $$HUB shell $(LAUNCH_RC) && echo "Installed and started"'

# First deploy onto a hub whose Robot Controller app is signed with another key
# (factory or REV Hardware Client install). /sdcard/FIRST is left untouched.
deploy-wifi-fresh: build
	$(ON_HUB) 'adb -s $$HUB uninstall com.qualcomm.ftcrobotcontroller || true; \
	  adb -s $$HUB install $(APK) && adb -s $$HUB shell $(LAUNCH_RC) && echo "Installed and started"'

# Push robot configurations (robot-config/*.xml) to the hub; activate them on the Driver Hub.
# Overwrites same-named configs on the hub, including edits made from the Driver Hub.
hub-config: $(IMAGE_STAMP)
	$(ON_HUB) 'for f in /ftc/robot-config/*.xml; do adb -s $$HUB push "$$f" /sdcard/FIRST/; done'

# Copy the hub's /sdcard/FIRST (Blocks, robot configs, OnBot Java, logs) into hub-backup/<timestamp>/.
hub-backup: $(IMAGE_STAMP)
	$(ON_HUB) 'dest=/ftc/hub-backup/$$(date +%Y%m%d-%H%M%S) && mkdir -p $$dest && \
	  adb -s $$HUB pull /sdcard/FIRST $$dest/ && echo "Saved to $${dest#/ftc/}"'

# Same backup from a USB-connected device (e.g. the Driver Hub) into usb-backup/<model>-<timestamp>/.
usb-backup: $(IMAGE_STAMP)
	$(RUN) $(IMAGE) sh -c 'adb wait-for-usb-device && \
	  dest=/ftc/usb-backup/$$(adb -d shell getprop ro.product.model | tr -d "\r" | tr " " _)-$$(date +%Y%m%d-%H%M%S) && \
	  mkdir -p $$dest && adb -d pull /sdcard/FIRST $$dest/ && echo "Saved to $${dest#/ftc/}"'

# Update the Driver Hub's Driver Station app (on USB) to FIRST's release matching our SDK.
# FIRST signs it, so it installs as an update and keeps the Driver Station's settings.
DS_VERSION ?= v12.0
DS_APK := apks/FtcDriverStation-$(DS_VERSION).apk

$(DS_APK):
	mkdir -p apks
	curl --fail --location --output $@ \
	  https://github.com/FIRST-Tech-Challenge/FtcRobotController/releases/download/$(DS_VERSION)/FtcDriverStation-release.apk

driver-station: $(IMAGE_STAMP) $(DS_APK)
	$(RUN) $(IMAGE) sh -c 'adb wait-for-usb-device && adb -d install -r /ftc/$(DS_APK) && \
	  adb -d shell dumpsys package com.qualcomm.ftcdriverstation | grep -m1 versionName'

# Blocks / OnBot Java editor over the USB cable: forward a laptop port to the Control Hub's
# web server, so the laptop keeps its normal Wi-Fi. The forward lasts until Ctrl-C.
# Picks the first USB device that isn't the Driver Hub, so both can be plugged in.
BLOCKS_PORT ?= 8080

blocks: $(IMAGE_STAMP)
	$(RUN) --init --interactive --tty $(IMAGE) sh -c '\
	  echo "Waiting for the Control Hub on USB..."; \
	  while :; do \
	    for s in $$(adb devices | awk "NR > 1 && \$$2 == \"device\" && \$$1 !~ /:/ { print \$$1 }"); do \
	      model=$$(adb -s $$s shell getprop ro.product.model | tr -d "\r"); \
	      [ "$$model" != "Driver Hub" ] && hub=$$s && break 2; \
	    done; \
	    sleep 2; \
	  done; \
	  adb -s $$hub forward tcp:$(BLOCKS_PORT) tcp:8080 && \
	  curl --silent --fail --max-time 10 --output /dev/null http://localhost:$(BLOCKS_PORT)/ && \
	  echo "Editor ready: http://localhost:$(BLOCKS_PORT)  ($$model, $$hub). Ctrl-C to stop." && \
	  while :; do sleep 60; done'

devices: $(IMAGE_STAMP)
	$(RUN) $(IMAGE) sh -c 'lsusb; adb devices -l'

logcat: $(IMAGE_STAMP)
	$(RUN) --interactive --tty $(IMAGE) adb logcat

shell: $(IMAGE_STAMP)
	$(RUN) --interactive --tty $(IMAGE) bash

clean:
	-$(RUN) $(IMAGE) ./gradlew clean
	-$(DOCKER) volume rm $(HOME_VOLUME)
	-rm -f $(IMAGE_STAMP)
