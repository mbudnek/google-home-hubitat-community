// Copyright 2020 Miles Budnek
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

import groovy.json.JsonOutput
import groovy.transform.Field

definition(
    name: "Google Home Community",
    namespace: "mbudnek",
    author: "Miles Budnek",
    description: "Community-maintained Google Home integration",
    category: "Integrations",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/mbudnek/google-home-hubitat-community/master/google-home-community.groovy"
)

preferences {
    page(name: "mainPreferences")
    page(name: "deviceTypePreferences")
    page(name: "deviceTypeDelete")
    page(name: "deviceTraitPreferences")
    page(name: "deviceTraitDelete")
    page(name: "togglePreferences")
    page(name: "toggleDelete")
}

mappings {
    path("/action") {
        action: [
            POST: "handleAction"
        ]
    }
}

def appButtonHandler(buttonPressed) {
    def match
    if ((match = (buttonPressed =~ /^addPin:(.+)$/))) {
        def deviceTypeName = match.group(1)
        def deviceType = deviceTypeFromSettings(deviceTypeName)
        addDeviceTypePin(deviceType)
    } else if ((match = (buttonPressed =~ /^deletePin:(.+)\.pin\.(.+)$/))) {
        def deviceTypeName = match.group(1)
        def pinId = match.group(2)
        // If we actually delete the PIN here then it will get added back when the
        // device type settings page re-submits after the button handler finishes.
        // Instead just set a flag that we want to delete this PIN, and the settings
        // page will take care of actually deleting it.
        state.pinToDelete = pinId
    }
}

def mainPreferences() {
    if (settings.deviceTypeToEdit != null) {
        def toEdit = settings.deviceTypeToEdit
        app.removeSetting("deviceTypeToEdit")
        return deviceTypePreferences(deviceTypeFromSettings(toEdit))
    }
    if (settings.deviceTypeToDelete != null) {
        def toDelete = settings.deviceTypeToDelete
        app.removeSetting("deviceTypeToDelete")
        return deviceTypeDelete(deviceTypeFromSettings(toDelete))
    }

    state.remove("currentlyEditingDeviceType")
    return dynamicPage(name: "mainPreferences", title: "Device Selection", install: true, uninstall: true) {
        section {
            input(
                name: "modesToExpose",
                title: "Modes to expose",
                type: "mode",
                multiple: true
            )
        }
        def allDeviceTypes = deviceTypes()
        section {
            allDeviceTypes.each{ deviceType ->
                input(
                    // Note: This name _must_ be converted to a String.
                    //       If it isn't, then all devices will be removed when linking to Google Home
                    name: "${deviceType.name}.devices" as String,
                    type: "capability.${deviceType.type}",
                    title: "${deviceType.display} devices",
                    multiple: true
                )
            }
        }
        section {
            if (allDeviceTypes) {
                def deviceTypeOptions = allDeviceTypes.collectEntries { deviceType ->
                    [deviceType.name, deviceType.display]
                }
                input(
                    name: "deviceTypeToEdit",
                    title: "Edit Device Type",
                    description: "Select a device type to edit...",
                    width: 6,
                    type: "enum",
                    options: deviceTypeOptions,
                    submitOnChange: true
                )
                input(
                    name: "deviceTypeToDelete",
                    title: "Delete Device Type",
                    description: "Select a device type to delete...",
                    width: 6,
                    type: "enum",
                    options: deviceTypeOptions,
                    submitOnChange: true
                )
            }
            href(title: "Define new device type", description: "", style: "page", page: "deviceTypePreferences")
        }
        section {
            input(
                name: "debugLogging",
                title: "Enable Debug Logging",
                type: "bool",
                defaultValue: false
            )
        }
    }
}

def deviceTypePreferences(deviceType) {
    state.remove("currentlyEditingDeviceTrait")
    if (deviceType == null) {
        deviceType = deviceTypeFromSettings(state.currentlyEditingDeviceType)
    }

    if (settings.deviceTraitToAdd != null) {
        def toAdd = settings.deviceTraitToAdd
        app.removeSetting("deviceTraitToAdd")
        def traitName = "${deviceType.name}.traits.${toAdd}"
        addTraitToDeviceTypeState(deviceType.name, toAdd)
        return deviceTraitPreferences([name: traitName])
    }

    if (state.pinToDelete) {
        deleteDeviceTypePin(deviceType, state.pinToDelete)
        state.remove("pinToDelete")
    }

    return dynamicPage(name: "deviceTypePreferences", title: "Device Type Definition", nextPage: "mainPreferences") {
        def devicePropertyName = deviceType != null ? deviceType.name : "deviceTypes.${state.nextDeviceTypeIndex++}"
        state.currentlyEditingDeviceType = devicePropertyName
        section {
            input(
                name: "${devicePropertyName}.display",
                title: "Device type name",
                type: "text",
                required: true
            )
            input(
                name: "${devicePropertyName}.type",
                title: "Device type",
                type: "enum",
                options: HUBITAT_DEVICE_TYPES,
                required: true
            )
            input(
                name: "${devicePropertyName}.googleDeviceType",
                title: "Google Home device type",
                description: "The device type to report to Google Home",
                type: "enum",
                options: GOOGLE_DEVICE_TYPES,
                required: true
            )
        }

        def currentDeviceTraits = deviceType?.traits ?: [:]
        section("Device Traits") {
            if (deviceType != null) {
                deviceType.traits.each { traitType, deviceTrait ->
                    href(
                        title: GOOGLE_DEVICE_TRAITS[traitType],
                        description: "",
                        style: "page",
                        page: "deviceTraitPreferences",
                        params: deviceTrait
                    )
                }
            }

            def deviceTraitOptions = GOOGLE_DEVICE_TRAITS.findAll { key, value ->
                !(key in currentDeviceTraits.keySet())
            }
            input(
                name: "deviceTraitToAdd",
                title: "Add Trait",
                description: "Select a trait to add to this device...",
                type: "enum",
                options: deviceTraitOptions,
                submitOnChange: true
            )
        }

        def deviceTypeCommands = []
        currentDeviceTraits.each { traitType, deviceTrait ->
            deviceTypeCommands += deviceTrait.commands
        }
        if (deviceTypeCommands) {
            section {
                input(
                    name: "${devicePropertyName}.confirmCommands",
                    title: "The Google Assistant will ask for confirmation before performing these actions",
                    type: "enum",
                    options: deviceTypeCommands,
                    multiple: true
                )
                input(
                    name: "${devicePropertyName}.secureCommands",
                    title: "The Google Assistant will ask for a PIN code before performing these actions",
                    type: "enum",
                    options: deviceTypeCommands,
                    multiple: true,
                    submitOnChange: true
                )
            }
            if (deviceType?.secureCommands || deviceType?.pinCodes) {
                section("PIN Codes") {
                    deviceType.pinCodes.each { pinCode ->
                        input(
                            name: "${devicePropertyName}.pin.${pinCode.id}.name",
                            title: "PIN Code Name",
                            type: "text",
                            required: true,
                            width: 6
                        )
                        input(
                            name: "${devicePropertyName}.pin.${pinCode.id}.value",
                            title: "PIN Code Value",
                            type: "password",
                            required: true,
                            width: 5
                        )
                        input(
                            name: "deletePin:${devicePropertyName}.pin.${pinCode.id}",
                            title: "X",
                            type: "button",
                            width: 1
                        )
                    }
                    if (deviceType?.secureCommands) {
                        input(
                            name: "addPin:${devicePropertyName}",
                            title: "Add PIN Code",
                            type: "button"
                        )
                    }
                }
            }
        }
    }
}

def deviceTypeDelete(deviceType) {
    return dynamicPage(name: "deviceTypeDelete", title: "Device Type Deleted", nextPage: "mainPreferences") {
        LOGGER.debug("Deleting device type ${deviceType.display}")
        app.removeSetting("${deviceType.name}.display")
        app.removeSetting("${deviceType.name}.type")
        app.removeSetting("${deviceType.name}.googleDeviceType")
        app.removeSetting("${deviceType.name}.devices")
        app.removeSetting("${deviceType.name}.confirmCommands")
        app.removeSetting("${deviceType.name}.secureCommands")
        def pinCodeIds = deviceType.pinCodes.collect { it.id }
        pinCodeIds.each { pinCodeId -> deleteDeviceTypePin(deviceType, pinCodeId) }
        app.removeSetting("${deviceType.name}.pinCodes")
        deviceType.traits.each { traitType, deviceTrait -> deleteDeviceTrait(deviceTrait) }
        state.deviceTraits.remove(deviceType.name as String)
        section {
            paragraph("The ${deviceType.display} device type was deleted")
        }
    }
}

def deviceTraitPreferences(deviceTrait) {
    if (deviceTrait == null) {
        deviceTrait = deviceTypeTraitFromSettings(state.currentlyEditingDeviceTrait)
    } else {
        // re-load in case individual trait preferences functions have traits with submitOnChange: true
        deviceTrait = deviceTypeTraitFromSettings(deviceTrait.name)
    }
    state.currentlyEditingDeviceTrait = deviceTrait.name
    return dynamicPage(
        name: "deviceTraitPreferences",
        title: "Preferences For ${GOOGLE_DEVICE_TRAITS[deviceTrait.type]} Trait",
        nextPage: "deviceTypePreferences"
    ) {
        section {
            "deviceTraitPreferences_${deviceTrait.type}"(deviceTrait)
        }

        section {
            href(
                title: "Remove Device Trait",
                description: "",
                style: "page",
                page: "deviceTraitDelete",
                params: deviceTrait
            )
        }
    }
}

def deviceTraitDelete(deviceTrait) {
    return dynamicPage(name: "deviceTraitDelete", title: "Device Trait Deleted", nextPage: "deviceTypePreferences") {
        deleteDeviceTrait(deviceTrait)
        section {
            paragraph("The ${GOOGLE_DEVICE_TRAITS[deviceTrait.type]} trait was removed")
        }
    }
}

private deviceTraitPreferences_Brightness(deviceTrait) {
    section ("Brightness Settings") {
        input(
            name: "${deviceTrait.name}.brightnessAttribute",
            title: "Current Brightness Attribute",
            type: "text",
            defaultValue: "level",
            required: true
        )
        input(
            name: "${deviceTrait.name}.setBrightnessCommand",
            title: "Set Brightness Command",
            type: "text",
            defaultValue: "setLevel",
            required: true
        )
    }
}

private deviceTraitPreferences_FanSpeed(deviceTrait) {
    hubitatFanSpeeds = [
        "low":         "Low",
        "medium-low":  "Medium-Low",
        "medium":      "Medium",
        "medium-high": "Medium-High",
        "high":        "High",
        "auto":        "Auto",
    ]
    section("Fan Speed Settings") {
        input(
            name: "${deviceTrait.name}.currentSpeedAttribute",
            title: "Current Speed Attribute",
            type: "text",
            defaultValue: "speed",
            required: true
        )
        input(
            name: "${deviceTrait.name}.setFanSpeedCommand",
            title: "Set Speed Command",
            type: "text",
            defaultValue: "setSpeed",
            required: true
        )
        input(
            name: "${deviceTrait.name}.fanSpeeds",
            title: "Supported Fan Speeds",
            type: "enum",
            options: hubitatFanSpeeds,
            multiple: true,
            required: true,
            submitOnChange: true
        )
        deviceTrait.fanSpeeds.each { fanSpeed ->
            input(
                name: "${deviceTrait.name}.speed.${fanSpeed.key}.googleNames",
                title: "Google Home Level Names for ${hubitatFanSpeeds[fanSpeed.key]}",
                description: "A comma-separated list of names that the Google Assistant will accept for this speed setting",
                type: "text",
                required: "true",
                defaultValue: hubitatFanSpeeds[fanSpeed.key]
            )
        }
    }

    section("Reverse Settings") {
        input(
            name: "${deviceTrait.name}.reversible",
            title: "Reversible",
            type: "bool",
            defaultValue: false,
            submitOnChange: true
        )

        if (settings."${deviceTrait.name}.reversible") {
            input(
                name: "${deviceTrait.name}.reverseCommand",
                title: "Reverse Command",
                type: "text",
                required: true
            )
        }
    }
}

private deviceTraitPreferences_LockUnlock(deviceTrait) {
    section("Lock/Unlock Settings") {
        input(
            name: "${deviceTrait.name}.lockedUnlockedAttribute",
            title: "Locked/Unlocked Attribute",
            type: "text",
            defaultValue: "lock",
            required: true
        )
        input(
            name: "${deviceTrait.name}.lockedValue",
            title: "Locked Value",
            type: "text",
            defaultValue: "locked",
            required: true
        )
        input(
            name: "${deviceTrait.name}.lockCommand",
            title: "Lock Command",
            type: "text",
            defaultValue: "lock",
            required: true
        )
        input(
            name: "${deviceTrait.name}.unlockCommand",
            title: "Unlock Command",
            type: "text",
            defaultValue: "unlock",
            required: true
        )
    }
}

private deviceTraitPreferences_OnOff(deviceTrait) {
    section("On/Off Settings") {
        input(
            name: "${deviceTrait.name}.onOffAttribute",
            title: "On/Off Attribute",
            type: "text",
            defaultValue: "switch",
            required: true
        )
        paragraph("At least one of On Value or Off Value must be specified")
        input(
            name: "${deviceTrait.name}.onValue",
            title: "On Value",
            type: "text",
            defaultValue: deviceTrait.offValue ? "" : "on",
            submitOnChange: true,
            required: !deviceTrait.offValue
        )
        input(
            name: "${deviceTrait.name}.offValue",
            title: "Off Value",
            type: "text",
            defaultValue: deviceTrait.onValue ? "" : "off",
            submitOnChange: true,
            required: !deviceTrait.onValue
        )
        input(
            name: "${deviceTrait.name}.controlType",
            title: "Control Type",
            type: "enum",
            options: [
                "separate": "Separate On and Off commands",
                "single": "Single On/Off command with parameter"
            ],
            defaultValue: "separate",
            required: true,
            submitOnChange: true
        )
        if (deviceTrait.controlType != "single") {
            input(
                name: "${deviceTrait.name}.onCommand",
                title: "On Command",
                type: "text",
                defaultValue: "on",
                required: true
            )
            input(
                name: "${deviceTrait.name}.offCommand",
                title: "Off Command",
                type: "text",
                defaultValue: "off",
                required: true
            )
        } else {
            input(
                name: "${deviceTrait.name}.onOffCommand",
                title: "On/Off Command",
                type: "text",
                required: true
            )
            input(
                name: "${deviceTrait.name}.onParameter",
                title: "On Parameter",
                type: "text",
                required: true
            )
            input(
                name: "${deviceTrait.name}.offParameter",
                title: "Off Parameter",
                type: "text",
                required: true
            )
        }
    }
}

private deviceTraitPreferences_OpenClose(deviceTrait) {
    section("Open/Close Settings") {
        paragraph("Can this device only be fully opened/closed, or can it be partially open?")
        input(
            name: "${deviceTrait.name}.discreteOnlyOpenClose",
            title: "Discrete Only Open/Close",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true
        )
        input(
            name: "${deviceTrait.name}.openCloseAttribute",
            title: "Open/Close Attribute",
            type: "text",
            required: true
        )

        if (deviceTrait.discreteOnlyOpenClose) {
            input(
                name: "${deviceTrait.name}.openValue",
                title: "Open Values",
                description: "Values of the Open/Close Attribute that indicate this device is open.  Separate multiple values with a comma",
                type: "text",
                defaultValue: "open",
                required: true
            )
            input(
                name: "${deviceTrait.name}.closedValue",
                title: "Closed Values",
                description: "Values of the Open/Close Attribute that indicate this device is closed.  Separate multiple values with a comma",
                type: "text",
                defaultValue: "closed",
                required: true
            )
        }
        input(
            name: "${deviceTrait.name}.queryOnly",
            title: "Query Only Open/Close",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true
        )
        if (!deviceTrait.queryOnly) {
            if (deviceTrait.discreteOnlyOpenClose) {
                input(
                    name: "${deviceTrait.name}.openCommand",
                    title: "Open Command",
                    type: "text",
                    defaultValue: "open",
                    required: true
                )
                input(
                    name: "${deviceTrait.name}.closeCommand",
                    title: "Close Command",
                    type: "text",
                    defaultValue: "close",
                    required: true
                )
            } else {
                input(
                    name: "${deviceTrait.name}.openPositionCommand",
                    title: "Open Position Command",
                    type: "text",
                    defaultValue: "setPosition",
                    required: true
                )
            }
        }
    }
}

def deviceTraitPreferences_Scene(deviceTrait) {
    section("Scene Preferences") {
        input(
            name: "${deviceTrait.name}.activateCommand",
            title: "Activate Command",
            type: "text",
            defaultValue: "on",
            required: true
        )
        input(
            name: "${deviceTrait.name}.sceneReversible",
            title: "Can this scene be deactivated?",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true
        )
        if (settings."${deviceTrait.name}.sceneReversible") {
            input(
                name: "${deviceTrait.name}.deactivateCommand",
                title: "Deactivate Command",
                type: "text",
                defaultValue: "off",
                required: true
            )
        }
    }
}

def deviceTraitPreferences_TemperatureSetting(deviceTrait) {
    def googleModes = [
        "off":      "Off",
        "on":       "On",
        "heat":     "Heat",
        "cool":     "Cool",
        "heatcool": "Heat/Cool",
        "auto":     "Auto",
        "fan-only": "Fan Only",
        "purifier": "Purifier",
        "eco":      "Energy Saving",
        "dry":      "Dry"
    ]
    section ("Temperature Setting Preferences") {
        input(
            name: "${deviceTrait.name}.temperatureUnit",
            title: "Temperature Unit",
            type: "enum",
            options: [
                "C": "Celsius",
                "F": "Fahrenheit"
            ],
            required: true,
            defaultValue: getTemperatureScale()
        )
        input(
            name: "${deviceTrait.name}.modes",
            title: "Supported Modes",
            type: "enum",
            options: googleModes,
            multiple: true,
            required: true,
            submitOnChange: true
        )
        input(
            name: "${deviceTrait.name}.currentTemperatureAttribute",
            title: "Current Temperature Attribute",
            type: "text",
            required: true,
            defaultValue: "temperature"
        )
        def supportedModes = settings."${deviceTrait.name}.modes"
        if (supportedModes) {
            def notHeatCoolModes = supportedModes.findAll { mode -> mode != "heatcool" }
            if (notHeatCoolModes) {
                input(
                    name: "${deviceTrait.name}.setpointAttribute",
                    title: "Set Point Attribute",
                    description: "The attribute used to report the current setpoint for modes other than heat/cool mode",
                    type: "text",
                    required: true,
                    defaultValue: "thermostatSetpoint"
                )
                input(
                    name: "${deviceTrait.name}.setSetpointCommand",
                    title: "Set Setpoint Command",
                    type: "text",
                    required: true,
                    defaultValue: "setCoolingSetpoint"
                )
            }
            if ("heatcool" in supportedModes) {
                input(
                    name: "${deviceTrait.name}.heatingSetpointAttribute",
                    title: "Heating Setpoint Attribute",
                    description: "The attribute used to report the current heating setpoint for heat/cool mode",
                    type: "text",
                    required: true,
                    defaultValue: "heatingSetpoint"
                )
                input(
                    name: "${deviceTrait.name}.setHeatingSetpointCommand",
                    title: "Set Heating Setpoint Command",
                    type: "text",
                    required: true,
                    defaultValue: "setHeatingSetpoint"
                )
                input(
                    name: "${deviceTrait.name}.coolingSetpointAttribute",
                    title: "Cooling Setpoint Attribute",
                    description: "The attribute used to report the current cooling setpoint for heat/cool mode",
                    type: "text",
                    required: true,
                    defaultValue: "coolingSetpoint"
                )
                input(
                    name: "${deviceTrait.name}.setCoolingSetpointCommand",
                    title: "Set Cooling Setpoint Command",
                    type: "text",
                    required: true,
                    defaultValue: "setCoolingSetpoint"
                )
                input(
                    name: "${deviceTrait.name}.heatcoolBuffer",
                    title: "Temperature Buffer",
                    description: "The minimum offset between the heat and cool setpoints when operating in heat/cool mode",
                    type: "real"
                )
            }
            def defaultModeMapping = [
                "off":      "off",
                "on":       "",
                "heat":     "heat",
                "cool":     "cool",
                "heatcool": "auto",
                "auto":     "",
                "fan-only": "",
                "purifier": "",
                "eco":      "",
                "dry":      ""
            ]
            supportedModes.each { mode ->
                input(
                    name: "${deviceTrait.name}.mode.${mode}.hubitatMode",
                    title: "${googleModes[mode]} Hubitat Mode",
                    description: "The mode name used by hubitat for the ${googleModes[mode]} mode",
                    type: "text",
                    required: true,
                    defaultValue: defaultModeMapping[mode]
                )
            }
        }
        input(
            name: "${deviceTrait.name}.setModeCommand",
            title: "Set Mode Command",
            type: "text",
            required: true,
            defaultValue: "setThermostatMode"
        )
        input(
            name: "${deviceTrait.name}.currentModeAttribute",
            title: "Current Mode Attribute",
            type: "text",
            required: true,
            defaultValue: "thermostatMode"
        )
        paragraph("If either the minimum setpoint or maximum setpoint is given then both must be given")
        input(
            name: "${deviceTrait.name}.range.min",
            title: "Minimum Set Point",
            type: "real",
            required: settings."${deviceTrait.name}.range.max" != null,
            submitOnChange: true
        )
        input(
            name: "${deviceTrait.name}.range.max",
            title: "Maximum Set Point",
            type: "real",
            required: settings."${deviceTrait.name}.range.min" != null,
            submitOnChange: true
        )
    }
}

private deviceTraitPreferences_Toggles(deviceTrait) {
    section("Toggles") {
        deviceTrait.toggles.each { toggle ->
            href(
                title: "${toggle.labels.join(",")}",
                description: "Click to edit",
                style: "page",
                page: "togglePreferences",
                params: toggle
            )
        }
        href(
            title: "New Toggle",
            description: "",
            style: "page",
            page: "togglePreferences",
            params: [
                traitName: deviceTrait.name,
                name: "${deviceTrait.name}.toggles.${UUID.randomUUID().toString()}"
            ]
        )
    }
}

def togglePreferences(toggle) {
    def toggles = settings."${toggle.traitName}.toggles" ?: []
    if (!(toggle.name in toggles)) {
        toggles << toggle.name
        app.updateSetting("${toggle.traitName}.toggles", toggles)
    }
    return dynamicPage(name: "togglePreferences", title: "Toggle Preferences", nextPage: "deviceTraitPreferences") {
        section {
            input(
                name: "${toggle.name}.labels",
                title: "Toggle Names",
                description: "A comma-separated list of names for this toggle",
                type: "text",
                required: true
            )
        }

        deviceTraitPreferences_OnOff(toggle)

        section {
            href(
                title: "Remove Toggle",
                description: "",
                style: "page",
                page: "toggleDelete",
                params: toggle
            )
        }
    }
}

def toggleDelete(toggle) {
    return dynamicPage(name: "toggleDelete", title: "Toggle Deleted", nextPage: "deviceTraitPreferences") {
        deleteToggle(toggle)
        section {
            paragraph("The ${toggle.labels ? toggle.labels.join(",") : "new"} toggle has been removed")
        }
    }
}

def handleAction() {
    LOGGER.debug(request.JSON)
    def requestType = request.JSON.inputs[0].intent
    if (requestType == "action.devices.SYNC") {
        return handleSyncRequest(request)
    } else if (requestType == "action.devices.QUERY") {
        return handleQueryRequest(request)
    } else if (requestType == "action.devices.EXECUTE") {
        return handleExecuteRequest(request)
    } else if (requestType == "action.devices.DISCONNECT") {
        return [:]
    }
}

private handleExecuteRequest(request) {
    def resp = [
        requestId: request.JSON.requestId,
        payload: [
            commands: []
        ]
    ]

    def knownDevices = allKnownDevices()
    def commands = request.JSON.inputs[0].payload.commands
    commands.each { command ->
        def devices = command.devices.collect { device -> knownDevices."${device.id}" }
        def attrsToAwait = [:].withDefault{ [:] }
        def results = [:]
        // Send appropriate commands to devices
        devices.each { device ->
            command.execution.each { execution ->
                def commandName = execution.command.split("\\.").last()
                try {
                    attrsToAwait[device.device] += "executeCommand_${commandName}"(device, execution)
                    results[device.device] = [
                        status: "SUCCESS"
                    ]
                } catch (Exception ex) {
                    LOGGER.debug("Error executing command ${commandName} on device ${device.device.getName()}: ${ex.message}")
                    results[device.device] = [
                        status: "ERROR"
                    ]
                    results[device.device] << parseJson(ex.message)
                }
            }
        }
        // Wait up to 5 seconds for devices to report their new state
        for (def i = 0; i < 50; ++i) {
            def ready = true
            attrsToAwait.each { device, attributes ->
                attributes.each { attrName, attrValue ->
                    def currentValue = device.currentValue(attrName, true)
                    if (attrValue instanceof Closure) {
                        if (!attrValue(currentValue)) {
                            LOGGER.debug("${device.getName()}: Expected value test returned false for attribute ${attrName} with value ${currentValue}")
                            ready = false
                        }
                    } else if (currentValue != attrValue) {
                        LOGGER.debug("${device.getName()}: current value of ${attrName} (${currentValue}) does does not yet match expected value (${attrValue})")
                        ready = false
                    }
                }
            }
            if (ready) {
                break
            } else {
                pauseExecution(100)
            }
        }
        // Now build our response message
        devices.each { device ->
            def result = results[device.device]
            result.ids = [device.device.getId()]
            if (result.status == "SUCCESS") {
                def deviceState = [
                    online: true
                ]
                device.deviceType.traits.each { traitType, deviceTrait ->
                    deviceState += "deviceStateForTrait_${traitType}"(deviceTrait, device.device)
                }
                result.states = deviceState
            }
            resp.payload.commands << result
        }

    }
    LOGGER.debug(resp)
    return resp
}

private checkMfa(deviceType, commandType, command) {
    commandType = commandType as String
    LOGGER.debug("Checking MFA for ${commandType} command")
    if (commandType in deviceType.confirmCommands && !command.challenge?.ack) {
        throw new Exception(JsonOutput.toJson([
            errorCode: "challengeNeeded",
            challengeNeeded: [
                type: "ackNeeded"
            ]
        ]))
    }
    if (commandType in deviceType.secureCommands) {
        if (!command.challenge?.pin) {
            throw new Exception(JsonOutput.toJson([
                errorCode: "challengeNeeded",
                challengeNeeded: [
                    type: "pinNeeded"
                ]
            ]))
        } else if (!(command.challenge.pin in deviceType.pinCodes.collect{ it.value })) {
            throw new Exception(JsonOutput.toJson([
                errorCode: "challengeNeeded",
                challengeNeeded: [
                    type: "challengeFailedPinNeeded"
                ]
            ]))
        }
    }
}

private executeCommand_ActivateScene(deviceInfo, command) {
    def sceneTrait = deviceInfo.deviceType.traits.Scene
    if (sceneTrait.name == "hubitat_mode") {
        location.setMode(deviceInfo.device.getName())
    } else {
        if (command.params.deactivate) {
            checkMfa(deviceInfo.deviceType, "Deactivate Scene", command)
            deviceInfo.device."${sceneTrait.deactivateCommand}"()
        } else {
            checkMfa(deviceInfo.deviceType, "Activate Scene", command)
            deviceInfo.device."${sceneTrait.activateCommand}"()
        }
    }
    return [:]
}

private executeCommand_BrightnessAbsolute(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Brightness", command)
    def brightnessTrait = deviceInfo.deviceType.traits.Brightness
    // Google uses 0...100 for brightness but hubitat uses 0...99, so clamp
    def brightnessToSet = Math.min(command.params.brightness, 99)

    deviceInfo.device."${brightnessTrait.setBrightnessCommand}"(brightnessToSet)
    return [
        (brightnessTrait.brightnessAttribute): brightnessToSet
    ]
}

private executeCommand_Reverse(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Reverse", command)
    def fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    deviceInfo.device."${fanSpeedTrait.reverseCommand}"()
    return [:]
}

private executeCommand_LockUnlock(deviceInfo, command) {
    def lockUnlockTrait = deviceInfo.deviceType.traits.LockUnlock
    def checkValue
    if (command.params.lock) {
        checkMfa(deviceInfo.deviceType, "Lock", command)
        checkValue = lockUnlockTrait.lockedValue
        deviceInfo.device."${lockUnlockTrait.lockCommand}"()
    } else {
        checkMfa(deviceInfo.deviceType, "Unlock", command)
        checkValue = { it != lockUnlockTrait.lockedValue }
        deviceInfo.device."${lockUnlockTrait.unlockCommand}"()
    }

    return [
        (lockUnlockTrait.lockedUnlockedAttribute): checkValue
    ]
}

private executeCommand_OnOff(deviceInfo, command) {
    def onOffTrait = deviceInfo.deviceType.traits.OnOff

    def on
    def off
    if (onOffTrait.controlType == "single") {
        on = { device -> device."${onOffTrait.onOffCommand}"(onOffTrait.onParam) }
        off = { device -> device."${onOffTrait.onOffCommand}"(onOffTrait.offParam) }
    } else {
        on = { device -> device."${onOffTrait.onCommand}"() }
        off = { device -> device."${onOffTrait.offCommand}"() }
    }

    def checkValue
    if (command.params.on) {
        checkMfa(deviceInfo.deviceType, "On", command)
        on(deviceInfo.device)
        if (onOffTrait.onValue) {
            checkValue = onOffTrait.onValue
        } else {
            checkValue = { it != onOffTrait.offValue }
        }
    } else {
        checkMfa(deviceInfo.deviceType, "Off", command)
        off(deviceInfo.device)
        if (onOffTrait.onValue) {
            checkValue = onOffTrait.offValue
        } else {
            checkValue = { it != onOffTrait.onValue }
        }
    }
    return [
        (onOffTrait.onOffAttribute): checkValue
    ]
}

private executeCommand_OpenClose(deviceInfo, command) {
    def openCloseTrait = deviceInfo.deviceType.traits.OpenClose
    def openPercent = command.params.openPercent as int
    def checkValue
    if (openCloseTrait.discreteOnlyOpenClose && openPercent == 100) {
        checkMfa(deviceInfo.deviceType, "Open", command)
        deviceInfo.device."${openCloseTrait.openCommand}"()
        checkValue = { it in openCloseTrait.openValue.split(",") }
    } else if (openCloseTrait.discreteOnlyOpenClose && openPercent == 0) {
        checkMfa(deviceInfo.deviceType, "Close", command)
        deviceInfo.device."${openCloseTrait.closeCommand}"()
        checkValue = { it in openCloseTrait.closedValue.split(",") }
    } else {
        checkMfa(deviceInfo.deviceType, "Set Position", command)
        // Google uses 0...100 for position but hubitat uses 0...99, so clamp
        openPercent = Math.min(openPercent, 99)
        deviceInfo.device."${openCloseTrait.openPositionCommand}"(openPercent)
        checkValue = openPercent
    }
    return [
        (openCloseTrait.openCloseAttribute): checkValue
    ]
}

private executeCommand_SetFanSpeed(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Fan Speed", command)
    def fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    def fanSpeed = command.params.fanSpeed

    deviceInfo.device."${fanSpeedTrait.setFanSpeedCommand}"(fanSpeed)
    return [
        (fanSpeedTrait.currentSpeedAttribute): fanSpeed
    ]
}

private executeCommand_SetToggles(deviceInfo, command) {
    def togglesTrait = deviceInfo.deviceType.traits.Toggles
    def togglesToSet = command.params.updateToggleSettings

    def statesToCheck = [:]
    togglesToSet.each { toggleName, toggleValue ->
        def toggle = togglesTrait.toggles.find { it.name == toggleName }
        def fakeDeviceInfo = [
            deviceType: [
                traits: [
                    // We're delegating to the OnOff handler, so we need
                    // to fake the OnOff trait.
                    OnOff: toggle
                ]
            ],
            device: deviceInfo.device
        ]
        checkMfa(deviceInfo.deviceType, "${toggle.labels[0]} ${toggleValue ? "On" : "Off"}", command)
        statesToCheck << executeCommand_OnOff(fakeDeviceInfo, [params: [on: toggleValue]])
    }
    return statesToCheck
}

private executeCommand_ThermostatTemperatureSetpoint(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Setpoint", command)
    def temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def setpoint = command.params.thermostatTemperatureSetpoint
    if (temperatureSettingTrait.temperatureUnit == "F") {
        setpoint = celsiusToFahrenheitRounded(setpoint)
    }
    deviceInfo.device."${temperatureSettingTrait.setSetpointCommand}"(setpoint)

    return [
        (temperatureSettingTrait.setpointAttribute): setpoint
    ]
}

private executeCommand_ThermostatTemperatureSetRange(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Setpoint", command)
    def temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def coolSetpoint = command.params.thermostatTemperatureSetpointHigh
    def heatSetpoint = command.params.thermostatTemperatureSetpointLow
    if (temperatureSettingTrait.temperatureUnit == "F") {
        coolSetpoint = celsiusToFahrenheitRounded(coolSetpoint)
        heatSetpoint = celsiusToFahrenheitRounded(heatSetpoint)
    }
    deviceInfo.device."${temperatureSettingTrait.setCoolingSetpointCommand}"(coolSetpoint)
    deviceInfo.device."${temperatureSettingTrait.setHeatingSetpointCommand}"(heatSetpoint)

    return [
        (temperatureSettingTrait.coolingSetpointAttribute): coolSetpoint,
        (temperatureSettingTrait.heatingSetpointAttribute): heatSetpoint
    ]
}

private executeCommand_ThermostatSetMode(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Mode", command)
    def temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def googleMode = command.params.thermostatMode
    def hubitatMode = temperatureSettingTrait.googleToHubitatModeMap[googleMode]
    deviceInfo.device."${temperatureSettingTrait.setModeCommand}"(hubitatMode)

    return [
        (temperatureSettingTrait.currentModeAttribute): hubitatMode
    ]
}

private handleQueryRequest(request) {
    def resp = [
        requestId: request.JSON.requestId,
        payload: [
            devices: [:]
        ]
    ]
    def requestedDevices = request.JSON.inputs[0].payload.devices
    def knownDevices = allKnownDevices()

    requestedDevices.each { requestedDevice ->
        def deviceInfo = knownDevices."${requestedDevice.id}"
        def deviceState = [:]
        deviceInfo.deviceType.traits.each { traitType, deviceTrait ->
            deviceState += "deviceStateForTrait_${traitType}"(deviceTrait, deviceInfo.device)
        }
        resp.payload.devices."${requestedDevice.id}" = deviceState
    }
    LOGGER.debug(resp)
    return resp
}

private deviceStateForTrait_Brightness(deviceTrait, device) {
    return [
        brightness: device.currentValue(deviceTrait.brightnessAttribute)
    ]
}

private deviceStateForTrait_FanSpeed(deviceTrait, device) {
    def currentSpeed = device.currentValue(deviceTrait.currentSpeedAttribute)

    return [
        currentFanSpeedSetting: currentSpeed
    ]
}

private deviceStateForTrait_LockUnlock(deviceTrait, device) {
    def isLocked = device.currentValue(deviceTrait.lockedUnlockedAttribute) == deviceTrait.lockedValue`
    return [
        isLocked: isLocked,
        isJammed: false
    ]
}

private deviceStateForTrait_OnOff(deviceTrait, device) {
    def isOn
    if (deviceTrait.onValue) {
        isOn = device.currentValue(deviceTrait.onOffAttribute) == deviceTrait.onValue
    } else {
        isOn = device.currentValue(deviceTrait.onOffAttribute) != deviceTrait.offValue
    }
    return [
        on: isOn
    ]
}

private deviceStateForTrait_OpenClose(deviceTrait, device) {
    def openPercent
    if (deviceTrait.discreteOnlyOpenClose) {
        def openValues = deviceTrait.openValue.split(",")
        if (device.currentValue(deviceTrait.openCloseAttribute) in openValues) {
            openPercent = 100
        } else {
            openPercent = 0
        }
    } else {
        openPercent = device.currentValue(deviceTrait.openCloseAttribute)
    }
    return [
        openPercent: openPercent
    ]
}

private deviceStateForTrait_Scene(deviceTrait, device) {
    return [:]
}

private deviceStateForTrait_TemperatureSetting(deviceTrait, device) {
    def state = [:]

    def currentTemperature = device.currentValue(deviceTrait.currentTemperatureAttribute)
    if (deviceTrait.temperatureUnit == "F") {
        currentTemperature = fahrenheitToCelsiusRounded(currentTemperature)
    }
    state.thermostatTemperatureAmbient = currentTemperature

    def hubitatMode = device.currentValue(deviceTrait.currentModeAttribute)
    def googleMode = deviceTrait.hubitatToGoogleModeMap[hubitatMode]
    state.thermostatMode = googleMode

    if (googleMode == "heatcool") {
        def heatSetpoint = device.currentValue(deviceTrait.heatingSetpointAttribute)
        def coolSetpoint = device.currentValue(deviceTrait.coolingSetpointAttribute)
        if (deviceTrait.temperatureUnit == "F") {
            heatSetpoint = fahrenheitToCelsiusRounded(heatSetpoint)
            coolSetpoint = fahrenheitToCelsiusRounded(coolSetpoint)
        }
        state.thermostatTemperatureSetpointHigh = coolSetpoint
        state.thermostatTemperatureSetpointLow = heatSetpoint
    } else {
        def setpoint = device.currentValue(deviceTrait.setpointAttribute)
        if (deviceTrait.temperatureUnit == "F") {
            setpoint = fahrenheitToCelsiusRounded(setpoint)
        }
        state.thermostatTemperatureSetpoint = setpoint
    }
    return state
}

private deviceStateForTrait_Toggles(deviceTrait, device) {
    return [
        currentToggleSettings: deviceTrait.toggles.collectEntries { toggle ->
            [toggle.name, deviceStateForTrait_OnOff(toggle, device).on]
        }
    ]
}

private handleSyncRequest(request) {
    def resp = [
        requestId: request.JSON.requestId,
        payload: [
            devices: []
        ]
    ]
    (deviceTypes() + [modeSceneDeviceType()]).each { deviceType ->
        def traits = deviceType.traits.collect { traitType, deviceTrait ->
            "action.devices.traits.${traitType}"
        }
        def attributes = [:]
        deviceType.traits.each { traitType, deviceTrait ->
            attributes += "attributesForTrait_${traitType}"(deviceTrait)
        }
        deviceType.devices.each { device ->
            resp.payload.devices << [
                id: device.getId(),
                type: "action.devices.types.${deviceType.googleDeviceType}",
                traits: traits,
                name: [
                    defaultNames: [device.getName()],
                    name: device.getLabel() ?: device.getName()
                ],
                willReportState: false,
                attributes: attributes,
            ]
        }
    }
    LOGGER.debug(resp)
    return resp
}

private attributesForTrait_Brightness(deviceTrait) {
    return [:]
}

private attributesForTrait_FanSpeed(deviceTrait) {
    def fanSpeedAttrs = [
        availableFanSpeeds: [
            speeds: deviceTrait.fanSpeeds.collect { hubitatLevelName, googleLevelNames ->
                def speeds = googleLevelNames.split(",")
                [
                    speed_name: hubitatLevelName,
                    speed_values: [
                        [
                            speed_synonym: speeds,
                            lang: "en"
                        ]
                    ]
                ]
            },
            ordered: true
        ],
        reversible: deviceTrait.reversible
    ]
    return fanSpeedAttrs
}

private attributesForTrait_LockUnlock(deviceTrait) {
    return [:]
}

private attributesForTrait_OnOff(deviceTrait) {
    return [:]
}

private attributesForTrait_OpenClose(deviceTrait) {
    return [
        discreteOnlyOpenClose: deviceTrait.discreteOnlyOpenClose,
        queryOnlyOpenClose: deviceTrait.queryOnly
    ]
}

private attributesForTrait_Scene(deviceTrait) {
    return [
        sceneReversible: deviceTrait.sceneReversible
    ]
}

private attributesForTrait_TemperatureSetting(deviceTrait) {
    def attrs = [
        availableThermostatModes: deviceTrait.modes.join(","),
        thermostatTemperatureUnit: deviceTrait.temperatureUnit
    ]
    if (deviceTrait.setRangeMin != null) {
        def minSetpoint = deviceTrait.setRangeMin
        def maxSetpoint = deviceTrait.setRangeMax
        if (deviceTrait.temperatureUnit == "F") {
            minSetpoint = fahrenheitToCelsiusRounded(minSetpoint)
            maxSetpoint = fahrenheitToCelsiusRounded(maxSetpoint)
        }
        attrs.thermostatTemperatureRange = [
            minThresholdCelsius: minSetpoint,
            maxThresholdCelsius: maxSetpoint
        ]
    }
    if (deviceTrait.heatcoolBuffer != null) {
        def buffer = deviceTrait.heatcoolBuffer
        if (deviceTrait.temperatureUnit == "F") {
            buffer = fahrenheitToCelsiusRounded(buffer)
        }
        attrs.bufferRangeCelsius = buffer
    }
    return attrs
}

private attributesForTrait_Toggles(deviceTrait) {
    return [
        availableToggles: deviceTrait.toggles.collect { toggle ->
            [
                name: toggle.name,
                name_values: [
                    [
                        name_synonym: toggle.labels,
                        lang: "en"
                    ]
                ]
            ]
        }
    ]
}

private traitFromSettings_Brightness(traitName) {
    return [
        brightnessAttribute:  settings."${traitName}.brightnessAttribute",
        setBrightnessCommand: settings."${traitName}.setBrightnessCommand",
        commands:             ["Set Brightness"]
    ]
}

private traitFromSettings_FanSpeed(traitName) {
    def fanSpeedMapping = [
        currentSpeedAttribute: settings."${traitName}.currentSpeedAttribute",
        setFanSpeedCommand:    settings."${traitName}.setFanSpeedCommand",
        fanSpeeds:             [:],
        reversible:            settings."${traitName}.reversible",
        commands:              ["Set Fan Speed"]
    ]
    if (fanSpeedMapping.reversible) {
        fanSpeedMapping.reverseCommand = settings."${traitName}.reverseCommand"
        fanSpeedMapping.commands << "Reverse"
    }
    settings."${traitName}.fanSpeeds"?.each { fanSpeed ->
        fanSpeedMapping.fanSpeeds[fanSpeed] = settings."${traitName}.speed.${fanSpeed}.googleNames"
    }

    return fanSpeedMapping
}

private traitFromSettings_LockUnlock(traitName) {
    return [
        lockedUnlockedAttribute: settings."${traitName}.lockedUnlockedAttribute",
        lockedValue:             settings."${traitName}.lockedValue",
        lockCommand:             settings."${traitName}.lockCommand",
        unlockCommand:           settings."${traitName}.unlockCommand",
        commands:                ["Lock", "Unlock"]
    ]
}

private traitFromSettings_OnOff(traitName) {
    def deviceTrait = [
        onOffAttribute: settings."${traitName}.onOffAttribute",
        onValue:        settings."${traitName}.onValue",
        offValue:       settings."${traitName}.offValue",
        controlType:    settings."${traitName}.controlType",
        commands:       ["On", "Off"]
    ]

    if (deviceTrait.controlType == "single") {
        deviceTrait.onOffCommand = settings."${traitName}.onOffCommand"
        deviceTrait.onParam = settings."${traitName}.onParameter"
        deviceTrait.offParam = settings."${traitName}.offParameter"
    } else {
        deviceTrait.onCommand = settings."${traitName}.onCommand"
        deviceTrait.offCommand = settings."${traitName}.offCommand"
    }
    return deviceTrait
}

private traitFromSettings_OpenClose(traitName) {
    def openCloseTrait = [
        discreteOnlyOpenClose: settings."${traitName}.discreteOnlyOpenClose",
        openCloseAttribute:    settings."${traitName}.openCloseAttribute",
        // queryOnly may be null for device traits defined with older versions,
        // so coerce it to a boolean
        queryOnly:             settings."${traitName}.queryOnly" as boolean,
        commands:              []
    ]
    if (openCloseTrait.discreteOnlyOpenClose) {
        openCloseTrait.openValue = settings."${traitName}.openValue"
        openCloseTrait.closedValue = settings."${traitName}.closedValue"
    }

    if (!openCloseTrait.queryOnly) {
        if (openCloseTrait.discreteOnlyOpenClose) {
            openCloseTrait.openCommand = settings."${traitName}.openCommand"
            openCloseTrait.closeCommand = settings."${traitName}.closeCommand"
            openCloseTrait.commands += ["Open", "Close"]
        } else {
            openCloseTrait.openPositionCommand = settings."${traitName}.openPositionCommand"
            openCloseTrait.commands << "Set Position"
        }
    }

    return openCloseTrait
}

private traitFromSettings_Scene(traitName) {
    def sceneTrait = [
        activateCommand: settings."${traitName}.activateCommand",
        sceneReversible: settings."${traitName}.sceneReversible",
        commands:        ["Activate Scene"]
    ]
    if (sceneTrait.sceneReversible) {
        sceneTrait.deactivateCommand = settings."${traitName}.deactivateCommand"
        sceneTrait.commands << "Deactivate Scene"
    }
    return sceneTrait
}

private traitFromSettings_TemperatureSetting(traitName) {
    def tempSettingTrait = [
        temperatureUnit:             settings."${traitName}.temperatureUnit",
        modes:                       settings."${traitName}.modes",
        setModeCommand:              settings."${traitName}.setModeCommand",
        currentModeAttribute:        settings."${traitName}.currentModeAttribute",
        googleToHubitatModeMap:      [:],
        hubitatToGoogleModeMap:      [:],
        currentTemperatureAttribute: settings."${traitName}.currentTemperatureAttribute",
        commands:                    ["Set Mode"],
    ]
    tempSettingTrait.modes.each { mode ->
        def hubitatMode = settings."${traitName}.mode.${mode}.hubitatMode"
        if (hubitatMode != null) {
            tempSettingTrait.googleToHubitatModeMap[mode] = hubitatMode
            tempSettingTrait.hubitatToGoogleModeMap[hubitatMode] = mode
        }
    }
    def setpointAttr = settings."${traitName}.setpointAttribute"
    if (setpointAttr != null) {
        tempSettingTrait.setpointAttribute = setpointAttr
        tempSettingTrait.setSetpointCommand = settings."${traitName}.setSetpointCommand"
        tempSettingTrait.commands << "Set Setpoint"
    }
    def heatSetpointAttr = settings."${traitName}.heatingSetpointAttribute"
    if (heatSetpointAttr != null) {
        tempSettingTrait.heatingSetpointAttribute = heatSetpointAttr
        tempSettingTrait.setHeatingSetpointCommand = settings."${traitName}.setHeatingSetpointCommand"
        if (!("Set Setpoint" in tempSettingTrait.commands)) {
            tempSettingTrait.commands << "Set Setpoint"
        }
    }
    def coolSetpointAttr = settings."${traitName}.coolingSetpointAttribute"
    if (coolSetpointAttr != null) {
        tempSettingTrait.coolingSetpointAttribute = coolSetpointAttr
        tempSettingTrait.setCoolingSetpointCommand = settings."${traitName}.setCoolingSetpointCommand"
    }
    def heatcoolBuffer = settings."${traitName}.heatcoolBuffer"
    if (heatcoolBuffer != null) {
        tempSettingTrait.heatcoolBuffer = heatcoolBuffer
    }
    def setRangeMin = settings."${traitName}.range.min"
    if (setRangeMin != null) {
        tempSettingTrait.setRangeMin = setRangeMin
    }
    def setRangeMax = settings."${traitName}.range.max"
    if (setRangeMax != null) {
        tempSettingTrait.setRangeMax = setRangeMax
    }
    return tempSettingTrait
}

private traitFromSettings_Toggles(traitName) {
    def togglesTrait = [
        toggles:  [],
        commands: [],
    ]
    def toggles = settings."${traitName}.toggles"?.collect { toggle ->
        def toggleAttrs = [
            name: toggle,
            traitName: traitName,
            labels: settings."${toggle}.labels"?.split(",")
        ]
        toggleAttrs << traitFromSettings_OnOff(toggle)
        toggleAttrs
    }
    if (toggles) {
        togglesTrait.toggles = toggles
        toggles.each { toggle ->
            togglesTrait.commands += [
                "${toggle.labels[0]} On" as String,
                "${toggle.labels[0]} Off" as String
            ]
        }
    }
    return togglesTrait
}

private deviceTraitsFromState(deviceType) {
    if (state.deviceTraits == null) {
        state.deviceTraits = [:]
    }
    return state.deviceTraits."${deviceType}" ?: []
}

private addTraitToDeviceTypeState(deviceTypeName, traitType) {
    def deviceTraits = deviceTraitsFromState(deviceTypeName)
    deviceTraits << traitType
    state.deviceTraits."${deviceTypeName}" = deviceTraits
}

private deviceTypeTraitFromSettings(traitName) {
    def pieces = traitName.split("\\.traits\\.")
    def deviceType = pieces[0]
    def traitType = pieces[1]

    def traitAttrs = "traitFromSettings_${traitType}"(traitName)
    traitAttrs.name = traitName
    traitAttrs.type = traitType

    return traitAttrs
}

private deleteDeviceTrait(deviceTrait) {
    LOGGER.debug("Deleting device trait ${deviceTrait.name}")
    "deleteDeviceTrait_${deviceTrait.type}"(deviceTrait)
    def pieces = deviceTrait.name.split("\\.traits\\.")
    def deviceType = pieces[0]
    def traitType = pieces[1]
    deviceTraitsFromState(deviceType).remove(traitType as String)
}

private deleteDeviceTrait_Brightness(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.brightnessAttribute")
    app.removeSetting("${deviceTrait.name}.setBrightnessCommand")
}

private deleteDeviceTrait_FanSpeed(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.currentSpeedAttribute")
    app.removeSetting("${deviceTrait.name}.setFanSpeedCommand")
    app.removeSetting("${deviceTrait.name}.reversible")
    app.removeSetting("${deviceTrait.name}.reverseCommand")
    deviceTrait.fanSpeeds.each { fanSpeed, googleNames ->
        app.removeSetting("${deviceTrait.name}.speed.${fanSpeed}.googleNames")
    }
    app.removeSetting("${deviceTrait.name}.fanSpeeds")
}

private deleteDeviceTrait_LockUnlock(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.lockedUnlockedAttribute")
    app.removeSetting("${deviceTrait.name}.lockedValue")
    app.removeSetting("${deviceTrait.name}.lockCommand")
    app.removeSetting("${deviceTrait.name}.unlockCommand")
}

private deleteDeviceTrait_OnOff(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.onOffAttribute")
    app.removeSetting("${deviceTrait.name}.onValue")
    app.removeSetting("${deviceTrait.name}.offValue")
    app.removeSetting("${deviceTrait.name}.controlType")
    app.removeSetting("${deviceTrait.name}.onCommand")
    app.removeSetting("${deviceTrait.name}.offCommand")
    app.removeSetting("${deviceTrait.name}.onOffCommand")
    app.removeSetting("${deviceTrait.name}.onParameter")
    app.removeSetting("${deviceTrait.name}.offParameter")
}

private deleteDeviceTrait_OpenClose(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.discreteOnlyOpenClose")
    app.removeSetting("${deviceTrait.name}.openCloseAttribute")
    app.removeSetting("${deviceTrait.name}.openValue")
    app.removeSetting("${deviceTrait.name}.closedValue")
    app.removeSetting("${deviceTrait.name}.openCommand")
    app.removeSetting("${deviceTrait.name}.closeCommand")
    app.removeSetting("${deviceTrait.name}.openPositionCommand")
    app.removeSetting("${deviceTrait.name}.queryOnly")
}

private deleteDeviceTrait_Scene(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.activateCommand")
    app.removeSetting("${deviceTrait.name}.deactivateCommand")
    app.removeSetting("${deviceTrait.name}.sceneReversible")
}

private deleteDeviceTrait_TemperatureSetting(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.temperatureUnit")
    app.removeSetting("${deviceTrait.name}.modes")
    app.removeSetting("${deviceTrait.name}.currentTemperatureAttribute")
    app.removeSetting("${deviceTrait.name}.setpointAttribute")
    app.removeSetting("${deviceTrait.name}.setSetpointCommand")
    app.removeSetting("${deviceTrait.name}.heatingSetpointAttribute")
    app.removeSetting("${deviceTrait.name}.setHeatingSetpointCommand")
    app.removeSetting("${deviceTrait.name}.coolingSetpointAttribute")
    app.removeSetting("${deviceTrait.name}.setCoolingSetpointCommand")
    app.removeSetting("${deviceTrait.name}.heatcoolBuffer")
    app.removeSetting("${deviceTrait.name}.range.min")
    app.removeSetting("${deviceTrait.name}.range.max")
    app.removeSetting("${deviceTrait.name}.setModeCommand")
    app.removeSetting("${deviceTrait.name}.currentModeAttribute")
    deviceTrait.modes.each { mode ->
        app.removeSetting("${deviceTrait.name}.mode.${mode}.hubitatMode")
    }
}

private deleteDeviceTrait_Toggles(deviceTrait) {
    deviceTrait.toggles.each { toggle ->
        deleteToggle(toggle)
    }
    app.removeSetting("${deviceTrait.name}.toggles")
}

private deleteToggle(toggle) {
    LOGGER.debug("Deleting toggle: ${toggle}")
    def toggles = settings."${toggle.traitName}.toggles"
    toggles.remove(toggle.name)
    app.updateSetting("${toggle.traitName}.toggles", toggles)
    app.removeSetting("${toggle.name}.labels")
    deleteDeviceTrait_OnOff(toggle)
}

private deviceTypeTraitsFromSettings(deviceTypeName) {
    return deviceTraitsFromState(deviceTypeName).collectEntries { traitType ->
        def traitName = "${deviceTypeName}.traits.${traitType}"
        def deviceTrait = deviceTypeTraitFromSettings(traitName)
        [traitType, deviceTrait]
    }
}

private deviceTypeFromSettings(deviceTypeName) {
    def deviceType = [
        name:             deviceTypeName,
        display:          settings."${deviceTypeName}.display",
        type:             settings."${deviceTypeName}.type",
        googleDeviceType: settings."${deviceTypeName}.googleDeviceType",
        devices:          settings."${deviceTypeName}.devices",
        traits:           deviceTypeTraitsFromSettings(deviceTypeName),
        confirmCommands:  [],
        secureCommands:   [],
        pinCodes:         [],
    ]

    if (deviceType.display == null) {
        return null
    }

    def confirmCommands = settings."${deviceTypeName}.confirmCommands"
    if (confirmCommands) {
        deviceType.confirmCommands = confirmCommands
    }

    def secureCommands = settings."${deviceTypeName}.secureCommands"
    if (secureCommands) {
        deviceType.secureCommands = secureCommands
    }

    def pinCodes = settings."${deviceTypeName}.pinCodes"
    pinCodes?.each { pinCodeId ->
        deviceType.pinCodes << [
            id:    pinCodeId,
            name:  settings."${deviceTypeName}.pin.${pinCodeId}.name",
            value: settings."${deviceTypeName}.pin.${pinCodeId}.value",
        ]
    }

    return deviceType
}

private deleteDeviceTypePin(deviceType, pinId) {
    LOGGER.debug("Removing pin with ID ${pinId} from device type ${deviceType.name}")
    def pinIndex = deviceType.pinCodes.findIndexOf { it.id == pinId }
    deviceType.pinCodes.removeAt(pinIndex)
    app.updateSetting("${deviceType.name}.pinCodes", deviceType.pinCodes.collect { it.id })
    app.removeSetting("${deviceType.name}.pin.${pinId}.name")
    app.removeSetting("${deviceType.name}.pin.${pinId}.value")
}

private addDeviceTypePin(deviceType) {
    deviceType.pinCodes << [
        id: UUID.randomUUID().toString(),
        name: null,
        value: null
    ]
    app.updateSetting("${deviceType.name}.pinCodes", deviceType.pinCodes.collect { it.id })
}

private modeSceneDeviceType() {
    return [
        name:             "hubitat_mode",
        display:          "Hubitat Mode",
        googleDeviceType: "SCENE",
        confirmCommands:  [],
        secureCommands:   [],
        pinCodes:         [],
        traits: [
            Scene: [
                name: "hubitat_mode",
                type: "Scene",
                sceneReversible: false
            ]
        ],
        devices: settings.modesToExpose.collect { mode ->
            [
                getName: { mode },
                getLabel: { "${mode} Mode" },
                getId: { "hubitat_mode_${mode}" }
            ]
        },
    ]
}

private deviceTypes() {
    if (state.nextDeviceTypeIndex == null) {
        state.nextDeviceTypeIndex = 0
    }

    def deviceTypes = []
    (0..<state.nextDeviceTypeIndex).each { i ->
        def deviceType = deviceTypeFromSettings("deviceTypes.${i}")
        if (deviceType != null) {
            deviceTypes << deviceType
        }
    }
    return deviceTypes
}

private allKnownDevices() {
    def knownDevices = [:]
    // Add "device" entries for exposed mode scenes
    (deviceTypes() + [modeSceneDeviceType()]).each { deviceType ->
        deviceType.devices.each { device ->
            knownDevices."${device.getId()}" = [
                device: device,
                deviceType: deviceType
            ]
        }
    }

    return knownDevices
}

private fahrenheitToCelsiusRounded(temperature) {
    def tempCelsius = fahrenheitToCelsius(temperature)
    // Round to one decimal place
    return Math.round(tempCelsius * 10) / 10
}

private celsiusToFahrenheitRounded(temperature) {
    def tempFahrenheit = celsiusToFahrenheit(temperature)
    // Round to one decimal place
    return Math.round(tempFahrenheit * 10) / 10
}

@Field
private LOGGER = [
    debug: { if (settings.debugLogging) log.debug(it) },
    info: { log.info(it) },
    warn: { log.warn(it) },
    error: { log.error(it) }
]

@Field
private static final HUBITAT_DEVICE_TYPES = [
    accelerationSensor:          "Acceleration Sensor",
    actuator:                    "Actuator",
    alarm:                       "Alarm",
    audioNotification:           "Audio Notification",
    audioVolume:                 "Audio Volume",
    battery:                     "Battery",
    beacon:                      "Beacon",
    bulb:                        "Bulb",
    button:                      "Button",
    carbonDioxideMeasurement:    "Carbon Dioxide Measurement",
    carbonMonoxideDetector:      "Carbon Monoxide Detector",
    changeLevel:                 "Change Level",
    chime:                       "Chime",
    colorControl:                "Color Control",
    colorMode:                   "Color Mode",
    colorTemperature:            "Color Temperature",
    configuration:               "Configuration",
    consumable:                  "Consumable",
    contactSensor:               "Contact Sensor",
    doorControl:                 "Door Control",
    doubleTapableButton:         "Double Tapable Button",
    energyMeter:                 "Energy Meter",
    estimatedTimeOfArrival:      "Estimated Time Of Arrival",
    fanControl:                  "Fan Control",
    filterStatus:                "Filter Status",
    garageDoorControl:           "Garage Door Control",
    healthCheck:                 "Health Check",
    holdableButton:              "Holdable Button",
    illuminanceMeasurement:      "Illuminance Measurement",
    imageCapture:                "Image Capture",
    indicator:                   "Indicator",
    initialize:                  "Initialize",
    light:                       "Light",
    lightEffects:                "Light Effects",
    locationMode:                "Location Mode",
    lock:                        "Lock",
    lockCodes:                   "Lock Codes",
    mediaController:             "Media Controller",
    momentary:                   "Momentary",
    motionSensor:                "Motion Sensor",
    musicPlayer:                 "Music Player",
    notification:                "Notification",
    outlet:                      "Outlet",
    polling:                     "Polling",
    powerMeter:                  "Power Meter",
    powerSource:                 "Power Source",
    presenceSensor:              "Presence Sensor",
    pressureMeasurement:         "Pressure Measurement",
    pushableButton:              "Pushable Button",
    refresh:                     "Refresh",
    relativeHumidityMeasurement: "Relative Humidity Measurement",
    relaySwitch:                 "Relay Switch",
    releasableButton:            "Releasable Button",
    samsungTV:                   "Samsung TV",
    securityKeypad:              "Security Keypad",
    sensor:                      "Sensor",
    shockSensor:                 "Shock Sensor",
    signalStrength:              "Signal Strength",
    sleepSensor:                 "Sleep Sensor",
    smokeDetector:               "Smoke Detector",
    soundPressureLevel:          "Sound PressureLevel",
    soundSensor:                 "Sound Sensor",
    speechRecognition:           "Speech Recognition",
    speechSynthesis:             "Speech Synthesis",
    stepSensor:                  "Step Sensor",
    switch:                      "Switch",
    switchLevel:                 "Switch Level",
    tv:                          "TV",
    tamperAlert:                 "Tamper Alert",
    telnet:                      "Telnet",
    temperatureMeasurement:      "Temperature Measurement",
    thermostat:                  "Thermostat",
    thermostatCoolingSetpoint:   "Thermostat Cooling Setpoint",
    thermostatFanMode:           "Thermostat Fan Mode",
    thermostatHeatingSetpoint:   "Thermostat Heating Setpoint",
    thermostatMode:              "Thermostat Mode",
    thermostatOperatingState:    "Thermostat Operating State",
    thermostatSchedule:          "Thermostat Schedule",
    thermostatSetpoint:          "Thermostat Setpoint",
    threeAxis:                   "Three Axis",
    timedSession:                "Timed Session",
    tone:                        "Tone",
    touchSensor:                 "Touch Sensor",
    ultravioletIndex:            "Ultraviolet Index",
    valve:                       "Valve",
    videoCamera:                 "Video Camera",
    videoCapture:                "Video Capture",
    voltageMeasurement:          "Voltage Measurement",
    waterSensor:                 "Water Sensor",
    windowShade:                 "Window Shade",
    pHMeasurement:               "PH Measurement"
]

@Field
private static final GOOGLE_DEVICE_TYPES = [
    AC_UNIT:        "Air conditioning unit",
    AIRFRESHENER:   "Air freshener",
    AIRPURIFIER:    "Air purifier",
    AWNING:         "Awning",
    BATHTUB:        "Bathtub",
    BED:            "Bed",
    BLENDER:        "Blender",
    BLINDS:         "Blinds",
    BOILER:         "Boiler",
    CAMERA:         "Camera",
    CLOSET:         "Closet",
    COFFEE_MAKER:   "Coffee maker",
    COOKTOP:        "Cooktop",
    CURTAIN:        "Curtain",
    DEHUMIDIFIER:   "Dehumidifier",
    DEHYDRATOR:     "Dehydrator",
    DISHWASHER:     "Dishwasher",
    DOOR:           "Door",
    DRAWER:         "Drawer",
    DRYER:          "Dryer",
    FAN:            "Fan",
    FAUCET:         "Faucet",
    FIREPLACE:      "Fireplace",
    FRYER:          "Fryer",
    GARAGE:         "Garage Door",
    GATE:           "Gate",
    GRILL:          "Grill",
    HEATER:         "Heater",
    HOOD:           "Hood",
    HUMIDIFIER:     "Humidifier",
    KETTLE:         "Kettle",
    LIGHT:          "Light",
    LOCK:           "Lock",
    MOP:            "Mop",
    MOWER:          "Mower",
    MICROWAVE:      "Microwave",
    MULTICOOKER:    "Multicooker",
    OUTLET:         "Outlet",
    OVEN:           "Oven",
    PERGOLA:        "Pergola",
    PETFEEDER:      "Pet Feeder",
    PRESSURECOOKER: "Pressure cooker",
    RADIATOR:       "Radiator",
    REFRIGERATOR:   "Refrigerator",
    SCENE:          "Scene",
    SECURITYSYSTEM: "Security system",
    SENSOR:         "Sensor",
    SHUTTER:        "Shutter",
    SHOWER:         "Shower",
    SOUSVIDE:       "Sous vide",
    SPRINKLER:      "Sprinkler",
    STANDMIXER:     "Stand Mixer",
    SWITCH:         "Switch",
    THERMOSTAT:     "Thermostat",
    VACUUM:         "Vacuum",
    VALVE:          "Valve",
    WASHER:         "Washer",
    WATERHEATER:    "Water heater",
    WINDOW:         "Window",
    YOGURTMAKER:    "Yogurt maker"
]

@Field
private static final GOOGLE_DEVICE_TRAITS = [
    //ArmDisarm: "Arm/Disarm",
    Brightness: "Brightness",
    //CameraStream: "Camera Stream",
    //ColorSetting: "Color Setting",
    //Cook: "Cook",
    //Dispense: "Dispense",
    //Dock: "Dock",
    FanSpeed: "Fan Speed",
    //Fill: "Fill",
    //HumiditySetting: "Humidity Setting",
    //LightEffects: "Light Effects",
    //Locator: "Locator",
    LockUnlock: "Lock/Unlock",
    //Modes: "Modes",
    OnOff: "On/Off",
    OpenClose: "Open/Close",
    //Reboot: "Reboot",
    //Rotation: "Rotation",
    //RunCycle: "Run Cycle",
    Scene: "Scene",
    //SoftwareUpdate: "Software Update",
    //StartStop: "Start/Stop",
    //StatusReport: "Status Report",
    //TemperatureControl: "Temperature Control",
    TemperatureSetting: "Temperature Setting",
    //Timer: "Timer",
    Toggles: "Toggles",
]
