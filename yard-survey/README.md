# Yard Survey

A small Android app for shooting survey points in a yard with a phone strapped to a
grading rod. Every capture averages raw GPS fixes over a window, subtracts the rod height,
and uses the phone's orientation sensors to move the point from the phone to the rod tip.
Points are stored on the phone and export as CSV or JSON.

No third-party libraries, no AndroidX, no accounts, no network: platform APIs only.

## Build and install

You need Android Studio (Ladybug or newer, which bundles a JDK 17+). Command-line builds
need `ANDROID_HOME` pointing at an SDK with platform 34.

```
cd yard-survey
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the `yard-survey` folder in Android Studio and press Run with the phone plugged in
(USB debugging on). To sideload without a cable, copy `app-debug.apk` to the phone and open it.

Unit tests for the geodesy, tilt and averaging math:

```
./gradlew test
```

## Field use

1. Strap the phone to the rod, long edge along the rod, screen toward you, top of the phone
   up. (Or lay it flat on top of the rod and change the mount mode in Settings.)
2. Settings: enter the rod height from the tip to roughly the middle of the phone. Pick feet
   or metres. Leave tilt compensation on.
3. Stand outside with a clear sky view and wait for "GPS fix" on the Capture tab. The first
   fix can take a minute.
4. Put the tip on the spot, watch the bubble, hold still and press CAPTURE. The phone beeps
   when the averaging window is done. The point is saved and the name auto-increments.
5. The first point becomes the base (origin) of a local east/north/up grid. Points and Map
   show every other point relative to it: distance, bearing, elevation difference and grade.
   Change the base on the Points tab (tap a point) or in Settings.
6. Points tab: Save CSV writes a file wherever you choose; Share CSV sends it as text to any
   app; Backup / Import JSON round-trips the whole set.

Steel rods will disturb the compass. That only affects headings and the direction of the tilt
correction, not tilt magnitude or elevation. If the heading looks wrong, wave the phone in a
figure-8 to recalibrate, or use a fibreglass or aluminium rod.

## What "orientation and all that cool stuff" actually does

* Rotation-vector sensor (gyro + accelerometer + magnetometer fusion) gives the phone's
  attitude. The app resolves it into rod tilt from vertical, the true bearing the rod top
  leans toward, and the true heading you are facing (magnetic declination is applied from
  the platform geomagnetic model at your position).
* Per fix, the rod tip is placed `rodHeight` metres back along the rod direction from the
  antenna. Plumb rod: straight down. Rod leaning 10 degrees east with a 2 m rod: the tip is
  0.35 m west of and 1.97 m below the phone. That is the tilt compensation.
* Fixes over the window are converted to a local metric frame, the tips are averaged, and the
  scatter is reported alongside the accuracy figures the GPS chip claims.
* The bubble level warns when tilt exceeds the threshold. You can still capture; the
  correction handles it, but a wobbling rod adds scatter.

## Accuracy, honestly

A phone's GPS is not a survey receiver. Expect absolute position errors of a few metres and
elevation noise of a metre or so even after averaging; relative positions between points shot
within a few minutes of each other are better because the errors are correlated. Use longer
averaging windows (30 to 60 s), re-shoot the base point occasionally to see the drift, and
treat single-shot elevation differences under a foot as noise. Android 14+ adds a
mean-sea-level altitude from the platform geoid model; grading only needs differences, which
are the same in ellipsoidal and MSL heights.

## Layout

```
app/src/main/java/com/garage/yardsurvey/
  Geo.kt                WGS84 local ENU frame, rod tip offset, declination rotation
  Attitude.kt           rotation matrix -> tilt, lean azimuth, heading, bubble
  Capture.kt            fix averaging for one capture window
  Model.kt              SurveyPoint, JSON store, CSV export
  Units.kt, Prefs.kt    display units and settings
  LocationTracker.kt    raw GPS provider, satellite counts, MSL altitude on API 34+
  OrientationTracker.kt rotation-vector sensor
  LevelView.kt          bubble level widget
  PlanView.kt           pan/zoom plan view of the points
  *Screen.kt            the four tabs, built in code (no layout XML)
  MainActivity.kt       wiring, capture flow, export/import
app/src/test/...        JUnit tests for the math
```
