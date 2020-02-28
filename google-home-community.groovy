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

import groovy.transform.Field

definition(
    name: "Google Home Community",
    namespace: "mbudnek",
    author: "Miles Budnek",
    description: "Community-maintained Google Home integration",
    category: "Integrations",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPreferences")
    page(name: "deviceTypePreferences")
    page(name: "deviceTypeDelete")
    page(name: "deviceTraitPreferences")
    page(name: "deviceTraitDelete")
}

mappings {
    path("/action") {
        action: [
            POST: "handleAction"
        ]
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

        section(name: "Device Traits") {
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
            def currentDeviceTraits = deviceType?.traits ?: [:]
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
    }
}

def deviceTypeDelete(deviceType) {
    return dynamicPage(name: "deviceTypeDelete", title: "Device Type Deleted", nextPage: "mainPreferences") {
        logger.debug("Deleting device type ${deviceType.display}")
        app.removeSetting("${deviceType.name}.display")
        app.removeSetting("${deviceType.name}.type")
        app.removeSetting("${deviceType.name}.googleDeviceType")
        app.removeSetting("${deviceType.name}.devices")
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

private deviceTraitPreferences_OnOff(deviceTrait) {
    section("On/Off Settings") {
        input(
            name: "${deviceTrait.name}.onOffAttribute",
            title: "On/Off Attribute",
            type: "text",
            defaultValue: "switch",
            required: true
        )
        input(
            name: "${deviceTrait.name}.onValue",
            title: "On Value",
            type: "text",
            defaultValue: "on",
            required: true
        )
        input(
            name: "${deviceTrait.name}.offValue",
            title: "Off Value",
            type: "text",
            defaultValue: "off",
            required: true
        )
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
                title: "Open Value",
                type: "text",
                defaultValue: "open",
                required: true
            )
            input(
                name: "${deviceTrait.name}.closedValue",
                title: "Closed Value",
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

def handleAction() {
    logger.debug(request.JSON)
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
        // Send appropriate commands to devices
        devices.each { device ->
            command.execution.each { execution ->
                def commandName = execution.command.split("\\.").last()
                attrsToAwait[device.device] += "executeCommand_${commandName}"(device, execution)
            }
        }
        // Wait a little while for devices to report their new state
        attrsToAwait.each { device, attributes ->
            attributes.each { attrName, attrValue ->
                for (def i = 0; i < 100000; ++i) {
                    if (device.currentValue(attrName, true) == attrValue) {
                        break
                    }
                }
            }
        }
        // Now build our response message
        devices.each { device ->
            def deviceState = [
                online: true
            ]
            device.deviceType.traits.each { traitType, deviceTrait ->
                deviceState += "deviceStateForTrait_${traitType}"(deviceTrait, device.device)
            }
            resp.payload.commands << [
                status: "SUCCESS",
                ids: [device.device.getId()],
                states: deviceState
            ]
        }

    }
    logger.debug(resp)
    return resp
}

private executeCommand_ActivateScene(deviceInfo, command) {
    def sceneTrait = deviceInfo.deviceType.traits.Scene
    if (sceneTrait.name == "hubitat_mode") {
        location.setMode(deviceInfo.device.getName())
    } else {
        if (command.params.deactivate) {
            deviceInfo.device."${sceneTrait.deactivateCommand}"()
        } else {
            deviceInfo.device."${sceneTrait.activateCommand}"()
        }
    }
    return [:]
}

private executeCommand_BrightnessAbsolute(deviceInfo, command) {
    def brightnessTrait = deviceInfo.deviceType.traits.Brightness
    // Google uses 0...100 for brightness but hubitat uses 0...99, so clamp
    def brightnessToSet = Math.min(command.params.brightness, 99)

    deviceInfo.device."${brightnessTrait.setBrightnessCommand}"(brightnessToSet)
    return [
        (brightnessTrait.brightnessAttribute): brightnessToSet
    ]
}

private executeCommand_Reverse(deviceInfo, command) {
    def fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    deviceInfo.device."${fanSpeedTrait.reverseCommand}"()
    return [:]
}

private executeCommand_OnOff(deviceInfo, command) {
    def onOffTrait = deviceInfo.deviceType.traits.OnOff
    def checkValue
    if (command.params.on) {
        deviceInfo.device."${onOffTrait.onCommand}"()
        checkValue = onOffTrait.onValue
    } else {
        deviceInfo.device."${onOffTrait.offCommand}"()
        checkValue = onOffTrait.offValue
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
        deviceInfo.device."${openCloseTrait.openCommand}"()
        checkValue = openCloseTrait.openValue
    } else if (openCloseTrait.discreteOnlyOpenClose && openPercent == 0) {
        deviceInfo.device."${openCloseTrait.closeCommand}"()
        checkValue = openCloseTrait.closedValue
    } else {
        deviceInfo.device."${openCloseTrait.openPositionCommand}"(openPercent)
        checkValue = openPercent
    }
    return [
        (openCloseTrait.openCloseAttribute): checkValue
    ]
}

private executeCommand_SetFanSpeed(deviceInfo, command) {
    def fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    def fanSpeed = command.params.fanSpeed

    deviceInfo.device."${fanSpeedTrait.setFanSpeedCommand}"(fanSpeed)
    return [
        (fanSpeedTrait.currentSpeedAttribute): fanSpeed
    ]
}

private executeCommand_ThermostatTemperatureSetpoint(deviceInfo, command) {
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
    logger.debug(resp)
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

private deviceStateForTrait_OnOff(deviceTrait, device) {
    return [
        on: device.latestValue(deviceTrait.onOffAttribute) == deviceTrait.onValue
    ]
}

private deviceStateForTrait_OpenClose(deviceTrait, device) {
    def openPercent
    if (deviceTrait.discreteOnlyOpenClose) {
        if (device.currentValue(deviceTrait.openCloseAttribute) == deviceTrait.openValue) {
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
                    name: device.getLabel()
                ],
                willReportState: false,
                attributes: attributes,
            ]
        }
    }
    logger.debug(resp)
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

void installed() {}
void uninstalled() {}
void updated() {}

private traitFromSettings_Brightness(traitName) {
    return [
        brightnessAttribute: settings."${traitName}.brightnessAttribute",
        setBrightnessCommand: settings."${traitName}.setBrightnessCommand"
    ]
}

private traitFromSettings_FanSpeed(traitName) {
    def fanSpeedMapping = [
        currentSpeedAttribute: settings."${traitName}.currentSpeedAttribute",
        setFanSpeedCommand: settings."${traitName}.setFanSpeedCommand",
        fanSpeeds: [:],
        reversible: settings."${traitName}.reversible"
    ]
    if (fanSpeedMapping.reversible) {
        fanSpeedMapping.reverseCommand = settings."${traitName}.reverseCommand"
    }
    settings."${traitName}.fanSpeeds"?.each { fanSpeed ->
        fanSpeedMapping.fanSpeeds[fanSpeed] = settings."${traitName}.speed.${fanSpeed}.googleNames"
    }

    return fanSpeedMapping
}

private traitFromSettings_OnOff(traitName) {
    return [
        onOffAttribute: settings."${traitName}.onOffAttribute",
        onValue: settings."${traitName}.onValue",
        offValue: settings."${traitName}.offValue",
        onCommand: settings."${traitName}.onCommand",
        offCommand: settings."${traitName}.offCommand"
    ]
}

private traitFromSettings_OpenClose(traitName) {
    def openCloseTrait = [
        discreteOnlyOpenClose: settings."${traitName}.discreteOnlyOpenClose",
        openCloseAttribute: settings."${traitName}.openCloseAttribute",
        // queryOnly may be null for device traits defined with older versions,
        // so coerce it to a boolean by negating it twice
        queryOnly: !!settings."${traitName}.queryOnly",
    ]
    if (openCloseTrait.discreteOnlyOpenClose) {
        openCloseTrait.openValue = settings."${traitName}.openValue"
        openCloseTrait.closedValue = settings."${traitName}.closedValue"
    }

    if (!openCloseTrait.queryOnly) {
        if (openCloseTrait.discreteOnlyOpenClose) {
            openCloseTrait.openCommand = settings."${traitName}.openCommand"
            openCloseTrait.closeCommand = settings."${traitName}.closeCommand"
        } else {
            openCloseTrait.setPositionCommand = settings."${traitName}.setPositionCommand"
        }
    }

    return openCloseTrait
}

private traitFromSettings_Scene(traitName) {
    def sceneTrait = [
        activateCommand: settings."${traitName}.activateCommand",
        sceneReversible: settings."${traitName}.sceneReversible"
    ]
    if (sceneTrait.sceneReversible) {
        sceneTrait.deactivateCommand = settings."${traitName}.deactivateCommand"
    }
    return sceneTrait
}

private traitFromSettings_TemperatureSetting(traitName) {
    def tempSettingTrait = [
        temperatureUnit: settings."${traitName}.temperatureUnit",
        modes: settings."${traitName}.modes",
        setModeCommand: settings."${traitName}.setModeCommand",
        currentModeAttribute: settings."${traitName}.currentModeAttribute",
        googleToHubitatModeMap: [:],
        hubitatToGoogleModeMap: [:],
        currentTemperatureAttribute: settings."${traitName}.currentTemperatureAttribute"
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
    }
    def heatSetpointAttr = settings."${traitName}.heatingSetpointAttribute"
    if (heatSetpointAttr != null) {
        tempSettingTrait.heatingSetpointAttribute = heatSetpointAttr
        tempSettingTrait.setHeatingSetpointCommand = settings."${traitName}.setHeatingSetpointCommand"
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
    logger.debug("Deleting device trait ${deviceTrait.name}")
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

private deleteDeviceTrait_OnOff(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.onOffAttribute")
    app.removeSetting("${deviceTrait.name}.onValue")
    app.removeSetting("${deviceTrait.name}.offValue")
    app.removeSetting("${deviceTrait.name}.onCommand")
    app.removeSetting("${deviceTrait.name}.offCommand")
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
    ]

    if (deviceType.display == null) {
        return null
    }
    return deviceType
}

private modeSceneDeviceType() {
    return [
        name: "hubitat_mode",
        display: "Hubitat Mode",
        googleDeviceType: "SCENE",
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
        }
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
private logger = [
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
    videoCapture:                "Video Camera",
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
    //LockUnlock: "Lock/Unlock",
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
    //Toggles: "Toggles",
]
