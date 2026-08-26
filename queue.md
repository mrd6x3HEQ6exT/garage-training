QUEUE — approved work not yet built. Newest first. Check alongside mistake.md before
any build session. Remove an item once it ships (note the build number).

---
QUEUED: 2026-08-26 — consolidate the two kettlebell entries in Gear into one
User owns ONE adjustable kettlebell, but Gear/DEFAULT_EQUIPMENT/SEED_DATA model it as two
entries: kb1 "8–16 kg" loads [18,22,26,31,35] and kb2 "16–24 kg" loads [35,40,44,49,53].
Both show as separate rows and both are on:true. loadsFor() already unions loads across
owned entries (v36), so snapping already spans the full range — this item is about the
Gear UI and the data model matching reality: ONE kettlebell row.
DO: replace kb1+kb2 with a single entry, e.g. {id:"kb", name:"Adjustable kettlebell 8–24 kg",
caps:["kettlebell"], on:true, loads:[18,22,26,31,35,40,44,49,53]} (35 deduped), in both
DEFAULT_EQUIPMENT and the SEED_DATA equipment array.
WATCH (why this waited for a real build, not an overnight quick-fix): existing saved state
(localStorage + account storage) still holds kb1+kb2 — changing the defaults alone won't
migrate a user who already has data. Needs a one-time migration on load: if equipment has
kb1 and/or kb2, collapse them into the single kb entry, union their loads, preserve the
on/off state (on if EITHER was on), and drop the old ids. Verify: (1) a fresh install shows
one KB; (2) a user with the old two-entry state ends up with one KB and the full 18–53 load
range; (3) any saved workout referencing kettlebell loads still snaps correctly; (4) no
exercise availability changes (kettlebell cap still present once). Run the suite after.
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
STILL NEEDS: confirmation on the actual Pixel 9 Pro Fold that the lock screen no longer
returns after Ungroup mid-workout. If it still recurs, capture `_wakeErr` from the console
— that will show what the browser is refusing. Remove this entry once confirmed on device.
