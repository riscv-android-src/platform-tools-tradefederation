# ATEST

Atest is a command line tool that allows users to build, install and run Android tests locally.
This markdown will explain how to use atest on the commandline to run android tests.<br>

**For instructions on writing tests [go here](https://android.googlesource.com/platform/platform_testing/+/master/docs/index.md).**
Importantly, when writing your test's build script file (Android.mk), make sure to include
the variable `LOCAL_COMPATIBILITY_SUITE`.  A good default to use for it is `device-test`.

##### Table of Contents
1. [Environment Setup](#environment-setup)
2. [Basic Usage](#basic-usage)
3. [Identifying Tests](#identifying-tests)
4. [Specifying Steps: Build, Install or Run](#specifying-steps)
5. [Running Specific Methods](#running-specific-methods)
6. [Running Multiple Classes](#running-multiple-classes)
7. [Additional Examples](#additional-examples)


## <a name="environment-setup">Environment Setup</a>

Before you can run atest, you first have to setup your environment.

##### 1. Run envsetup.sh
From the root of the android source checkout, run the following command:

`$ source build/envsetup.sh`

##### 2. Run lunch

Run the `$ lunch` command to bring up a "menu" of supported devices.  Find the device that matches
the device you have connected locally and run that command.

For instance, if you have a marlin device connected, you would run the following command:

`$ lunch marlin-userdebug`

This will set various environment variables necessary for running atest. It will also add the
atest command to your $PATH.

## <a name="basic-usage">Basic Usage</a>

Atest commands take the following form:

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**atest \<optional arguments> \<tests to run>**

#### \<optional arguments>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-b&nbsp;&nbsp;&nbsp;--build&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Build test targets.<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-i &nbsp;&nbsp;&nbsp;--install&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Install test artifacts (APKs) on device.<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-t &nbsp;&nbsp;&nbsp;--test&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Run the tests.<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-w&nbsp;&nbsp;&nbsp;--wait-for-debugger&nbsp;&nbsp;&nbsp;&nbsp; Only for instrumentation tests. Waits for debugger prior to execution.<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-v&nbsp;&nbsp;&nbsp;--verbose&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Display DEBUG level logging.<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-h&nbsp;&nbsp;&nbsp;--help&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;show this help message and exit<br>

More information about **-b**, **-i** and **-t** can be found below under [Specifying Steps: Build, Install or Run.](#specifying-steps)

#### \<tests to run>

   The positional argument **\<tests to run>** should be a reference to one or more of the tests
   you'd like to run. Multiple tests can be run by separating test references with spaces.<br>

   Usage form: &nbsp; `atest <reference_to_test_1> <reference_to_test_2>`

   Here are some simple examples:

   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests`<br>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest example/reboot`<br>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests CtsJankDeviceTestCases`<br>
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests:ScreenDecorWindowTests`<br>

   More information on how to reference a test can be found under [Identifying Tests.](#identifying-tests)


## <a name="identifying-tests">Identifying Tests</a>

  You can identify a test by inputting it's **Module Name**, **Module:Class**, **Class Name**, **TF Integration Test** or **File Path**.

  #### Module Name

  Identifying a test by its module name will run the entire module. Input
  the name as it appears in the `LOCAL_MODULE` or `LOCAL_PACKAGE_NAME`
  variables in that test's **Android.mk** or **Android.bp** file.

  Note: Use TF Integration Test below to run non-module tests integrated directly into TradeFed.

  Examples:<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests`<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest CtsJankDeviceTestCases`<br>


  #### Module:Class

  Identifying a test by its class name will run just the tests in that
  class and not the whole module. **Module:Class** is the preferred way to run
  a single class. **Module** is the same as described above. **Class** is the
  name of the test class in the .java file. It can either be the fully
  qualified class name or just the basic name.

  Examples:<br>
       &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests:ScreenDecorWindowTests`<br>
       &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest PtsBatteryTestCases:com.google.android.battery.pts.BatteryTest`<br>
       &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest CtsJankDeviceTestCases:CtsDeviceJankUi`<br>


  #### Class Name

  A single class can also be run by referencing the class name without
  the module name. However, this will take more time than the equivalent
  **Module:Class** reference, so we suggest using a **Module:Class** reference
  whenever possible.

  Examples:<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest ScreenDecorWindowTests`<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest com.google.android.battery.pts.BatteryTest`<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest CtsDeviceJankUi`<br>


  #### TF Integration Test

  To run tests that are integrated directly into TradeFed (non-modules),
  input the name as it appears in the output of the "tradefed.sh list
  configs" cmd.

  Example:<br>
     &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest example/reboot` &nbsp;(runs [this test](https://android.googlesource.com/platform/tools/tradefederation/contrib/+/master/res/config/example/reboot.xml))<br>
     &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest native-benchmark` &nbsp;(runs [this test](https://android.googlesource.com/platform/tools/tradefederation/+/master/res/config/native-benchmark.xml))<br>


  #### File Path

  Both module-based tests and integration-based tests can be run by
  inputting the path to their test file or dir as appropriate. A single
  class can also be run by inputting the path to the class's java file.
  Both relative and absolute paths are supported.

  Example - 4 ways to run the `CtsJankDeviceTestCases` module via path:<br>
  1. From android repo root: `atest cts/tests/jank`
  2. From android repo root: `atest cts/tests/jank/Android.mk`
  3. From \<android root>/cts/tests/jank: `atest .`
  4. Atest can resolve a module from anywhere inside that module's tree, so from \<android root>/cts/tests/jank/src/android: `atest .`

  Example - run a specific class within `CtsJankDeviceTestCases` module via path:<br>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;From android repo root: `atest cts/tests/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java`

  Example - run an integration test via path:<br>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;From android repo root: `atest tools/tradefederation/contrib/res/config/example/reboot.xml`


## <a name="specifying-steps">Specifying Steps: Build, Install or Run</a>

The **-b**, **-i** and **-t** options allow you to specify which steps you want to run.  If none of
those options are given, then all steps are run. If any of these options are provided then only the
listed steps are run.

Note: **-i** alone is not currently support and can only be included with **-t**.  Both **-b** and **-t** can be run alone.

- Build targets only:  `atest -b <test>`
- Run tests only: `atest -t <test> `
- Install apk and run tests: `atest -it <test> `
- Build and run, but don't install: `atest -bt <test> `


## <a name="running-specific-methods">Running Specific Methods</a>

It is possible to run only specific methods within a test class. While the whole module will
still need to be built, this will greatly speed up the time needed to run the tests. To run only
specific methods, identify the class in any of the ways supported for identifying a class
(MODULE:CLASS, FILE PATH, etc) and then append the name of the method or method using the following
template:

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`<reference_to_class>#<method1>`

Multiple methods can be specified with commas:

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`<reference_to_class>#<method1>,<method2>,<method3>`

Examples:<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests:ScreenDecorWindowTests#testFlagChange,testRemoval`<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest com.google.android.battery.pts.BatteryTest#testDischarge`


## <a name="running-multiple-classes">Running Multiple Classes</a>

To run multiple classes, deliminate them with spaces just like you would when running multiple tests.
Atest will handle building and running classes in the most efficient way possible, so specifying
a subset of classes in a module will improve performance over running the whole module.

Examples:<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests:ScreenDecorWindowTests FrameworksServicesTest:DimmerTests` <br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests:ScreenDecorWindowTests CtsJankDeviceTestCases:CtsDeviceJankUi`


## <a name="additional-examples">Additional Examples</a>

Here are the two preferred ways to run a single method, we're specifying the `testFlagChange` method.
These are preferred over just the class name, because specifying the module or the java file location
allows atest to find the test much faster:

1. `atest FrameworksServicesTests:ScreenDecorWindowTests#testFlagChange`
2. From android repo root: `atest frameworks/base/services/tests/servicestests/src/com/android/server/wm/ScreenDecorWindowTests.java#testFlagChange`

Here we run multiple methods from different classes and modules.<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`atest FrameworksServicesTests:ScreenDecorWindowTests#testFlagChange,testRemoval PtsBatteryTestCases:BatteryTest#testDischarge`
