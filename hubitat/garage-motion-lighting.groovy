/**
 *  Garage Motion Lighting
 *
 *  A small, predictable motion-lighting app for Hubitat.
 *
 *  Behaviour
 *  ---------
 *  - Any selected motion sensor going ACTIVE turns ALL selected lights on (if it is dark enough / late enough).
 *    If some lights are already on, motion brings the rest on without re-checking brightness.
 *  - While any sensor stays active the lights stay on. No timer runs during activity.
 *  - When ALL sensors are inactive, a countdown starts (default 10 min). New motion cancels it.
 *  - When the countdown expires the lights turn off.
 *  - "Dark enough" = EITHER the clock is inside the night window (sunset - 45 min -> sunrise)
 *    OR any illuminance sensor reads below the lux threshold. Either condition alone is enough.
 *    Lux only gates turning ON; once on, only the countdown turns them off (the lights themselves
 *    will make the lux sensor read bright, which must not switch them off).
 *  - Manual off at the wall: the app will not turn the lights straight back on until every sensor
 *    has gone inactive (so you can switch the light off while you are still in the garage).
 *  - Manual on at the wall (optional): the app leaves the lights alone until they are turned off.
 *
 *  Install
 *  -------
 *  Apps Code -> New App -> paste -> Save -> Apps -> Add User App -> Garage Motion Lighting.
 *  Install one instance per room/zone.
 */

definition(
    name: "Garage Motion Lighting",
    namespace: "garage-training",
    author: "garage-training",
    description: "Motion turns lights on when it is dark; lights stay on while motion continues and go off a set number of minutes after the last motion.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleThreaded: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Garage Motion Lighting", install: true, uninstall: true) {
        section("Name") {
            label title: "Name this instance", required: false
        }
        section("Devices") {
            input "motionSensors", "capability.motionSensor", title: "Motion sensors (any one triggers)", multiple: true, required: true
            input "lights", "capability.switch", title: "Lights to control (on/off)", multiple: true, required: true
        }
        section("Timing") {
            input "offDelayMinutes", "number", title: "Turn lights off this many minutes after ALL motion has stopped", defaultValue: 10, range: "1..180", required: true
        }
        section("Only turn on when it is dark") {
            paragraph "The lights are allowed to turn on if EITHER of the two checks below says it is dark. Leave a check unconfigured to ignore it. If both are unconfigured, motion always turns the lights on."
            input "useSunWindow", "bool", title: "Use the sunset/sunrise window", defaultValue: true, submitOnChange: true
            if (useSunWindow != false) {
                input "sunsetOffsetMinutes", "number", title: "Lights may start coming on this many minutes BEFORE sunset", defaultValue: 45, range: "0..240", required: true
                input "sunriseOffsetMinutes", "number", title: "...and keep being allowed until this many minutes AFTER sunrise", defaultValue: 0, range: "0..240", required: true
            }
            input "luxSensors", "capability.illuminanceMeasurement", title: "Illuminance (lux) sensors (optional; the Zooz Q sensors report lux)", multiple: true, required: false, submitOnChange: true
            if (luxSensors) {
                input "luxThreshold", "number", title: "It is dark when any selected sensor reads BELOW this many lux", defaultValue: 50, range: "1..100000", required: true
            }
        }
        section("Manual switch handling") {
            input "manualOffHolds", "bool", title: "If a light is switched off by hand, do not turn it back on until all motion has stopped", defaultValue: true
            input "manualOnHolds", "bool", title: "If a light is switched on by hand, leave it on (ignore the countdown) until it is switched off", defaultValue: false
        }
        section("Logging") {
            input "txtEnable", "bool", title: "Enable info logging", defaultValue: true
            input "logEnable", "bool", title: "Enable debug logging (turns itself off after 30 minutes)", defaultValue: false
        }
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    logInfo "Installed"
    initialize()
}

def updated() {
    logInfo "Updated"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.lastAppOn  = state.lastAppOn  ?: 0L
    state.lastAppOff = state.lastAppOff ?: 0L
    state.manualOffHold = false
    state.manualOnHold  = false

    subscribe(motionSensors, "motion.active",   "motionActiveHandler")
    subscribe(motionSensors, "motion.inactive", "motionInactiveHandler")
    subscribe(lights, "switch", "switchHandler")

    if (logEnable) runIn(1800, "logsOff")

    // Safety net after a hub restart or a settings change: if the lights are already on and
    // nobody is moving, start the countdown so they do not stay on forever.
    if (anyLightOn() && !anyMotionActive()) {
        logDebug "initialize: lights on with no motion -> starting countdown"
        startOffCountdown()
    }
    logInfo "Initialized: ${motionSensors?.size() ?: 0} motion sensor(s), ${lights?.size() ?: 0} light(s), off delay ${offDelayMinutes} min"
}

def logsOff() {
    log.warn "Debug logging disabled"
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ---------------------------------------------------------------------------
// Event handlers
// ---------------------------------------------------------------------------

def motionActiveHandler(evt) {
    logDebug "Motion ACTIVE on ${evt.displayName}"

    // Any motion cancels a pending off, whether or not we end up turning anything on.
    cancelOffCountdown()

    if (state.manualOffHold) {
        logDebug "Manual-off hold is set; not turning on until all motion has stopped"
        return
    }
    if (allLightsOn()) {
        logDebug "All lights already on; countdown cancelled"
        return
    }
    if (anyLightOn()) {
        // Some lights are on and some are not (e.g. one wall switch was flipped). Motion means
        // the room is in use, so bring the rest on without re-checking brightness.
        turnLightsOn("motion on ${evt.displayName}; bringing remaining lights on")
        return
    }
    if (!isDarkEnough()) {
        logInfo "Motion on ${evt.displayName} but it is bright / daytime; leaving lights off"
        return
    }
    turnLightsOn("motion on ${evt.displayName}")
}

def motionInactiveHandler(evt) {
    logDebug "Motion INACTIVE on ${evt.displayName}"
    if (anyMotionActive()) {
        logDebug "Another sensor is still active; countdown not started"
        return
    }
    // All sensors are now quiet.
    if (state.manualOffHold) {
        logDebug "All motion stopped; clearing manual-off hold"
        state.manualOffHold = false
    }
    if (!anyLightOn()) {
        logDebug "All motion stopped but lights are already off; nothing to do"
        return
    }
    if (state.manualOnHold) {
        logDebug "Manual-on hold is set; lights stay on"
        return
    }
    startOffCountdown()
}

def switchHandler(evt) {
    // Decide whether this switch change came from us or from a person at the wall.
    // We stamp state.lastAppOn / lastAppOff just before sending commands; a switch event
    // arriving within the grace window of that stamp is treated as ours.
    long graceMs = 20000L
    long nowMs = now()
    if (evt.value == "on") {
        boolean ours = (nowMs - (state.lastAppOn as Long)) < graceMs
        logDebug "${evt.displayName} turned ON (${ours ? 'by this app' : 'manually'})"
        if (!ours) {
            state.manualOffHold = false
            if (manualOnHolds) {
                state.manualOnHold = true
                cancelOffCountdown()
                logInfo "${evt.displayName} switched on manually; holding on until switched off"
            } else if (!anyMotionActive()) {
                // Someone flipped it on with nobody moving; still give it the normal countdown.
                startOffCountdown()
            }
        }
    } else if (evt.value == "off") {
        boolean ours = (nowMs - (state.lastAppOff as Long)) < graceMs
        logDebug "${evt.displayName} turned OFF (${ours ? 'by this app' : 'manually'})"
        state.manualOnHold = false
        if (!ours) {
            if (manualOffHolds && anyMotionActive()) {
                state.manualOffHold = true
                logInfo "${evt.displayName} switched off manually while motion is active; will not re-light until all motion has stopped"
            }
        }
        if (!anyLightOn()) cancelOffCountdown()
    }
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

def turnLightsOn(String reason) {
    state.lastAppOn = now()
    logInfo "Turning lights ON (${reason})"
    lights.each { it.on() }
}

def turnLightsOff() {
    if (anyMotionActive()) {
        // Should not normally happen (motion cancels the countdown), but be safe.
        logDebug "turnLightsOff called while motion is active; ignoring"
        return
    }
    if (state.manualOnHold) {
        logDebug "turnLightsOff called while manual-on hold is set; ignoring"
        return
    }
    state.lastAppOff = now()
    logInfo "Turning lights OFF (no motion for ${offDelayMinutes} min)"
    lights.each { it.off() }
}

def startOffCountdown() {
    int secs = ((offDelayMinutes ?: 10) as Integer) * 60
    logInfo "No motion; lights will turn off in ${offDelayMinutes} min"
    runIn(secs, "turnLightsOff", [overwrite: true])
}

def cancelOffCountdown() {
    unschedule("turnLightsOff")
}

// ---------------------------------------------------------------------------
// Conditions
// ---------------------------------------------------------------------------

boolean anyMotionActive() {
    return motionSensors?.any { it.currentValue("motion") == "active" } ?: false
}

boolean anyLightOn() {
    return lights?.any { it.currentValue("switch") == "on" } ?: false
}

boolean allLightsOn() {
    return lights ? lights.every { it.currentValue("switch") == "on" } : false
}

/** True if either the sun window or the lux sensors say it is dark. */
boolean isDarkEnough() {
    boolean sunConfigured = (useSunWindow != false)
    boolean luxConfigured = (luxSensors && luxSensors.size() > 0)

    if (!sunConfigured && !luxConfigured) return true

    boolean nightBySun = sunConfigured && isNightBySun()
    boolean darkByLux  = luxConfigured && isDarkByLux()

    logDebug "isDarkEnough: nightBySun=${nightBySun} darkByLux=${darkByLux}"
    return nightBySun || darkByLux
}

/** Night window = (sunset - sunsetOffset) today, through (sunrise + sunriseOffset) today. */
boolean isNightBySun() {
    def sun = getSunriseAndSunset()
    if (!sun?.sunrise || !sun?.sunset) {
        log.warn "Hub has no sunrise/sunset (check hub location settings); treating as night"
        return true
    }
    long beforeSunsetMs = ((sunsetOffsetMinutes  ?: 45) as Long) * 60000L
    long afterSunriseMs = ((sunriseOffsetMinutes ?: 0)  as Long) * 60000L
    long nowMs = now()
    long eveningStart = sun.sunset.time  - beforeSunsetMs
    long morningEnd   = sun.sunrise.time + afterSunriseMs
    // Both times are today's. It is night if we are past this evening's start,
    // or still before this morning's end (i.e. the tail of last night).
    boolean night = (nowMs >= eveningStart) || (nowMs <= morningEnd)
    logDebug "isNightBySun: now=${new Date(nowMs)} eveningStart=${new Date(eveningStart)} morningEnd=${new Date(morningEnd)} -> ${night}"
    return night
}

/** Dark if ANY lux sensor reads below the threshold (errs on the side of giving you light). */
boolean isDarkByLux() {
    def threshold = (luxThreshold ?: 50) as BigDecimal
    def readings = luxSensors.collect { [name: it.displayName, lux: it.currentValue("illuminance")] }
    def known = readings.findAll { it.lux != null }
    if (!known) {
        log.warn "No lux readings available from ${readings*.name}; treating as dark"
        return true
    }
    boolean dark = known.any { (it.lux as BigDecimal) < threshold }
    logDebug "isDarkByLux: ${known.collect { "${it.name}=${it.lux}" }.join(', ')} threshold=${threshold} -> ${dark}"
    return dark
}

// ---------------------------------------------------------------------------
// Logging helpers
// ---------------------------------------------------------------------------

private void logInfo(String msg)  { if (txtEnable != false) log.info  "${app.label ?: 'Garage Motion Lighting'}: ${msg}" }
private void logDebug(String msg) { if (logEnable)          log.debug "${app.label ?: 'Garage Motion Lighting'}: ${msg}" }
