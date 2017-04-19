#!/usr/bin/env bash

if [[ ! ( ($# == 1) || ($# == 2 && ($2 == "HTML" || $2 == "XML" || $2 == "CSV"))) ]]; then
  echo "$0: usage: coverage.sh OUTPUT_DIR [REPORT_TYPE]"
  echo "REPORT_TYPE [HTML | XML | CSV] : the type of the report (default is HTML)"
  exit 1
fi

if [ -z $ANDROID_BUILD_TOP ]; then
  echo "You need to source and lunch before you can use this script"
  exit 1
fi

cd "$(dirname $0)" #cd to directory containing this script


#USE_JACK=true
USE_JACK=false
if $USE_JACK; then
  REPORTER_JAR=$ANDROID_BUILD_TOP/prebuilts/sdk/tools/jack-jacoco-reporter.jar
else
  REPORTER_JAR=$ANDROID_HOST_OUT/framework/jacoco-cli.jar
fi

OUTPUT_DIR=$1
if [[ $# == 2 ]]; then
  REPORT_TYPE=$2
else
  REPORT_TYPE="HTML"
fi

echo "Running tests and generating coverage report"
echo "Output dir: $OUTPUT_DIR"
echo "Report type: $REPORT_TYPE"

REMOTE_COVERAGE_OUTPUT_FILE=/data/data/com.android.server.wifi.test/files/coverage.ec
COVERAGE_OUTPUT_FILE=$OUTPUT_DIR/wifi_coverage.ec
COVERAGE_METADATA_FILE=$ANDROID_BUILD_TOP/out/target/common/obj/APPS/FrameworksWifiTests_intermediates/coverage.em

set -e # fail early

echo "+ EMMA_INSTRUMENT_STATIC=true mmma -j32 $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/tests"
# NOTE Don't actually run the command above since this shell doesn't inherit functions from the
#      caller.
#build this module so we can run its tests, and build system/core so we can invoke `adb`
INSTRUMENT_FRAMEWORK=false
EMMA_INSTRUMENT_FRAMEWORK=${INSTRUMENT_FRAMEWORK} EMMA_INSTRUMENT=true EMMA_INSTRUMENT_STATIC=true ANDROID_COMPILE_WITH_JACK=${USE_JACK} SKIP_BOOT_JARS_CHECK=true make -j32 -C $ANDROID_BUILD_TOP -f build/core/main.mk MODULES-IN-frameworks-opt-net-wifi-tests MODULES-IN-system-core

set -x # print commands

RUN_TESTS=true
if [ "$RUN_TESTS" == "true" ]; then
  adb root
  adb wait-for-device

  adb shell rm -f $REMOTE_COVERAGE_OUTPUT_FILE

  adb install -r -g "$OUT/data/app/FrameworksWifiTests/FrameworksWifiTests.apk"

  adb shell am instrument -e coverage true -w 'com.android.server.wifi.test/android.support.test.runner.AndroidJUnitRunner'
fi

mkdir -p $OUTPUT_DIR

adb pull $REMOTE_COVERAGE_OUTPUT_FILE $COVERAGE_OUTPUT_FILE

if $USE_JACK; then
  java -jar $REPORTER_JAR \
    --report-dir $OUTPUT_DIR \
    --metadata-file $COVERAGE_METADATA_FILE \
    --coverage-file $COVERAGE_OUTPUT_FILE \
    --report-type $REPORT_TYPE \
    --source-dir $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/tests/wifitests/src \
    --source-dir $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/service/java
else

  #TODO how can we consolidate the includes and excludes here that were duplicated from Android.mk?
  sourceFiles="$(cd src && find -name '*.java' | sed 's|^\./||')"
  exclusions="$(echo ${sourceFiles} | sed 's|\.java|*|g' | sed 's|\/|.|g' | sed 's| |:|g')"
  java -jar $REPORTER_JAR \
    report \
    --classes $ANDROID_PRODUCT_OUT/../../common/obj/APPS/FrameworksWifiTests_intermediates/classes-no-debug-var.jar \
    --html-out $OUTPUT_DIR \
    --sources $ANDROID_BUILD_TOP/frameworks/opt/net/wifi/tests/wifitests/src,$ANDROID_BUILD_TOP/frameworks/opt/net/wifi/service/java \
    --data $COVERAGE_OUTPUT_FILE \
    --title wifi-coverage \
    --include "com.android.server.wifi.*" \
    --exclude "${exclusions}"

  echo Created report at $OUTPUT_DIR/index.html
fi

