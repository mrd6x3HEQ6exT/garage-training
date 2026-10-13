# Garage Motion Lighting (Hubitat app)

A small Hubitat app that replaces "Lights on Motion Plus" for the garage.

## What it does

- Any motion sensor seeing movement turns the lights on, as long as it is dark enough.
- The lights stay on for as long as any sensor keeps seeing movement. No timer runs while you are moving.
- Once **all** sensors have gone quiet, a 10 minute countdown starts. Any new motion cancels it. When it runs out, the lights go off.
- "Dark enough" means either of these is true:
  - the clock is inside the night window: **45 minutes before sunset** through sunrise (both offsets adjustable), or
  - any selected lux sensor reads **below 50 lux** (adjustable). This covers a shut garage on a bright day.
- Lux only decides whether to turn the lights **on**. Once on, only the countdown turns them off, so the lights lighting up the sensor never switches them off.
- If you flip the wall switch **off** while you are still in the garage, the app will not fight you. It waits until all motion has stopped before it will light the room again.
- Optional: if you flip the wall switch **on** by hand, leave the lights on until you switch them off.

## Install

1. Hubitat web UI: **Apps Code** (left menu) > **New App** > paste the whole of `garage-motion-lighting.groovy` > **Save**.
2. **Apps** > **Add User App** > **Garage Motion Lighting**.
3. Fill in:
   - **Motion sensors**: `2.5 - Garage - Workbench Motion Sensor` and `2.5 - Garage - Storage Rack Motion Sensor`.
   - **Lights**: `2.5 - Garage - WorkBench Light`.
   - **Off delay**: 10.
   - **Sunset window**: on, 45 minutes before sunset, 0 after sunrise.
   - **Lux sensors**: the same two Zooz Q sensors (they report illuminance). Threshold 50 to start; look at the lux values the sensors report on a bright day with the door open vs. shut and adjust.
4. **Done**.
5. Disable or remove the Lights on Motion Plus instance that controls the same light, otherwise the two apps will fight over it.

## Tuning notes

- The Zooz ZSE11 has its own "motion timeout" parameter (how long after the last movement it reports inactive). That time is added on top of the 10 minutes. Set it short (15 to 30 seconds) so the app's countdown is what governs.
- The ZSE11 only reports lux when it changes by more than its configured threshold, so a reading can be a little stale. The sunset window does not depend on it, so evenings always work even if the lux reading lags.
- Turn on **debug logging** in the app for the first few days. Every decision is logged ("bright / daytime; leaving lights off", "lights will turn off in 10 min", etc.). It switches itself off after 30 minutes.
- One instance controls one group of lights. Install the app again for another room.
