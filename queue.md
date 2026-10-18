QUEUE — approved work not yet built. Newest first. Check alongside mistake.md before
any build session. Remove an item once it ships (note the build number).

---
SHIPPED v36 (pending on-device confirmation) — 2026-08-24 — screen lock re-activates after tapping Ungroup
Reported: mid-workout, tapping Ungroup let the phone's lock screen come back on (the
wake-lock stopped holding). Root-cause theory (from the original trace): the Wake Lock
API silently drops the lock on events like a screen dim, and some browsers refuse a
re-request unless it is tied closely to a user gesture — and Ungroup only re-requested
from renderToday(), AFTER a full DOM rebuild, far enough from the tap that the browser
could reject it. requestWake()'s catch block was also empty, so any failure was invisible.
FIX (v36): the main click handler now calls requestWake() synchronously at the top, inside
the user-gesture window, on EVERY tap during an active workout (before the action's own
async render) — so Ungroup and every other button keep the screen awake. requestWake()
no-ops when the lock is already held, so it's cheap. Also: requestWake() now records the
last failure in `_wakeErr` instead of swallowing it silently, for future diagnosis.
FIX (v42, 2026-10-18): found a race — the click handler's requestWake() and renderToday()'s
both fired while the first request was still awaiting, so ONE tap was granted TWO locks and
the first was orphaned (releaseWake() freed only one; the screen stayed held after Finish).
requestWake() now shares a single in-flight request and drops a late grant if the workout
ended. Failures are no longer only in `_wakeErr`: a refusal (e.g. battery saver) or a
system release while the app is on screen shows a 6 s toast "Screen may lock: <reason>"
once per workout, and a system release re-requests immediately.
STILL NEEDS: confirmation on the actual Pixel 9 Pro Fold that the lock screen no longer
returns after Ungroup mid-workout. If it still recurs, the "Screen may lock: …" toast now
shows the reason on screen (no console needed). Remove this entry once confirmed on device.
