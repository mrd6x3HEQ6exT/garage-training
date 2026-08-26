QUEUE — approved work not yet built. Newest first. Check alongside mistake.md before
any build session. Remove an item once it ships (note the build number).

---
QUEUED: 2026-08-24 — screen lock re-activates after tapping Ungroup
Reported: mid-workout, tapping Ungroup causes the phone's lock screen to come back on
(the wake-lock/keep-awake protection stops holding). Traced the code path: ungroup's
handler calls pCur() then renderToday(), and renderToday() DOES end with the correct
wake-lock check — `if(STATE.current&&STATE.current.startedAt){ startWoTimer();
requestWake(); } else { stopWoTimer(); releaseWake(); }` — so on paper, an active
workout (startedAt set) should re-request (or no-op if already held) rather than
release. Nothing in ungroup's own logic touches startedAt or calls releaseWake()
directly. Static reading doesn't show an obvious JS bug.
SUSPECTED CAUSE (unconfirmed without live device testing): the Screen Wake Lock API has
real, spec-level quirks — some browsers require a wake-lock (re)request to be tied
closely to a user gesture, and can silently refuse a request if too much async/render
work happens between the tap and the request (requestWake's catch block is empty,
so a silent failure here would be invisible). Also possible: the API auto-releases on
any visibility-state hiccup, and the visibilitychange listener that's supposed to
re-acquire it doesn't fire or loses the race.
FIX DIRECTION: needs live reproduction on the actual device (or at least logging what
requestWake()'s catch block is silently swallowing) before a real fix can be targeted —
static code reading found the intended logic looks correct, so this is likely a runtime/
browser-API timing issue, not a straightforward code bug to patch blind.
