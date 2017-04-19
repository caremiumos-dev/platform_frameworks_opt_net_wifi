#!/usr/bin/env bash

if [[ ! ($# == 1) ]]; then
  echo "$0: usage: coverage.sh OUTPUT_DIR"
  exit 1
fi

if [ -z $ANDROID_BUILD_TOP ]; then
  echo "You need to source and lunch before you can use this script"
  exit 1
fi

cd "$(dirname $0)" #cd to directory containing this script


REPORTER_JAR=$ANDROID_HOST_OUT/framework/jacoco-cli.jar

OUTPUT_DIR=$1

echo "Running tests and generating coverage report"
echo "Output dir: $OUTPUT_DIR"

REMOTE_COVERAGE_OUTPUT_FILE=/data/data/com.android.server.wifi.test/files/coverage.ec
COVERAGE_OUTPUT_FILE=$OUTPUT_DIR/wifi_coverage.ec

set -e # fail early

echo "+ EMMA_INSTRUMENT_STATIC=true mmma -j32 $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/tests"
# NOTE Don't actually run the command above since this shell doesn't inherit functions from the
#      caller.
# build this module so we can run its tests, and
# build system/core so we can invoke `adb`, and
# build jacoco-report-classes.jar so we can generate the report
INSTRUMENT_FRAMEWORK=false
EMMA_INSTRUMENT_FRAMEWORK=${INSTRUMENT_FRAMEWORK} EMMA_INSTRUMENT=true EMMA_INSTRUMENT_STATIC=true ANDROID_COMPILE_WITH_JACK=false SKIP_BOOT_JARS_CHECK=true make -j32 -C $ANDROID_BUILD_TOP -f build/core/main.mk MODULES-IN-frameworks-opt-net-wifi-tests MODULES-IN-system-core out/target/common/obj/APPS/FrameworksWifiTests_intermediates/jacoco/report-resources/jacoco-report-classes.jar

set -x # print commands

adb root
adb wait-for-device

adb shell rm -f $REMOTE_COVERAGE_OUTPUT_FILE

adb install -r -g "$OUT/data/app/FrameworksWifiTests/FrameworksWifiTests.apk"

adb shell am instrument -e coverage true -w 'com.android.server.wifi.test/android.support.test.runner.AndroidJUnitRunner'

mkdir -p $OUTPUT_DIR

adb pull $REMOTE_COVERAGE_OUTPUT_FILE $COVERAGE_OUTPUT_FILE

java -jar $REPORTER_JAR \
  report \
  -classfiles $ANDROID_PRODUCT_OUT/../../common/obj/APPS/FrameworksWifiTests_intermediates/jacoco/report-resources/jacoco-report-classes.jar \
  -html $OUTPUT_DIR \
  -sourcefiles $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/tests/wifitests/src -sourcefiles $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/service/java \
  -name wifi-coverage \
  $COVERAGE_OUTPUT_FILE

echo Created report at $OUTPUT_DIR/index.html

