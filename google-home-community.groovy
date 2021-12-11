// Copyright 2021 The Google Home Community Contributors
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

// Changelog:
//   * Feb 24 2020 - Initial release
//   * Feb 24 2020 - Add the Sensor device type and the Query Only Open/Close setting to better support contact sensors.
//   * Feb 27 2020 - Fix issue with devices getting un-selected when linking to Google Home
//   * Feb 27 2020 - Add Setting to enable/disable debug logging
//   * Feb 28 2020 - Add support for using a single command with a parameter for the OnOff trait
//   * Feb 29 2020 - Fall back to using device name if device label isn't defined
//   * Mar 01 2020 - Add support for the Toggles device trait
//   * Mar 15 2020 - Add confirmation and PIN code support
//   * Mar 15 2020 - Fix Open/Close trait when "Discrete Only Open/Close" isn't set
//   * Mar 17 2020 - Add support for the Lock/Unlock trait
//   * Mar 18 2020 - Add support for the Color Setting trait
//   * Mar 19 2020 - Add support for ambient temperature sensors using the "Query Only Temperature Setting" attribute
//                   of the Temperature Setting trait
//   * Mar 20 2020 - Add support for the Temperature Control trait
//   * Mar 21 2020 - Change Temperature Setting trait to use different setpoint commands and attributes per mode
//   * Mar 21 2020 - Sort device types by name on the main settings page
//   * Mar 21 2020 - Don't configure setpoint attribute and command for the "off" thermostat mode
//   * Mar 21 2020 - Fix some Temperture Setting and Temperature Control settings that were using the wrong input type
//   * Mar 21 2020 - Fix the Temperature Setting heat/cool buffer and Temperature Control temperature step conversions
//                   from Fahrenheit to Celsius
//   * Mar 29 2020 - Add support for the Humidity Setting trait
//   * Apr 08 2020 - Fix timeout error by making scene activation asynchronous
//   * Apr 08 2020 - Add support for the Rotation trait
//   * Apr 10 2020 - Add new device types: Carbon Monoxide Sensor, Charger, Remote Control, Set-Top Box,
//                   Smoke Detector, Television, Water Purifier, and Water Softener
//   * Apr 10 2020 - Add support for the Volume trait
//   * Aug 05 2020 - Add support for Camera trait
//   * Aug 25 2020 - Add support for Global PIN Codes
//   * Oct 03 2020 - Add support for devices not allowing volumeSet command when changing volume
//   * Jan 18 2021 - Fix SetTemperature command of the TemperatureControl trait
//   * Jan 19 2021 - Added Dock and StartStop Traits
//   * Jan 31 2021 - Don't break the whole app if someone creates an invalid toggle
//   * Feb 28 2021 - Add new device types supported by Google
//   * Apr 18 2021 - Added Locator Trait
//   * Apr 23 2021 - Added Energy Storage, Software Update, Reboot, Media State (query untested) and
//                   Timer (commands untested) Traits.  Added missing camera trait protocol attributes.
//   * May 04 2021 - Fixed time remaining trait of Energy Storage
//   * May 07 2021 - Immediate response mode: change poll from 5 seconds to 1 second and return
//                   PENDING response for any devices which haven't yet reached the desired state
//   * May 07 2021 - Add roomHint based on Hubitat room names
//   * May 07 2021 - Log requests and responses in JSON to make debugging easier
//   * May 09 2021 - Handle missing rooms API gracefully for compatibility with Hubitat < 2.2.7
//   * May 10 2021 - Treat level/position of 99 as 100 instead of trying to scale
//   * May 20 2021 - Add a reverseDirection setting to the Open/Close trait to support devices that consider position
//                   0 to be fully open
//   * Jun 27 2021 - Log a warning on SYNC if a device is selected as multiple device types
//   * Sep 14 2021 - Fixed CameraStream trait to match the latest Google API.  Moved protocol support to the
//                   driver level to accomodate different camera stream sources
//   * Dec 10 2021 - Added Arm/Disarm Trait

import groovy.json.JsonException
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
    importUrl: "https://raw.githubusercontent.com/mbudnek/google-home-hubitat-community/master/google-home-community.groovy"  // IgnoreLineLength
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

@SuppressWarnings('AssignmentInConditional')
def appButtonHandler(buttonPressed) {
    def match
    if ((match = (buttonPressed =~ /^addPin:(.+)$/))) {
        def deviceTypeName = match.group(1)
        def deviceType = deviceTypeFromSettings(deviceTypeName)
        addDeviceTypePin(deviceType)
    } else if ((match = (buttonPressed =~ /^deletePin:(.+)\.pin\.(.+)$/))) {
        def pinId = match.group(2)
        // If we actually delete the PIN here then it will get added back when the
        // device type settings page re-submits after the button handler finishes.
        // Instead just set a flag that we want to delete this PIN, and the settings
        // page will take care of actually deleting it.
        state.pinToDelete = pinId
    }
}

@SuppressWarnings('MethodSize')
def mainPreferences() {
    // Make sure that the deviceTypeFromSettings returns by giving it a display name
    app.updateSetting("GlobalPinCodes.display", "Global PIN Codes")
    def globalPinCodes = deviceTypeFromSettings('GlobalPinCodes')
    if (state.pinToDelete) {
        deleteDeviceTypePin(globalPinCodes, state.pinToDelete)
        state.remove("pinToDelete")
    }
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
        def allDeviceTypes = deviceTypes().sort { it.display }
        section {
            allDeviceTypes.each { deviceType ->
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
        section("Global PIN Codes") {
            globalPinCodes?.pinCodes?.each { pinCode ->
                input(
                    name: "GlobalPinCodes.pin.${pinCode.id}.name",
                    title: "PIN Code Name",
                    type: "text",
                    required: true,
                    width: 6
                )
                input(
                    name: "GlobalPinCodes.pin.${pinCode.id}.value",
                    title: "PIN Code Value",
                    type: "password",
                    required: true,
                    width: 5
                )
                input(
                    name: "deletePin:GlobalPinCodes.pin.${pinCode.id}",
                    title: "X",
                    type: "button",
                    width: 1
                )
            }
            input(
                name: "addPin:GlobalPinCodes",
                title: "Add PIN Code",
                type: "button"
            )
        }
    }
}

@SuppressWarnings('MethodSize')
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
        def pinCodeIds = deviceType.pinCodes*.id
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

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_ArmDisarm(deviceTrait) {
    hubitatAlarmLevels = [
        "disarmed":              "Disarm",
        "armed home":            "Home",
        "armed night":           "Night",
        "armed away":            "Away",
    ]
    hubitatAlarmCommands = [
        "disarmed":              "disarm",
        "armed home":            "armHome",
        "armed night":           "armNight",
        "armed away":            "armAway",
    ]
    hubitatAlarmValues = [
        "disarmed":              "disarmed",
        "armed home":            "armed home",
        "armed night":           "armed night",
        "armed away":            "armed away",
    ]
    section("Arm/Disarm Settings") {
        input(
            name: "${deviceTrait.name}.armedAttribute",
            title: "Armed/Disarmed Attribute",
            type: "text",
            defaultValue: "securityKeypad",
            required: true
        )
        input(
            name: "${deviceTrait.name}.armLevelAttribute",
            title: "Current Arm Level Attribute",
            type: "text",
            defaultValue: "securityKeypad",
            required: true
        )
        input(
            name: "${deviceTrait.name}.exitAllowanceAttribute",
            title: "Exit Delay Value Attribute",
            type: "text",
            defaultValue: "exitAllowance",
            required: true
        )
        input(
            name: "${deviceTrait.name}.disarmedValue",
            title: "Disarmed Value",
            type: "text",
            defaultValue: "disarmed",
            required: true
        )
        input(
            name: "${deviceTrait.name}.cancelCommand",
            title: "Cancel Arming Command",
            type: "text",
            defaultValue: "disarm",
            required: true
        )
        input(
            name: "${deviceTrait.name}.armLevels",
            title: "Supported Alarm Levels",
            type: "enum",
            options: hubitatAlarmLevels,
            multiple: true,
            required: true,
            submitOnChange: true
        )
        deviceTrait.armLevels.each { armLevel ->
            input(
                name: "${deviceTrait.name}.armLevels.${armLevel.key}.googleNames",
                title: "Google Home Level Names for ${hubitatAlarmLevels[armLevel.key]}",
                description: "A comma-separated list of names that the Google Assistant will " +
                             "accept for this alarm setting",
                type: "text",
                required: "true",
                defaultValue: hubitatAlarmLevels[armLevel.key]
            )
            input(
                name: "${deviceTrait.name}.armCommands.${armLevel.key}.commandName",
                title: "Hubitat Command for ${hubitatAlarmLevels[armLevel.key]}",
                type: "text",
                required: "true",
                defaultValue: hubitatAlarmCommands[armLevel.key]
            )
            input(
                name: "${deviceTrait.name}.armValues.${armLevel.key}.value",
                title: "Hubitat Value for ${hubitatAlarmLevels[armLevel.key]}",
                type: "text",
                required: "true",
                defaultValue: hubitatAlarmValues[armLevel.key]
            )
        }
    }
}
@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
def deviceTraitPreferences_CameraStream(deviceTrait) {
    section("Stream Camera") {
        input(
            name: "${deviceTrait.name}.cameraStreamURLAttribute",
            title: "Camera Stream URL Attribute",
            type: "text",
            defaultValue: "streamURL",
            required: true
        )
        input(
            name: "${deviceTrait.name}.cameraStreamProtocolAttribute",
            title: "Camera Stream Protocol Attribute",
            type: "text",
            defaultValue: "streamProtocol",
            required: true
        )
        input(
            name: "${deviceTrait.name}.cameraStreamCommand",
            title: "Start Camera Stream Command",
            type: "text",
            defaultValue: "on",
            required: true
        )
    }
}

@SuppressWarnings(['MethodSize', 'UnusedPrivateMethod'])
private deviceTraitPreferences_ColorSetting(deviceTrait) {
    section ("Color Setting Preferences") {
        input(
            name: "${deviceTrait.name}.fullSpectrum",
            title: "Full-Spectrum Color Control",
            type: "bool",
            required: true,
            submitOnChange: true
        )
        if (deviceTrait.fullSpectrum) {
            input(
                name: "${deviceTrait.name}.hueAttribute",
                title: "Hue Attribute",
                type: "text",
                defaultValue: "hue",
                required: true
            )
            input(
                name: "${deviceTrait.name}.saturationAttribute",
                title: "Saturation Attribute",
                type: "text",
                defaultValue: "saturation",
                required: true
            )
            input(
                name: "${deviceTrait.name}.levelAttribute",
                title: "Level Attribute",
                type: "text",
                defaultValue: "level",
                required: true
            )
            input(
                name: "${deviceTrait.name}.setColorCommand",
                title: "Set Color Command",
                type: "text",
                defaultValue: "setColor",
                required: true
            )
        }
        input(
            name: "${deviceTrait.name}.colorTemperature",
            title: "Color Temperature Control",
            type: "bool",
            required: true,
            submitOnChange: true
        )
        if (deviceTrait.colorTemperature) {
            input(
                name: "${deviceTrait.name}.colorTemperature.min",
                title: "Minimum Color Temperature",
                type: "number",
                defaultValue: 2200,
                required: true
            )
            input(
                name: "${deviceTrait.name}.colorTemperature.max",
                title: "Maximum Color Temperature",
                type: "number",
                defaultValue: 6500,
                required: true
            )
            input(
                name: "${deviceTrait.name}.colorTemperatureAttribute",
                title: "Color Temperature Attribute",
                type: "text",
                defaultValue: "colorTemperature",
                required: true
            )
            input(
                name: "${deviceTrait.name}.setColorTemperatureCommand",
                title: "Set Color Temperature Command",
                type: "text",
                defaultValue: "setColorTemperature",
                required: true
            )
        }
        if (deviceTrait.fullSpectrum && deviceTrait.colorTemperature) {
            input(
                name: "${deviceTrait.name}.colorModeAttribute",
                title: "Color Mode Attribute",
                type: "text",
                defaultValue: "colorMode",
                required: true
            )
            input(
                name: "${deviceTrait.name}.fullSpectrumModeValue",
                title: "Full-Spectrum Mode Value",
                type: "text",
                defaultValue: "RGB",
                required: true
            )
            input(
                name: "${deviceTrait.name}.temperatureModeValue",
                title: "Color Temperature Mode Value",
                type: "text",
                defaultValue: "CT",
                required: true
            )
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_Dock(deviceTrait) {
    section("Dock Settings") {
        input(
            name: "${deviceTrait.name}.dockAttribute",
            title: "Dock Attribute",
            type: "text",
            defaultValue: "status",
            required: true
        )
        input(
            name: "${deviceTrait.name}.dockValue",
            title: "Docked Value",
            type: "text",
            defaultValue: "docked",
            required: true
        )
        input(
            name: "${deviceTrait.name}.dockCommand",
            title: "Dock Command",
            type: "text",
            defaultValue: "returnToDock",
            required: true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod', 'MethodSize'])
private deviceTraitPreferences_EnergyStorage(deviceTrait) {
    googleEnergyStorageDistanceUnitForUX = [
        "KILOMETERS":      "Kilometers",
        "MILES":           "Miles",
    ]
    googleCapacityUnits = [
        "SECONDS":         "Seconds",
        "MILES":           "Miles",
        "KILOMETERS":      "Kilometers",
        "PERCENTAGE":      "Percentage",
        "KILOWATT_HOURS":  "Kilowatt Hours",
    ]
    section("Energy Storage Settings") {
        input(
            name: "${deviceTrait.name}.isRechargeable",
            title: "Rechargeable",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true
        )
        input(
            name: "${deviceTrait.name}.capacityRemainingRawValue",
            title: "Capacity Remaining Value",
            type: "text",
            defaultValue: "battery",
            required: true
        )
        input(
            name: "${deviceTrait.name}.capacityRemainingUnit",
            title: "Capacity Remaining Unit",
            type: "enum",
            options: googleCapacityUnits,
            defaultValue: "PERCENTAGE",
            multiple: false,
            required: true,
            submitOnChange: true
        )
    }
    section(hideable: true, hidden: true, "Advanced Settings") {
        input(
            name: "${deviceTrait.name}.queryOnlyEnergyStorage",
            title: "Query Only Energy Storage",
            type: "bool",
            defaultValue: true,
            required: true,
            submitOnChange: true
        )
        if (deviceTrait.queryOnlyEnergyStorage == false) {
            input(
                name: "${deviceTrait.name}.chargeCommand",
                title: "Charge Command",
                type: "text",
                required: true
            )
        }
        input(
            name: "${deviceTrait.name}.capacityUntilFullRawValue",
            title: "Capacity Until Full Value",
            type: "text",
        )
        input(
            name: "${deviceTrait.name}.capacityUntilFullUnit",
            title: "Capacity Until Full Unit",
            type: "enum",
            options: googleCapacityUnits,
            multiple: false,
            submitOnChange: true
        )
        input(
            name: "${deviceTrait.name}.descriptiveCapacityRemainingAttribute",
            title: "Descriptive Capacity Remaining",
            type: "text",
        )
        input(
            name: "${deviceTrait.name}.isChargingAttribute",
            title: "Charging Attribute",
            type: "text",
        )
        input(
            name: "${deviceTrait.name}.chargingValue",
            title: "Charging Value",
            type: "text",
        )
        input(
            name: "${deviceTrait.name}.isPluggedInAttribute",
            title: "Plugged in Attribute",
            type: "text",
        )
        input(
            name: "${deviceTrait.name}.pluggedInValue",
            title: "Plugged in Value",
            type: "text",
        )
        if ((deviceTrait.capacityRemainingUnit == "MILES")
            || (deviceTrait.capacityRemainingUnit == "KILOMETERS")
            || (deviceTrait.capacityUntilFullUnit == "MILES")
            || (deviceTrait.capacityUntilFullUnit == "KILOMETERS")) {
            input(
                name: "${deviceTrait.name}.energyStorageDistanceUnitForUX",
                title: "Supported Distance Units",
                type: "enum",
                options: googleEnergyStorageDistanceUnitForUX,
                multiple: false,
                submitOnChange: true
            )
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
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
                description: "A comma-separated list of names that the Google Assistant will " +
                             "accept for this speed setting",
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

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_HumiditySetting(deviceTrait) {
    section("Humidity Setting Preferences") {
        input(
            name: "${deviceTrait.name}.humidityAttribute",
            title: "Humidity Attribute",
            type: "text",
            defaultValue: "humidity",
            required: true
        )
        input(
            name: "${deviceTrait.name}.queryOnly",
            title: "Query Only Humidity",
            type: "bool",
            defaultValue: false,
            require: true,
            submitOnChange: true
        )
        if (!deviceTrait.queryOnly) {
            input(
                name: "${deviceTrait.name}.humiditySetpointAttribute",
                title: "Humidity Setpoint Attribute",
                type: "text",
                required: true
            )
            input(
                name: "${deviceTrait.name}.setHumidityCommand",
                title: "Set Humidity Command",
                type: "text",
                required: true
            )
            paragraph("If either a minimum or maximum humidity setpoint is configured then the other must be as well")
            input(
                name: "${deviceTrait.name}.humidityRange.min",
                title: "Minimum Humidity Setpoint",
                type: "number",
                required: deviceTrait.humidityRange?.max != null,
                submitOnChange: true
            )
            input(
                name: "${deviceTrait.name}.humidityRange.max",
                title: "Maximum Humidity Setpoint",
                type: "number",
                required: deviceTrait.humidityRange?.min != null,
                submitOnChange: true
            )
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_Locator(deviceTrait) {
    section("Locator Settings") {
        input(
            name: "${deviceTrait.name}.locatorCommand",
            title: "Locator Command",
            type: "text",
            defaultValue: "locate",
            required: true
        )
    }
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_MediaState(deviceTrait) {
    section("Media State Settings") {
        input(
            name: "${deviceTrait.name}.supportActivityState",
            title: "Support Activity State",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true,
        )
        if (deviceTrait.supportActivityState) {
            input(
                name: "${deviceTrait.name}.activityStateAttribute",
                title: "Activity State Attribute",
                type: "text",
                required: true
            )
        }
        input(
            name: "${deviceTrait.name}.supportPlaybackState",
            title: "Support Playback State",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true,
        )
        if (deviceTrait.supportPlaybackState) {
            input(
                name: "${deviceTrait.name}.playbackStateAttribute",
                title: "Playback State Attribute",
                type: "text",
                defaultValue: "status",
                required: true
            )
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
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
                description: "Values of the Open/Close Attribute that indicate this device is open.  " +
                             "Separate multiple values with a comma",
                type: "text",
                defaultValue: "open",
                required: true
            )
            input(
                name: "${deviceTrait.name}.closedValue",
                title: "Closed Values",
                description: "Values of the Open/Close Attribute that indicate this device is closed.  " +
                             "Separate multiple values with a comma",
                type: "text",
                defaultValue: "closed",
                required: true
            )
        } else {
            paragraph("Set this if your device considers position 0 to be fully open")
            input(
                name: "${deviceTrait.name}.reverseDirection",
                title: "Reverse Direction",
                type: "bool"
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

@SuppressWarnings('UnusedPrivateMethod')
def deviceTraitPreferences_Reboot(deviceTrait) {
    section("Reboot Preferences") {
        input(
            name: "${deviceTrait.name}.rebootCommand",
            title: "Reboot Command",
            type: "text",
            required: true
        )
    }
}

@SuppressWarnings('UnusedPrivateMethod')
def deviceTraitPreferences_Rotation(deviceTrait) {
    section("Rotation Preferences") {
        input(
            name: "${deviceTrait.name}.rotationAttribute",
            title: "Current Rotation Attribute",
            type: "text",
            required: true
        )
        input(
            name: "${deviceTrait.name}.setRotationCommand",
            title: "Set Rotation Command",
            type: "text",
            required: true
        )
        input(
            name: "${deviceTrait.name}.continuousRotation",
            title: "Supports Continuous Rotation",
            type: "bool",
            defaultValue: false
        )
    }
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
def deviceTraitPreferences_SoftwareUpdate(deviceTrait) {
    section("Software Update Settings") {
        input(
            name: "${deviceTrait.name}.lastSoftwareUpdateUnixTimestampSecAttribute",
            title: "Last Software Update Unix Timestamp in Seconds Attribute",
            type: "text",
            required: true
        )
        input(
            name: "${deviceTrait.name}.softwareUpdateCommand",
            title: "Software Update Command",
            type: "text",
            required: true
        )
    }
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_StartStop(deviceTrait) {
    section("Start/Stop Settings") {
        input(
            name: "${deviceTrait.name}.startStopAttribute",
            title: "Start/Stop Attribute",
            type: "text",
            defaultValue: "status",
            required: true
        )
        input(
            name: "${deviceTrait.name}.startValue",
            title: "Start Value",
            type: "text",
            defaultValue: "running",
            required: true
        )
        input(
            name: "${deviceTrait.name}.stopValue",
            title: "Stop Value",
            type: "text",
            defaultValue: "returning to dock",
            required: true
        )
        input(
            name: "${deviceTrait.name}.startCommand",
            title: "Start Command",
            type: "text",
            defaultValue: "start",
            required: true
        )
        input(
            name: "${deviceTrait.name}.stopCommand",
            title: "Stop Command",
            type: "text",
            defaultValue: "returnToDock",
            required: true
        )
        input(
            name:"${deviceTrait.name}.canPause",
            type: "bool",
            title: "Is this device pausable? disable this option if not pausable",
            defaultValue: true,
            submitOnChange: true
        )
        if (deviceTrait.canPause) {
            input(
                name: "${deviceTrait.name}.pauseUnPauseAttribute",
                title: "Pause/UnPause Attribute",
                type: "text",
                required: true,
                defaultValue: "status"
            )
            input(
                name: "${deviceTrait.name}.pauseValue",
                title: "Pause Value",
                type: "text",
                required: true,
                defaultValue: "paused",
            )
            input(
                name: "${deviceTrait.name}.pauseCommand",
                title: "Pause Command",
                type: "text",
                required: true,
                defaultValue: "pause"
            )
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
def deviceTraitPreferences_TemperatureControl(deviceTrait) {
    section ("Temperature Control Preferences") {
        input(
            name: "${deviceTrait.name}.temperatureUnit",
            title: "Temperature Unit",
            type: "enum",
            options: [
                "C": "Celsius",
                "F": "Fahrenheit"
            ],
            required: true,
            defaultValue: temperatureScale
        )
        input(
            name: "${deviceTrait.name}.currentTemperatureAttribute",
            title: "Current Temperature Attribute",
            type: "text",
            required: true,
            defaultValue: "temperature"
        )
        input(
            name: "${deviceTrait.name}.queryOnly",
            title: "Query Only Temperature Control",
            type: "bool",
            defaultValue: false,
            required: true,
            submitOnChange: true
        )
        if (!deviceTrait.queryOnly) {
            input(
                name: "${deviceTrait.name}.setpointAttribute",
                title: "Current Temperature Setpoint Attribute",
                type: "text",
                required: true
            )
            input(
                name: "${deviceTrait.name}.setTemperatureCommand",
                title: "Set Temperature Command",
                type: "text",
                required: true
            )
            input(
                name: "${deviceTrait.name}.minTemperature",
                title: "Minimum Temperature Setting",
                type: "decimal",
                required: true
            )
            input(
                name: "${deviceTrait.name}.maxTemperature",
                title: "Maximum Temperature Setting",
                type: "decimal",
                required: true
            )
            input(
                name: "${deviceTrait.name}.temperatureStep",
                title: "Temperature Step",
                type: "decimal"
            )
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
def deviceTraitPreferences_TemperatureSetting(deviceTrait) {
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
            defaultValue: temperatureScale
        )
        input(
            name: "${deviceTrait.name}.currentTemperatureAttribute",
            title: "Current Temperature Attribute",
            type: "text",
            required: true,
            defaultValue: "temperature"
        )
        input(
            name: "${deviceTrait.name}.queryOnly",
            title: "Query Only Temperature Setting",
            type: "bool",
            required: true,
            defaultValue: false,
            submitOnChange: true
        )
        if (!deviceTrait.queryOnly) {
            temperatureSettingControlPreferences(deviceTrait)
        }
    }
}

private thermostatSetpointAttributePreferenceForModes(modes) {
    def setpointAttributeDefaults = [
        "heatingSetpointAttribute": "heatingSetpoint",
        "coolingSetpointAttribute": "coolingSetpoint"
    ]
    def prefs = []
    modes.each { mode ->
        def pref = THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES[mode]
        if (pref) {
            prefs << [
                name:         pref.name,
                title:        pref.title,
                defaultValue: setpointAttributeDefaults[pref.name]
            ]
        }
    }
    return prefs
}

private thermostatSetpointCommandPreferenceForModes(modes) {
    def setpointCommandDefaults = [
        "setHeatingSetpointCommand": "setHeatingSetpoint",
        "setCoolingSetpointCommand": "setCoolingSetpoint"
    ]
    def prefs = []
    modes.each { mode ->
        def pref = THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES[mode]
        if (pref) {
            prefs << [
                name:         pref.name,
                title:        pref.title,
                defaultValue: setpointCommandDefaults[pref.name]
            ]
        }
    }
    return prefs
}

@SuppressWarnings('MethodSize')
private temperatureSettingControlPreferences(deviceTrait) {
    input(
        name: "${deviceTrait.name}.modes",
        title: "Supported Modes",
        type: "enum",
        options: GOOGLE_THERMOSTAT_MODES,
        multiple: true,
        required: true,
        submitOnChange: true
    )

    def supportedModes = settings."${deviceTrait.name}.modes"
    def attributePreferences = []
    def commandPreferences = []
    supportedModes.each { mode ->
        def attrPrefs
        def commandPrefs
        if (mode == "heatcool") {
            attrPrefs = thermostatSetpointAttributePreferenceForModes(["heat", "cool"])
            commandPrefs = thermostatSetpointCommandPreferenceForModes(["heat", "cool"])
        } else {
            attrPrefs = thermostatSetpointAttributePreferenceForModes([mode])
            commandPrefs = thermostatSetpointCommandPreferenceForModes([mode])
        }
        attrPrefs.each { attrPref ->
            if (attributePreferences.find { it.name == attrPref.name } == null) {
                attributePreferences << attrPref
            }
        }
        commandPrefs.each { commandPref ->
            if (commandPreferences.find { it.name == commandPref.name } == null) {
                commandPreferences << commandPref
            }
        }
    }
    def attributeCommandPairs = [attributePreferences, commandPreferences].transpose()
    attributeCommandPairs.each { attributePreference, commandPreference ->
        input(
            name: "${deviceTrait.name}.${attributePreference.name}",
            title: attributePreference.title,
            type: "text",
            required: true,
            defaultValue: attributePreference.defaultValue
        )
        input(
            name: "${deviceTrait.name}.${commandPreference.name}",
            title: commandPreference.title,
            type: "text",
            required: true,
            defaultValue: commandPreference.defaultValue
        )
    }

    if (supportedModes) {
        if ("heatcool" in supportedModes) {
            input(
                name: "${deviceTrait.name}.heatcoolBuffer",
                title: "Temperature Buffer",
                description: "The minimum offset between the heat and cool setpoints when operating in heat/cool mode",
                type: "decimal"
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
                title: "${GOOGLE_THERMOSTAT_MODES[mode]} Hubitat Mode",
                description: "The mode name used by hubitat for the ${GOOGLE_THERMOSTAT_MODES[mode]} mode",
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
        type: "decimal",
        required: settings."${deviceTrait.name}.range.max" != null,
        submitOnChange: true
    )
    input(
        name: "${deviceTrait.name}.range.max",
        title: "Maximum Set Point",
        type: "decimal",
        required: settings."${deviceTrait.name}.range.min" != null,
        submitOnChange: true
    )
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_Timer(deviceTrait) {
    section("Timer Settings") {
        input(
            name: "${deviceTrait.name}.commandOnlyTimer",
            title: "Command Only (No Query)",
            type: "bool",
            required: true,
            defaultValue: false,
            submitOnChange: true,
        )
        input(
            name: "${deviceTrait.name}.maxTimerLimitSec",
            title: "Maximum Timer Duration (seconds)",
            type: "integer",
            required: true,
            defaultValue: "86400"
        )
        if (deviceTrait.commandOnlyTimer == false) {
            input(
                name: "${deviceTrait.name}.timerRemainingSecAttribute",
                title: "Time Remaining Attribute",
                type: "text",
                required: true,
                defaultValue: "timeRemaining"
            )
            input(
                name: "${deviceTrait.name}.timerPausedAttribute",
                title: "Timer Paused Attribute",
                type: "text",
                required: true,
                defaultValue: "sessionStatus"
            )
            input(
                name: "${deviceTrait.name}.timerPausedValue",
                title: "Timer Paused Value",
                type: "text",
                required: true,
                defaultValue: "paused"
            )
        }
        input(
            name: "${deviceTrait.name}.timerStartCommand",
            title: "Timer Start Command",
            type: "text",
            required: true,
            defaultValue: "startTimer"
        )
        input(
            name: "${deviceTrait.name}.timerAdjustCommand",
            title: "Timer Adjust Command",
            type: "text",
            required: true,
            defaultValue: "setTimeRemaining"
        )
        input(
            name: "${deviceTrait.name}.timerCancelCommand",
            title: "Timer Cancel Command",
            type: "text",
            required: true,
            defaultValue: "cancel"
        )
        input(
            name: "${deviceTrait.name}.timerPauseCommand",
            title: "Timer Pause Command",
            type: "text",
            required: true,
            defaultValue: "pause"
        )
        input(
            name: "${deviceTrait.name}.timerResumeCommand",
            title: "Timer Resume Command",
            type: "text",
            required: true,
            defaultValue: "start"
        )
    }
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private deviceTraitPreferences_Volume(deviceTrait) {
    section("Volume Preferences") {
        input(
            name: "${deviceTrait.name}.volumeAttribute",
            title: "Current Volume Attribute",
            type: "text",
            required: true,
            defaultValue: "volume"
        )
        input(
            name: "${deviceTrait.name}.canSetVolume",
            title: "Use `setVolume` command (otherwise will use Volume Up/Down instead)",
            type: "bool",
            defaultValue: true,
            submitOnChange: true
        )
        if (deviceTrait.canSetVolume) {
            input(
                name: "${deviceTrait.name}.setVolumeCommand",
                title: "Set Rotation Command",
                type: "text",
                required: true,
                defaultValue: "setVolume"
            )
        } else {
            input(
                name: "${deviceTrait.name}.volumeUpCommand",
                title: "Set Increase Volume Command",
                type: "text",
                required: true,
                defaultValue: "volumeUp"
            )
            input(
                name: "${deviceTrait.name}.volumeDownCommand",
                title: "Set Decrease Volume Command",
                type: "text",
                required: true,
                defaultValue: "volumeDown"
            )
        }
        input(
            name: "${deviceTrait.name}.volumeStep",
            title: "Volume Level Step",
            type: "number",
            required: true,
            defaultValue: 1
        )
        input(
            name: "${deviceTrait.name}.canMuteUnmute",
            title: "Supports Mute And Unmute",
            type: "bool",
            defaultValue: true,
            submitOnChange: true
        )
        if (deviceTrait.canMuteUnmute) {
            input(
                name: "${deviceTrait.name}.muteAttribute",
                title: "Mute State Attribute",
                type: "text",
                required: true,
                defaultValue: "mute"
            )
            input(
                name: "${deviceTrait.name}.mutedValue",
                title: "Muted Value",
                type: "text",
                required: true,
                defaultValue: "muted"
            )
            input(
                name: "${deviceTrait.name}.unmutedValue",
                title: "Unmuted Value",
                type: "text",
                required: true,
                defaultValue: "unmuted"
            )
            input(
                name: "${deviceTrait.name}.muteCommand",
                title: "Mute Command",
                type: "text",
                required: true,
                defaultValue: "mute"
            )
            input(
                name: "${deviceTrait.name}.unmuteCommand",
                title: "Unmute Command",
                type: "text",
                required: true,
                defaultValue: "unmute"
            )
        }
    }
}

def handleAction() {
    LOGGER.debug(request.body)
    def requestType = request.JSON.inputs[0].intent
    def response
    if (requestType == "action.devices.SYNC") {
        response = handleSyncRequest(request)
    } else if (requestType == "action.devices.QUERY") {
        response = handleQueryRequest(request)
    } else if (requestType == "action.devices.EXECUTE") {
        response = handleExecuteRequest(request)
    } else if (requestType == "action.devices.DISCONNECT") {
        response = [:]
    }
    LOGGER.debug(JsonOutput.toJson(response))
    return response
}

private attributeHasExpectedValue(device, attrName, attrValue) {
    def currentValue = device.currentValue(attrName, true)
    if (attrValue instanceof Closure) {
        if (!attrValue(currentValue)) {
            LOGGER.debug("${device.name}: Expected value test returned false for " +
                         "attribute ${attrName} with value ${currentValue}")
            return false
        }
    } else if (currentValue != attrValue) {
        LOGGER.debug("${device.name}: current value of ${attrName} (${currentValue}) " +
                     "does does not yet match expected value (${attrValue})")
        return false
    }
    return true
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
        def attrsToAwait = [:].withDefault { [:] }
        def states = [:].withDefault { [:] }
        def results = [:]
        // Send appropriate commands to devices
        devices.each { device ->
            command.execution.each { execution ->
                def commandName = execution.command.split("\\.").last()
                try {
                    def (resultAttrsToAwait, resultStates) = "executeCommand_${commandName}"(device, execution)
                    attrsToAwait[device.device] += resultAttrsToAwait
                    states[device.device] += resultStates
                    results[device.device] = [
                        status: "SUCCESS"
                    ]
                } catch (Exception ex) {
                    results[device.device] = [
                        status: "ERROR"
                    ]
                    try {
                        results[device.device] << parseJson(ex.message)
                    } catch (JsonException jex) {
                        LOGGER.exception(
                            "Error executing command ${commandName} on device ${device.device.name}",
                            ex
                        )
                        results[device.device] << [
                            errorCode: "hardError"
                        ]
                    }
                }
            }
        }
        // Wait up to 1 second for all devices to settle to the desired states.
        def pollTimeoutMs = 1000
        def singlePollTimeMs = 100
        def numLoops = pollTimeoutMs / singlePollTimeMs
        LOGGER.debug("Polling device attributes for ${pollTimeoutMs} ms")
        def deviceReadyStates = [:]
        for (def i = 0; i < numLoops; ++i) {
            deviceReadyStates = attrsToAwait.collectEntries { device, attributes ->
                [device, attributes.every { attrName, attrValue ->
                    attributeHasExpectedValue(device, attrName, attrValue)
                }]
            }
            def ready = deviceReadyStates.every { device, deviceReady -> deviceReady }
            if (ready) {
                LOGGER.debug("All devices reached expected state and are ready.")
                break
            } else {
                pauseExecution(singlePollTimeMs)
            }
        }

        // Now build our response message
        devices.each { device ->
            def result = results[device.device]
            def deviceReady = deviceReadyStates[device.device]
            result.ids = [device.device.id]
            if (result.status == "SUCCESS") {
                if (!deviceReady) {
                    LOGGER.debug("Device ${device.device} not ready, moving to PENDING")
                    result.status = "PENDING"
                }
                def deviceState = [
                    online: true
                ]
                deviceState += states[device.device]
                result.states = deviceState
            }
            resp.payload.commands << result
        }
    }
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
        def globalPinCodes = deviceTypeFromSettings('GlobalPinCodes')
        if (!command.challenge?.pin) {
            throw new Exception(JsonOutput.toJson([
                errorCode: "challengeNeeded",
                challengeNeeded: [
                    type: "pinNeeded"
                ]
            ]))
        } else if (!(command.challenge.pin in deviceType.pinCodes*.value)
                   && !(command.challenge.pin in globalPinCodes.pinCodes*.value)) {
            throw new Exception(JsonOutput.toJson([
                errorCode: "challengeNeeded",
                challengeNeeded: [
                    type: "challengeFailedPinNeeded"
                ]
            ]))
        }
    }
}

@SuppressWarnings('UnusedPrivateMethod')
private controlScene(options) {
    def allDevices = allKnownDevices()
    def device = allDevices[options.deviceId].device
    device."${options.command}"()
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_ActivateScene(deviceInfo, command) {
    def sceneTrait = deviceInfo.deviceType.traits.Scene
    if (sceneTrait.name == "hubitat_mode") {
        location.mode = deviceInfo.device.name
    } else {
        if (command.params.deactivate) {
            checkMfa(deviceInfo.deviceType, "Deactivate Scene", command)
            runIn(
                0,
                "controlScene",
                [
                    data: [
                        "deviceId": deviceInfo.device.id,
                        "command": sceneTrait.deactivateCommand
                    ]
                ]
            )
        } else {
            checkMfa(deviceInfo.deviceType, "Activate Scene", command)
            runIn(
                0,
                "controlScene",
                [
                    data: [
                        "deviceId": deviceInfo.device.id,
                        "command": sceneTrait.activateCommand
                    ]
                ]
            )
        }
    }
    return [[:], [:]]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_ArmDisarm(deviceInfo, command) {
    def armDisarmTrait = deviceInfo.deviceType.traits.ArmDisarm
    def checkValue = "${armDisarmTrait.armValues["disarmed"]}"
    // if the user canceled arming, issue the cancel command
    if (command.params.cancel == true) {
        checkMfa(deviceInfo.deviceType, "Cancel", command)
        deviceInfo.device."${armDisarmTrait.cancelCommand}"()
    } else {
        // if Google returns arm=false, that indicates disarm, otherwise execute the matching alarm level command
        if (command.params.arm == false) {
            // Google sent back disarm
            checkMfa(deviceInfo.deviceType, "Disarm", command)
            deviceInfo.device."${armDisarmTrait.armCommands["disarmed"]}"()
        } else {
            // Google sent back an alarm level
            checkMfa(deviceInfo.deviceType, "${armDisarmTrait.armLevels[command.params.armLevel]}", command)
            checkValue = "${armDisarmTrait.armValues[command.params.armLevel]}"
            deviceInfo.device."${armDisarmTrait.armCommands[command.params.armLevel]}"()
        }
    }
    return [
        [
            (armDisarmTrait.armedAttribute): checkValue,
        ],
        [
            isArmed: command.params.arm,
            armLevel: command.params.armLevel,
            exitAllowance: armDisarmTrait.exitAllowanceAttribute,
        ],
    ]
}
@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_BrightnessAbsolute(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Brightness", command)
    def brightnessTrait = deviceInfo.deviceType.traits.Brightness
    def brightnessToSet = googlePercentageToHubitat(command.params.brightness)
    deviceInfo.device."${brightnessTrait.setBrightnessCommand}"(brightnessToSet)
    def checkValue = { value ->
        if (brightnessToSet == 100) {
            // Handle Z-Wave dimmers which only go to 99.
            return value >= 99
        }
        return value == brightnessToSet
    }
    return [
        [
            (brightnessTrait.brightnessAttribute): checkValue,
        ],
        [
            brightness: command.params.brightness,
        ],
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private executeCommand_GetCameraStream(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Display", command)
    def cameraStreamTrait = deviceInfo.deviceType.traits.CameraStream
    def supportedStreamProtocols = command.params.SupportedStreamProtocols

    deviceInfo.device."${cameraStreamTrait.cameraStreamCommand}"(supportedStreamProtocols)
    return [
        [:],
        [
            cameraStreamAccessUrl:
                deviceInfo.device.currentValue(deviceInfo.deviceType.traits.CameraStream.cameraStreamURLAttribute),
            cameraStreamProtocol:
                deviceInfo.device.currentValue(deviceInfo.deviceType.traits.CameraStream.cameraStreamProtocolAttribute),
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_ColorAbsolute(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Color", command)
    def colorTrait = deviceInfo.deviceType.traits.ColorSetting

    def checkAttrs = [:]
    def states = [color: command.params.color]
    if (command.params.color.temperature) {
        def temperature = command.params.color.temperature
        deviceInfo.device."${colorTrait.setColorTemperatureCommand}"(temperature)
        checkAttrs << [
            (colorTrait.colorTemperatureAttribute): temperature
        ]
        if (colorTrait.fullSpectrum && colorTrait.colorTemperature) {
            checkAttrs << [
                (colorTrait.colorModeAttribute): colorTrait.temperatureModeValue
            ]
        }
    } else if (command.params.color.spectrumHSV) {
        def hsv = command.params.color.spectrumHSV
        // Google sends hue in degrees (0...360), but hubitat wants it in the range 0...100
        def hue = Math.round(hsv.hue * 100 / 360)
        // Google sends saturation and value as floats in the range 0...1,
        // but Hubitat wants them in the range 0...100
        def saturation = Math.round(hsv.saturation * 100)
        def value = Math.round(hsv.value * 100)

        deviceInfo.device."${colorTrait.setColorCommand}"([
            hue:        hue,
            saturation: saturation,
            level:      value
        ])
        checkAttrs << [
            (colorTrait.hueAttribute):        hue,
            (colorTrait.saturationAttribute): saturation,
            (colorTrait.levelAttribute):      value
        ]
        if (colorTrait.fullSpectrum && colorTrait.colorTemperature) {
            checkAttrs << [
                (colorTrait.colorModeAttribute): colorTrait.fullSpectrumModeValue
            ]
        }
    }
    return [checkAttrs, states]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_Dock(deviceInfo, command) {
    def dockTrait = deviceInfo.deviceType.traits.Dock
    def checkValue
    checkMfa(deviceInfo.deviceType, "Dock", command)
    checkValue = dockTrait.dockValue
    deviceInfo.device."${dockTrait.dockCommand}"()
    return [
        [
            (dockTrait.dockAttribute): checkValue,
        ],
        [
            isDocked: true,
        ]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_Charge(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Charge", command)
    def energyStorageTrait = deviceInfo.deviceType.traits.EnergyStorage
    deviceInfo.device."${energyStorageTrait.chargeCommand}"()
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_Reverse(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Reverse", command)
    def fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    deviceInfo.device."${fanSpeedTrait.reverseCommand}"()
    return [[:], [:]]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private executeCommand_Locate(deviceInfo, command) {
    def locatorTrait = deviceInfo.deviceType.traits.Locator
    checkMfa(deviceInfo.deviceType, "Locate", command)
    deviceInfo.device."${locatorTrait.locatorCommand}"()
    return [[:], [:]]
}

@SuppressWarnings('UnusedPrivateMethod')
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
        [
            (lockUnlockTrait.lockedUnlockedAttribute): checkValue,
        ],
        [
            isJammed: false,
            isLocked: command.params.lock,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_mute(deviceInfo, command) {
    def volumeTrait = deviceInfo.deviceType.traits.Volume
    def checkValue
    if (command.params.mute) {
        checkMfa(deviceInfo.deviceType, "Mute", command)
        deviceInfo.device."${volumeTrait.muteCommand}"()
        checkValue = volumeTrait.mutedValue
    } else {
        checkMfa(deviceInfo.deviceType, "Unmute", command)
        deviceInfo.device."${volumeTrait.unmuteCommand}"()
        checkValue = volumeTrait.unmutedValue
    }

    return [
        [
            (volumeTrait.muteAttribute): checkValue,
        ],
        [
            isMuted: command.params.mute,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
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
        [
            (onOffTrait.onOffAttribute): checkValue,
        ],
        [
            on: command.params.on,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_OpenClose(deviceInfo, command) {
    def openCloseTrait = deviceInfo.deviceType.traits.OpenClose
    def openPercent = googlePercentageToHubitat(command.params.openPercent)
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
        def hubitatOpenPercent = openPercent
        if (openCloseTrait.reverseDirection) {
            hubitatOpenPercent = 100 - openPercent
        }
        deviceInfo.device."${openCloseTrait.openPositionCommand}"(hubitatOpenPercent)
        checkValue = { value ->
            if (hubitatOpenPercent == 100) {
                // Handle Z-Wave shades which only go to 99.
                return value >= 99
            }
            return value == hubitatOpenPercent
        }
    }
    return [
        [
            (openCloseTrait.openCloseAttribute): checkValue,
        ],
        [
            openPercent: openPercent,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_Reboot(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Reboot", command)
    def rebootTrait = deviceInfo.deviceType.traits.Reboot
    deviceInfo.device."${rebootTrait.rebootCommand}"()
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_RotateAbsolute(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Rotate", command)
    def rotationTrait = deviceInfo.deviceType.traits.Rotation
    def position = command.params.rotationPercent

    deviceInfo.device."${rotationTrait.setRotationCommand}"(position)
    return [
        [
            (rotationTrait.rotationAttribute): position,
        ],
        [
            rotationPercent: command.params.rotationPercent,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_SetFanSpeed(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Fan Speed", command)
    def fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    def fanSpeed = command.params.fanSpeed

    deviceInfo.device."${fanSpeedTrait.setFanSpeedCommand}"(fanSpeed)
    return [
        [
            (fanSpeedTrait.currentSpeedAttribute): fanSpeed,
        ],
        [
            currentFanSpeedSetting: fanSpeed,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_SetHumidity(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Humidity", command)
    def humiditySettingTrait = deviceInfo.deviceType.traits.HumiditySetting
    def humiditySetpoint = command.params.humidity

    deviceInfo.device."${humiditySettingTrait.setHumidityCommand}"(humiditySetpoint)
    return [
        [
            (humiditySettingTrait.humiditySetpointAttribute): humiditySetpoint
        ],
        [
            humiditySetpointPercent: humiditySetpoint,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_SetTemperature(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Temperature", command)
    def temperatureControlTrait = deviceInfo.deviceType.traits.TemperatureControl
    def setpoint = command.params.temperature
    if (temperatureControlTrait.temperatureUnit == "F") {
        setpoint = celsiusToFahrenheitRounded(setpoint)
    }
    deviceInfo.device."${temperatureControlTrait.setTemperatureCommand}"(setpoint)

    return [
        [
            (temperatureControlTrait.setpointAttribute): setpoint,
        ],
        [
            temperatureSetpointCelcius: setpoint,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_SetToggles(deviceInfo, command) {
    def togglesTrait = deviceInfo.deviceType.traits.Toggles
    def togglesToSet = command.params.updateToggleSettings

    def attrsToCheck = [:]
    def states = [currentToggleSettings: togglesToSet]
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
        def onOffResponse = executeCommand_OnOff(fakeDeviceInfo, [params: [on: toggleValue]])
        attrsToCheck << onOffResponse[0]
        states << onOffResponse[1]
    }
    return [attrsToCheck, states]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_setVolume(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Volume", command)
    def volumeTrait = deviceInfo.deviceType.traits.Volume
    def volumeLevel = command.params.volumeLevel
    deviceInfo.device."${volumeTrait.setVolumeCommand}"(volumeLevel)
    states = [currentVolume: volumeLevel]
    if (deviceInfo.deviceType.traits.canMuteUnmute) {
        states.isMuted = deviceInfo.device.currentValue(volumeTrait.muteAttribute) == volumeTrait.mutedValue
    }
    return [
        [
            (volumeTrait.volumeAttribute): volumeLevel,
        ],
        states,
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_SoftwareUpdate(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Software Update", command)
    def softwareUpdateTrait = deviceInfo.deviceType.traits.SoftwareUpdate
    deviceInfo.device."${softwareUpdateTrait.softwareUpdateCommand}"()
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_StartStop(deviceInfo, command) {
    def startStopTrait = deviceInfo.deviceType.traits.StartStop
    def checkValue
    if (command.params.start) {
        checkMfa(deviceInfo.deviceType, "Start", command)
        checkValue = startStopTrait.startValue
        deviceInfo.device."${startStopTrait.startCommand}"()
    } else {
        checkMfa(deviceInfo.deviceType, "Stop", command)
        checkValue = { it != startStopTrait.startValue }
        deviceInfo.device."${startStopTrait.stopCommand}"()
    }
    return [
        [
            (startStopTrait.startStopAttribute): checkValue,
        ],
        [
            isRunning: command.params.start,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_PauseUnpause(deviceInfo, command) {
    def startStopTrait = deviceInfo.deviceType.traits.StartStop
    def checkValue
    if (command.params.pause) {
        checkMfa(deviceInfo.deviceType, "Pause", command)
        deviceInfo.device."${startStopTrait.pauseCommand}"()
        checkValue = startStopTrait.pauseValue
    }
    return [
        [
            (startStopTrait.pauseUnPauseAttribute): checkValue,
        ],
        [
            isPaused: command.params.pause,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_ThermostatTemperatureSetpoint(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Setpoint", command)
    def temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def setpoint = command.params.thermostatTemperatureSetpoint
    if (temperatureSettingTrait.temperatureUnit == "F") {
        setpoint = celsiusToFahrenheitRounded(setpoint)
    }

    def hubitatMode = deviceInfo.device.currentValue(temperatureSettingTrait.currentModeAttribute)
    def googleMode = temperatureSettingTrait.hubitatToGoogleModeMap[hubitatMode]
    def setSetpointCommand = temperatureSettingTrait.modeSetSetpointCommands[googleMode]
    deviceInfo.device."${setSetpointCommand}"(setpoint)

    return [
        [
            (temperatureSettingTrait.modeSetpointAttributes[googleMode]): setpoint,
        ],
        [
            thermostatTemperatureSetpoint: setpoint,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_ThermostatTemperatureSetRange(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Setpoint", command)
    def temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def coolSetpoint = command.params.thermostatTemperatureSetpointHigh
    def heatSetpoint = command.params.thermostatTemperatureSetpointLow
    if (temperatureSettingTrait.temperatureUnit == "F") {
        coolSetpoint = celsiusToFahrenheitRounded(coolSetpoint)
        heatSetpoint = celsiusToFahrenheitRounded(heatSetpoint)
    }
    def setRangeCommands = temperatureSettingTrait.modeSetSetpointCommands["heatcool"]
    deviceInfo.device."${setRangeCommands.setCoolingSetpointCommand}"(coolSetpoint)
    deviceInfo.device."${setRangeCommands.setHeatingSetpointCommand}"(heatSetpoint)

    def setRangeAttributes = temperatureSettingTrait.modeSetpointAttributes["heatcool"]
    return [
        [
            (setRangeAttributes.coolingSetpointAttribute): coolSetpoint,
            (setRangeAttributes.heatingSetpointAttribute): heatSetpoint,
        ],
        [
            thermostatTemperatureSetpointHigh: coolSetpoint,
            thermostatTemperatureSetpointLow: heatSetpoint,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_ThermostatSetMode(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Mode", command)
    def temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def googleMode = command.params.thermostatMode
    def hubitatMode = temperatureSettingTrait.googleToHubitatModeMap[googleMode]
    deviceInfo.device."${temperatureSettingTrait.setModeCommand}"(hubitatMode)

    return [
        [
            (temperatureSettingTrait.currentModeAttribute): hubitatMode,
        ],
        [
            thermostatMode: googleMode,
        ],
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_TimerAdjust(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Adjust", command)
    def timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerAdjustCommand}"()
    def retVal = [:]
    if (!deviceTrait.commandOnlyTimer) {
        retVal = [
            timerTimeSec: device.currentValue(timerTrait.timerRemainingSecAttribute)
        ]
    }
    return retVal
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_TimerCancel(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Cancel", command)
    def timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerCancelCommand}"()
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_TimerPause(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Pause", command)
    def timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerPauseCommand}"()
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_TimerResume(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Resume", command)
    def timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerResumeCommand}"()
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_TimerStart(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Start", command)
    def timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerStartCommand}"()
    def retVal = [:]
    if (!deviceTrait.commandOnlyTimer) {
        retVal = [
            timerTimeSec: device.currentValue(timerTrait.timerRemainingSecAttribute)
        ]
    }
    return retVal
}

@SuppressWarnings('UnusedPrivateMethod')
private executeCommand_volumeRelative(deviceInfo, command) {
    checkMfa(deviceInfo.deviceType, "Set Volume", command)
    def volumeTrait = deviceInfo.deviceType.traits.Volume
    def volumeChange = command.params.relativeSteps
    def device = deviceInfo.device

    def newVolume

    if (volumeTrait.canSetVolume) {
        def currentVolume = device.currentValue(volumeTrait.volumeAttribute)
        // volumeChange will be negative when decreasing volume
        newVolume = currentVolume + volumeChange
        device."${volumeTrait.setVolumeCommand}"(newVolume)
    } else {
        def volumeChangeCommand = volumeTrait.volumeUpCommand
        if (volumeChange < 0) {
            volumeChangeCommand = volumeTrait.volumeDownCommand
        }

        device."${volumeChangeCommand}"()
        for (int i = 1; i < Math.abs(volumeChange); i++) {
            pauseExecution(100)
            device."${volumeChangeCommand}"()
        }

        newVolume = device.currentValue(volumeTrait.volumeAttribute)
    }

    states = [currentVolume: newVolume]
    if (volumeTrait.canMuteUnmute) {
        states.isMuted = deviceInfo.device.currentValue(volumeTrait.muteAttribute) == volumeTrait.mutedValue
    }

    return [
        [
            (volumeTrait.volumeAttribute): newVolume,
        ],
        states,
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
        if (deviceInfo != null) {
            deviceInfo.deviceType.traits.each { traitType, deviceTrait ->
                deviceState += "deviceStateForTrait_${traitType}"(deviceTrait, deviceInfo.device)
            }
        } else {
            LOGGER.warn("Requested device ${requestedDevice.name} not found.")
        }
        resp.payload.devices."${requestedDevice.id}" = deviceState
    }
    return resp
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_ArmDisarm(deviceTrait, device) {
    def isArmed = device.currentValue(deviceTrait.armedAttribute) != deviceTrait.disarmedValue
    return [
        isArmed: isArmed,
        currentArmLevel: device.currentValue(deviceTrait.armLevelAttribute),
        exitAllowance: device.currentValue(deviceTrait.exitAllowanceAttribute),
    ]
}
@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_Brightness(deviceTrait, device) {
    def brightness = hubitatPercentageToGoogle(device.currentValue(deviceTrait.brightnessAttribute))
    return [
        brightness: brightness,
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private deviceStateForTrait_CameraStream(deviceTrait, device) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_ColorSetting(deviceTrait, device) {
    def colorMode
    if (deviceTrait.fullSpectrum && deviceTrait.colorTemperature) {
        if (device.currentValue(deviceTrait.colorModeAttribute) == deviceTrait.fullSpectrumModeValue) {
            colorMode = "spectrum"
        } else {
            colorMode = "temperature"
        }
    } else if (deviceTrait.fullSpectrum) {
        colorMode = "spectrum"
    } else {
        colorMode = "temperature"
    }

    def deviceState = [
        color: [:]
    ]

    if (colorMode == "spectrum") {
        def hue = device.currentValue(deviceTrait.hueAttribute)
        def saturation = device.currentValue(deviceTrait.saturationAttribute)
        def value = device.currentValue(deviceTrait.levelAttribute)

        // Hubitat reports hue in the range 0...100, but Google wants it in degrees (0...360)
        hue = Math.round(hue * 360 / 100)
        // Hubitat reports saturation and value in the range 0...100 but
        // Google wants them as floats in the range 0...1
        saturation = saturation / 100
        value = value / 100

        deviceState.color = [
            spectrumHsv: [
                hue: hue,
                saturation: saturation,
                value: value
            ]
        ]
    } else {
        deviceState.color = [
            temperatureK: device.currentValue(deviceTrait.colorTemperatureAttribute)
        ]
    }

    return deviceState
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_Dock(deviceTrait, device) {
    def isDocked = device.currentValue(deviceTrait.dockAttribute) == deviceTrait.dockValue
    return [
        isDocked:isDocked
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_EnergyStorage(deviceTrait, device) {
    def deviceState = [:]
    if (deviceTrait.descriptiveCapacityRemainingAttribute != null) {
        deviceState.descriptiveCapacityRemaining =
            device.currentValue(deviceTrait.descriptiveCapacityRemainingAttribute)
    }
    deviceState.capacityRemaining = [
        [
            rawValue: device.currentValue(deviceTrait.capacityRemainingRawValue).toInteger(),
            unit:     deviceTrait.capacityRemainingUnit,
        ]
    ]
    if (deviceTrait.isRechargeable) {
        if (deviceTrait.capacityUntilFullRawValue != null) {
            deviceState.capacityUntilFull = [
                [
                    rawValue: device.currentValue(deviceTrait.capacityUntilFullRawValue).toInteger(),
                    unit:     deviceTrait.capacityUntilFullUnit,
                ]
            ]
        }
        if (deviceTrait.chargingValue != null) {
            deviceState.isCharging =
                device.currentValue(deviceTrait.isChargingAttribute) == deviceTrait.chargingValue
        }
        if (deviceTrait.pluggedInValue != null) {
            deviceState.isPluggedIn =
                device.currentValue(deviceTrait.isPluggedInAttribute) == deviceTrait.pluggedInValue
        }
    }

    return deviceState
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_FanSpeed(deviceTrait, device) {
    def currentSpeed = device.currentValue(deviceTrait.currentSpeedAttribute)

    return [
        currentFanSpeedSetting: currentSpeed
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_HumiditySetting(deviceTrait, device) {
    def deviceState = [
        humidityAmbientPercent: Math.round(device.currentValue(deviceTrait.humidityAttribute))
    ]
    if (!deviceTrait.queryOnly) {
        deviceState.humiditySetpointPercent = Math.round(device.currentValue(deviceTrait.humiditySetpointAttribute))
    }
    return deviceState
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private deviceStateForTrait_Locator(deviceTrait, device) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_LockUnlock(deviceTrait, device) {
    def isLocked = device.currentValue(deviceTrait.lockedUnlockedAttribute) == deviceTrait.lockedValue
    return [
        isLocked: isLocked,
        isJammed: false
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_MediaState(deviceTrait, device) {
    return [
        activityState: device.currentValue(deviceTrait.activityStateAttribute),
        playbackState: device.currentValue(deviceTrait.playbackStateAttribute)
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
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
        openPercent = hubitatPercentageToGoogle(device.currentValue(deviceTrait.openCloseAttribute))
        if (deviceTrait.reverseDirection) {
            openPercent = 100 - openPercent
        }
    }
    return [
        openPercent: openPercent
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private deviceStateForTrait_Reboot(deviceTrait, device) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_Rotation(deviceTrait, device) {
    return [
        rotationPercent: device.currentValue(deviceTrait.rotationAttribute)
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private deviceStateForTrait_Scene(deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private deviceStateForTrait_SoftwareUpdate(deviceTrait, device) {
    return [
        lastSoftwareUpdateUnixTimestampSec:
            device.currentValue(deviceTrait.lastSoftwareUpdateUnixTimestampSecAttribute).toInteger()
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_StartStop(deviceTrait, device) {
    def deviceState = [
        isRunning: device.currentValue(deviceTrait.startStopAttribute) == deviceTrait.startValue
    ]
    if (deviceTrait.canPause) {
        deviceState.isPaused = device.currentValue(deviceTrait.pauseUnPauseAttribute) == deviceTrait.pauseValue
    }
    return deviceState
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_TemperatureControl(deviceTrait, device) {
    def currentTemperature = device.currentValue(deviceTrait.currentTemperatureAttribute)
    if (deviceTrait.temperatureUnit == "F") {
        currentTemperature = fahrenheitToCelsiusRounded(currentTemperature)
    }
    def state = [
        temperatureAmbientCelsius: currentTemperature
    ]

    if (deviceTrait.queryOnly) {
        state.temperatureSetpointCelsius = currentTemperature
    } else {
        def setpoint = device.currentValue(deviceTrait.currentSetpointAttribute)
        if (deviceTrait.temperatureUnit == "F") {
            setpoint = fahrenheitToCelsiusRounded(setpoint)
        }
        state.temperatureSetpointCelsius = setpoint
    }

    return state
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_TemperatureSetting(deviceTrait, device) {
    def state = [:]

    def currentTemperature = device.currentValue(deviceTrait.currentTemperatureAttribute)
    if (deviceTrait.temperatureUnit == "F") {
        currentTemperature = fahrenheitToCelsiusRounded(currentTemperature)
    }
    state.thermostatTemperatureAmbient = currentTemperature

    if (deviceTrait.queryOnly) {
        state.thermostatMode = "on"
        state.thermostatTemperatureSetpoint = currentTemperature
    } else {
        def hubitatMode = device.currentValue(deviceTrait.currentModeAttribute)
        def googleMode = deviceTrait.hubitatToGoogleModeMap[hubitatMode]
        state.thermostatMode = googleMode

        if (googleMode == "heatcool") {
            def heatingSetpointAttr = deviceTrait.modeSetpointAttributes[googleMode].heatingSetpointAttribute
            def coolingSetpointAttr = deviceTrait.modeSetpointAttributes[googleMode].coolingSetpointAttribute
            def heatSetpoint = device.currentValue(heatingSetpointAttr)
            def coolSetpoint = device.currentValue(coolingSetpointAttr)
            if (deviceTrait.temperatureUnit == "F") {
                heatSetpoint = fahrenheitToCelsiusRounded(heatSetpoint)
                coolSetpoint = fahrenheitToCelsiusRounded(coolSetpoint)
            }
            state.thermostatTemperatureSetpointHigh = coolSetpoint
            state.thermostatTemperatureSetpointLow = heatSetpoint
        } else {
            def setpointAttr = deviceTrait.modeSetpointAttributes[googleMode]
            if (setpointAttr) {
                def setpoint = device.currentValue(setpointAttr)
                if (deviceTrait.temperatureUnit == "F") {
                    setpoint = fahrenheitToCelsiusRounded(setpoint)
                }
                state.thermostatTemperatureSetpoint = setpoint
            }
        }
    }
    return state
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_Timer(deviceTrait, device) {
    def deviceState = [:]
    if (deviceTrait.commandOnlyTimer) {
        // report no running timers
        deviceState = [
            timerRemainingSec: -1
        ]
    } else {
        deviceState = [
            timerRemainingSec: device.currentValue(deviceTrait.timerRemainingSecAttribute),
            timerPaused: device.currentValue(deviceTrait.timerPausedAttribute) == deviceTrait.timerPausedValue
        ]
    }
    return deviceState
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_Toggles(deviceTrait, device) {
    return [
        currentToggleSettings: deviceTrait.toggles.collectEntries { toggle ->
            [toggle.name, deviceStateForTrait_OnOff(toggle, device).on]
        }
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private deviceStateForTrait_Volume(deviceTrait, device) {
    def deviceState = [
        currentVolume: device.currentValue(deviceTrait.volumeAttribute)
    ]
    if (deviceTrait.canMuteUnmute) {
        deviceState.isMuted = device.currentValue(deviceTrait.muteAttribute) == deviceTrait.mutedValue
    }
    return deviceState
}

private handleSyncRequest(request) {
    def rooms = this.rooms?.collectEntries { [(it.id): it] } ?: [:]

    def resp = [
        requestId: request.JSON.requestId,
        payload: [
            devices: []
        ]
    ]

    def deviceIdsEncountered = [] as Set
    (deviceTypes() + [modeSceneDeviceType()]).each { deviceType ->
        def traits = deviceType.traits.collect { traitType, deviceTrait ->
            "action.devices.traits.${traitType}"
        }
        def attributes = [:]
        deviceType.traits.each { traitType, deviceTrait ->
            attributes += "attributesForTrait_${traitType}"(deviceTrait)
        }
        deviceType.devices.each { device ->
            def deviceName = device.label ?: device.name
            if (deviceIdsEncountered.contains(device.id)) {
                LOGGER.warn(
                    "The device ${deviceName} with ID ${device.id} is selected as multiple device types. " +
                    "Ignoring configuration from the device type ${deviceType.display}!"
                )
            } else {
                def roomName = null
                try {
                    def roomId = device.device?.roomId
                    roomName = rooms[roomId]?.name
                } catch (MissingPropertyException) {
                    // The roomId property isn't defined prior to Hubitat 2.2.7,
                    // so ignore the error; we just can't report a room on this
                    // version
                }
                deviceIdsEncountered.add(device.id)
                resp.payload.devices << [
                    id: device.id,
                    type: "action.devices.types.${deviceType.googleDeviceType}",
                    traits: traits,
                    name: [
                        defaultNames: [device.name],
                        name: device.label ?: device.name
                    ],
                    willReportState: false,
                    attributes: attributes,
                    roomHint: roomName,
                ]
            }
        }
    }

    return resp
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_ArmDisarm(deviceTrait) {
    def armDisarmAttrs = [
        availableArmLevels: [
            levels: deviceTrait.armLevels.collect { hubitatLevelName, googleLevelNames ->
                def levels = googleLevelNames.split(",")
                [
                    level_name: hubitatLevelName,
                    level_values: [
                        [
                            level_synonym: levels,
                            lang: "en"
                        ]
                    ]
                ]
            },
            ordered: true
        ],
    ]
    return armDisarmAttrs
}
@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_Brightness(deviceTrait) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_CameraStream(deviceTrait) {
    return [
        cameraStreamSupportedProtocols: ["progressive_mp4", "hls", "dash", "smooth_stream"],
        cameraStreamNeedAuthToken:      false,
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_ColorSetting(deviceTrait) {
    def colorAttrs = [:]
    if (deviceTrait.fullSpectrum) {
        colorAttrs << [
            colorModel: "hsv"
        ]
    }
    if (deviceTrait.colorTemperature) {
        colorAttrs << [
            colorTemperatureRange: [
                temperatureMinK: deviceTrait.colorTemperatureMin,
                temperatureMaxK: deviceTrait.colorTemperatureMax
            ]
        ]
    }
    return colorAttrs
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_Dock(deviceTrait) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_EnergyStorage(deviceTrait) {
    return [
        queryOnlyEnergyStorage:         deviceTrait.queryOnlyEnergyStorage,
        energyStorageDistanceUnitForUX: deviceTrait.energyStorageDistanceUnitForUX,
        isRechargeable:                 deviceTrait.isRechargeable
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
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
        reversible: deviceTrait.reversible,
        supportsFanSpeedPercent: false,
        commandOnlyFanSpeed: false
    ]
    return fanSpeedAttrs
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_HumiditySetting(deviceTrait) {
    def attrs = [
        queryOnlyHumiditySetting: deviceTrait.queryOnly
    ]
    if (deviceTrait.humidityRange) {
        attrs.humiditySetpointRange = [
            minPercent: deviceTrait.humidityRange.min,
            maxPercent: deviceTrait.humidityRange.max
        ]
    }
    return attrs
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_Locator(deviceTrait) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_LockUnlock(deviceTrait) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_MediaState(deviceTrait) {
    return [
        supportActivityState: deviceTrait.supportActivityState,
        supportPlaybackState: deviceTrait.supportPlaybackState
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_OnOff(deviceTrait) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_OpenClose(deviceTrait) {
    return [
        discreteOnlyOpenClose: deviceTrait.discreteOnlyOpenClose,
        queryOnlyOpenClose: deviceTrait.queryOnly
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_Reboot(deviceTrait) {
    return [:]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_Rotation(deviceTrait) {
    return [
        supportsContinuousRotation: deviceTrait.continuousRotation,
        supportsPercent:            true,
        supportsDegrees:            false
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_Scene(deviceTrait) {
    return [
        sceneReversible: deviceTrait.sceneReversible
    ]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_SoftwareUpdate(deviceTrait) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod', 'UnusedPrivateMethodParameter'])
private attributesForTrait_StartStop(deviceTrait) {
    return [
        pausable: deviceTrait.canPause
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_TemperatureControl(deviceTrait) {
    def attrs = [
        temperatureUnitForUX:        deviceTrait.temperatureUnit,
        queryOnlyTemperatureControl: deviceTrait.queryOnly
    ]

    if (!deviceTrait.queryOnly) {
        if (deviceTrait.temperatureUnit == "C") {
            attrs.temperatureRange = [
                minThresholdCelsius: deviceTrait.minTemperature,
                maxThresholdCelsius: deviceTrait.maxTemperature
            ]
        } else {
            attrs.temperatureRange = [
                minThresholdCelsius: fahrenheitToCelsiusRounded(deviceTrait.minTemperature),
                maxThresholdCelsius: fahrenheitToCelsiusRounded(deviceTrait.maxTemperature)
            ]
        }

        if (deviceTrait.temperatureStep) {
            if (deviceTrait.temperatureUnit == "C") {
                attrs.temperatureStepCelsius = deviceTrait.temperatureStep
            } else {
                // 5/9 is the scale factor for converting from F to C
                attrs.temperatureStepCelsius = deviceTrait.temperatureStep * (5 / 9)
            }
        }
    }

    return attrs
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_TemperatureSetting(deviceTrait) {
    def attrs = [
        thermostatTemperatureUnit:   deviceTrait.temperatureUnit,
        queryOnlyTemperatureSetting: deviceTrait.queryOnly
    ]

    if (!deviceTrait.queryOnly) {
        attrs.availableThermostatModes = deviceTrait.modes

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
                buffer = buffer * (5 / 9)  // 5/9 is the scale factor for converting from F to C
            }
            attrs.bufferRangeCelsius = buffer
        }
    }
    return attrs
}

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_Timer(deviceTrait) {
    return [
        maxTimerLimitSec:    deviceTrait.maxTimerLimitSec,
        commandOnlyTimer:    deviceTrait.commandOnlyTimer,
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private attributesForTrait_Volume(deviceTrait) {
    return [
        volumeMaxLevel:         100,
        volumeCanMuteAndUnmute: deviceTrait.canMuteUnmute,
        levelStepSize:          deviceTrait.volumeStep
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_ArmDisarm(traitName) {
    def armDisarmMapping = [
        armedAttribute:                 settings."${traitName}.armedAttribute",
        armLevelAttribute:              settings."${traitName}.armLevelAttribute",
        exitAllowanceAttribute:         settings."${traitName}.exitAllowanceAttribute",
        disarmedValue:                  settings."${traitName}.disarmedValue",
        cancelCommand:                  settings."${traitName}.cancelCommand",
        armLevels:                      [:],
        armCommands:                    [:],
        armValues:                      [:],
        commands:                       ["Cancel", "Disarm", "Arm Home", "Arm Night", "Arm Away"]
    ]
    settings."${traitName}.armLevels"?.each { armLevel ->
        armDisarmMapping.armLevels[armLevel] = settings."${traitName}.armLevels.${armLevel}.googleNames"
        armDisarmMapping.armCommands[armLevel] = settings."${traitName}.armCommands.${armLevel}.commandName"
        armDisarmMapping.armValues[armLevel] = settings."${traitName}.armValues.${armLevel}.value"
    }
    return armDisarmMapping
}
@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Brightness(traitName) {
    return [
        brightnessAttribute:  settings."${traitName}.brightnessAttribute",
        setBrightnessCommand: settings."${traitName}.setBrightnessCommand",
        commands:             ["Set Brightness"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_CameraStream(traitName) {
    return [
        cameraStreamURLAttribute:       settings."${traitName}.cameraStreamURLAttribute",
        cameraStreamProtocolAttribute:  settings."${traitName}.cameraStreamProtocolAttribute",
        cameraStreamCommand:            settings."${traitName}.cameraStreamCommand",
        commands:                       ["Display"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_ColorSetting(traitName) {
    def deviceTrait = [
        fullSpectrum:     settings."${traitName}.fullSpectrum",
        colorTemperature: settings."${traitName}.colorTemperature",
        commands:         ["Set Color"]
    ]

    if (deviceTrait.fullSpectrum) {
        deviceTrait << [
            hueAttribute:        settings."${traitName}.hueAttribute",
            saturationAttribute: settings."${traitName}.saturationAttribute",
            levelAttribute:      settings."${traitName}.levelAttribute",
            setColorCommand:     settings."${traitName}.setColorCommand"
        ]
    }
    if (deviceTrait.colorTemperature) {
        deviceTrait << [
            colorTemperatureMin:        settings."${traitName}.colorTemperature.min",
            colorTemperatureMax:        settings."${traitName}.colorTemperature.max",
            colorTemperatureAttribute:  settings."${traitName}.colorTemperatureAttribute",
            setColorTemperatureCommand: settings."${traitName}.setColorTemperatureCommand"
        ]
    }
    if (deviceTrait.fullSpectrum && deviceTrait.colorTemperature) {
        deviceTrait << [
            colorModeAttribute:    settings."${traitName}.colorModeAttribute",
            fullSpectrumModeValue: settings."${traitName}.fullSpectrumModeValue",
            temperatureModeValue:  settings."${traitName}.temperatureModeValue"
        ]
    }

    return deviceTrait
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Dock(traitName) {
    return [
        dockAttribute: settings."${traitName}.dockAttribute",
        dockValue:     settings."${traitName}.dockValue",
        dockCommand:   settings."${traitName}.dockCommand",
        commands:      ["Dock"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_EnergyStorage(traitName) {
    def energyStorageTrait = [
        energyStorageDistanceUnitForUX:           settings."${traitName}.energyStorageDistanceUnitForUX",
        isRechargeable:                           settings."${traitName}.isRechargeable",
        queryOnlyEnergyStorage:                   settings."${traitName}.queryOnlyEnergyStorage",
        descriptiveCapacityRemainingAttribute:    settings."${traitName}.descriptiveCapacityRemainingAttribute",
        capacityRemainingRawValue:                settings."${traitName}.capacityRemainingRawValue",
        capacityRemainingUnit:                    settings."${traitName}.capacityRemainingUnit",
        capacityUntilFullRawValue:                settings."${traitName}.capacityUntilFullRawValue",
        capacityUntilFullUnit:                    settings."${traitName}.capacityUntilFullUnit",
        isChargingAttribute:                      settings."${traitName}.isChargingAttribute",
        chargingValue:                            settings."${traitName}.chargingValue",
        isPluggedInAttribute:                     settings."${traitName}.isPluggedInAttribute",
        pluggedInValue:                           settings."${traitName}.pluggedInValue",
        chargeCommand:                            settings."${traitName}.chargeCommand",
        commands:                                 []
    ]
    if (!energyStorageTrait.queryOnlyEnergyStorage) {
        energyStorageTrait.commands += ["Charge"]
    }

    return energyStorageTrait
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_HumiditySetting(traitName) {
    def humidityTrait = [
        humidityAttribute: settings."${traitName}.humidityAttribute",
        queryOnly:         settings."${traitName}.queryOnly",
        commands:          []
    ]
    if (!humidityTrait.queryOnly) {
        humidityTrait << [
            humiditySetpointAttribute: settings."${traitName}.humiditySetpointAttribute",
            setHumidityCommand:        settings."${traitName}.setHumidityCommand"
        ]
        humidityTrait.commands << "Set Humidity"

        def humidityRange = [
            min: settings."${traitName}.humidityRange.min",
            max: settings."${traitName}.humidityRange.max"
        ]
        if (humidityRange.min != null || humidityRange.max != null) {
            humidityTrait << [
                humidityRange: humidityRange
            ]
        }
    }
    return humidityTrait
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Locator(traitName) {
    return [
        locatorCommand:   settings."${traitName}.locatorCommand",
        commands:         ["Locate"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_LockUnlock(traitName) {
    return [
        lockedUnlockedAttribute: settings."${traitName}.lockedUnlockedAttribute",
        lockedValue:             settings."${traitName}.lockedValue",
        lockCommand:             settings."${traitName}.lockCommand",
        unlockCommand:           settings."${traitName}.unlockCommand",
        commands:                ["Lock", "Unlock"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_MediaState(traitName) {
    return [
        supportActivityState:     settings."${traitName}.supportActivityState",
        supportPlaybackState:     settings."${traitName}.supportPlaybackState",
        activityStateAttribute:   settings."${traitName}.activityStateAttribute",
        playbackStateAttribute:   settings."${traitName}.playbackStateAttribute",
        commands:                 []
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
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
    } else {
        openCloseTrait.reverseDirection = settings."${traitName}.reverseDirection" as boolean
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

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Reboot(traitName) {
    return [
        rebootCommand:      settings."${traitName}.rebootCommand",
        commands:           ["Reboot"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Rotation(traitName) {
    return [
        rotationAttribute:  settings."${traitName}.rotationAttribute",
        setRotationCommand: settings."${traitName}.setRotationCommand",
        continuousRotation: settings."${traitName}.continuousRotation",
        commands:           ["Rotate"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_SoftwareUpdate(traitName) {
    return [
        lastSoftwareUpdateUnixTimestampSecAttribute:
                                  settings."${traitName}.lastSoftwareUpdateUnixTimestampSecAttribute",
        softwareUpdateCommand:                          settings."${traitName}.softwareUpdateCommand",
        commands:                                       ["Software Update"]
    ]
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_StartStop(traitName) {
    def canPause = settings."${traitName}.canPause"
    if (canPause == null) {
        canPause = true
    }
    def  startStopTrait = [
         startStopAttribute: settings."${traitName}.startStopAttribute",
         startValue:         settings."${traitName}.startValue",
         stopValue:          settings."${traitName}.stopValue",
         startCommand:       settings."${traitName}.startCommand",
         stopCommand:        settings."${traitName}.stopCommand",
         canPause:           canPause,
         commands:           ["Start", "Stop"]
    ]
    if (canPause) {
        startStopTrait << [
            pauseUnPauseAttribute: settings."${traitName}.pauseUnPauseAttribute",
            pauseValue:            settings."${traitName}.pauseValue",
            pauseCommand:          settings."${traitName}.pauseCommand"
        ]
        startStopTrait.commands += ["Pause"]
    }
    return startStopTrait
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_TemperatureControl(traitName) {
    def tempControlTrait = [
        temperatureUnit:             settings."${traitName}.temperatureUnit",
        currentTemperatureAttribute: settings."${traitName}.currentTemperatureAttribute",
        queryOnly:                   settings."${traitName}.queryOnly",
        commands:                    []
    ]

    if (!tempControlTrait.queryOnly) {
        tempControlTrait << [
            currentSetpointAttribute: settings."${traitName}.setpointAttribute",
            setTemperatureCommand:    settings."${traitName}.setTemperatureCommand",
            // Min and Max temperature used the wrong input type originally, so coerce them
            // from String to BigDecimal if this trait was saved with a broken version
            minTemperature:           settings."${traitName}.minTemperature" as BigDecimal,
            maxTemperature:           settings."${traitName}.maxTemperature" as BigDecimal
        ]
        tempControlTrait.commands << "Set Temperature"

        def temperatureStep = settings."${traitName}.temperatureStep"
        if (temperatureStep) {
            // Temperature step used the wrong input type originally, so coerce them
            // from String to BigDecimal if this trait was saved with a broken version
            tempControlTrait.temperatureStep = temperatureStep as BigDecimal
        }
    }

    return tempControlTrait
}

private thermostatSetpointAttributeForMode(traitName, mode) {
    def attrPref = THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES[mode]
    if (!attrPref) {
        return null
    }
    def value = settings."${traitName}.${attrPref.name}"
    // Device types created with older versions of the app may not have this set,
    // so fall back if the mode-based setting isn't set
    value = value ?: settings."${traitName}.setpointAttribute"
    return value
}

private thermostatSetpointCommandForMode(traitName, mode) {
    def commandPref = THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES[mode]
    if (!commandPref) {
        return null
    }
    def value = settings."${traitName}.${commandPref.name}"
    // Device types created with older versions of the app may not have this set,
    // so fall back if the mode-based setting isn't set
    value = value ?: settings."${traitName}.setSetpointCommand"
    return value
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_TemperatureSetting(traitName) {
    def tempSettingTrait = [
        temperatureUnit:             settings."${traitName}.temperatureUnit",
        currentTemperatureAttribute: settings."${traitName}.currentTemperatureAttribute",
        // queryOnly may be null for device traits defined with older versions,
        // so coerce it to a boolean
        queryOnly:                   settings."${traitName}.queryOnly" as boolean,
        commands:                    []
    ]

    if (!tempSettingTrait.queryOnly) {
        tempSettingTrait << [
            modes:                       settings."${traitName}.modes",
            setModeCommand:              settings."${traitName}.setModeCommand",
            currentModeAttribute:        settings."${traitName}.currentModeAttribute",
            googleToHubitatModeMap:      [:],
            hubitatToGoogleModeMap:      [:],
            modeSetpointAttributes:      [:],
            modeSetSetpointCommands:     [:],
        ]
        tempSettingTrait.commands << "Set Mode"

        tempSettingTrait.modes.each { mode ->
            def hubitatMode = settings."${traitName}.mode.${mode}.hubitatMode"
            if (hubitatMode != null) {
                tempSettingTrait.googleToHubitatModeMap[mode] = hubitatMode
                tempSettingTrait.hubitatToGoogleModeMap[hubitatMode] = mode
            }

            if (mode == "heatcool") {
                tempSettingTrait.modeSetpointAttributes[mode] = [
                    heatingSetpointAttribute: thermostatSetpointAttributeForMode(traitName, "heat"),
                    coolingSetpointAttribute: thermostatSetpointAttributeForMode(traitName, "cool")
                ]
                tempSettingTrait.modeSetSetpointCommands[mode] = [
                    setHeatingSetpointCommand: thermostatSetpointCommandForMode(traitName, "heat"),
                    setCoolingSetpointCommand: thermostatSetpointCommandForMode(traitName, "cool")
                ]
            } else {
                tempSettingTrait.modeSetpointAttributes[mode] = thermostatSetpointAttributeForMode(traitName, mode)
                tempSettingTrait.modeSetSetpointCommands[mode] = thermostatSetpointCommandForMode(traitName, mode)
            }

            if (!("Set Setpoint" in tempSettingTrait.commands)) {
                tempSettingTrait.commands << "Set Setpoint"
            }
        }
        // HeatCool buffer, Min temperature, and Max Temperature used the wrong input
        // type originally, so coerce them from String to BigDecimal if this trait
        // was saved with a broken version
        def heatcoolBuffer = settings."${traitName}.heatcoolBuffer"
        if (heatcoolBuffer != null) {
            tempSettingTrait.heatcoolBuffer = heatcoolBuffer as BigDecimal
        }
        def setRangeMin = settings."${traitName}.range.min"
        if (setRangeMin != null) {
            tempSettingTrait.setRangeMin = setRangeMin as BigDecimal
        }
        def setRangeMax = settings."${traitName}.range.max"
        if (setRangeMax != null) {
            tempSettingTrait.setRangeMax = setRangeMax as BigDecimal
        }
    }
    return tempSettingTrait
}

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Timer(traitName) {
    def timerTrait = [
        maxTimerLimitSec:                 settings."${traitName}.maxTimerLimitSec",
        commandOnlyTimer:                 settings."${traitName}.commandOnlyTimer",

        commands:                         ["Start", "Adjust", "Cancel", "Pause", "Resume"]
    ]
    if (!timerTrait.commandOnlyTimer) {
        timerTrait << [
            timerRemainingSecAttribute:   settings."${traitName}.timerRemainingSecAttribute",
            timerPausedAttribute:         settings."${traitName}.timerPausedAttribute",
            timerPausedValue:             settings."${traitName}.timerPausedValue",
        ]
    }
    return timerTrait
}

@SuppressWarnings('UnusedPrivateMethod')
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
        if (toggleAttrs.labels == null) {
            toggleAttrs.labels = ["Unknown"]
        }
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

@SuppressWarnings('UnusedPrivateMethod')
private traitFromSettings_Volume(traitName) {
    def canMuteUnmute = settings."${traitName}.canMuteUnmute"
    if (canMuteUnmute == null) {
        canMuteUnmute = true
    }

    def canSetVolume = settings."${traitName}.canSetVolume"
    if (canSetVolume == null) {
        canSetVolume = true
    }

    def volumeTrait = [
        volumeAttribute:   settings."${traitName}.volumeAttribute",
        setVolumeCommand:  settings."${traitName}.setVolumeCommand",
        volumeUpCommand:   settings."${traitName}.volumeUpCommand",
        volumeDownCommand: settings."${traitName}.volumeDownCommand",
        volumeStep:        settings."${traitName}.volumeStep",
        canMuteUnmute:     canMuteUnmute,
        canSetVolume:      canSetVolume,
        commands: ["Set Volume"],
    ]

    if (canMuteUnmute) {
        volumeTrait << [
            muteAttribute: settings."${traitName}.muteAttribute",
            mutedValue:    settings."${traitName}.mutedValue",
            unmutedValue:  settings."${traitName}.unmutedValue",
            muteCommand:   settings."${traitName}.muteCommand",
            unmuteCommand: settings."${traitName}.unmuteCommand"
        ]
        volumeTrait.commands += ["Mute", "Unmute"]
    }

    return volumeTrait
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

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_ArmDisarm(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.armedAttribute")
    app.removeSetting("${deviceTrait.name}.armLevelAttribute")
    app.removeSetting("${deviceTrait.name}.exitAllowanceAttribute")
    app.removeSetting("${deviceTrait.name}.cancelCommand")
    deviceTrait.armLevels.each { armLevel, googleNames ->
        app.removeSetting("${deviceTrait.name}.armLevels.${armLevels}.googleNames")
    }
    deviceTrait.armLevels.each { armLevel, commandName ->
        app.removeSetting("${deviceTrait.name}.armCommands.${armLevels}.commandName")
    }
    deviceTrait.armLevels.each { armLevel, value ->
        app.removeSetting("${deviceTrait.name}.armValues.${armLevels}.value")
    }
    app.removeSetting("${deviceTrait.name}.armLevels")
}
@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Brightness(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.brightnessAttribute")
    app.removeSetting("${deviceTrait.name}.setBrightnessCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_CameraStream(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.cameraStreamURLAttribute")
    app.removeSetting("${deviceTrait.name}.cameraStreamProtocolAttribute")
    app.removeSetting("${deviceTrait.name}.cameraStreamCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_ColorSetting(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.fullSpectrum")
    app.removeSetting("${deviceTrait.name}.hueAttribute")
    app.removeSetting("${deviceTrait.name}.saturationAttribute")
    app.removeSetting("${deviceTrait.name}.levelAttribute")
    app.removeSetting("${deviceTrait.name}.setColorCommand")
    app.removeSetting("${deviceTrait.name}.colorTemperature")
    app.removeSetting("${deviceTrait.name}.colorTemperature.min")
    app.removeSetting("${deviceTrait.name}.colorTemperature.max")
    app.removeSetting("${deviceTrait.name}.colorTemperatureAttribute")
    app.removeSetting("${deviceTrait.name}.setColorTemperatureCommand")
    app.removeSetting("${deviceTrait.name}.colorModeAttribute")
    app.removeSetting("${deviceTrait.name}.fullSpectrumModeValue")
    app.removeSetting("${deviceTrait.name}.temperatureModeValue")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Dock(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.dockAttribute")
    app.removeSetting("${deviceTrait.name}.dockValue")
    app.removeSetting("${deviceTrait.name}.dockCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_EnergyStorage(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.energyStorageDistanceUnitForUX")
    app.removeSetting("${deviceTrait.name}.isRechargeable")
    app.removeSetting("${deviceTrait.name}.queryOnlyEnergyStorage")
    app.removeSetting("${deviceTrait.name}.chargeCommand")
    app.removeSetting("${deviceTrait.name}.descriptiveCapacityRemaining")
    app.removeSetting("${deviceTrait.name}.capacityRemainingRawValue")
    app.removeSetting("${deviceTrait.name}.capacityRemainingUnit")
    app.removeSetting("${deviceTrait.name}.capacityUntilFullRawValue")
    app.removeSetting("${deviceTrait.name}.capacityUntilFullUnit")
    app.removeSetting("${deviceTrait.name}.isChargingAttribute")
    app.removeSetting("${deviceTrait.name}.chargingValue")
    app.removeSetting("${deviceTrait.name}.isPluggedInAttribute")
    app.removeSetting("${deviceTrait.name}.pluggedInValue")
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_HumiditySetting(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.humidityAttribute")
    app.removeSetting("${deviceTrait.name}.humiditySetpointAttribute")
    app.removeSetting("${deviceTrait.name}.setHumidityCommand")
    app.removeSetting("${deviceTrait.name}.humidityRange.min")
    app.removeSetting("${deviceTrait.name}.humidityRange.max")
    app.removeSetting("${deviceTrait.name}.queryOnly")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Locator(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.locatorCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_LockUnlock(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.lockedUnlockedAttribute")
    app.removeSetting("${deviceTrait.name}.lockedValue")
    app.removeSetting("${deviceTrait.name}.lockCommand")
    app.removeSetting("${deviceTrait.name}.unlockCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_MediaState(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.supportActivityState")
    app.removeSetting("${deviceTrait.name}.supportPlaybackState")
    app.removeSetting("${deviceTrait.name}.activityStateAttribute")
    app.removeSetting("${deviceTrait.name}.playbackStateAttribute")
}

@SuppressWarnings('UnusedPrivateMethod')
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

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_OpenClose(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.discreteOnlyOpenClose")
    app.removeSetting("${deviceTrait.name}.openCloseAttribute")
    app.removeSetting("${deviceTrait.name}.openValue")
    app.removeSetting("${deviceTrait.name}.closedValue")
    app.removeSetting("${deviceTrait.name}.reverseDirection")
    app.removeSetting("${deviceTrait.name}.openCommand")
    app.removeSetting("${deviceTrait.name}.closeCommand")
    app.removeSetting("${deviceTrait.name}.openPositionCommand")
    app.removeSetting("${deviceTrait.name}.queryOnly")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Reboot(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.rebootCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Rotation(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.rotationAttribute")
    app.removeSetting("${deviceTrait.name}.setRotationCommand")
    app.removeSetting("${deviceTrait.name}.continuousRotation")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Scene(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.activateCommand")
    app.removeSetting("${deviceTrait.name}.deactivateCommand")
    app.removeSetting("${deviceTrait.name}.sceneReversible")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_SoftwareUpdate(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.lastSoftwareUpdateUnixTimestampSecAttribute")
    app.removeSetting("${deviceTrait.name}.softwareUpdateCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_StartStop(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.canPause")
    app.removeSetting("${deviceTrait.name}.startStopAttribute")
    app.removeSetting("${deviceTrait.name}.pauseUnPauseAttribute")
    app.removeSetting("${deviceTrait.name}.startValue")
    app.removeSetting("${deviceTrait.name}.stopValue")
    app.removeSetting("${deviceTrait.name}.pauseValue")
    app.removeSetting("${deviceTrait.name}.startCommand")
    app.removeSetting("${deviceTrait.name}.stopCommand")
    app.removeSetting("${deviceTrait.name}.pauseCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_TemperatureControl(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.temperatureUnit")
    app.removeSetting("${deviceTrait.name}.currentTemperatureAttribute")
    app.removeSetting("${deviceTrait.name}.queryOnly")
    app.removeSetting("${deviceTrait.name}.setpointAttribute")
    app.removeSetting("${deviceTrait.name}.setTemperatureCommand")
    app.removeSetting("${deviceTrait.name}.minTemperature")
    app.removeSetting("${deviceTrait.name}.maxTemperature")
    app.removeSetting("${deviceTrait.name}.temperatureStep")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_TemperatureSetting(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.temperatureUnit")
    app.removeSetting("${deviceTrait.name}.currentTemperatureAttribute")
    app.removeSetting("${deviceTrait.name}.queryOnly")
    app.removeSetting("${deviceTrait.name}.modes")
    app.removeSetting("${deviceTrait.name}.heatcoolBuffer")
    app.removeSetting("${deviceTrait.name}.range.min")
    app.removeSetting("${deviceTrait.name}.range.max")
    app.removeSetting("${deviceTrait.name}.setModeCommand")
    app.removeSetting("${deviceTrait.name}.currentModeAttribute")
    GOOGLE_THERMOSTAT_MODES.each { mode, display ->
        app.removeSetting("${deviceTrait.name}.mode.${mode}.hubitatMode")
        def attrPrefName = THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES[mode]?.name
        if (attrPrefName) {
            app.removeSetting("${deviceTrait.name}.${attrPrefName}")
        }
        def commandPrefName = THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES[mode]?.name
        if (commandPrefName) {
            app.removeSetting("${deviceTrait.name}.${commandPrefName}")
        }
    }
    // These settings are no longer set for new device types, but may still exist
    // for device types created with older versions of the app
    app.removeSetting("${deviceTrait.name}.setpointAttribute")
    app.removeSetting("${deviceTrait.name}.setSetpointCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Timer(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.maxTimerLimitSec")
    app.removeSetting("${deviceTrait.name}.commandOnlyTimer")
    app.removeSetting("${deviceTrait.name}.timerRemainingSecAttribute")
    app.removeSetting("${deviceTrait.name}.timerPausedAttribute")
    app.removeSetting("${deviceTrait.name}.timerStartCommand")
    app.removeSetting("${deviceTrait.name}.timerAdjustCommand")
    app.removeSetting("${deviceTrait.name}.timerCancelCommand")
    app.removeSetting("${deviceTrait.name}.timerPauseCommand")
    app.removeSetting("${deviceTrait.name}.timerResumeCommand")
    app.removeSetting("${deviceTrait.name}.timerPausedValue")
}

@SuppressWarnings('UnusedPrivateMethod')
private deleteDeviceTrait_Volume(deviceTrait) {
    app.removeSetting("${deviceTrait.name}.volumeAttribute")
    app.removeSetting("${deviceTrait.name}.setVolumeCommand")
    app.removeSetting("${deviceTrait.name}.volumeStep")
    app.removeSetting("${deviceTrait.name}.canMuteUnmute")
    app.removeSetting("${deviceTrait.name}.canSetVolume")
    app.removeSetting("${deviceTrait.name}.muteAttribute")
    app.removeSetting("${deviceTrait.name}.mutedValue")
    app.removeSetting("${deviceTrait.name}.unmutedValue")
    app.removeSetting("${deviceTrait.name}.muteCommand")
    app.removeSetting("${deviceTrait.name}.unmuteCommand")
}

@SuppressWarnings('UnusedPrivateMethod')
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
    app.updateSetting("${deviceType.name}.pinCodes", deviceType.pinCodes*.id)
    app.removeSetting("${deviceType.name}.pin.${pinId}.name")
    app.removeSetting("${deviceType.name}.pin.${pinId}.value")
}

private addDeviceTypePin(deviceType) {
    deviceType.pinCodes << [
        id: UUID.randomUUID().toString(),
        name: null,
        value: null
    ]
    app.updateSetting("${deviceType.name}.pinCodes", deviceType.pinCodes*.id)
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
                name: mode,
                label: "${mode} Mode",
                id: "hubitat_mode_${mode}"
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
            knownDevices."${device.id}" = [
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

private googlePercentageToHubitat(percentage) {
    // Google is documented to provide percentages in the range [0..100], as
    // is Hubitat's SwitchLevel (setLevel), WindowBlind (setPosition).
    //
    // Just to be safe, clamp incoming values from Google to the range [0..100].
    return Math.max(0, Math.min(100, percentage as int))
}

private hubitatPercentageToGoogle(percentage) {
    // Hubitat's driver capabilities for SwitchLevel (setLevel) and WindowBlind
    // (setPosition) are documented to use the range [0..100], but several
    // Z-Wave dimmer devices return values in the range [0..99].
    //
    // Rather than try to guess which is which, assume nobody will set a
    // device to 99% and map that specific value to 100.
    //
    // Clamp the value to ensure it's in the range [0..100], then map 99
    // to 100.
    def clamped = Math.max(0, Math.min(100, percentage as int))
    return clamped == 99 ? 100 : clamped
}

@Field
private final LOGGER = [
    debug: { if (settings.debugLogging) { log.debug(it) } },
    info: { log.info(it) },
    warn: { log.warn(it) },
    error: { log.error(it) },
    exception: { message, exception ->
        def relevantEntries = exception.stackTrace.findAll { entry -> entry.className.startsWith("user_app") }
        def line = relevantEntries[0].lineNumber
        def method = relevantEntries[0].methodName
        log.error("${message}: ${exception} at line ${line} (${method})")
        if (settings.debugLogging) {
            log.debug("App exception stack trace:\n${relevantEntries.join("\n")}")
        }
    }
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
    AC_UNIT:                "Air Conditioning Unit",
    AIRCOOLER:              "Air Cooler",
    AIRFRESHENER:           "Air Freshener",
    AIRPURIFIER:            "Air Purifier",
    AUDIO_VIDEO_RECEIVER:   "Audio/Video Receiver",
    AWNING:                 "Awning",
    BATHTUB:                "Bathtub",
    BED:                    "Bed",
    BLENDER:                "Blender",
    BLINDS:                 "Blinds",
    BOILER:                 "Boiler",
    CAMERA:                 "Camera",
    CARBON_MONOXIDE_SENSOR: "Carbon Monoxide Sensor",
    CHARGER:                "Charger",
    CLOSET:                 "Closet",
    COFFEE_MAKER:           "Coffee Maker",
    COOKTOP:                "Cooktop",
    CURTAIN:                "Curtain",
    DEHUMIDIFIER:           "Dehumidifier",
    DEHYDRATOR:             "Dehydrator",
    DISHWASHER:             "Dishwasher",
    DOOR:                   "Door",
    DRAWER:                 "Drawer",
    DRYER:                  "Dryer",
    FAN:                    "Fan",
    FAUCET:                 "Faucet",
    FIREPLACE:              "Fireplace",
    FREEZER:                "Freezer",
    FRYER:                  "Fryer",
    GARAGE:                 "Garage Door",
    GATE:                   "Gate",
    GRILL:                  "Grill",
    HEATER:                 "Heater",
    HOOD:                   "Hood",
    HUMIDIFIER:             "Humidifier",
    KETTLE:                 "Kettle",
    LIGHT:                  "Light",
    LOCK:                   "Lock",
    MICROWAVE:              "Microwave",
    MOP:                    "Mop",
    MOWER:                  "Mower",
    MULTICOOKER:            "Multicooker",
    NETWORK:                "Network",
    OUTLET:                 "Outlet",
    OVEN:                   "Oven",
    PERGOLA:                "Pergola",
    PETFEEDER:              "Pet Feeder",
    PRESSURECOOKER:         "Pressure Cooker",
    RADIATOR:               "Radiator",
    REFRIGERATOR:           "Refrigerator",
    REMOTECONTROL:          "Remote Control",
    ROUTER:                 "Router",
    SCENE:                  "Scene",
    SECURITYSYSTEM:         "Security System",
    SENSOR:                 "Sensor",
    SETTOP:                 "Set-Top Box",
    SHOWER:                 "Shower",
    SHUTTER:                "Shutter",
    SMOKE_DETECTOR:         "Smoke Detector",
    SOUSVIDE:               "Sous Vide",
    SPEAKER:                "Speaker",
    SPRINKLER:              "Sprinkler",
    STANDMIXER:             "Stand Mixer",
    STREAMING_BOX:          "Streaming Box",
    STREAMING_SOUNDBAR:     "Streaming Soundbar",
    STREAMING_STICK:        "Streaming Stick",
    SWITCH:                 "Switch",
    TV:                     "Television",
    THERMOSTAT:             "Thermostat",
    VACUUM:                 "Vacuum",
    VALVE:                  "Valve",
    WASHER:                 "Washer",
    WATERHEATER:            "Water Heater",
    WATERPURIFIER:          "Water Purifier",
    WATERSOFTENER:          "Water Softener",
    WINDOW:                 "Window",
    YOGURTMAKER:            "Yogurt Maker"
]

@Field
private static final GOOGLE_DEVICE_TRAITS = [
    //AppSelector: "App Selector",
    ArmDisarm: "Arm/Disarm",
    Brightness: "Brightness",
    CameraStream: "Camera Stream",
    //Channel: "Channel",
    ColorSetting: "Color Setting",
    //Cook: "Cook",
    //Dispense: "Dispense",
    Dock: "Dock",
    EnergyStorage: "Energy Storage",
    FanSpeed: "Fan Speed",
    //Fill: "Fill",
    HumiditySetting: "Humidity Setting",
    //InputSelector: "Input Selector",
    //LightEffects: "Light Effects",
    Locator: "Locator",
    LockUnlock: "Lock/Unlock",
    MediaState: "Media State",
    //Modes: "Modes",
    //NetworkControl: "Network Control",
    //ObjectDetection: "Object Detection",
    OnOff: "On/Off",
    OpenClose: "Open/Close",
    Reboot: "Reboot",
    Rotation: "Rotation",
    //RunCycle: "Run Cycle",
    //SensorState: "Sensor State",
    Scene: "Scene",
    SoftwareUpdate: "Software Update",
    StartStop: "Start/Stop",
    //StatusReport: "Status Report",
    TemperatureControl: "Temperature Control",
    TemperatureSetting: "Temperature Setting",
    Timer: "Timer",
    Toggles: "Toggles",
    //TransportControl: "Transport Control",
    Volume: "Volume",
]

@Field
private static final GOOGLE_THERMOSTAT_MODES = [
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

@Field
private static final THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES = [
    "off": null,
    "heat": [
        name:  "setHeatingSetpointCommand",
        title: "Set Heating Setpoint Command"
    ],
    "cool": [
        name:  "setCoolingSetpointCommand",
        title: "Set Cooling Setpoint Command"
    ],
].withDefault { mode ->
    [
        name:  "set${mode.capitalize()}SetpointCommand",
        title: "Set ${GOOGLE_THERMOSTAT_MODES[mode]} Setpoint Command"
    ]
}

@Field
private static final THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES = [
    "off": null,
    "heat": [
        name:  "heatingSetpointAttribute",
        title: "Heating Setpoint Attribute"
    ],
    "cool": [
        name:  "coolingSetpointAttribute",
        title: "Cooling Setpoint Attribute"
    ]
].withDefault { mode ->
    [
        name:  "${mode}SetpointAttribute",
        title: "${GOOGLE_THERMOSTAT_MODES[mode]} Setpoint Attribute",
    ]
}
