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
//   * Mar 21 2020 - Don't configure setpoint attribute and command for the sOFF thermostat mode
//   * Mar 21 2020 - Fix some Temperature Setting and Temperature Control settings that were using the wrong input type
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
//   * Mar 05 2022 - Added supportsFanSpeedPercent trait for controlling fan by percentage
//   * May 07 2022 - Add error handling so one bad device doesn't prevent reporting state of other devices
//   * Jun 15 2022 - Fix a crash on trait configuration introduced by Hubitat 2.3.2.127
//   * Jun 20 2022 - Fixed CameraStream trait to match the latest Google API.  Moved protocol support to the
//                   driver level to accommodate different camera stream sources
//                 - Added Arm/Disarm Trait
//                 - Added ability for the app to use device level pin codes retrieved from the device driver
//                 - Pincode challenge in the order device_driver -> device_GHC -> global_GHC -> null
//                 - Added support for returning the matching user position for Arm/Disarm and Lock/Unlock to the device driver
//   * Jun 21 2022 - Apply rounding more consistently to temperatures
//   * Jun 21 2022 - Added SensorState Trait
//   * Jun 23 2022 - Fix error attempting to round null
//   * Sep 08 2022 - Fix SensorState labels
//   * Oct 18 2022 - Added TransportControl Trait
//   * Nov 30 2022 - Implement RequestSync and ReportState APIs
//   * Feb 03 2023 - Uppercase values sent for MediaState attributes
//   * Mar 06 2023 - Fix hub version comparison
//   * May 20 2023 - Fix error in SensorState which prevented multiple trait types from being reported.
//                   Allow for traits to support descriptive and/or numeric responses.
//   * Jun 06 2023 - Add support for the OccupancySensing trait
//   * Apr 11 2024 - Many fixes, optimizations including asynchttp requests; reduce memory and cpu usage


//file:noinspection GroovySillyAssignment
//file:noinspection GrDeprecatedAPIUsage
//file:noinspection GroovyDoubleNegation
//file:noinspection GroovyUnusedAssignment
//file:noinspection unused
//file:noinspection SpellCheckingInspection
//file:noinspection GroovyFallthrough
//file:noinspection GrMethodMayBeStatic
//file:noinspection GroovyAssignabilityCheck
//file:noinspection UnnecessaryQualifiedReference

import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field

//import java.security.*
import java.security.KeyFactory
import java.security.PrivateKey
//import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
//import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

definition(
    (sNM): "Google Home Community",
    namespace: "mbudnek",
    author: "Miles Budnek",
    description: "Community-maintained Google Home integration",
    category: "Integrations",
    iconUrl: sBLK,
    iconX2Url: sBLK,
    iconX3Url: sBLK,
    importUrl: "https://raw.githubusercontent.com/mbudnek/google-home-hubitat-community/master/google-home-community.groovy"  // IgnoreLineLength
)

preferences {
    page((sNM): "mainPreferences")
    page((sNM): "deviceTypePreferences")
    page((sNM): "deviceTypeDelete")
    page((sNM): "appInstructions")
    page((sNM): "deviceTraitPreferences")
    page((sNM): "deviceTraitDelete")
    page((sNM): "togglePreferences")
    page((sNM): "toggleDelete")
    page((sNM): "pageDump")
}

mappings {
    path("/action") {
        action: [
            POST: "handleAction"
        ]
    }
}

def installed() {
    //updateDeviceEventSubscription()
}

def updated() {
    LOGGER.debug("Preferences updated")
    state.remove('oauthExpiryTimeMillis')
    LOGGER.debug("App agentUserId=${agentUserId}")
    unsubscribe("handleDeviceEvent")
    if (gtSetStr('googleServiceAccountJSON')) {
        requestSync()
        if (gtSetB('reportState')) {
            Map<String,Map<String,Object>> akd = allKnownDevices()
            akd.each { entry ->
                subscribe(entry.value.device, "handleDeviceEvent", [filterEvents: true])
            }
            reportStateForDevices(akd)
        }
    }
}

def handleDeviceEvent(event) {
    String deviceId = event.deviceId.toString()
    LOGGER.debug("Handling device event, deviceId=${deviceId} device=${event.device}")
    Map<String,Object> deviceInfo = allKnownDevices()[deviceId]
    reportStateForDevices([(deviceId): deviceInfo])
}

private void reportStateForDevices(Map<String,Map<String,Object>>devices) {
    String requestId = UUID.randomUUID().toString()
    Map req = [
        requestId: requestId,
        agentUserId: agentUserId,
        payload: [
            devices: [
                states: [:],
            ],
        ],
    ]

    devices.each { String deviceId, Map<String,Object> deviceInfo ->
        Map deviceState; deviceState = [:]
        ((Map<String,Map>)deviceInfo.deviceType).traits.each { String traitType, Map deviceTrait ->
            deviceState += "deviceStateForTrait_${traitType}"(deviceTrait, deviceInfo.device)
        }
        if (deviceState.size()) {
            req.payload.devices.states[deviceId] = deviceState
        } else {
            LOGGER.debug(
                "Not reporting state for device ${deviceInfo.device} to Home Graph (no state -- maybe a scene?)"
            )
        }
    }

    if (req.payload.devices.states.size()) {
        String token = fetchOAuthToken()
        Map param = [
            uri: "https://homegraph.googleapis.com/v1/devices:reportStateAndNotification",
            headers: [
                Authorization: "Bearer REDACTED",
            ],
            contentType: 'application/json',
            requestContentType: 'application/json',
            body: req,
        ]
        LOGGER.debug("Posting device state requestId=${requestId}: ${param}")
        param.headers.authorization = "Bearer ${token}"
        try {
            asynchttpPost(handleDevResp, param, [id:requestId])
            LOGGER.debug("Posted device state requestId=${requestId} ")
        } catch (Exception ex) {
            LOGGER.exception(
                "Error posting device state:\nrequest=${req}\n",
                ex
            )
        }
    } else {
        LOGGER.debug("No device state to report; not sending device state report to Home Graph")
    }
}

@SuppressWarnings('unused')
def handleDevResp(resp,data){
    if(!resp.hasError())
        LOGGER.debug("COMPLETED requestId=${data.id}")
   else
        LOGGER.warn("ERROR Completing requestId=${data.id}")
}

void requestSync() {
    Map param = [
        uri: "https://homegraph.googleapis.com/v1/devices:requestSync",
        headers: [
            Authorization: "Bearer ${fetchOAuthToken()}",
        ],
        contentType: 'application/json',
        requestContentType: 'application/json',
        body: [ agentUserId: agentUserId ]
    ]
    LOGGER.debug("Requesting Google sync devices")
    try {
        asynchttpPost(handleDevResp, param, [id:'SYNC'])
        LOGGER.debug("Finished requesting Google sync devices")
    } catch (Exception ex) {
        LOGGER.exception(
                "Error requesting sync:\nrequest=${param}\n",
                ex
        )
    }
}

def uninstalled() {
    LOGGER.debug("App uninstalled")
    // TODO: Uninstall from Google
}

@SuppressWarnings('AssignmentInConditional')
def appButtonHandler(buttonPressed) {
    def match
    if ((match = (buttonPressed =~ /^addPin:(.+)$/))) {
        String deviceTypeName = match.group(1)
        Map deviceType = deviceTypeFromSettings(deviceTypeName)
        addDeviceTypePin(deviceType)
    } else if ((match = (buttonPressed =~ /^deletePin:(.+)\.pin\.(.+)$/))) {
        String pinId = match.group(2)
        // If we actually delete the PIN here then it will get added back when the
        // device type settings page re-submits after the button handler finishes.
        // Instead just set a flag that we want to delete this PIN, and the settings
        // page will take care of actually deleting it.
        state.pinToDelete = pinId
    }
}

private Boolean hubVersionLessThan(String versionString) {
    String[] hubVersion = ((String)location.hub.firmwareVersionString).split("\\.")
    String[] targetVersion = versionString.split("\\.")
    Integer i
    for (i = iZ; i < targetVersion.length; ++i) {
        if ((hubVersion[i] as Integer) < (targetVersion[i] as Integer)) {
            return true
        } else if ((hubVersion[i] as Integer) > (targetVersion[i] as Integer)) {
            return false
        }
    }
    return false
}

@SuppressWarnings('MethodSize')
def mainPreferences() {
    // Make sure that the deviceTypeFromSettings returns by giving it a display name
    app.updateSetting("GlobalPinCodes.display", "Global PIN Codes")
    Map globalPinCodes = deviceTypeFromSettings('GlobalPinCodes')
    String pinDel= gtStStr('pinToDelete')
    if (pinDel) {
        deleteDeviceTypePin(globalPinCodes, pinDel)
        state.remove('pinToDelete')
    }
    String toEdit = gtSetStr('deviceTypeToEdit')
    if (toEdit != sNL) {
        app.removeSetting("deviceTypeToEdit")
        return deviceTypePreferences(deviceTypeFromSettings(toEdit))
    }
    String toDelete = gtSetStr('deviceTypeToDelete')
    if (toDelete != sNL) {
        app.removeSetting("deviceTypeToDelete")
        return deviceTypeDelete(deviceTypeFromSettings(toDelete))
    }

    state.remove('currentlyEditingDeviceType')
    return dynamicPage((sNM): "mainPreferences", (sTIT): "Device Selection", install: true, uninstall: true) {
        section {
            input(
                (sNM): "modesToExpose",
                (sTIT): "Modes to expose",
                (sTYPE): "mode",
                (sMULTIPLE): true
            )
        }
        List<Map> allDeviceTypes = deviceTypes().sort { it.display }
        section {
            allDeviceTypes.each { deviceType ->
                input(
                    // Note: This name _must_ be converted to a String.
                    //       If it isn't, then all devices will be removed when linking to Google Home
                    (sNM): "${deviceType.name}.devices" as String,
                    (sTYPE): "capability.${deviceType.type}",
                    (sTIT): "${deviceType.display} devices",
                    (sMULTIPLE): true
                )
            }
        }
        section {
            if (allDeviceTypes) {
                Map<String,String> deviceTypeOptions = allDeviceTypes.collectEntries { deviceType ->
                    [deviceType.name, deviceType.display]
                }
                input(
                    (sNM): 'deviceTypeToEdit',
                    (sTIT): "Edit Device Type",
                    description: "Select a device type to edit...",
                    width: 6,
                    (sTYPE): sENUM,
                    (sOPTIONS): deviceTypeOptions,
                    (sSUBONCHG): true
                )
                input(
                    (sNM): 'deviceTypeToDelete',
                    (sTIT): "Delete Device Type",
                    description: "Select a device type to delete...",
                    width: 6,
                    (sTYPE): sENUM,
                    (sOPTIONS): deviceTypeOptions,
                    (sSUBONCHG): true
                )
            }
            href((sTIT): "Define new device type", description: sBLK, style: "page", page: "deviceTypePreferences")
        }
        if (!hubVersionLessThan("2.3.4.115")) {
            section("Home Graph Integration") {
                if (!gtSetStr('googleServiceAccountJSON')) {
                    paragraph(
                            '''\
                    Follow these steps to enable Google Home Graph Integration:
                      1) Enable Google Home Graph API at https://console.developers.google.com/apis/api/homegraph.googleapis.com/overview
                      2) Create a Service Account with Role='Service Account Token Creator' at https://console.cloud.google.com/apis/credentials/serviceaccountkey
                      3) From Service Accounts, go to Keys -> Add Key -> Create new key -> JSON and save to disk
                      4) Paste contents of the file in Google Service Account Authorization below\
                    '''.stripIndent()
                    )
                }
                input(
                    (sNM): 'googleServiceAccountJSON',
                    (sTIT): "Google Service Account Authorization",
                    (sTYPE): "password",
                    (sSUBONCHG): true
                )

                if (gtSetStr('googleServiceAccountJSON')) {
                    input(
                        (sNM): 'reportState',
                        (sTIT): "Push device events to Google",
                        (sTYPE): sBOOL,
                        (sDEFVAL): true
                    )
                    href(
                        (sTIT): "Google Action setup details",
                        description: sBLK,
                        style: "page",
                        page: "appInstructions")
                }
            }
        }
        section("Global PIN Codes") {
            globalPinCodes?.pinCodes?.each { pinCode ->
                input(
                    (sNM): "GlobalPinCodes.pin.${pinCode.id}.name",
                    (sTIT): "PIN Code Name",
                    (sTYPE): sTEXT,
                    (sREQ): true,
                    width: 6
                )
                input(
                    (sNM): "GlobalPinCodes.pin.${pinCode.id}.value",
                    (sTIT): "PIN Code Value",
                    (sTYPE): "password",
                    (sREQ): true,
                    width: 5
                )
                input(
                    (sNM): "deletePin:GlobalPinCodes.pin.${pinCode.id}",
                    (sTIT): "X",
                    (sTYPE): "button",
                    width: i1
                )
            }
            input(
                (sNM): "addPin:GlobalPinCodes",
                (sTIT): "Add PIN Code",
                (sTYPE): "button"
            )

        }
        section {
            input(
                (sNM): 'debugLogging',
                (sTIT): "Enable Debug Logging",
                (sTYPE): sBOOL,
                (sDEFVAL): false,
                (sSUBONCHG): true
            )

            if (gtSetB('debugLogging')) {
                href((sTIT): "Dump device map", description: sBLK, style: "page", page: "pageDump")
            }
        }
    }
}

@SuppressWarnings('MethodSize')
def deviceTypePreferences(Map ideviceType=null) {
    state.remove('currentlyEditingDeviceTrait')
    Map<String,Object> deviceType; deviceType= ideviceType
    if (deviceType == null) {
        deviceType = deviceTypeFromSettings(gtStStr('currentlyEditingDeviceType'))
    }

    if(deviceType!=null){
        String toAdd = gtSetStr('deviceTraitToAdd')
        if (toAdd != sNL) {
            app.removeSetting('deviceTraitToAdd')
            String traitName = "${deviceType.name}.traits.${toAdd}"
            addTraitToDeviceTypeState((String)deviceType.name, toAdd)
            return deviceTraitPreferences([(sNM): traitName])
        }

        String pinDel= gtStStr('pinToDelete')
        if (pinDel) {
            deleteDeviceTypePin(deviceType, pinDel)
            state.remove('pinToDelete')
        }
    } else {
        app.removeSetting('deviceTraitToAdd')
        state.remove('pinToDelete')
        (iZ..<state.nextDeviceTypeIndex).each { i ->
            deviceType = deviceTypeFromSettings("deviceTypes.${i}",true)
            //LOGGER.debug("deviceTypePreferences() $i checking $deviceType")
            if (deviceType != null && !deviceType.traits) {
                deleteDeviceType(deviceType)
            }
        }
        deviceType=null
    }

    return dynamicPage((sNM): "deviceTypePreferences", (sTIT): "Device Type Definition", nextPage: "mainPreferences") {
        String devicePropertyName = deviceType != null ? deviceType.name : "deviceTypes.${state.nextDeviceTypeIndex++}"
        state.currentlyEditingDeviceType = devicePropertyName
        section {
            input(
                (sNM): "${devicePropertyName}.display",
                (sTIT): "Device type name",
                (sTYPE): sTEXT,
                (sREQ): (!!gtSetStr("${devicePropertyName}.type") || !!gtSetStr("${devicePropertyName}.googleDeviceType"))
            )
            input(
                (sNM): "${devicePropertyName}.type",
                (sTIT): "Device type",
                (sTYPE): sENUM,
                (sOPTIONS): HUBITAT_DEVICE_TYPES,
                (sREQ): !!gtSetStr("${devicePropertyName}.display")
            )
            input(
                (sNM): "${devicePropertyName}.googleDeviceType",
                (sTIT): "Google Home device type",
                description: "The device type to report to Google Home",
                (sTYPE): sENUM,
                (sOPTIONS): GOOGLE_DEVICE_TYPES,
                (sREQ): !!gtSetStr("${devicePropertyName}.type")
            )
        }

        // todo
        /* section(sDBG +' deviceType: '+myObj(deviceType)){
            String str=getMapDescStr(deviceType,false)
            paragraph str
        } */

        Map<String,Map> currentDeviceTraits = (Map<String,Map>)deviceType?.traits ?: [:]
        section("Device Traits") {
            if (deviceType != null) {
                ((Map<String,Map>)deviceType.traits).each { String traitType, Map deviceTrait ->
                    href(
                        (sTIT): GOOGLE_DEVICE_TRAITS[traitType],
                        description: sBLK,
                        style: "page",
                        page: "deviceTraitPreferences",
                        params: deviceTrait
                    )
                }
            }

            Map<String,String> deviceTraitOptions = GOOGLE_DEVICE_TRAITS.findAll { key, value ->
                !(key in currentDeviceTraits.keySet())
            }
            input(
                (sNM): 'deviceTraitToAdd',
                (sTIT): "Add Trait",
                description: "Select a trait to add to this device...",
                (sTYPE): sENUM,
                (sOPTIONS): deviceTraitOptions,
                (sSUBONCHG): true
            )
        }

        List<String> deviceTypeCommands; deviceTypeCommands = []
        currentDeviceTraits.each { traitType, deviceTrait ->
            deviceTypeCommands += (List<String>)deviceTrait.commands
        }
        if (deviceTypeCommands) {
            section {
                input(
                    (sNM): "${devicePropertyName}.confirmCommands",
                    (sTIT): "The Google Assistant will ask for confirmation before performing these actions",
                    (sTYPE): sENUM,
                    (sOPTIONS): deviceTypeCommands,
                    (sMULTIPLE): true
                )
                input(
                    (sNM): "${devicePropertyName}.secureCommands",
                    (sTIT): "The Google Assistant will ask for a PIN code before performing these actions",
                    (sTYPE): sENUM,
                    (sOPTIONS): deviceTypeCommands,
                    (sMULTIPLE): true,
                    (sSUBONCHG): true
                )
            }

            if (deviceType?.secureCommands || deviceType?.pinCodes) {
                section {
                    input(
                        (sNM): "${devicePropertyName}.useDevicePinCodes",
                        (sTIT): "Select to use device driver pincodes.  Deselect to use Google Home Community app pincodes.",
                        (sTYPE): sBOOL,
                        (sDEFVAL): false,
                        (sSUBONCHG): true
                    )
                }

                if (deviceType?.useDevicePinCodes == true) {
                    // device attribute is set to use device driver pincodes
                    section("PIN Codes (Device Driver)") {
                        input(
                            (sNM): "${devicePropertyName}.pinCodeAttribute",
                            (sTIT): "Device pin code attribute",
                            (sTYPE): sTEXT,
                            (sDEFVAL): "lockCodes",
                            (sREQ): true
                        )

                        input(
                            (sNM): "${devicePropertyName}.pinCodeValue",
                            (sTIT): "Device pin code value",
                            (sTYPE): sTEXT,
                            (sDEFVAL): "code",
                            (sREQ): true
                        )
                    }
                } else {
                    // device attribute is set to use app pincodes
                    section("PIN Codes (Google Home Community)") {
                        ((List<Map>)deviceType.pinCodes).each { pinCode ->
                            input(
                                (sNM): "${devicePropertyName}.pin.${pinCode.id}.name",
                                (sTIT): "PIN Code Name",
                                (sTYPE): sTEXT,
                                (sREQ): true,
                                width: 6
                            )
                            input(
                                (sNM): "${devicePropertyName}.pin.${pinCode.id}.value",
                                (sTIT): "PIN Code Value",
                                (sTYPE): "password",
                                (sREQ): true,
                                width: 5
                            )
                            input(
                                (sNM): "deletePin:${devicePropertyName}.pin.${pinCode.id}",
                                (sTIT): "X",
                                (sTYPE): "button",
                                width: i1
                            )
                        }
                        if (deviceType?.secureCommands) {
                            input(
                                (sNM): "addPin:${devicePropertyName}",
                                (sTIT): "Add PIN Code",
                                (sTYPE): "button"
                            )
                        }
                    }
                }
            }
        }
    }
}

void deleteDeviceType(Map deviceType){
    LOGGER.debug("Deleting device type ${deviceType.display} ${deviceType.name}")
    List<String> s
    s=['display','type','googleDeviceType', 'devices',
        'confirmCommands','secureCommands','useDevicePinCodes', 'pinCodeAttribute',
        'picCodeValue']
    rmSettingList((String)deviceType.name,s)

    List<String> pinCodeIds = deviceType.pinCodes*.id
    pinCodeIds.each { pinCodeId -> deleteDeviceTypePin(deviceType, pinCodeId) }
    app.removeSetting("${deviceType.name}.pinCodes")
    ((Map<String,Map>)deviceType.traits).each { String traitType, Map deviceTrait -> deleteDeviceTrait(deviceTrait) }
    state.deviceTraits.remove(deviceType.name as String)
}

def deviceTypeDelete(Map deviceType) {
    return dynamicPage((sNM): "deviceTypeDelete", (sTIT): "Device Type Deleted", nextPage: "mainPreferences") {
        deleteDeviceType(deviceType)
        section {
            paragraph("The ${deviceType.display} device type was deleted")
        }
    }
}

@SuppressWarnings('unused')
def appInstructions(){
    return dynamicPage((sNM): "appInstructions", (sTIT): "Google Action Setup Instructions", install: true, uninstall: true) {
        section("Instructions") {
            paragraph(
                    '''\
                    Follow these steps to enable Google Home Graph Integration:
                        More instructions at: https://community.hubitat.com/t/alpha-community-maintained-google-home-integration/34957
                        
                      1) Install Hubitat Community-maintained App (should be done)
                          a) create a device to share with Google Home (to complete linking in step 3)
                              aa) Navigate to HE console -> Apps -> Google Home Community
                              bb) Select "Define new device type"
                              cc) Fill in "Device Type Definition"

                      2) Setup Google Home Graph API (should be done)
                          a) Enable Google Home Graph API at https://console.developers.google.com/apis/api/homegraph.googleapis.com/overview
                          b) Create a Service Account with Role='Service Account Token Creator' at https://console.cloud.google.com/apis/credentials/serviceaccountkey
                          c) From Service Accounts, go to Keys -> Add Key -> Create new key -> JSON and save to disk
                          d) Paste contents of the file in Google Service Account Authorization into this app

                      3) Create the Google smart home Action (these instructions)
                          a) Navigate to https://console.actions.google.com 
                              aa) Click "New project"
                              bb) Enter name for project; click "Create Project"
                              cc) Select "Smart Home" and click "Start Building"
                              dd) Click the "Develop" tab.
                              ee) On the "Invocation" screen, give your Action a name
                              ff) Click "Actions" in the menu
                              gg) Enter the Fulfillment URL (listed below)
                              hh) Click "Account linking" in the menu
                              ii) Enter the Client ID and Client Secret from Hubitat (see below)
                              jj) Enter Authorization URL (listed below)
                              kk) Enter Token URL (listed below)
                              ll) Click "Next"
                              mm) Leave everything unchecked in the "Use your app for account linking (optional)" section and click "Next"
                              nn) In the "Configure your client (optional)" section, enter "app" in the Scopes box
                              oo) Click "Save"

                              pp) Click the "Test" tab
                              qq) In the top-right of the page, click "Settings" and ensure "On device testing" is enabled
                              
                          b) In the Goggle Home app on your phone or tablet:
                              
                              a1) Select Devices, then Tap + Add 
                              b1) Tap "Set up device"
                              c1) tap "Works with Google"
                              d1) In the list, select the entry: [test] {your action name}
                              e1) Enter your Hubitat account credentials and click "Sign In"
                              f1) Select your hub and tap "Select"
                                  - Make sure at least one device is selected to expose to Google Home (Step 1a above)
                              g1) Tap "Authorize"
                              ...
                    '''.stripIndent()
            )
        }
        section("Hub details") {
            paragraph "Fulfillment URL: ${getFullApiServerUrl()}/action - NOTE OTHERWISE DO NOT SHARE BEYOND setup"
            paragraph "Client Id: See HE console -> Apps Code -> Google Home Community -> OAuth"
            paragraph "Client Secret: See HE console -> Apps Code -> Google Home Community -> OAuth"
            paragraph "Authorization URL: https://oauth.cloud.hubitat.com/oauth/authorize"
            paragraph "Token URL: https://oauth.cloud.hubitat.com/oauth/token"

        }
    }
}

def deviceTraitPreferences(Map ideviceTrait) {
    Map deviceTrait; deviceTrait=ideviceTrait
    if (deviceTrait == null) {
        deviceTrait = deviceTypeTraitFromSettings(gtStStr('currentlyEditingDeviceTrait'))
    } else {
        // re-load in case individual trait preferences functions have traits with (sSUBONCHG): true
        deviceTrait = deviceTypeTraitFromSettings((String)deviceTrait.name)
    }
    state.currentlyEditingDeviceTrait = (String)deviceTrait.name
    return dynamicPage(
        (sNM): "deviceTraitPreferences",
        (sTIT): "Preferences For ${GOOGLE_DEVICE_TRAITS[(String)deviceTrait.type]} Trait",
        nextPage: "deviceTypePreferences" ) {

        // get all the custom inputs
        "deviceTraitPreferences_${deviceTrait.type}"(deviceTrait)

        section {
            href(
                (sTIT): "Remove Device Trait",
                description: sBLK,
                style: "page",
                page: "deviceTraitDelete",
                params: deviceTrait
            )
        }
    }
}

def deviceTraitDelete(Map deviceTrait) {
    return dynamicPage((sNM): "deviceTraitDelete", (sTIT): "Device Trait Deleted", nextPage: "deviceTypePreferences") {
        deleteDeviceTrait(deviceTrait)
        section {
            paragraph("The ${GOOGLE_DEVICE_TRAITS[(String)deviceTrait.type]} trait was removed")
        }
    }
}

@SuppressWarnings(['MethodSize', 'UnusedPrivateMethod','unused'])
private deviceTraitPreferences_ArmDisarm(Map deviceTrait) {
    Map<String,String> hubitatAlarmLevels = [
        "disarmed":              "Disarm",
        "armed home":            "Home",
        "armed night":           "Night",
        "armed away":            "Away",
    ]
    Map<String,String> hubitatAlarmCommands = [
        "disarmed":              "disarm",
        "armed home":            "armHome",
        "armed night":           "armNight",
        "armed away":            "armAway",
    ]
    Map<String,String> hubitatAlarmValues = [
        "disarmed":              "disarmed",
        "armed home":            "armed home",
        "armed night":           "armed night",
        "armed away":            "armed away",
    ]

    String tname= (String)deviceTrait.name
    section("Arm/Disarm Settings") {
        input(
            (sNM): tname + ".armedAttribute",
            (sTIT): "Armed/Disarmed Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "securityKeypad",
            (sREQ): true
        )
        input(
            (sNM): tname + ".armLevelAttribute",
            (sTIT): "Current Arm Level Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "securityKeypad",
            (sREQ): true
        )
        input(
            (sNM): tname + ".exitAllowanceAttribute",
            (sTIT): "Exit Delay Value Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "exitAllowance",
            (sREQ): true
        )
        input(
            (sNM): tname + ".cancelCommand",
            (sTIT): "Cancel Arming Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "disarm",
            (sREQ): true
        )
        input(
            (sNM): tname + ".armLevels",
            (sTIT): "Supported Alarm Levels",
            (sTYPE): sENUM,
            (sOPTIONS): hubitatAlarmLevels,
            (sMULTIPLE): true,
            (sREQ): true,
            (sSUBONCHG): true
        )

        ((Map<String,Object>)deviceTrait.armLevels).each { armLevel ->
            String aname= armLevel.key
            paragraph("A comma-separated list of names that the Google Assistant will " +
                    "accept for this alarm setting")
            input(
                (sNM): tname + ".armLevels.${aname}.googleNames",
                (sTIT): "Google Home Level Names for ${hubitatAlarmLevels[aname]}",
                description: "A comma-separated list of names that the Google Assistant will " +
                             "accept for this alarm setting",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): hubitatAlarmLevels[aname]
            )

            input(
                (sNM): tname + ".armCommands.${aname}.commandName",
                (sTIT): "Hubitat Command for ${hubitatAlarmLevels[aname]}",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): hubitatAlarmCommands[aname]
            )

            input(
                (sNM): tname + ".armValues.${aname}.value",
                (sTIT): "Hubitat Value for ${hubitatAlarmLevels[aname]}",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): hubitatAlarmValues[aname]
            )
        }
        input(
            (sNM): tname + ".returnUserIndexToDevice",
            (sTIT): "Select to return the user index with the device command on a pincode match. " +
                "Not all device drivers support this operation.",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sSUBONCHG): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_Brightness(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section ("Brightness Settings") {
        input(
            (sNM): tname + ".brightnessAttribute",
            (sTIT): "Current Brightness Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "level",
            (sREQ): true
        )
        input(
            (sNM): tname + ".setBrightnessCommand",
            (sTIT): "Set Brightness Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "setLevel",
            (sREQ): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_CameraStream(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Stream Camera") {
        input(
            (sNM): tname + ".cameraStreamURLAttribute",
            (sTIT): "Camera Stream URL Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "streamURL",
            (sREQ): true
        )
        input(
            (sNM): tname + ".cameraSupportedProtocolsAttribute",
            (sTIT): "Camera Stream Supported Protocols Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "supportedProtocols",
            (sREQ): true
        )
        input(
            (sNM): tname + ".cameraStreamProtocolAttribute",
            (sTIT): "Camera Stream Protocol Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "streamProtocol",
            (sREQ): true
        )
        input(
            (sNM): tname + ".cameraStreamCommand",
            (sTIT): "Start Camera Stream Command",
            (sTYPE): sTEXT,
            (sDEFVAL): sON,
            (sREQ): true
        )
    }
}

@SuppressWarnings(['MethodSize', 'UnusedPrivateMethod','unused'])
private deviceTraitPreferences_ColorSetting(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section ("Color Setting Preferences") {
        input(
            (sNM): tname + ".fullSpectrum",
            (sTIT): "Full-Spectrum Color Control",
            (sTYPE): sBOOL,
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.fullSpectrum) {
            input(
                (sNM): tname + ".hueAttribute",
                (sTIT): "Hue Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "hue",
                (sREQ): true
            )
            input(
                (sNM): tname + ".saturationAttribute",
                (sTIT): "Saturation Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "saturation",
                (sREQ): true
            )
            input(
                (sNM): tname + ".levelAttribute",
                (sTIT): "Level Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "level",
                (sREQ): true
            )
            input(
                (sNM): tname + ".setColorCommand",
                (sTIT): "Set Color Command",
                (sTYPE): sTEXT,
                (sDEFVAL): "setColor",
                (sREQ): true
            )
        }
        input(
            (sNM): tname + ".colorTemperature",
            (sTIT): "Color Temperature Control",
            (sTYPE): sBOOL,
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.colorTemperature) {
            input(
                (sNM): tname + ".colorTemperature.min",
                (sTIT): "Minimum Color Temperature",
                (sTYPE): "number",
                (sDEFVAL): 2200,
                (sREQ): true
            )
            input(
                (sNM): tname + ".colorTemperature.max",
                (sTIT): "Maximum Color Temperature",
                (sTYPE): "number",
                (sDEFVAL): 6500,
                (sREQ): true
            )
            input(
                (sNM): tname + ".colorTemperatureAttribute",
                (sTIT): "Color Temperature Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "colorTemperature",
                (sREQ): true
            )
            input(
                (sNM): tname + ".setColorTemperatureCommand",
                (sTIT): "Set Color Temperature Command",
                (sTYPE): sTEXT,
                (sDEFVAL): "setColorTemperature",
                (sREQ): true
            )
        }
        if (deviceTrait.fullSpectrum && deviceTrait.colorTemperature) {
            input(
                (sNM): tname + ".colorModeAttribute",
                (sTIT): "Color Mode Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "colorMode",
                (sREQ): true
            )
            input(
                (sNM): tname + ".fullSpectrumModeValue",
                (sTIT): "Full-Spectrum Mode Value",
                (sTYPE): sTEXT,
                (sDEFVAL): "RGB",
                (sREQ): true
            )
            input(
                (sNM): tname + ".temperatureModeValue",
                (sTIT): "Color Temperature Mode Value",
                (sTYPE): sTEXT,
                (sDEFVAL): "CT",
                (sREQ): true
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_Dock(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Dock Settings") {
        input(
            (sNM): tname + ".dockAttribute",
            (sTIT): "Dock Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "status",
            (sREQ): true
        )
        input(
            (sNM): tname + ".dockValue",
            (sTIT): "Docked Value",
            (sTYPE): sTEXT,
            (sDEFVAL): "docked",
            (sREQ): true
        )
        input(
            (sNM): tname + ".dockCommand",
            (sTIT): "Dock Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "returnToDock",
            (sREQ): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'MethodSize'])
private deviceTraitPreferences_EnergyStorage(Map deviceTrait) {
    Map<String,String> googleEnergyStorageDistanceUnitForUX = [
        "KILOMETERS":      "Kilometers",
        "MILES":           "Miles",
    ]
    Map<String,String> googleCapacityUnits = [
        "SECONDS":         "Seconds",
        "MILES":           "Miles",
        "KILOMETERS":      "Kilometers",
        "PERCENTAGE":      "Percentage",
        "KILOWATT_HOURS":  "Kilowatt Hours",
    ]
    String tname= (String)deviceTrait.name
    section("Energy Storage Settings") {
        input(
            (sNM): tname + ".isRechargeable",
            (sTIT): "Rechargeable",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true
        )
        input(
            (sNM): tname + ".capacityRemainingRawValue",
            (sTIT): "Capacity Remaining Value",
            (sTYPE): sTEXT,
            (sDEFVAL): "battery",
            (sREQ): true
        )
        input(
            (sNM): tname + ".capacityRemainingUnit",
            (sTIT): "Capacity Remaining Unit",
            (sTYPE): sENUM,
            (sOPTIONS): googleCapacityUnits,
            (sDEFVAL): "PERCENTAGE",
            (sMULTIPLE): false,
            (sREQ): true,
            (sSUBONCHG): true
        )
    }
    section(hideable: true, hidden: true, "Advanced Settings") {
        input(
            (sNM): tname + ".queryOnlyEnergyStorage",
            (sTIT): "Query Only Energy Storage",
            (sTYPE): sBOOL,
            (sDEFVAL): true,
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.queryOnlyEnergyStorage == false) {
            input(
                (sNM): tname + ".chargeCommand",
                (sTIT): "Charge Command",
                (sTYPE): sTEXT,
                (sREQ): true
            )
        }
        input(
            (sNM): tname + ".capacityUntilFullRawValue",
            (sTIT): "Capacity Until Full Value",
            (sTYPE): sTEXT,
        )
        input(
            (sNM): tname + ".capacityUntilFullUnit",
            (sTIT): "Capacity Until Full Unit",
            (sTYPE): sENUM,
            (sOPTIONS): googleCapacityUnits,
            (sMULTIPLE): false,
            (sSUBONCHG): true
        )
        input(
            (sNM): tname + ".descriptiveCapacityRemainingAttribute",
            (sTIT): "Descriptive Capacity Remaining",
            (sTYPE): sTEXT,
        )
        input(
            (sNM): tname + ".isChargingAttribute",
            (sTIT): "Charging Attribute",
            (sTYPE): sTEXT,
        )
        input(
            (sNM): tname + ".chargingValue",
            (sTIT): "Charging Value",
            (sTYPE): sTEXT,
        )
        input(
            (sNM): tname + ".isPluggedInAttribute",
            (sTIT): "Plugged in Attribute",
            (sTYPE): sTEXT,
        )
        input(
            (sNM): tname + ".pluggedInValue",
            (sTIT): "Plugged in Value",
            (sTYPE): sTEXT,
        )
        List<String> mk = ["MILES","KILOMETERS"]
        if ( ((String)deviceTrait.capacityRemainingUnit in mk)
            || ((String)deviceTrait.capacityUntilFullUnit in mk) ) {
            input(
                (sNM): tname + ".energyStorageDistanceUnitForUX",
                (sTIT): "Supported Distance Units",
                (sTYPE): sENUM,
                (sOPTIONS): googleEnergyStorageDistanceUnitForUX,
                (sMULTIPLE): false,
                (sSUBONCHG): true
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_FanSpeed(Map deviceTrait) {
    Map<String,String> hubitatFanSpeeds = [
        "low":         "Low",
        "medium-low":  "Medium-Low",
        "medium":      "Medium",
        "medium-high": "Medium-High",
        "high":        "High",
        "auto":        "Auto",
    ]
    String tname= (String)deviceTrait.name
    section("Fan Speed Settings") {
        input(
            (sNM): tname + ".currentSpeedAttribute",
            (sTIT): "Current Speed Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "speed",
            (sREQ): true
        )
        input(
            (sNM): tname + ".setFanSpeedCommand",
            (sTIT): "Set Speed Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "setSpeed",
            (sREQ): true
        )
        input(
            (sNM): tname + ".fanSpeeds",
            (sTIT): "Supported Fan Speeds",
            (sTYPE): sENUM,
            (sOPTIONS): hubitatFanSpeeds,
            (sMULTIPLE): true,
            (sREQ): true,
            (sSUBONCHG): true
        )
        ((Map<String,Object>)deviceTrait.fanSpeeds).each { fanSpeed ->
            paragraph("A comma-separated list of names that the Google Assistant will " +
                    "accept for this speed setting")
            input(
                (sNM): tname + ".speed.${fanSpeed.key}.googleNames",
                (sTIT): "Google Home Level Names for ${hubitatFanSpeeds[fanSpeed.key]}",
                description: "A comma-separated list of names that the Google Assistant will " +
                             "accept for this speed setting",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): hubitatFanSpeeds[fanSpeed.key]
            )
        }
    }

    section("Reverse Settings") {
        input(
            (sNM): tname + ".reversible",
            (sTIT): "Reversible",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sSUBONCHG): true
        )

        if (settings."${deviceTrait.name}.reversible") {
            input(
                (sNM): tname + ".reverseCommand",
                (sTIT): "Reverse Command",
                (sTYPE): sTEXT,
                (sREQ): true
            )
        }
    }

    section("Supports Percentage Settings") {
        input(
            (sNM): tname + ".supportsFanSpeedPercent",
            (sTIT): "Supports Fan Speed Percent",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sSUBONCHG): true
        )

        if (settings."${deviceTrait.name}.supportsFanSpeedPercent") {
            input(
                (sNM): tname + ".currentFanSpeedPercent",
                (sTIT): "Current Fan Speed Percentage Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "level",
                (sREQ): true
            )

            input(
                (sNM): tname + ".setFanSpeedPercentCommand",
                (sTIT): "Fan Speed Percent Command",
                (sTYPE): sTEXT,
                (sDEFVAL): "setLevel",
                (sREQ): true
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_HumiditySetting(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Humidity Setting Preferences") {
        input(
            (sNM): tname + ".humidityAttribute",
            (sTIT): "Humidity Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "humidity",
            (sREQ): true
        )
        input(
            (sNM): tname + ".queryOnly",
            (sTIT): "Query Only Humidity",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            require: true,
            (sSUBONCHG): true
        )
        if (!deviceTrait.queryOnly) {
            input(
                (sNM): tname + ".humiditySetpointAttribute",
                (sTIT): "Humidity Setpoint Attribute",
                (sTYPE): sTEXT,
                (sREQ): true
            )
            input(
                (sNM): tname + ".setHumidityCommand",
                (sTIT): "Set Humidity Command",
                (sTYPE): sTEXT,
                (sREQ): true
            )
            paragraph("If either a minimum or maximum humidity setpoint is configured then the other must be as well")
            input(
                (sNM): tname + ".humidityRange.min",
                (sTIT): "Minimum Humidity Setpoint",
                (sTYPE): "number",
                (sREQ): deviceTrait.humidityRange?.max != null,
                (sSUBONCHG): true
            )
            input(
                (sNM): tname + ".humidityRange.max",
                (sTIT): "Maximum Humidity Setpoint",
                (sTYPE): "number",
                (sREQ): deviceTrait.humidityRange?.min != null,
                (sSUBONCHG): true
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_Locator(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Locator Settings") {
        input(
            (sNM): tname + ".locatorCommand",
            (sTIT): "Locator Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "locate",
            (sREQ): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_LockUnlock(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Lock/Unlock Settings") {
        input(
            (sNM): tname + ".lockedUnlockedAttribute",
            (sTIT): "Locked/Unlocked Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "lock",
            (sREQ): true
        )
        input(
            (sNM): tname + ".lockedValue",
            (sTIT): "Locked Value",
            (sTYPE): sTEXT,
            (sDEFVAL): "locked",
            (sREQ): true
        )
        input(
            (sNM): tname + ".lockCommand",
            (sTIT): "Lock Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "lock",
            (sREQ): true
        )
        input(
            (sNM): tname + ".unlockCommand",
            (sTIT): "Unlock Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "unlock",
            (sREQ): true
        )
        input(
            (sNM): tname + ".returnUserIndexToDevice",
            (sTIT): "Select to return the user index with the device command on a pincode match. " +
                "Not all device drivers support this operation.",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sSUBONCHG): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_MediaState(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Media State Settings") {
        input(
            (sNM): tname + ".supportActivityState",
            (sTIT): "Support Activity State",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true,
        )
        if (deviceTrait.supportActivityState) {
            input(
                (sNM): tname + ".activityStateAttribute",
                (sTIT): "Activity State Attribute",
                (sTYPE): sTEXT,
                (sREQ): true
            )
        }
        input(
            (sNM): tname + ".supportPlaybackState",
            (sTIT): "Support Playback State",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true,
        )
        if (deviceTrait.supportPlaybackState) {
            input(
                (sNM): tname + ".playbackStateAttribute",
                (sTIT): "Playback State Attribute",
                (sTYPE): sTEXT,
                (sDEFVAL): "status",
                (sREQ): true
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_OccupancySensing(Map deviceTrait) {
    Map<String,String> SENSOR_TYPES = [
        PIR:              "Passive Infrared (PIR)",
        ULTRASONIC:       "Ultrasonic",
        PHYSICAL_CONTACT: "Physical Contact"
    ]
    String tname= (String)deviceTrait.name
    section("Occupancy Sensing Settings") {
        input(
            (sNM): tname + ".occupancySensorType",
            (sTIT): "Sensor Type",
            (sTYPE): sENUM,
            (sOPTIONS): SENSOR_TYPES,
            (sREQ): true
        )
        input(
            (sNM): tname + ".occupancyAttribute",
            (sTIT): "Occupancy Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "motion",
            (sREQ): true
        )
        input(
            (sNM): tname + ".occupiedValue",
            (sTIT): "Occupied Value",
            (sTYPE): sTEXT,
            (sDEFVAL): "active",
            (sREQ): true
        )
        input(
            (sNM): tname + ".occupiedToUnoccupiedDelaySec",
            (sTIT): "Occupied to Unoccupied Delay (seconds)",
            (sTYPE): "number",
            (sSUBONCHG): true
        )
        input(
            (sNM): tname + ".unoccupiedToOccupiedDelaySec",
            (sTIT): "Unoccupied to Occupied Delay (seconds)",
            (sTYPE): "number",
            (sSUBONCHG): true,
            (sREQ): deviceTrait.occupiedToUnoccupiedDelaySec != null
        )
        input(
            (sNM): tname + ".unoccupiedToOccupiedEventThreshold",
            (sTIT): "Unoccupied to Occupied Event Threshold",
            (sTYPE): "number",
            (sSUBONCHG): true,
            (sREQ): deviceTrait.unoccupiedToOccupiedDelaySec != null,
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_OnOff(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("On/Off Settings") {
        input(
            (sNM): tname + ".onOffAttribute",
            (sTIT): "On/Off Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "switch",
            (sREQ): true
        )
        paragraph("At least one of On Value or Off Value must be specified")
        input(
            (sNM): tname + ".onValue",
            (sTIT): "On Value",
            (sTYPE): sTEXT,
            (sDEFVAL): deviceTrait.offValue ? sBLK : sON,
            (sSUBONCHG): true,
            (sREQ): !deviceTrait.offValue
        )
        input(
            (sNM): tname + ".offValue",
            (sTIT): "Off Value",
            (sTYPE): sTEXT,
            (sDEFVAL): deviceTrait.onValue ? sBLK : sOFF,
            (sSUBONCHG): true,
            (sREQ): !deviceTrait.onValue
        )
        input(
            (sNM): tname + ".controlType",
            (sTIT): "Control Type",
            (sTYPE): sENUM,
            (sOPTIONS): [
                "separate": "Separate On and Off commands",
                "single": "Single On/Off command with parameter"
            ],
            (sDEFVAL): "separate",
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.controlType != "single") {
            input(
                (sNM): tname + ".onCommand",
                (sTIT): "On Command",
                (sTYPE): sTEXT,
                (sDEFVAL): sON,
                (sREQ): true
            )
            input(
                (sNM): tname + ".offCommand",
                (sTIT): "Off Command",
                (sTYPE): sTEXT,
                (sDEFVAL): sOFF,
                (sREQ): true
            )
        } else {
            input(
                (sNM): tname + ".onOffCommand",
                (sTIT): "On/Off Command",
                (sTYPE): sTEXT,
                (sREQ): true
            )
            input(
                (sNM): tname + ".onParameter",
                (sTIT): "On Parameter",
                (sTYPE): sTEXT,
                (sREQ): true
            )
            input(
                (sNM): tname + ".offParameter",
                (sTIT): "Off Parameter",
                (sTYPE): sTEXT,
                (sREQ): true
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_OpenClose(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Open/Close Settings") {
        paragraph("Can this device only be fully opened/closed, or can it be partially open?")
        input(
            (sNM): tname + ".discreteOnlyOpenClose",
            (sTIT): "Discrete Only Open/Close",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true
        )
        paragraph("Commonly door or valve")
        input(
            (sNM): tname + ".openCloseAttribute",
            (sTIT): "Open/Close Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "door",
            (sREQ): true
        )

        if (deviceTrait.discreteOnlyOpenClose) {
            paragraph("Values of the Open/Close Attribute that indicate this device is open.  " +
                    "Separate multiple values with a comma")
            input(
                (sNM): tname + ".openValue",
                (sTIT): "Open Values",
                description: "Values of the Open/Close Attribute that indicate this device is open.  " +
                             "Separate multiple values with a comma",
                (sTYPE): sTEXT,
                (sDEFVAL): "open",
                (sREQ): true
            )
            paragraph("Values of the Open/Close Attribute that indicate this device is closed.  " +
                    "Separate multiple values with a comma")
            input(
                (sNM): tname + ".closedValue",
                (sTIT): "Closed Values",
                description: "Values of the Open/Close Attribute that indicate this device is closed.  " +
                             "Separate multiple values with a comma",
                (sTYPE): sTEXT,
                (sDEFVAL): "closed",
                (sREQ): true
            )
        } else {
            paragraph("Set this if your device considers position 0 to be fully open")
            input(
                (sNM): tname + ".reverseDirection",
                (sTIT): "Reverse Direction",
                (sTYPE): sBOOL
            )
        }
        input(
            (sNM): tname + ".queryOnly",
            (sTIT): "Query Only Open/Close",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (!deviceTrait.queryOnly) {
            if (deviceTrait.discreteOnlyOpenClose) {
                input(
                    (sNM): tname + ".openCommand",
                    (sTIT): "Open Command",
                    (sTYPE): sTEXT,
                    (sDEFVAL): "open",
                    (sREQ): true
                )
                input(
                    (sNM): tname + ".closeCommand",
                    (sTIT): "Close Command",
                    (sTYPE): sTEXT,
                    (sDEFVAL): "close",
                    (sREQ): true
                )
            } else {
                input(
                    (sNM): tname + ".openPositionCommand",
                    (sTIT): "Open Position Command",
                    (sTYPE): sTEXT,
                    (sDEFVAL): "setPosition",
                    (sREQ): true
                )
            }
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_Reboot(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Reboot Preferences") {
        input(
            (sNM): tname + ".rebootCommand",
            (sTIT): "Reboot Command",
            (sTYPE): sTEXT,
            (sREQ): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_Rotation(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Rotation Preferences") {
        input(
            (sNM): tname + ".rotationAttribute",
            (sTIT): "Current Rotation Attribute",
            (sTYPE): sTEXT,
            (sREQ): true
        )
        input(
            (sNM): tname + ".setRotationCommand",
            (sTIT): "Set Rotation Command",
            (sTYPE): sTEXT,
            (sREQ): true
        )
        input(
            (sNM): tname + ".continuousRotation",
            (sTIT): "Supports Continuous Rotation",
            (sTYPE): sBOOL,
            (sDEFVAL): false
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_Scene(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Scene Preferences") {
        input(
            (sNM): tname + ".activateCommand",
            (sTIT): "Activate Command",
            (sTYPE): sTEXT,
            (sDEFVAL): sON,
            (sREQ): true
        )
        input(
            (sNM): tname + ".sceneReversible",
            (sTIT): "Can this scene be deactivated?",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (settings."${deviceTrait.name}.sceneReversible") {
            input(
                (sNM): tname + ".deactivateCommand",
                (sTIT): "Deactivate Command",
                (sTYPE): sTEXT,
                (sDEFVAL): sOFF,
                (sREQ): true
            )
        }
    }
}

@SuppressWarnings(['MethodSize', 'UnusedPrivateMethod','unused'])
private deviceTraitPreferences_SensorState(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Sensor State Settings") {
        input(
            (sNM): tname + ".sensorTypes",
            (sTIT): "Supported Sensor Types",
            (sTYPE): sENUM,
            (sOPTIONS): GOOGLE_SENSOR_STATES.collectEntries {key, value ->
                [key, value.label]
            },
            (sMULTIPLE): true,
            (sREQ): true,
            (sSUBONCHG): true
        )

        ((Map<String,Object>)deviceTrait.sensorTypes).each { sensorType ->
            String sname= (String)sensorType.key
            // only display if the sensor has descriptive values
            if (GOOGLE_SENSOR_STATES[sname].descriptiveState) {
                // if the sensor does not have a numeric state, do not allow the user to disable the descriptive state
                if (GOOGLE_SENSOR_STATES[sname].numericAttribute != sBLK) {
                    input(
                        (sNM): tname + ".sensorTypes.${sname}.reportsDescriptiveState",
                        (sTIT): "${sname} reports descriptive state",
                        (sTYPE): sBOOL,
                        (sDEFVAL): true,
                        (sREQ): true,
                        (sSUBONCHG): true
                    )
                }
                if (deviceTrait.sensorTypes[sname].reportsDescriptiveState) {
                    input(
                        (sNM): tname + ".sensorTypes.${sname}.availableStates",
                        (sTIT): "Google Home Available States for ${sname}",
                        (sTYPE): sENUM,
                        (sMULTIPLE): true,
                        (sREQ): true,
                        (sOPTIONS): GOOGLE_SENSOR_STATES[sname].descriptiveState,
                    )
                    input(
                        (sNM): tname + ".sensorTypes.${sname}.descriptiveAttribute",
                        (sTIT): "Hubitat Descriptive State Attribute for ${sname}",
                        (sTYPE): sTEXT,
                        (sREQ): true,
                        (sDEFVAL): GOOGLE_SENSOR_STATES[sname].descriptiveAttribute
                    )
                }
            }
            // only display if the sensor has numerical values
            if (GOOGLE_SENSOR_STATES[sname].numericAttribute) {
                // if the sensor does not have a descriptive state, do not allow the user to disable the numeric state
                if (GOOGLE_SENSOR_STATES[sname].descriptiveState != sBLK) {
                    input(
                        (sNM): tname + ".sensorTypes.${sname}.reportsNumericState",
                        (sTIT): "${sname} reports numeric values",
                        (sTYPE): sBOOL,
                        (sDEFVAL): true,
                        (sREQ): true,
                        (sSUBONCHG): true
                    )
                }
                if (deviceTrait.sensorTypes[sname].reportsNumericState) {
                    input(
                        (sNM): tname + ".sensorTypes.${sname}.numericAttribute",
                        (sTIT): "Hubitat Numeric Attribute for ${sname} with units ${GOOGLE_SENSOR_STATES[sname].numericUnits}",
                        (sTYPE): sTEXT,
                        (sREQ): true,
                        (sDEFVAL): GOOGLE_SENSOR_STATES[sname].numericAttribute
                    )
                    input(
                        (sNM): tname + ".sensorTypes.${sname}.numericUnits",
                        (sTIT): "Google Numeric Units for ${sname}",
                        (sTYPE): sTEXT,
                        (sREQ): true,
                        (sDEFVAL): GOOGLE_SENSOR_STATES[sname].numericUnits
                    )
                }
            }
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_SoftwareUpdate(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Software Update Settings") {
        input(
            (sNM): tname + ".lastSoftwareUpdateUnixTimestampSecAttribute",
            (sTIT): "Last Software Update Unix Timestamp in Seconds Attribute",
            (sTYPE): sTEXT,
            (sREQ): true
        )
        input(
            (sNM): tname + ".softwareUpdateCommand",
            (sTIT): "Software Update Command",
            (sTYPE): sTEXT,
            (sREQ): true
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_StartStop(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Start/Stop Settings") {
        input(
            (sNM): tname + ".startStopAttribute",
            (sTIT): "Start/Stop Attribute",
            (sTYPE): sTEXT,
            (sDEFVAL): "status",
            (sREQ): true
        )
        input(
            (sNM): tname + ".startValue",
            (sTIT): "Start Value",
            (sTYPE): sTEXT,
            (sDEFVAL): "running",
            (sREQ): true
        )
        input(
            (sNM): tname + ".stopValue",
            (sTIT): "Stop Value",
            (sTYPE): sTEXT,
            (sDEFVAL): "returning to dock",
            (sREQ): true
        )
        input(
            (sNM): tname + ".startCommand",
            (sTIT): "Start Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "start",
            (sREQ): true
        )
        input(
            (sNM): tname + ".stopCommand",
            (sTIT): "Stop Command",
            (sTYPE): sTEXT,
            (sDEFVAL): "returnToDock",
            (sREQ): true
        )
        input(
            (sNM):tname + ".canPause",
            (sTYPE): sBOOL,
            (sTIT): "Is this device pausable? disable this option if not pausable",
            (sDEFVAL): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.canPause) {
            input(
                (sNM): tname + ".pauseUnPauseAttribute",
                (sTIT): "Pause/UnPause Attribute",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "status"
            )
            input(
                (sNM): tname + ".pauseValue",
                (sTIT): "Pause Value",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "paused",
            )
            input(
                (sNM): tname + ".pauseCommand",
                (sTIT): "Pause Command",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "pause"
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_TemperatureControl(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section ("Temperature Control Preferences") {
        input(
            (sNM): tname + ".temperatureUnit",
            (sTIT): "Temperature Unit",
            (sTYPE): sENUM,
            (sOPTIONS): [
                "C": "Celsius",
                "F": "Fahrenheit"
            ],
            (sREQ): true,
            (sDEFVAL): temperatureScale
        )
        input(
            (sNM): tname + ".currentTemperatureAttribute",
            (sTIT): "Current Temperature Attribute",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "temperature"
        )
        input(
            (sNM): tname + ".queryOnly",
            (sTIT): "Query Only Temperature Control",
            (sTYPE): sBOOL,
            (sDEFVAL): false,
            (sREQ): true,
            (sSUBONCHG): true
        )
        if (!deviceTrait.queryOnly) {
            input(
                (sNM): tname + ".setpointAttribute",
                (sTIT): "Current Temperature Setpoint Attribute",
                (sTYPE): sTEXT,
                (sREQ): true
            )
            input(
                (sNM): tname + ".setTemperatureCommand",
                (sTIT): "Set Temperature Command",
                (sTYPE): sTEXT,
                (sREQ): true
            )
            input(
                (sNM): tname + ".minTemperature",
                (sTIT): "Minimum Temperature Setting",
                (sTYPE): "decimal",
                (sREQ): true
            )
            input(
                (sNM): tname + ".maxTemperature",
                (sTIT): "Maximum Temperature Setting",
                (sTYPE): "decimal",
                (sREQ): true
            )
            input(
                (sNM): tname + ".temperatureStep",
                (sTIT): "Temperature Step",
                (sTYPE): "decimal"
            )
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
def deviceTraitPreferences_TemperatureSetting(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section ("Temperature Setting Preferences") {
        input(
            (sNM): tname + ".temperatureUnit",
            (sTIT): "Temperature Unit",
            (sTYPE): sENUM,
            (sOPTIONS): [
                "C": "Celsius",
                "F": "Fahrenheit"
            ],
            (sREQ): true,
            (sDEFVAL): temperatureScale
        )
        input(
            (sNM): tname + ".currentTemperatureAttribute",
            (sTIT): "Current Temperature Attribute",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "temperature"
        )
        input(
            (sNM): tname + ".queryOnly",
            (sTIT): "Query Only Temperature Setting",
            (sTYPE): sBOOL,
            (sREQ): true,
            (sDEFVAL): false,
            (sSUBONCHG): true
        )
        if (!deviceTrait.queryOnly) {
            temperatureSettingControlPreferences(deviceTrait)
        }
    }
}

private static List<Map> thermostatSetpointAttributePreferenceForModes(List<String> modes) {
    def setpointAttributeDefaults = [
        "heatingSetpointAttribute": "heatingSetpoint",
        "coolingSetpointAttribute": "coolingSetpoint"
    ]
    List<Map> prefs = []
    modes.each { String mode ->
        Map pref = THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES[mode]
        if (pref) {
            String s= (String)pref.name
            prefs << [
                (sNM):         s,
                (sTIT):        pref.title,
                (sDEFVAL): setpointAttributeDefaults[s]
            ]
        }
    }
    return prefs
}

private static List<Map> thermostatSetpointCommandPreferenceForModes(List<String> modes) {
    Map<String,String> setpointCommandDefaults = [
        "setHeatingSetpointCommand": "setHeatingSetpoint",
        "setCoolingSetpointCommand": "setCoolingSetpoint"
    ]
    List<Map> prefs = []
    modes.each { String mode ->
        Map pref = THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES[mode]
        if (pref) {
            String s= (String)pref.name
            prefs << [
                (sNM):         s,
                (sTIT):        pref.title,
                (sDEFVAL): setpointCommandDefaults[s]
            ]
        }
    }
    return prefs
}

@SuppressWarnings('MethodSize')
private temperatureSettingControlPreferences(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    input(
        (sNM): tname + ".modes",
        (sTIT): "Supported Modes",
        (sTYPE): sENUM,
        (sOPTIONS): GOOGLE_THERMOSTAT_MODES,
        (sMULTIPLE): true,
        (sREQ): true,
        (sSUBONCHG): true
    )

    List<String> supportedModes = (List<String>)settings."${deviceTrait.name}.modes"
    List attributePreferences = []
    List commandPreferences = []
    supportedModes.each { mode ->
        List<Map> attrPrefs
        List<Map> commandPrefs
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
    List attributeCommandPairs = [attributePreferences, commandPreferences].transpose()
    attributeCommandPairs.each { attributePreference, commandPreference ->
        input(
            (sNM): tname + ".${attributePreference.name}",
            (sTIT): attributePreference.title,
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): attributePreference.defaultValue
        )
        input(
            (sNM): tname + ".${commandPreference.name}",
            (sTIT): commandPreference.title,
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): commandPreference.defaultValue
        )
    }

    if (supportedModes) {
        if ("heatcool" in supportedModes) {
            paragraph("The minimum offset between the heat and cool setpoints when operating in heat/cool mode")
            input(
                (sNM): tname + ".heatcoolBuffer",
                (sTIT): "Temperature Buffer",
                description: "The minimum offset between the heat and cool setpoints when operating in heat/cool mode",
                (sTYPE): "decimal"
            )
        }
        def defaultModeMapping = [
            (sOFF):      sOFF,
            (sON):       sBLK,
            "heat":     "heat",
            "cool":     "cool",
            "heatcool": "auto",
            "auto":     sBLK,
            "fan-only": sBLK,
            "purifier": sBLK,
            "eco":      sBLK,
            "dry":      sBLK
        ]
        supportedModes.each { mode ->
            paragraph("The mode name used by hubitat for the ${GOOGLE_THERMOSTAT_MODES[mode]} mode")
            input(
                (sNM): tname + ".mode.${mode}.hubitatMode",
                (sTIT): "${GOOGLE_THERMOSTAT_MODES[mode]} Hubitat Mode",
                description: "The mode name used by hubitat for the ${GOOGLE_THERMOSTAT_MODES[mode]} mode",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): defaultModeMapping[mode]
            )
        }
    }
    input(
        (sNM): tname + ".setModeCommand",
        (sTIT): "Set Mode Command",
        (sTYPE): sTEXT,
        (sREQ): true,
        (sDEFVAL): "setThermostatMode"
    )
    input(
        (sNM): tname + ".currentModeAttribute",
        (sTIT): "Current Mode Attribute",
        (sTYPE): sTEXT,
        (sREQ): true,
        (sDEFVAL): "thermostatMode"
    )
    paragraph("If either the minimum setpoint or maximum setpoint is given then both must be given")
    input(
        (sNM): tname + ".range.min",
        (sTIT): "Minimum Set Point",
        (sTYPE): "decimal",
        (sREQ): settings."${deviceTrait.name}.range.max" != null,
        (sSUBONCHG): true
    )
    input(
        (sNM): tname + ".range.max",
        (sTIT): "Maximum Set Point",
        (sTYPE): "decimal",
        (sREQ): settings."${deviceTrait.name}.range.min" != null,
        (sSUBONCHG): true
    )
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_Timer(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Timer Settings") {
        input(
            (sNM): tname + ".commandOnlyTimer",
            (sTIT): "Command Only (No Query)",
            (sTYPE): sBOOL,
            (sREQ): true,
            (sDEFVAL): false,
            (sSUBONCHG): true,
        )
        input(
            (sNM): tname + ".maxTimerLimitSec",
            (sTIT): "Maximum Timer Duration (seconds)",
            (sTYPE): "integer",
            (sREQ): true,
            (sDEFVAL): "86400"
        )
        if (deviceTrait.commandOnlyTimer == false) {
            input(
                (sNM): tname + ".timerRemainingSecAttribute",
                (sTIT): "Time Remaining Attribute",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "timeRemaining"
            )
            input(
                (sNM): tname + ".timerPausedAttribute",
                (sTIT): "Timer Paused Attribute",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "sessionStatus"
            )
            input(
                (sNM): tname + ".timerPausedValue",
                (sTIT): "Timer Paused Value",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "paused"
            )
        }
        input(
            (sNM): tname + ".timerStartCommand",
            (sTIT): "Timer Start Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "startTimer"
        )
        input(
            (sNM): tname + ".timerAdjustCommand",
            (sTIT): "Timer Adjust Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "setTimeRemaining"
        )
        input(
            (sNM): tname + ".timerCancelCommand",
            (sTIT): "Timer Cancel Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "cancel"
        )
        input(
            (sNM): tname + ".timerPauseCommand",
            (sTIT): "Timer Pause Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "pause"
        )
        input(
            (sNM): tname + ".timerResumeCommand",
            (sTIT): "Timer Resume Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "start"
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_Toggles(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Toggles") {
        ((List<Map>)deviceTrait.toggles).each { toggle ->
            href(
                (sTIT): "${((List)toggle.labels).join(",")}",
                description: "Click to edit",
                style: "page",
                page: "togglePreferences",
                params: toggle
            )
        }
        href(
            (sTIT): "New Toggle",
            description: sBLK,
            style: "page",
            page: "togglePreferences",
            params: [
                traitName: tname,
                (sNM): tname + ".toggles.${UUID.randomUUID().toString()}"
            ]
        )
    }
}

def togglePreferences(Map toggle) {
    List<String> toggles = settings."${toggle.traitName}.toggles" ?: []
    if (!(toggle.name in toggles)) {
        toggles << toggle.name
        app.updateSetting("${toggle.traitName}.toggles", toggles)
    }
    return dynamicPage((sNM): "togglePreferences", (sTIT): "Toggle Preferences", nextPage: "deviceTraitPreferences") {
        section {
            paragraph("A comma-separated list of names for this toggle")
            input(
                (sNM): "${toggle.name}.labels",
                (sTIT): "Toggle Names",
                description: "A comma-separated list of names for this toggle",
                (sTYPE): sTEXT,
                (sREQ): true
            )
        }

        deviceTraitPreferences_OnOff(toggle)

        section {
            href(
                (sTIT): "Remove Toggle",
                description: sBLK,
                style: "page",
                page: "toggleDelete",
                params: toggle
            )
        }
    }
}

def toggleDelete(Map toggle) {
    return dynamicPage((sNM): "toggleDelete", (sTIT): "Toggle Deleted", nextPage: "deviceTraitPreferences") {
        deleteToggle(toggle)
        section {
            paragraph("The ${toggle.labels ? ((List)toggle.labels).join(",") : "new"} toggle has been removed")
        }
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_TransportControl(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Transport Control Preferences") {
        input(
            (sNM): tname + ".nextCommand",
            (sTIT): "Next Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "nextTrack"
        )
        input(
            (sNM): tname + ".pauseCommand",
            (sTIT): "Pause Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "pause"
        )
        input(
            (sNM): tname + ".previousCommand",
            (sTIT): "Previous Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "previousTrack"
        )
        input(
            (sNM): tname + ".resumeCommand",
            (sTIT): "Resume Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "play"
        )
        input(
            (sNM): tname + ".stopCommand",
            (sTIT): "Stop Command",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "stop"
        )
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private deviceTraitPreferences_Volume(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    section("Volume Preferences") {
        input(
            (sNM): tname + ".volumeAttribute",
            (sTIT): "Current Volume Attribute",
            (sTYPE): sTEXT,
            (sREQ): true,
            (sDEFVAL): "volume"
        )
        input(
            (sNM): tname + ".canSetVolume",
            (sTIT): "Use `setVolume` command (otherwise will use Volume Up/Down instead)",
            (sTYPE): sBOOL,
            (sDEFVAL): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.canSetVolume) {
            input(
                (sNM): tname + ".setVolumeCommand",
                (sTIT): "Set Rotation Command",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "setVolume"
            )
        } else {
            input(
                (sNM): tname + ".volumeUpCommand",
                (sTIT): "Set Increase Volume Command",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "volumeUp"
            )
            input(
                (sNM): tname + ".volumeDownCommand",
                (sTIT): "Set Decrease Volume Command",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "volumeDown"
            )
        }
        input(
            (sNM): tname + ".volumeStep",
            (sTIT): "Volume Level Step",
            (sTYPE): "number",
            (sREQ): true,
            (sDEFVAL): i1
        )
        input(
            (sNM): tname + ".canMuteUnmute",
            (sTIT): "Supports Mute And Unmute",
            (sTYPE): sBOOL,
            (sDEFVAL): true,
            (sSUBONCHG): true
        )
        if (deviceTrait.canMuteUnmute) {
            input(
                (sNM): tname + ".muteAttribute",
                (sTIT): "Mute State Attribute",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "mute"
            )
            input(
                (sNM): tname + ".mutedValue",
                (sTIT): "Muted Value",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "muted"
            )
            input(
                (sNM): tname + ".unmutedValue",
                (sTIT): "Unmuted Value",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "unmuted"
            )
            input(
                (sNM): tname + ".muteCommand",
                (sTIT): "Mute Command",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "mute"
            )
            input(
                (sNM): tname + ".unmuteCommand",
                (sTIT): "Unmute Command",
                (sTYPE): sTEXT,
                (sREQ): true,
                (sDEFVAL): "unmute"
            )
        }
    }
}

def handleAction() {
    LOGGER.debug('handleAction() '+request.body)
    // todo
    /*
    doLog(sWARN, "request is: ${myObj(request)}")
    doLog(sWARN, "request : ${getMapDescStr(request,false)}")
	*/

    def requestType = ((List<Map>)request.JSON.inputs)[0].intent
    def response; response=[:]
    if (requestType == "action.devices.SYNC") {
        response = handleSyncRequest(request)
    } else if (requestType == "action.devices.QUERY") {
        response = handleQueryRequest(request)
    } else if (requestType == "action.devices.EXECUTE") {
        response = handleExecuteRequest(request)
    } else if (requestType == "action.devices.DISCONNECT") {
        response = [:]
    }
    LOGGER.debug('handleAction() '+JsonOutput.toJson(response))
    return response
}

private Boolean attributeHasExpectedValue(device, String attrName, attrValue) {
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

private Map handleExecuteRequest(request) {
    Map resp = [
        requestId: request.JSON.requestId,
        payload: [
            commands: []
        ]
    ]

    Map<String, Map<String,Object>> knownDevices = allKnownDevices()
    List<Map> commands = ((List<Map>)request.JSON.inputs)[0].payload.commands

    commands.each { command ->
        List<Map> devices = command.devices.collect { device -> knownDevices."${device.id}" }
        Map<String,Map<String,Object>> attrsToAwait = [:].withDefault { [:] }
        Map<String,Object> states = [:].withDefault { [:] }
        Map<String,Map> results = [:]
        // Send appropriate commands to devices
        devices.each { device ->
            doLog(sWARN, "device : ${getMapDescStr(device,false)}")
            String dname= device.device.id.toString()
            command.execution.each { Map execution ->
                String commandName = ((String)execution.command).split("\\.").last()
                try {
                    def (resultAttrsToAwait, resultStates) = "executeCommand_${commandName}"(device, execution)
                    attrsToAwait[dname] += resultAttrsToAwait
                    states[dname] += resultStates
                    results[dname] = [ status: "SUCCESS" ]
                } catch (Exception ex) {
                    results[dname] = [ status: "ERROR" ]
                    try {
                        results[dname] << parseJson(ex.message)
                    } catch (JsonException jex) {
                        LOGGER.exception(
                            "Error executing command ${commandName} on device ${gtLbl(device.device)}",
                            ex
                        )
                        results[dname] << [
                            errorCode: "hardError"
                        ]
                    }
                }
            }
        }
        doLog(sWARN, "results : ${getMapDescStr(results,false)}")
        // Wait up to 1 second for all devices to settle to the desired states.
        Long pollTimeoutMs = 1000L
        Long singlePollTimeMs = 100L
        Long numLoops = Math.round(pollTimeoutMs / singlePollTimeMs)
        Map<String,Boolean> deviceReadyStates; deviceReadyStates = [:]
        Integer i
        for (i = iZ; i < numLoops; ++i) {
            deviceReadyStates = attrsToAwait.collectEntries { String dname, Map<String,Object> attributes ->
                [dname, attributes.every { String attrName, attrValue ->
                    def dev = knownDevices[dname].device
                    attributeHasExpectedValue(dev, attrName, attrValue)
                }]
            } as Map<String, Boolean>
            Boolean ready = deviceReadyStates.every { dname, deviceReady -> deviceReady }
            if (ready) {
                LOGGER.debug("All devices reached expected state and are ready.")
                break
            } else {
                pauseExecution(singlePollTimeMs)
                LOGGER.debug("Polling device attributes for ${(i+i1)*singlePollTimeMs} ms")
            }
        }

        // Now build our response message
        devices.each { Map device ->
            String dname= device.device.id.toString()
            Map result = results[dname]
            Boolean deviceReady = deviceReadyStates[dname]
            result.ids = [device.device.id]
            if (result.status == "SUCCESS") {
                if (!deviceReady) {
                    LOGGER.debug("Device ${gtLbl(device.device)} not ready, moving to PENDING")
                    result.status = "PENDING"
                }
                Map<String,Object> deviceState
                deviceState = [ online: true ]
                deviceState += states[dname]
                result.states = deviceState
            }
            resp.payload.commands << result
        }
    }
    return resp
}

private deviceCurrentValue(Map deviceInfo, String attr){
    com.hubitat.app.DeviceWrapper device = deviceInfo.device as com.hubitat.app.DeviceWrapper
    device.currentValue(attr,true)
}

private Integer checkDevicePinMatch(Map deviceInfo, Map command) {
    Integer matchPosition
    matchPosition = null

    if (deviceInfo.deviceType.useDevicePinCodes == true) {
        Map lockCodeMap
        // grab the lock code map and decrypt if necessary
        String jsonLockCodeMap = deviceCurrentValue(deviceInfo,(String)deviceInfo.deviceType.pinCodeAttribute)
        if (jsonLockCodeMap[iZ] == '{') {
            lockCodeMap = parseJson(jsonLockCodeMap)
        } else {
            lockCodeMap = parseJson(decrypt(jsonLockCodeMap))
        }
        // check all users for a pin code match
        lockCodeMap.each { position, user ->
            if (user.((String)deviceInfo.deviceType?.pinCodeValue) == command.challenge.pin) {
                // grab the code position in the JSON map
                matchPosition = position as Integer
            }
        }
    }
    return matchPosition
}

@SuppressWarnings(['InvertedIfElse', 'NestedBlockDepth'])
private Integer checkMfa(Map<String,Map> deviceInfo, icommandType, Map<String,Map> command) {
    Integer matchPosition
    matchPosition = null
    String commandType = icommandType as String
    LOGGER.debug("Checking MFA for ${commandType} command")
    if (commandType in (List)deviceInfo.deviceType.confirmCommands && !command.challenge?.ack) {
        throw new Exception(JsonOutput.toJson([
            errorCode: "challengeNeeded",
            challengeNeeded: [
                (sTYPE): "ackNeeded"
            ]
        ]))
    }
    if (commandType in (List)deviceInfo.deviceType.secureCommands) {
        Map globalPinCodes = deviceTypeFromSettings('GlobalPinCodes')
        if (!command.challenge?.pin) {
            throw new Exception(JsonOutput.toJson([
                errorCode: "challengeNeeded",
                challengeNeeded: [
                    (sTYPE): "pinNeeded"
                ]
            ]))
        } else {
            // check for a match in the global and device level pincodes and return the position if found, 0 otherwise
            Integer positionMatchGlobal =
                (globalPinCodes.pinCodes*.value.findIndexOf { it ==~ command.challenge.pin }) + i1
            Integer positionMatchDevice =
                (deviceInfo.deviceType.pinCodes*.value.findIndexOf { it ==~ command.challenge.pin }) + i1
            Integer positionMatchTrait = checkDevicePinMatch(deviceInfo, command)

            // return pincode matches in the order of trait -> device -> global -> null
            if (positionMatchTrait != null) {
                matchPosition = positionMatchTrait
            } else if (positionMatchDevice != iZ) {
                matchPosition = positionMatchDevice
            } else if (positionMatchGlobal != iZ) {
                matchPosition = positionMatchGlobal
            } else {
                // no matching pins
                throw new Exception(JsonOutput.toJson([
                    errorCode: "challengeNeeded",
                    challengeNeeded: [
                        (sTYPE): "challengeFailedPinNeeded"
                    ]
                ]))
            }
        }
    }

    return matchPosition
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private controlScene(Map options) {
    Map<String,Map<String,Object>> allDevices = allKnownDevices()
    def device = allDevices[(String)options.deviceId].device
    device."${options.command}"()
}

private issueCommandWithCodePosition(Map deviceInfo, String command, Integer codePosition, Boolean returnIndex) {
    if (returnIndex) {
        deviceInfo.device."${command}"(codePosition)
    } else {
        deviceInfo.device."${command}"()
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_ActivateScene(Map deviceInfo, Map command) {
    Map sceneTrait = deviceInfo.deviceType.traits.Scene
    if (sceneTrait.name == "hubitat_mode") {
        location.mode = deviceInfo.device.name
    } else {
        if (command.params.deactivate) {
            checkMfa(deviceInfo, "Deactivate Scene", command)
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
            checkMfa(deviceInfo, "Activate Scene", command)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_ArmDisarm(Map deviceInfo, Map command) {
    Map armDisarmTrait = deviceInfo.deviceType.traits.ArmDisarm
    String checkValue; checkValue = "${armDisarmTrait.armValues["disarmed"]}"

    // if the user canceled arming, issue the cancel command
    if (command.params.cancel) {
        Integer codePosition = checkMfa(deviceInfo, "Cancel", command)
        issueCommandWithCodePosition(deviceInfo, (String)armDisarmTrait.cancelCommand, codePosition,
                (Boolean)armDisarmTrait.returnUserIndexToDevice)
    } else if (command.params.arm) {
        // Google sent back an alarm level, execute the matching alarm level command
        String armval= command.params.armLevel
        Integer codePosition = checkMfa(deviceInfo, (String)armDisarmTrait.armLevels[armval], command)
        checkValue = "${armDisarmTrait.armValues[armval]}"
        issueCommandWithCodePosition(deviceInfo, (String)armDisarmTrait.armCommands[armval], codePosition,
                (Boolean)armDisarmTrait.returnUserIndexToDevice)
    } else {
        // if Google returns arm=false, that indicates disarm
        Integer codePosition = checkMfa(deviceInfo, "Disarm", command)
        issueCommandWithCodePosition(deviceInfo, (String)armDisarmTrait.armCommands["disarmed"], codePosition,
                (Boolean)armDisarmTrait.returnUserIndexToDevice)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_BrightnessAbsolute(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Brightness", command)
    Map brightnessTrait = deviceInfo.deviceType.traits.Brightness
    Integer brightnessToSet = googlePercentageToHubitat(command.params.brightness)
    deviceInfo.device."${brightnessTrait.setBrightnessCommand}"(brightnessToSet)
    def checkValue = { value ->
        if (brightnessToSet == i100) {
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

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private List<Map> executeCommand_GetCameraStream(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Display", command)
    Map cameraStreamTrait = deviceInfo.deviceType.traits.CameraStream
    def supportedStreamProtocols = command.params.SupportedStreamProtocols

    deviceInfo.device."${cameraStreamTrait.cameraStreamCommand}"(supportedStreamProtocols)
    return [
        [:],
        [
            cameraStreamAccessUrl:
                deviceCurrentValue(deviceInfo, (String)deviceInfo.deviceType.traits.CameraStream.cameraStreamURLAttribute),
            cameraStreamProtocol:
                deviceCurrentValue(deviceInfo, (String)deviceInfo.deviceType.traits.CameraStream.cameraStreamProtocolAttribute)
        ],
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_ColorAbsolute(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Color", command)
    Map colorTrait = deviceInfo.deviceType.traits.ColorSetting

    Map checkAttrs = [:]
    Map states = [color: command.params.color]
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
        def hue = Math.round(hsv.hue * i100 / 360)
        // Google sends saturation and value as floats in the range 0...1,
        // but Hubitat wants them in the range 0...100
        def saturation = Math.round(hsv.saturation * i100)
        def value = Math.round(hsv.value * i100)

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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_Dock(Map deviceInfo, Map command) {
    def dockTrait = deviceInfo.deviceType.traits.Dock
    def checkValue
    checkMfa(deviceInfo, "Dock", command)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_Charge(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Charge", command)
    Map energyStorageTrait = deviceInfo.deviceType.traits.EnergyStorage
    deviceInfo.device."${energyStorageTrait.chargeCommand}"()
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_Reverse(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Reverse", command)
    Map fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed
    deviceInfo.device."${fanSpeedTrait.reverseCommand}"()
    return [[:], [:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_Locate(Map deviceInfo, Map command) {
    Map locatorTrait = deviceInfo.deviceType.traits.Locator
    checkMfa(deviceInfo, "Locate", command)
    deviceInfo.device."${locatorTrait.locatorCommand}"()
    return [[:], [:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_LockUnlock(Map deviceInfo, Map command) {
    Map lockUnlockTrait = deviceInfo.deviceType.traits.LockUnlock
    def checkValue
    def codePosition
    if (command.params.lock) {
        codePosition = checkMfa(deviceInfo, "Lock", command)
        checkValue = lockUnlockTrait.lockedValue
        issueCommandWithCodePosition(deviceInfo, (String)lockUnlockTrait.lockCommand, codePosition, (Boolean)lockUnlockTrait.returnUserIndexToDevice)
    } else {
        codePosition = checkMfa(deviceInfo, "Unlock", command)
        checkValue = { it != lockUnlockTrait.lockedValue }
        issueCommandWithCodePosition(deviceInfo, (String)lockUnlockTrait.unlockCommand, codePosition, (Boolean)lockUnlockTrait.returnUserIndexToDevice)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_mute(Map deviceInfo, Map command) {
    Map volumeTrait = deviceInfo.deviceType.traits.Volume
    def checkValue
    if (command.params.mute) {
        checkMfa(deviceInfo, "Mute", command)
        deviceInfo.device."${volumeTrait.muteCommand}"()
        checkValue = volumeTrait.mutedValue
    } else {
        checkMfa(deviceInfo, "Unmute", command)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_OnOff(Map deviceInfo, Map command) {
    Map onOffTrait = deviceInfo.deviceType.traits.OnOff

    Closure on
    Closure off
    if (onOffTrait.controlType == "single") {
        on = { device -> device."${onOffTrait.onOffCommand}"(onOffTrait.onParam) }
        off = { device -> device."${onOffTrait.onOffCommand}"(onOffTrait.offParam) }
    } else {
        on = { device -> device."${onOffTrait.onCommand}"() }
        off = { device -> device."${onOffTrait.offCommand}"() }
    }

    def checkValue
    if (command.params.on) {
        checkMfa(deviceInfo, "On", command)
        on(deviceInfo.device)
        if (onOffTrait.onValue) {
            checkValue = onOffTrait.onValue
        } else {
            checkValue = { it != onOffTrait.offValue }
        }
    } else {
        checkMfa(deviceInfo, "Off", command)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_OpenClose(Map deviceInfo, Map command) {
    Map openCloseTrait = deviceInfo.deviceType.traits.OpenClose
    Integer openPercent = googlePercentageToHubitat(command.params.openPercent)
    Closure checkValue
    if (openCloseTrait.discreteOnlyOpenClose && openPercent == i100) {
        checkMfa(deviceInfo, "Open", command)
        deviceInfo.device."${openCloseTrait.openCommand}"()
        checkValue = { it in ((String)openCloseTrait.openValue).split(",") }
    } else if (openCloseTrait.discreteOnlyOpenClose && openPercent == iZ) {
        checkMfa(deviceInfo, "Close", command)
        deviceInfo.device."${openCloseTrait.closeCommand}"()
        checkValue = { it in ((String)openCloseTrait.closedValue).split(",") }
    } else {
        checkMfa(deviceInfo, "Set Position", command)
        Integer hubitatOpenPercent
        hubitatOpenPercent = openPercent
        if (openCloseTrait.reverseDirection) {
            hubitatOpenPercent = i100 - openPercent
        }
        deviceInfo.device."${openCloseTrait.openPositionCommand}"(hubitatOpenPercent)
        checkValue = { value ->
            if (hubitatOpenPercent == i100) {
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_Reboot(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Reboot", command)
    Map rebootTrait = deviceInfo.deviceType.traits.Reboot
    deviceInfo.device."${rebootTrait.rebootCommand}"()
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_RotateAbsolute(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Rotate", command)
    Map rotationTrait = deviceInfo.deviceType.traits.Rotation
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_SetFanSpeed(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Fan Speed", command)
    Map fanSpeedTrait = deviceInfo.deviceType.traits.FanSpeed

    if (fanSpeedTrait.supportsFanSpeedPercent && command.params.fanSpeedPercent) {
        def fanSpeedPercent = command.params.fanSpeedPercent

        deviceInfo.device."${fanSpeedTrait.setFanSpeedPercentCommand}"(fanSpeedPercent)
        return [
            [
                (fanSpeedTrait.currentFanSpeedPercent): fanSpeedPercent,
            ],
            [
                currentFanSpeedPercent: fanSpeedPercent,
            ],
        ]
    } else {
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
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_SetHumidity(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Humidity", command)
    Map humiditySettingTrait = deviceInfo.deviceType.traits.HumiditySetting
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_SetTemperature(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Temperature", command)
    Map temperatureControlTrait = deviceInfo.deviceType.traits.TemperatureControl
    def setpoint
    setpoint = command.params.temperature
    if (temperatureControlTrait.temperatureUnit == "F") {
        setpoint = celsiusToFahrenheit(setpoint)
    }
    setpoint = roundTo(setpoint, i1)

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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_SetToggles(Map deviceInfo, Map command) {
    Map togglesTrait = deviceInfo.deviceType.traits.Toggles
    def togglesToSet = command.params.updateToggleSettings

    Map attrsToCheck = [:]
    Map states = [currentToggleSettings: togglesToSet]
    togglesToSet.each { toggleName, toggleValue ->
        Map toggle = ((List<Map>)togglesTrait.toggles).find { it.name == toggleName }
        Map fakeDeviceInfo = [
            deviceType: [
                traits: [
                    // We're delegating to the OnOff handler, so we need
                    // to fake the OnOff trait.
                    OnOff: toggle
                ]
            ],
            device: deviceInfo.device
        ]
        checkMfa(deviceInfo, "${((List)toggle.labels)[iZ]} ${toggleValue ? "On" : "Off"}", command)
        List<Map> onOffResponse = executeCommand_OnOff(fakeDeviceInfo, [params: [on: toggleValue]])
        attrsToCheck << onOffResponse[iZ]
        states << onOffResponse[i1]
    }
    return [attrsToCheck, states]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_setVolume(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Volume", command)
    Map volumeTrait = deviceInfo.deviceType.traits.Volume
    def volumeLevel = command.params.volumeLevel
    deviceInfo.device."${volumeTrait.setVolumeCommand}"(volumeLevel)
    Map states = [currentVolume: volumeLevel]
    if (deviceInfo.deviceType.traits.canMuteUnmute) {
        states.isMuted = deviceCurrentValue(deviceInfo, (String)volumeTrait.muteAttribute) == volumeTrait.mutedValue
    }
    return [
        [
            (volumeTrait.volumeAttribute): volumeLevel,
        ],
        states,
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_SoftwareUpdate(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Software Update", command)
    Map softwareUpdateTrait = deviceInfo.deviceType.traits.SoftwareUpdate
    deviceInfo.device."${softwareUpdateTrait.softwareUpdateCommand}"()
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_StartStop(Map deviceInfo, Map command) {
    Map startStopTrait = deviceInfo.deviceType.traits.StartStop
    def checkValue
    if (command.params.start) {
        checkMfa(deviceInfo, "Start", command)
        checkValue = startStopTrait.startValue
        deviceInfo.device."${startStopTrait.startCommand}"()
    } else {
        checkMfa(deviceInfo, "Stop", command)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_PauseUnpause(Map deviceInfo, Map command) {
    Map startStopTrait = deviceInfo.deviceType.traits.StartStop
    def checkValue; checkValue = null
    if (command.params.pause) {
        checkMfa(deviceInfo, "Pause", command)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_ThermostatTemperatureSetpoint(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Setpoint", command)
    Map temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def setpoint
    setpoint = command.params.thermostatTemperatureSetpoint
    if (temperatureSettingTrait.temperatureUnit == "F") {
        setpoint = celsiusToFahrenheit(setpoint)
    }

    setpoint = roundTo(setpoint, i1)

    String hubitatMode = deviceCurrentValue(deviceInfo, (String)temperatureSettingTrait.currentModeAttribute)
    String googleMode = temperatureSettingTrait.hubitatToGoogleModeMap[hubitatMode]
    String setSetpointCommand = temperatureSettingTrait.modeSetSetpointCommands[googleMode]
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_ThermostatTemperatureSetRange(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Setpoint", command)
    Map temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    def coolSetpoint, heatSetpoint
    coolSetpoint = command.params.thermostatTemperatureSetpointHigh
    heatSetpoint = command.params.thermostatTemperatureSetpointLow
    if (temperatureSettingTrait.temperatureUnit == "F") {
        coolSetpoint = celsiusToFahrenheit(coolSetpoint)
        heatSetpoint = celsiusToFahrenheit(heatSetpoint)
    }
    coolSetpoint = roundTo(coolSetpoint, i1)
    heatSetpoint = roundTo(heatSetpoint, i1)
    Map setRangeCommands = (Map)temperatureSettingTrait.modeSetSetpointCommands["heatcool"]
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_ThermostatSetMode(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Mode", command)
    Map temperatureSettingTrait = deviceInfo.deviceType.traits.TemperatureSetting
    String googleMode = command.params.thermostatMode
    String hubitatMode = temperatureSettingTrait.googleToHubitatModeMap[googleMode]
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_TimerAdjust(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Adjust", command)
    Map timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerAdjustCommand}"()
    Map retVal; retVal = [:]
    if (!timerTrait.commandOnlyTimer) {
        retVal = [
            timerTimeSec: deviceCurrentValue(deviceInfo, (String)timerTrait.timerRemainingSecAttribute)
        ]
    }
    return retVal
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_TimerCancel(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Cancel", command)
    Map timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerCancelCommand}"()
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_TimerPause(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Pause", command)
    Map timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerPauseCommand}"()
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_TimerResume(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Resume", command)
    Map timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerResumeCommand}"()
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map executeCommand_TimerStart(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Start", command)
    Map timerTrait = deviceInfo.deviceType.traits.Timer
    deviceInfo.device."${timerTrait.timerStartCommand}"()
    Map retVal; retVal = [:]
    if (!timerTrait.commandOnlyTimer) {
        retVal = [
            timerTimeSec: deviceCurrentValue(deviceInfo, (String)timerTrait.timerRemainingSecAttribute)
        ]
    }
    return retVal
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_mediaNext(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Next", command)
    Map transportControlTrait = deviceInfo.deviceType.traits.TransportControl
    deviceInfo.device."${transportControlTrait.nextCommand}"()
    return [[:],[:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_mediaPause(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Pause", command)
    Map transportControlTrait = deviceInfo.deviceType.traits.TransportControl
    deviceInfo.device."${transportControlTrait.pauseCommand}"()
    return [[:],[:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_mediaPrevious(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Previous", command)
    Map transportControlTrait = deviceInfo.deviceType.traits.TransportControl
    deviceInfo.device."${transportControlTrait.previousCommand}"()
    return [[:],[:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_mediaResume(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Resume", command)
    Map transportControlTrait = deviceInfo.deviceType.traits.TransportControl
    deviceInfo.device."${transportControlTrait.resumeCommand}"()
    return [[:],[:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_mediaStop(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Stop", command)
    Map transportControlTrait = deviceInfo.deviceType.traits.TransportControl
    deviceInfo.device."${transportControlTrait.stopCommand}"()
    return [[:],[:]]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private List<Map> executeCommand_volumeRelative(Map deviceInfo, Map command) {
    checkMfa(deviceInfo, "Set Volume", command)
    Map volumeTrait = deviceInfo.deviceType.traits.Volume
    Integer volumeChange = (Integer)command.params.relativeSteps
    def device = deviceInfo.device

    Integer newVolume

    if (volumeTrait.canSetVolume) {
        Integer currentVolume = (Integer)deviceCurrentValue(deviceInfo, (String)volumeTrait.volumeAttribute)
        // volumeChange will be negative when decreasing volume
        newVolume = currentVolume + volumeChange
        device."${volumeTrait.setVolumeCommand}"(newVolume)
    } else {
        def volumeChangeCommand
        volumeChangeCommand = volumeTrait.volumeUpCommand
        if (volumeChange < 0) {
            volumeChangeCommand = volumeTrait.volumeDownCommand
        }

        device."${volumeChangeCommand}"()
        Integer i
        for (i = i1; i < Math.abs(volumeChange); i++) {
            pauseExecution(i100)
            device."${volumeChangeCommand}"()
        }

        newVolume = (Integer)deviceCurrentValue(deviceInfo, (String)volumeTrait.volumeAttribute)
    }

    Map states; states = [:]
    states += [currentVolume: newVolume]
    if (volumeTrait.canMuteUnmute) {
        states.isMuted = deviceCurrentValue(deviceInfo, (String)volumeTrait.muteAttribute) == volumeTrait.mutedValue
    }

    return [
        [
            (volumeTrait.volumeAttribute): newVolume,
        ],
        states,
    ]
}

private Map handleQueryRequest(request) {
    Map resp = [
        requestId: request.JSON.requestId,
        payload: [
            devices: [:]
        ]
    ]
    List<Map> requestedDevices = ((List<Map>)request.JSON.inputs)[0].payload.devices
    Map knownDevices = allKnownDevices()

    requestedDevices.each { requestedDevice ->
        Map deviceInfo = knownDevices."${requestedDevice.id}"
        Map deviceState; deviceState = [:]
        if (deviceInfo != null) {
            try {
                deviceInfo.deviceType.traits.each { String traitType, Map deviceTrait ->
                    deviceState += "deviceStateForTrait_${traitType}"(deviceTrait, deviceInfo.device)
                }
                deviceState.status = "SUCCESS"
            } catch (Exception ex) {
                String deviceName = gtLbl(deviceInfo.device)
                LOGGER.exception("Error retreiving state for device ${deviceName}", ex)
                deviceState.status = "ERROR"
            }
        } else {
            LOGGER.warn("Requested device ${requestedDevice.name} not found.")
        }
        resp.payload.devices."${requestedDevice.id}" = deviceState
    }
    return resp
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_ArmDisarm(Map deviceTrait, device) {
    Boolean isArmed = device.currentValue(deviceTrait.armedAttribute) != deviceTrait.disarmedValue
    return [
        isArmed: isArmed,
        currentArmLevel: device.currentValue(deviceTrait.armLevelAttribute),
        exitAllowance: device.currentValue(deviceTrait.exitAllowanceAttribute),
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_Brightness(Map deviceTrait, device) {
    Integer brightness = hubitatPercentageToGoogle(device.currentValue(deviceTrait.brightnessAttribute))
    return [
        brightness: brightness,
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map deviceStateForTrait_CameraStream(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_ColorSetting(Map deviceTrait, device) {
    String colorMode
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

    Map deviceState = [
        color: [:]
    ]

    if (colorMode == "spectrum") {
        def hue, saturation, value
        hue = device.currentValue(deviceTrait.hueAttribute)
        saturation = device.currentValue(deviceTrait.saturationAttribute)
        value = device.currentValue(deviceTrait.levelAttribute)

        // Hubitat reports hue in the range 0...100, but Google wants it in degrees (0...360)
        hue = Math.round(hue * 360 / i100)
        // Hubitat reports saturation and value in the range 0...100 but
        // Google wants them as floats in the range 0...1
        saturation = saturation / i100
        value = value / i100

        deviceState.color = [
            spectrumHsv: [
                hue: hue,
                saturation: saturation,
                (sVAL): value
            ]
        ]
    } else {
        deviceState.color = [
            temperatureK: device.currentValue(deviceTrait.colorTemperatureAttribute)
        ]
    }

    return deviceState
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_Dock(Map deviceTrait, device) {
    def isDocked = device.currentValue(deviceTrait.dockAttribute) == deviceTrait.dockValue
    return [
        isDocked:isDocked
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_EnergyStorage(Map deviceTrait, device) {
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_FanSpeed(Map deviceTrait, device) {
    def currentSpeedSetting = device.currentValue(deviceTrait.currentSpeedAttribute)

    Map fanSpeedState = [
        currentFanSpeedSetting: currentSpeedSetting
    ]

    if (deviceTrait.supportsFanSpeedPercent) {
        def currentSpeedPercent = hubitatPercentageToGoogle(device.currentValue(deviceTrait.currentFanSpeedPercent))
        fanSpeedState.currentFanSpeedPercent = currentSpeedPercent
    }

    return fanSpeedState
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_HumiditySetting(Map deviceTrait, device) {
    Map deviceState = [
        humidityAmbientPercent: Math.round((Integer)device.currentValue(deviceTrait.humidityAttribute))
    ]
    if (!deviceTrait.queryOnly) {
        deviceState.humiditySetpointPercent = Math.round((Integer)device.currentValue(deviceTrait.humiditySetpointAttribute))
    }
    return deviceState
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map deviceStateForTrait_Locator(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_LockUnlock(Map deviceTrait, device) {
    Boolean isLocked = device.currentValue(deviceTrait.lockedUnlockedAttribute) == deviceTrait.lockedValue
    return [
        isLocked: isLocked,
        isJammed: false
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_MediaState(Map deviceTrait, device) {
    return [
        activityState: device.currentValue(deviceTrait.activityStateAttribute)?.toUpperCase(),
        playbackState: device.currentValue(deviceTrait.playbackStateAttribute)?.toUpperCase()
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_OccupancySensing(Map deviceTrait, device) {
    String occupancy; occupancy = "UNOCCUPIED"
    if (device.currentValue(deviceTrait.occupancyAttribute) == deviceTrait.occupiedValue) {
        occupancy = "OCCUPIED"
    }
    return [
        occupancy: occupancy
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_OnOff(Map deviceTrait, device) {
    Boolean isOn
    if (deviceTrait.onValue) {
        isOn = device.currentValue(deviceTrait.onOffAttribute) == deviceTrait.onValue
    } else {
        isOn = device.currentValue(deviceTrait.onOffAttribute) != deviceTrait.offValue
    }
    return [
        on: isOn
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_OpenClose(Map deviceTrait, device) {
    Integer openPercent
    if (deviceTrait.discreteOnlyOpenClose) {
        String[] openValues = ((String)deviceTrait.openValue).split(",")
        if (device.currentValue(deviceTrait.openCloseAttribute) in openValues) {
            openPercent = i100
        } else {
            openPercent = iZ
        }
    } else {
        openPercent = hubitatPercentageToGoogle(device.currentValue(deviceTrait.openCloseAttribute))
        if (deviceTrait.reverseDirection) {
            openPercent = i100 - openPercent
        }
    }
    return [
        openPercent: openPercent
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map deviceStateForTrait_Reboot(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_Rotation(Map deviceTrait, device) {
    return [
        rotationPercent: device.currentValue(deviceTrait.rotationAttribute)
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map deviceStateForTrait_Scene(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_SensorState(Map deviceTrait, device) {
    List deviceState = []

    ((Map<String,Object>)deviceTrait.sensorTypes).collect { sensorType ->
        String sname= (String)sensorType.key
        Map deviceStateMapping = [:]
        deviceStateMapping << [ (sNM): sname ]
        Map dTSensorT= (Map)deviceTrait.sensorTypes[sname]
        if (dTSensorT.reportsDescriptiveState) {
            deviceStateMapping << [ currentSensorState: device.currentValue(dTSensorT.descriptiveAttribute) ]
        }
        if (dTSensorT.reportsNumericState) {
            deviceStateMapping << [ rawValue: device.currentValue(dTSensorT.numericAttribute) ]
        }
        deviceState << deviceStateMapping
    }

    return [
        currentSensorStateData: deviceState
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map deviceStateForTrait_SoftwareUpdate(Map deviceTrait, device) {
    return [
        lastSoftwareUpdateUnixTimestampSec:
            device.currentValue(deviceTrait.lastSoftwareUpdateUnixTimestampSecAttribute).toInteger()
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_StartStop(Map deviceTrait, device) {
    Map deviceState = [
        isRunning: device.currentValue(deviceTrait.startStopAttribute) == deviceTrait.startValue
    ]
    if (deviceTrait.canPause) {
        deviceState.isPaused = device.currentValue(deviceTrait.pauseUnPauseAttribute) == deviceTrait.pauseValue
    }
    return deviceState
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_TemperatureControl(Map deviceTrait, device) {
    def currentTemperature
    currentTemperature = device.currentValue(deviceTrait.currentTemperatureAttribute)
    if (deviceTrait.temperatureUnit == "F") {
        currentTemperature = fahrenheitToCelsius(currentTemperature)
    }
    Map states; states = [:]
    states += [
        temperatureAmbientCelsius: roundTo(currentTemperature, i1)
    ]

    if (deviceTrait.queryOnly) {
        states.temperatureSetpointCelsius = currentTemperature
    } else {
        def setpoint
        setpoint = device.currentValue(deviceTrait.currentSetpointAttribute)
        if (deviceTrait.temperatureUnit == "F") {
            setpoint = fahrenheitToCelsius(setpoint)
        }
        states.temperatureSetpointCelsius = roundTo(setpoint, i1)
    }

    return states
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_TemperatureSetting(Map deviceTrait, device) {
    Map states
    states = [:]

    def currentTemperature
    currentTemperature = device.currentValue(deviceTrait.currentTemperatureAttribute)
    if (deviceTrait.temperatureUnit == "F") {
        currentTemperature = fahrenheitToCelsius(currentTemperature)
    }
    states.thermostatTemperatureAmbient = roundTo(currentTemperature, i1)

    if (deviceTrait.queryOnly) {
        states.thermostatMode = sON
        states.thermostatTemperatureSetpoint = currentTemperature
    } else {
        String hubitatMode = device.currentValue(deviceTrait.currentModeAttribute)
        String googleMode = deviceTrait.hubitatToGoogleModeMap[hubitatMode]
        states.thermostatMode = googleMode

        if (googleMode == "heatcool") {
            String heatingSetpointAttr = deviceTrait.modeSetpointAttributes[googleMode].heatingSetpointAttribute
            String coolingSetpointAttr = deviceTrait.modeSetpointAttributes[googleMode].coolingSetpointAttribute
            def heatSetpoint, coolSetpoint
            heatSetpoint = device.currentValue(heatingSetpointAttr)
            coolSetpoint = device.currentValue(coolingSetpointAttr)
            if (deviceTrait.temperatureUnit == "F") {
                heatSetpoint = fahrenheitToCelsius(heatSetpoint)
                coolSetpoint = fahrenheitToCelsius(coolSetpoint)
            }
            states.thermostatTemperatureSetpointHigh = roundTo(coolSetpoint, i1)
            states.thermostatTemperatureSetpointLow = roundTo(heatSetpoint, i1)
        } else {
            String setpointAttr = deviceTrait.modeSetpointAttributes[googleMode]
            if (setpointAttr) {
                def setpoint
                setpoint = device.currentValue(setpointAttr)
                if (deviceTrait.temperatureUnit == "F") {
                    setpoint = fahrenheitToCelsius(setpoint)
                }
                states.thermostatTemperatureSetpoint = roundTo(setpoint, i1)
            }
        }
    }
    return states
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_Timer(Map deviceTrait, device) {
    Map deviceState
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_Toggles(Map deviceTrait, device) {
    return [
        currentToggleSettings: ((List<Map>)deviceTrait.toggles).collectEntries { Map toggle ->
            [toggle.name, deviceStateForTrait_OnOff(toggle, device).on]
        }
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map deviceStateForTrait_TransportControl(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map deviceStateForTrait_Volume(Map deviceTrait, device) {
    Map deviceState = [
        currentVolume: device.currentValue(deviceTrait.volumeAttribute)
    ]
    if (deviceTrait.canMuteUnmute) {
        deviceState.isMuted = device.currentValue(deviceTrait.muteAttribute) == deviceTrait.mutedValue
    }
    return deviceState
}

private PrivateKey parsePemPrivateKey(pem) {
    // Dynamically load these classes and use them via reflection because they weren't available prior
    // to Hubitat version 2.3.4.115 and so I can't import them at the top level and maintain any sort
    // of compatibility with older Hubitat versions
    /* def KeyFactory
    def PKCS8EncodedKeySpec
    try {
        KeyFactory = "java.security.KeyFactory" as Class
        PKCS8EncodedKeySpec = "java.security.spec.PKCS8EncodedKeySpec" as Class
    } catch (Exception ex) {
        throw new Exception(
            "Unable to load required java.security classes.  Hub version is likely older than 2.3.4.115",
             ex
        )
    } */

    def rawB64
    try {
        rawB64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", sBLK)
            .replaceAll("\n", sBLK)
            .replace("-----END PRIVATE KEY-----", sBLK)
    } catch (ignored) {
        throw new Exception("Invalid Google Service Account JSON and JWK JSON in Preferences")
    }

    byte[] decoded = rawB64.decodeBase64()

    KeyFactory keyFactory = KeyFactory.getInstance("RSA")
    return keyFactory.generatePrivate(PKCS8EncodedKeySpec.newInstance(decoded))
}

private List generateSignedJWT() {
    if (!gtSetStr('googleServiceAccountJSON')) {
        throw new Exception("Must generate and paste Google Service Account JSON and JWK JSON into Preferences")
    }
    Map keyJson = parseJson(gtSetStr('googleServiceAccountJSON'))

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
        .keyID((String)keyJson.private_key_id)
        .type(JOSEObjectType.JWT)
        .build()

    // Must be in the past, so start 30 seconds ago.
    Instant issueTime = Instant.now() - Duration.ofSeconds(30)

    // Must be no more than 60 minutes in the future.
    Instant expirationTime = issueTime + Duration.ofMinutes(60)
    JWTClaimsSet payload = new JWTClaimsSet.Builder()
        .audience("https://oauth2.googleapis.com/token")
        .issueTime(Date.from(issueTime))
        .expirationTime(Date.from(expirationTime))
        .issuer((String)keyJson.client_email)
        .claim("scope", "https://www.googleapis.com/auth/homegraph")
        .build()

    SignedJWT signedJWT = new SignedJWT(header, payload)
    RSASSASigner signer = new RSASSASigner(parsePemPrivateKey(keyJson.private_key))
    signedJWT.sign(signer)

    return [signedJWT.serialize(), expirationTime]
}

private String fetchOAuthToken() {
    if (!gtSetStr('googleServiceAccountJSON')) {
        LOGGER.debug("Can't refresh Report State auth without Google Service Account JSON")
        return sNL
    }
    Long refreshAfterMillis = (new Date().toInstant() + Duration.ofMinutes(10)).toEpochMilli()
    if (state.oauthAccessToken &&
        state.oauthExpiryTimeMillis &&
        state.oauthExpiryTimeMillis >= refreshAfterMillis) {
        LOGGER.debug("Re-using existing OAuth token (expires ${state.oauthExpiryTimeMillis-now()})")
        return (String)state.oauthAccessToken
    }
    LOGGER.debug("Generating JWT to exchange for OAuth token")
    def (signedJWT, signedJWTExpiryTime) = generateSignedJWT()
    LOGGER.debug("Generated signed JWT: ${signedJWT}")
    Map params = [
        uri: "https://accounts.google.com/o/oauth2/token",
        body: [
            grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
            assertion: signedJWT,
        ],
        requestContentType: "application/x-www-form-urlencoded",
    ]
    //LOGGER.debug("Fetching OAuth token with signed JWT: ${params}")
    LOGGER.debug("Fetching OAuth token with signed JWT")
    String token; token=sNL
    httpPost(params) { resp ->
        token= resp.data.access_token
        //LOGGER.debug("Got OAuth token response $resp.data")
        LOGGER.debug("Got OAuth token response")
        atomicState.oauthAccessToken = token
        state.oauthAccessToken = token
        Long oauthExpiryTimeMillis = (Instant.now() + Duration.ofSeconds((Long)resp.data.expires_in)).toEpochMilli()
        LOGGER.debug("oauthExpiryTimeMillis: ${oauthExpiryTimeMillis}")
        atomicState.oauthExpiryTimeMillis = oauthExpiryTimeMillis
        state.oauthExpiryTimeMillis = oauthExpiryTimeMillis
    }
    return token
}

private String getAgentUserId() {
    return "${hubUID}_${app?.id}".toString()
}

private Map handleSyncRequest(request) {
    Map resp = [
        requestId: request.JSON.requestId,
        payload: [
            agentUserId: agentUserId,
            devices: [],
        ]
    ]

    Boolean willReportState = gtSetB('reportState') && gtSetStr('googleServiceAccountJSON') != sNL
    Map<String,Map> rooms = ((List<Map>)app.getRooms()).collectEntries { [(it.id): it] } ?: [:]

    Set deviceIdsEncountered = [] as Set
    (deviceTypes() + [modeSceneDeviceType()] as List<Map>).each { Map deviceType ->
        List<String> traits = deviceType.traits.collect { traitType, deviceTrait ->
            "action.devices.traits.${traitType}".toString()
        }
        deviceType.devices.each { device ->
            Map attributes; attributes = [:]
            ((Map<String,Map>)deviceType.traits).each { String traitType, Map deviceTrait ->
                attributes += "attributesForTrait_${traitType}"(deviceTrait, device)
            }
            String deviceName = gtLbl(device)
            if (deviceIdsEncountered.contains(device.id)) {
                LOGGER.warn(
                    "The device ${deviceName} with ID ${device.id} is selected as multiple device types. " +
                    "Ignoring configuration from the device type ${deviceType.display}!"
                )
            } else {
                String roomName; roomName = sNL
                try {
                    String roomId = device.getRoomId()?.toString()
                    roomName = roomId ? rooms[roomId]?.name : sBLK
                } catch (MissingPropertyException) {
                    // The roomId property isn't defined prior to Hubitat 2.2.7,
                    // so ignore the error; we just can't report a room on this
                    // version
                }
                deviceIdsEncountered.add(device.id)
                resp.payload.devices << [
                    id: device.id,
                    (sTYPE): "action.devices.types.${deviceType.googleDeviceType}",
                    traits: traits,
                    (sNM): [
                        defaultNames: [device.name],
                        (sNM): gtLbl(device)
                    ],
                    willReportState: willReportState,
                    attributes: attributes,
                    roomHint: roomName,
                ]
            }
        }
    }

    return resp
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_ArmDisarm(Map deviceTrait, device) {
    Map armDisarmAttrs = [
        availableArmLevels: [
            levels: ((Map<String,String>)deviceTrait.armLevels).collect { String hubitatLevelName, String googleLevelNames ->
                String[] levels = googleLevelNames.split(",")
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

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_Brightness(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_CameraStream(Map deviceTrait, device) {
    return [
        cameraStreamSupportedProtocols: device.currentValue(deviceTrait.cameraSupportedProtocolsAttribute).tokenize(','),
        cameraStreamNeedAuthToken:      false,
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_ColorSetting(Map deviceTrait, device) {
    Map colorAttrs = [:]
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

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_Dock(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_EnergyStorage(Map deviceTrait, device) {
    return [
        queryOnlyEnergyStorage:         deviceTrait.queryOnlyEnergyStorage,
        energyStorageDistanceUnitForUX: deviceTrait.energyStorageDistanceUnitForUX,
        isRechargeable:                 deviceTrait.isRechargeable
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_FanSpeed(Map deviceTrait, device) {
    Map fanSpeedAttrs = [
        availableFanSpeeds: [
            speeds: ((Map<String,String>)deviceTrait.fanSpeeds).collect { String hubitatLevelName, String googleLevelNames ->
                String[] speeds = googleLevelNames.split(",")
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
        supportsFanSpeedPercent: deviceTrait.supportsFanSpeedPercent,
        commandOnlyFanSpeed: false
    ]
    return fanSpeedAttrs
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_HumiditySetting(Map deviceTrait, device) {
    Map attrs = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_Locator(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_LockUnlock(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_MediaState(Map deviceTrait, device) {
    return [
        supportActivityState: deviceTrait.supportActivityState,
        supportPlaybackState: deviceTrait.supportPlaybackState
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_OccupancySensing(Map deviceTrait, device) {
    return [
        occupancySensorConfiguration: [
            [
                occupancySensorType: deviceTrait.occupancySensorType,
                occupiedToUnoccupiedDelaySec: deviceTrait.occupiedToUnoccupiedDelaySec,
                unoccupiedToOccupiedDelaySec: deviceTrait.unoccupiedToOccupiedDelaySec,
                unoccupiedToOccupiedEventTheshold: deviceTrait.unoccupiedToOccupiedEventThreshold
            ]
        ]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_OnOff(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_OpenClose(Map deviceTrait, device) {
    return [
        discreteOnlyOpenClose: deviceTrait.discreteOnlyOpenClose,
        queryOnlyOpenClose: deviceTrait.queryOnly
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_Reboot(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_Rotation(Map deviceTrait, device) {
    return [
        supportsContinuousRotation: deviceTrait.continuousRotation,
        supportsPercent:            true,
        supportsDegrees:            false
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_Scene(Map deviceTrait, device) {
    return [
        sceneReversible: deviceTrait.sceneReversible
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_SensorState(Map deviceTrait, device) {
    List sensorStateAttrs = []

    ((Map)deviceTrait.sensorTypes).collect { sensorType ->
        Map supportedStateAttrs = [:]
        String sname= sensorType.key
        supportedStateAttrs << [ (sNM): sname ]
        if (deviceTrait.sensorTypes[sname].reportsDescriptiveState) {
            supportedStateAttrs << [
                descriptiveCapabilities: [
                    availableStates: deviceTrait.sensorTypes[sname].descriptiveState,
                ]
            ]
        }
        if (deviceTrait.sensorTypes[sname].reportsNumericState) {
            supportedStateAttrs << [
                numericCapabilities: [
                    rawValueUnit: deviceTrait.sensorTypes[sname].numericUnits,
                ]
            ]
        }
        sensorStateAttrs << supportedStateAttrs
    }

    return [
        sensorStatesSupported: sensorStateAttrs
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_SoftwareUpdate(Map deviceTrait, device) {
    return [:]
}

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_StartStop(Map deviceTrait, device) {
    return [
        pausable: deviceTrait.canPause
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_TemperatureControl(Map deviceTrait, device) {
    Map attrs = [
        temperatureUnitForUX:        deviceTrait.temperatureUnit,
        queryOnlyTemperatureControl: deviceTrait.queryOnly
    ]

    if (!deviceTrait.queryOnly) {
        def minTemperature = deviceTrait.minTemperature
        def maxTemperature = deviceTrait.maxTemperature
        if (deviceTrait.temperatureUnit == "F") {
            attrs.temperatureRange = [
                minTemperature = fahrenheitToCelsius(minTemperature),
                maxTemperature = fahrenheitToCelsius(maxTemperature)
            ]
        }
        attrs.temperatureRange = [
            minThresholdCelsius: roundTo(minTemperature, i1),
            maxThresholdCelsius: roundTo(maxTemperature, i1)
        ]

        if (deviceTrait.temperatureStep) {
            def temperatureStep
            temperatureStep = deviceTrait.temperatureStep
            if (deviceTrait.temperatureUnit == "F") {
                // 5/9 is the scale factor for converting from F to C
                temperatureStep = temperatureStep * (5 / 9)
            }
            attrs.temperatureStepCelsius = roundTo(temperatureStep, i1)
        }
    }

    return attrs
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_TemperatureSetting(Map deviceTrait, device) {
    Map attrs = [
        thermostatTemperatureUnit:   deviceTrait.temperatureUnit,
        queryOnlyTemperatureSetting: deviceTrait.queryOnly
    ]

    if (!deviceTrait.queryOnly) {
        attrs.availableThermostatModes = deviceTrait.modes

        if (deviceTrait.setRangeMin != null) {
            def minSetpoint, maxSetpoint
            minSetpoint = deviceTrait.setRangeMin
            maxSetpoint = deviceTrait.setRangeMax
            if (deviceTrait.temperatureUnit == "F") {
                minSetpoint = fahrenheitToCelsius(minSetpoint)
                maxSetpoint = fahrenheitToCelsius(maxSetpoint)
            }
            attrs.thermostatTemperatureRange = [
                minThresholdCelsius: roundTo(minSetpoint, i1),
                maxThresholdCelsius: roundTo(maxSetpoint, i1)
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_Timer(Map deviceTrait, device) {
    return [
        maxTimerLimitSec:    deviceTrait.maxTimerLimitSec,
        commandOnlyTimer:    deviceTrait.commandOnlyTimer,
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_Toggles(Map deviceTrait, device) {
    return [
        availableToggles: ((List<Map>)deviceTrait.toggles).collect { toggle ->
            [
                (sNM): toggle.name,
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

@SuppressWarnings(['UnusedPrivateMethod','unused', 'UnusedPrivateMethodParameter'])
private Map attributesForTrait_TransportControl(Map deviceTrait, device) {
    return [
        transportControlSupportedCommands: ["NEXT", "PAUSE", "PREVIOUS", "RESUME", "STOP"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map attributesForTrait_Volume(Map deviceTrait, device) {
    return [
        volumeMaxLevel:         i100,
        volumeCanMuteAndUnmute: deviceTrait.canMuteUnmute,
        levelStepSize:          deviceTrait.volumeStep
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_ArmDisarm(String traitName) {

    Map armDisarmMapping = [
        armedAttribute:                 settings."${traitName}.armedAttribute",
        armLevelAttribute:              settings."${traitName}.armLevelAttribute",
        exitAllowanceAttribute:         settings."${traitName}.exitAllowanceAttribute",
        disarmedValue:                  settings."${traitName}.disarmedValue",
        cancelCommand:                  settings."${traitName}.cancelCommand",
        armLevels:                      [:],
        armCommands:                    [:],
        armValues:                      [:],
        returnUserIndexToDevice:        settings."${traitName}.returnUserIndexToDevice",
        commands:                       ["Cancel", "Disarm", "Arm Home", "Arm Night", "Arm Away"]
    ]

    settings."${traitName}.armLevels"?.each { String armLevel ->
        armDisarmMapping.armLevels[armLevel] = settings."${traitName}.armLevels.${armLevel}.googleNames"
        armDisarmMapping.armCommands[armLevel] = settings."${traitName}.armCommands.${armLevel}.commandName"
        armDisarmMapping.armValues[armLevel] = settings."${traitName}.armValues.${armLevel}.value"
    }

    return armDisarmMapping
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Brightness(String traitName) {
    return [
        brightnessAttribute:  settings."${traitName}.brightnessAttribute",
        setBrightnessCommand: settings."${traitName}.setBrightnessCommand",
        commands:             ["Set Brightness"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_CameraStream(String traitName) {
    return [
        cameraStreamURLAttribute:           settings."${traitName}.cameraStreamURLAttribute",
        cameraSupportedProtocolsAttribute:  settings."${traitName}.cameraSupportedProtocolsAttribute",
        cameraStreamProtocolAttribute:      settings."${traitName}.cameraStreamProtocolAttribute",
        cameraStreamCommand:                settings."${traitName}.cameraStreamCommand",
        commands:                           ["Display"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_ColorSetting(String traitName) {
    Map deviceTrait = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Dock(String traitName) {
    return [
        dockAttribute: settings."${traitName}.dockAttribute",
        dockValue:     settings."${traitName}.dockValue",
        dockCommand:   settings."${traitName}.dockCommand",
        commands:      ["Dock"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_EnergyStorage(String traitName) {
    Map energyStorageTrait = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_FanSpeed(String traitName) {
    Map fanSpeedMapping = [
        currentSpeedAttribute: settings."${traitName}.currentSpeedAttribute",
        setFanSpeedCommand:    settings."${traitName}.setFanSpeedCommand",
        fanSpeeds:             [:],
        reversible:            settings."${traitName}.reversible",
        commands:              ["Set Fan Speed"],
        supportsFanSpeedPercent: settings."${traitName}.supportsFanSpeedPercent"
    ]
    if (fanSpeedMapping.reversible) {
        fanSpeedMapping.reverseCommand = settings."${traitName}.reverseCommand"
        fanSpeedMapping.commands << "Reverse"
    }
    if (fanSpeedMapping.supportsFanSpeedPercent) {
        fanSpeedMapping.setFanSpeedPercentCommand = settings."${traitName}.setFanSpeedPercentCommand"
        fanSpeedMapping.currentFanSpeedPercent = settings."${traitName}.currentFanSpeedPercent"
    }
    settings."${traitName}.fanSpeeds"?.each { String fanSpeed ->
        fanSpeedMapping.fanSpeeds[fanSpeed] = settings."${traitName}.speed.${fanSpeed}.googleNames"
    }

    return fanSpeedMapping
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_HumiditySetting(String traitName) {
    Map humidityTrait = [
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

        Map humidityRange = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Locator(String traitName) {
    return [
        locatorCommand:   settings."${traitName}.locatorCommand",
        commands:         ["Locate"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_LockUnlock(String traitName) {
    return [
        lockedUnlockedAttribute: settings."${traitName}.lockedUnlockedAttribute",
        lockedValue:             settings."${traitName}.lockedValue",
        lockCommand:             settings."${traitName}.lockCommand",
        unlockCommand:           settings."${traitName}.unlockCommand",
        returnUserIndexToDevice: settings."${traitName}.returnUserIndexToDevice",
        commands:                ["Lock", "Unlock"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_MediaState(String traitName) {
    return [
        supportActivityState:     settings."${traitName}.supportActivityState",
        supportPlaybackState:     settings."${traitName}.supportPlaybackState",
        activityStateAttribute:   settings."${traitName}.activityStateAttribute",
        playbackStateAttribute:   settings."${traitName}.playbackStateAttribute",
        commands:                 []
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_OccupancySensing(String traitName) {
    return [
        occupancySensorType:                settings."${traitName}.occupancySensorType",
        occupancyAttribute:                 settings."${traitName}.occupancyAttribute",
        occupiedValue:                      settings."${traitName}.occupiedValue",
        occupiedToUnoccupiedDelaySec:       settings."${traitName}.occupiedToUnoccupiedDelaySec",
        unoccupiedToOccupiedDelaySec:       settings."${traitName}.unoccupiedToOccupiedDelaySec",
        unoccupiedToOccupiedEventThreshold: settings."${traitName}.unoccupiedToOccupiedEventThreshold",
        commands:                           []
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_OnOff(String traitName) {
    Map<String,Object> deviceTrait = [
        onOffAttribute: settings."${traitName}.onOffAttribute",
        onValue:        settings."${traitName}.onValue",
        offValue:       settings."${traitName}.offValue",
        controlType:    settings."${traitName}.controlType",
        commands:       ["On", "Off"]
    ]

    if ((String)deviceTrait.controlType == "single") {
        deviceTrait.onOffCommand = settings."${traitName}.onOffCommand"
        deviceTrait.onParam = settings."${traitName}.onParameter"
        deviceTrait.offParam = settings."${traitName}.offParameter"
    } else {
        deviceTrait.onCommand = settings."${traitName}.onCommand"
        deviceTrait.offCommand = settings."${traitName}.offCommand"
    }
    return deviceTrait
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_OpenClose(String traitName) {
    Map openCloseTrait = [
        discreteOnlyOpenClose: settings."${traitName}.discreteOnlyOpenClose",
        openCloseAttribute:    settings."${traitName}.openCloseAttribute",
        // queryOnly may be null for device traits defined with older versions,
        // so coerce it to a boolean
        queryOnly:             settings."${traitName}.queryOnly" as Boolean,
        commands:              []
    ]
    if (openCloseTrait.discreteOnlyOpenClose) {
        openCloseTrait.openValue = settings."${traitName}.openValue"
        openCloseTrait.closedValue = settings."${traitName}.closedValue"
    } else {
        openCloseTrait.reverseDirection = settings."${traitName}.reverseDirection" as Boolean
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Reboot(String traitName) {
    return [
        rebootCommand:      settings."${traitName}.rebootCommand",
        commands:           ["Reboot"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Rotation(String traitName) {
    return [
        rotationAttribute:  settings."${traitName}.rotationAttribute",
        setRotationCommand: settings."${traitName}.setRotationCommand",
        continuousRotation: settings."${traitName}.continuousRotation",
        commands:           ["Rotate"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Scene(String traitName) {
    Map sceneTrait = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_SensorState(String traitName) {
    Map sensorStateMapping = [
        sensorTypes:                    [:],
        commands:                       []
    ]

    settings."${traitName}.sensorTypes"?.each { String sensorType ->
        // default to true
        Map sensorMapping = [
             reportsDescriptiveState:            true,
             reportsNumericState:                true,
        ]

        // build the descriptive traits
        if (GOOGLE_SENSOR_STATES[sensorType].descriptiveState) {
             if (GOOGLE_SENSOR_STATES[sensorType].numericAttribute != sBLK) {
                  sensorMapping.reportsDescriptiveState = settings."${traitName}.sensorTypes.${sensorType}.reportsDescriptiveState"
             }
             // assign a default if undefined
             if (sensorMapping.reportsDescriptiveState == null) {
                 sensorMapping.reportsDescriptiveState = true
             }
             if (sensorMapping.reportsDescriptiveState) {
                  sensorMapping << [
                       descriptiveState:         settings."${traitName}.sensorTypes.${sensorType}.availableStates",
                       descriptiveAttribute:     settings."${traitName}.sensorTypes.${sensorType}.descriptiveAttribute",
                  ]
             }
        } else {
            // not supported
            sensorMapping.reportsDescriptiveState = false
        }
        // build the numeric traits
        if (GOOGLE_SENSOR_STATES[sensorType].numericAttribute) {
             if (GOOGLE_SENSOR_STATES[sensorType].descriptiveState != sBLK) {
                  sensorMapping.reportsNumericState = settings."${traitName}.sensorTypes.${sensorType}.reportsNumericState"
             }
             // assign a default if undefined
             if (sensorMapping.reportsNumericState == null) {
                 sensorMapping.reportsNumericState = true
             }
             if (sensorMapping.reportsNumericState) {
                  sensorMapping << [
                       numericAttribute:         settings."${traitName}.sensorTypes.${sensorType}.numericAttribute",
                       numericUnits:             settings."${traitName}.sensorTypes.${sensorType}.numericUnits",
                  ]
             }
        } else {
            // not supported
            sensorMapping.reportsNumericState = false
        }
        sensorStateMapping.sensorTypes[sensorType] = sensorMapping
    }

    return sensorStateMapping
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_SoftwareUpdate(String traitName) {
    return [
        lastSoftwareUpdateUnixTimestampSecAttribute:
                                  settings."${traitName}.lastSoftwareUpdateUnixTimestampSecAttribute",
        softwareUpdateCommand:                          settings."${traitName}.softwareUpdateCommand",
        commands:                                       ["Software Update"]
    ]
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_StartStop(String traitName) {
    Boolean canPause
    canPause = settings."${traitName}.canPause"
    if (canPause == null) {
        canPause = true
    }
    Map  startStopTrait = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_TemperatureControl(String traitName) {
    Map<String,Object> tempControlTrait = [
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

private thermostatSetpointAttributeForMode(String traitName, String mode) {
    Map attrPref = THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES[mode]
    if (!attrPref) {
        return null
    }
    def value
    value = settings."${traitName}.${attrPref.name}"
    // Device types created with older versions of the app may not have this set,
    // so fall back if the mode-based setting isn't set
    value = value ?: settings."${traitName}.setpointAttribute"
    return value
}

private thermostatSetpointCommandForMode(String traitName, String mode) {
    Map commandPref = THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES[mode]
    if (!commandPref) {
        return null
    }
    def value
    value = settings."${traitName}.${commandPref.name}"
    // Device types created with older versions of the app may not have this set,
    // so fall back if the mode-based setting isn't set
    value = value ?: settings."${traitName}.setSetpointCommand"
    return value
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_TemperatureSetting(String traitName) {
    Map tempSettingTrait = [
        temperatureUnit:             settings."${traitName}.temperatureUnit",
        currentTemperatureAttribute: settings."${traitName}.currentTemperatureAttribute",
        // queryOnly may be null for device traits defined with older versions,
        // so coerce it to a boolean
        queryOnly:                   settings."${traitName}.queryOnly" as Boolean,
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

        tempSettingTrait.modes.each { String mode ->
            String hubitatMode = settings."${traitName}.mode.${mode}.hubitatMode"
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Timer(String traitName) {
    Map timerTrait = [
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

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Toggles(String traitName) {
    Map togglesTrait = [
        toggles:  [],
        commands: [],
    ]
    List<Map> toggles = ((List<String>)settings."${traitName}.toggles")?.collect { String toggle ->
        Map toggleAttrs = [
            (sNM): toggle,
            traitName: traitName,
            labels: ((String)settings."${toggle}.labels")?.split(",")
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
                "${((List)toggle.labels)[0]} On" as String,
                "${((List)toggle.labels)[0]} Off" as String
            ]
        }
    }
    return togglesTrait
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_TransportControl(String traitName) {
    Map transportControlTrait = [
        nextCommand:  settings."${traitName}.nextCommand",
        pauseCommand:  settings."${traitName}.pauseCommand",
        previousCommand:  settings."${traitName}.previousCommand",
        resumeCommand:  settings."${traitName}.resumeCommand",
        stopCommand:  settings."${traitName}.stopCommand",
        commands: ["Next", "Pause", "Previous", "Resume", "Stop"],
    ]
    return transportControlTrait
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private Map traitFromSettings_Volume(String traitName) {
    def canMuteUnmute = settings."${traitName}.canMuteUnmute"
    if (canMuteUnmute == null) {
        canMuteUnmute = true
    }

    def canSetVolume = settings."${traitName}.canSetVolume"
    if (canSetVolume == null) {
        canSetVolume = true
    }

    Map volumeTrait = [
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

private List<String> deviceTraitsFromState(String deviceType) {
    if (state.deviceTraits == null) {
        state.deviceTraits = [:]
    }
    return state.deviceTraits."${deviceType}" ?: []
}

private void addTraitToDeviceTypeState(String deviceTypeName, String traitType) {
    List<String> deviceTraits = deviceTraitsFromState(deviceTypeName)
    deviceTraits << traitType
    state.deviceTraits."${deviceTypeName}" = deviceTraits
}

private Map deviceTypeTraitFromSettings(String traitName) {
    String[] pieces = traitName.split("\\.traits\\.")
    String traitType = pieces[i1]
    Map traitAttrs = "traitFromSettings_${traitType}"(traitName)
    traitAttrs.name = traitName
    traitAttrs.type = traitType

    return traitAttrs
}

private deleteDeviceTrait(Map deviceTrait) {
    LOGGER.debug("Deleting device trait ${deviceTrait.name}")
    "deleteDeviceTrait_${deviceTrait.type}"(deviceTrait)
    String[] pieces = ((String)deviceTrait.name).split("\\.traits\\.")
    String deviceType = pieces[iZ]
    String traitType = pieces[i1]
    deviceTraitsFromState(deviceType).remove(traitType)
}

private void rmSettingList(String tname, List<String> s){
    s.each{ String it ->
        app.removeSetting(tname + '.' + it)
    }
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_ArmDisarm(Map deviceTrait) {
    List<String> s
    s=['armedAttribute','armLevelAttribute','exitAllowanceAttribute', 'cancelCommand']
    deviceTrait.armLevels.each { armLevel, googleNames ->
        s.push("armLevels.${armLevel}.googleNames".toString())
        s.push("armCommands.${armLevel}.commandName".toString())
        s.push("armValues.${armLevel}.value".toString())
    }
    s = s+ ['armLevels', 'returnUserIndexToDevice']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Brightness(Map deviceTrait) {
    List<String> s=['brightnessAttribute', 'setBrightnessCommand']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_CameraStream(Map deviceTrait) {
    List<String> s=['cameraStreamURLAttribute','cameraSupportedProtocolsAttribute',
                    'cameraStreamProtocolAttribute','cameraStreamCommand']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_ColorSetting(Map deviceTrait) {
    List<String> s=[ 'fullSpectrum','hueAttribute','saturationAttribute',
            'levelAttribute','setColorCommand','colorTemperature',
            'colorTemperature.min','colorTemperature.max','colorTemperatureAttribute',
            'setColorTemperatureCommand','colorModeAttribute','fullSpectrumModeValue',
            'temperatureModeValue' ]
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Dock(Map deviceTrait) {
    List<String> s=[ 'dockAttribute','dockValue','dockCommand']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_EnergyStorage(Map deviceTrait) {
    List<String> s=['energyStorageDistanceUnitForUX','isRechargeable','queryOnlyEnergyStorage',
            'chargeCommand','descriptiveCapacityRemaining','capacityRemainingRawValue',
            'capacityRemainingUnit','capacityUntilFullRawValue','capacityUntilFullUnit',
            'isChargingAttribute','chargingValue','isPluggedInAttribute','pluggedInValue' ]
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_FanSpeed(Map deviceTrait) {
    List<String> s
    s= ['currentSpeedAttribute','setFanSpeedCommand','reversible', 'reverseCommand']
    ((Map<String,String>)deviceTrait.fanSpeeds).each { fanSpeed, googleNames ->
        s.push("speed.${fanSpeed}.googleNames".toString())
    }
    s = s + ['supportsFanSpeedPercent', 'setFanSpeedPercentCommand','currentFanSpeedPercent',
            'fanSpeeds']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_HumiditySetting(Map deviceTrait) {
    List<String> s= ['humidityAttribute','humiditySetpointAttribute',
                'setHumidityCommand','humidityRange.min','humidityRange.max',
                'queryOnly']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Locator(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    app.removeSetting(tname + ".locatorCommand")
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_LockUnlock(Map deviceTrait) {
    List<String> s= [
    'lockedUnlockedAttribute','lockedValue','lockCommand', 'unlockCommand', 'returnUserIndexToDevice']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_MediaState(Map deviceTrait) {
    List<String> s= [
            'supportActivityState', 'supportPlaybackState', 'activityStateAttribute', 'playbackStateAttribute']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_OccupancySensing(Map deviceTrait) {
    List<String> s= [
            'occupancySensorType', 'occupancyAttribute', 'occupiedValue', 'occupiedToUnoccupiedDelaySec',
            'unoccupiedToOccupiedDelaySec', 'unoccupiedToOccupiedEventThreshold']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_OnOff(Map deviceTrait) {
    List<String> s= [
            'onOffAttribute','onValue', 'offValue', 'controlType', 'onCommand',
            'offCommand', 'onOffCommand', 'onParameter', 'offParameter']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_OpenClose(Map deviceTrait) {
    List<String> s= [
            'discreteOnlyOpenClose', 'openCloseAttribute', 'openValue', 'closedValue',
            'reverseDirection', 'openCommand', 'closeCommand', 'openPositionCommand', 'queryOnly']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Reboot(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    app.removeSetting(tname + ".rebootCommand")
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Rotation(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    app.removeSetting(tname + ".rotationAttribute")
    app.removeSetting(tname + ".setRotationCommand")
    app.removeSetting(tname + ".continuousRotation")
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Scene(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    app.removeSetting(tname + ".activateCommand")
    app.removeSetting(tname + ".deactivateCommand")
    app.removeSetting(tname + ".sceneReversible")
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_SensorState(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    GOOGLE_SENSOR_STATES.each { sensorType, sensorSettings ->
        app.removeSetting(tname + ".sensorTypes.${sensorType}.availableStates")
        app.removeSetting(tname + ".sensorTypes.${sensorType}.descriptiveAttribute")
        app.removeSetting(tname + ".sensorTypes.${sensorType}.numericAttribute")
        app.removeSetting(tname + ".sensorTypes.${sensorType}.numericUnits")
        app.removeSetting(tname + ".sensorTypes.${sensorType}.reportsDescriptiveState")
        app.removeSetting(tname + ".sensorTypes.${sensorType}.reportsNumericState")
    }
    app.removeSetting(tname + ".sensorTypes")
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_SoftwareUpdate(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    app.removeSetting(tname + ".lastSoftwareUpdateUnixTimestampSecAttribute")
    app.removeSetting(tname + ".softwareUpdateCommand")
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_StartStop(Map deviceTrait) {
    List<String> s= [
            'canPause', 'startStopAttribute', 'pauseUnPauseAttribute', 'startValue', 'stopValue',
            'pauseValue', 'startCommand', 'stopCommand', 'pauseCommand' ]
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_TemperatureControl(Map deviceTrait) {
    List<String> s= [
            'temperatureUnit', 'currentTemperatureAttribute', 'queryOnly', 'setpointAttribute',
            'setTemperatureCommand', 'minTemperature', 'maxTemperature', 'temperatureStep']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_TemperatureSetting(Map deviceTrait) {
    List<String> s
    s= [
        'temperatureUnit', 'currentTemperatureAttribute', 'queryOnly', 'modes',
        'heatcoolBuffer', 'range.min', 'range.max', 'setModeCommand', 'currentModeAttribute']
    GOOGLE_THERMOSTAT_MODES.each { String mode, String display ->
        s.push("mode.${mode}.hubitatMode")
        String attrPrefName = THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES[mode]?.name
        if (attrPrefName) {
            s.push("${attrPrefName}")
        }
        String commandPrefName = THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES[mode]?.name
        if (commandPrefName) {
            s.push("${commandPrefName}")
        }
    }
    // These settings are no longer set for new device types, but may still exist
    // for device types created with older versions of the app
    s.push("setpointAttribute")
    s.push("setSetpointCommand")
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Timer(Map deviceTrait) {
    List<String> s= [
            'maxTimerLimitSec', 'commandOnlyTimer', 'timerRemainingSecAttribute', 'timerPausedAttribute',
            'timerStartCommand', 'timerAdjustCommand', 'timerCancelCommand',
            'timerPauseCommand', 'timerResumeCommand', 'timerPausedValue']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_TransportControl(Map deviceTrait) {
    List<String> s= [ 'nextCommand', 'pauseCommand', 'previousCommand', 'resumeCommand', 'stopCommand']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Volume(Map deviceTrait) {
    List<String> s= [
            'volumeAttribute', 'setVolumeCommand', 'volumeStep', 'canMuteUnmute', 'canSetVolume',
            'muteAttribute', 'mutedValue', 'unmutedValue', 'muteCommand', 'unmuteCommand']
    rmSettingList((String)deviceTrait.name,s)
}

@SuppressWarnings(['UnusedPrivateMethod','unused'])
private void deleteDeviceTrait_Toggles(Map deviceTrait) {
    String tname= (String)deviceTrait.name
    ((List<Map>)deviceTrait.toggles).each { Map toggle ->
        deleteToggle(toggle)
    }
    app.removeSetting(tname + ".toggles")
}

private deleteToggle(Map toggle) {
    LOGGER.debug("Deleting toggle: ${toggle}")
    def toggles = settings."${toggle.traitName}.toggles"
    toggles.remove(toggle.name)
    app.updateSetting("${toggle.traitName}.toggles", toggles)
    app.removeSetting("${toggle.name}.labels")
    deleteDeviceTrait_OnOff(toggle)
}

private Map deviceTypeTraitsFromSettings(String deviceTypeName) {
    return deviceTraitsFromState(deviceTypeName).collectEntries { traitType ->
        String traitName = "${deviceTypeName}.traits.${traitType}"
        Map deviceTrait = deviceTypeTraitFromSettings(traitName)
        [traitType, deviceTrait]
    }
}

private Map<String,Object> deviceTypeFromSettings(String deviceTypeName, Boolean toClean=false) {
    def deviceType = [
        (sNM):                    deviceTypeName,
        display:                  (String)settings."${deviceTypeName}.display",
        (sTYPE):                  (String)settings."${deviceTypeName}.type",
        googleDeviceType:         (String)settings."${deviceTypeName}.googleDeviceType",
        devices:                  (List)settings."${deviceTypeName}.devices",
        traits:                   deviceTypeTraitsFromSettings(deviceTypeName),
        confirmCommands:          [],
        secureCommands:           [],
        pinCodes:                 [],
        useDevicePinCodes:        false,
        pinCodeAttribute:         sNL,
        pinCodeValue:             sNL,
    ]

    if (deviceType.display == null) {
        if(!toClean)return null
    }

    List confirmCommands = settings."${deviceTypeName}.confirmCommands"
    if (confirmCommands) {
        deviceType.confirmCommands = confirmCommands
    }

    def secureCommands = settings."${deviceTypeName}.secureCommands"
    if (secureCommands) {
        deviceType.secureCommands = secureCommands
    }

    List<String> pinCodes = settings."${deviceTypeName}.pinCodes"
    pinCodes?.each { String pinCodeId ->
        deviceType.pinCodes << [
            id:    pinCodeId,
            (sNM):  settings."${deviceTypeName}.pin.${pinCodeId}.name",
            (sVAL): settings."${deviceTypeName}.pin.${pinCodeId}.value",
        ]
    }

    def useDevicePinCodes = settings."${deviceTypeName}.useDevicePinCodes"
    if (useDevicePinCodes) {
        deviceType.useDevicePinCodes = useDevicePinCodes
        deviceType.pinCodeAttribute = settings."${deviceTypeName}.pinCodeAttribute"
        deviceType.pinCodeValue = settings."${deviceTypeName}.pinCodeValue"
    }

    return deviceType
}

private deleteDeviceTypePin(deviceType, String pinId) {
    LOGGER.debug("Removing pin with ID ${pinId} from device type ${deviceType.name}")
    Integer pinIndex = deviceType.pinCodes.findIndexOf { it.id == pinId }
    deviceType.pinCodes.removeAt(pinIndex)
    app.updateSetting("${deviceType.name}.pinCodes", ((List<Map<String,String>>)deviceType.pinCodes)*.id)
    app.removeSetting("${deviceType.name}.pin.${pinId}.name")
    app.removeSetting("${deviceType.name}.pin.${pinId}.value")
}

private addDeviceTypePin(deviceType) {
    deviceType.pinCodes << [
        id: UUID.randomUUID().toString(),
        (sNM): sNL,
        (sVAL): null
    ]
    app.updateSetting("${deviceType.name}.pinCodes", deviceType.pinCodes*.id)
}

private Map<String,Object> modeSceneDeviceType() {
    return [
        (sNM):             "hubitat_mode",
        display:          "Hubitat Mode",
        googleDeviceType: "SCENE",
        confirmCommands:  [],
        secureCommands:   [],
        pinCodes:         [],
        traits: [
            Scene: [
                (sNM): "hubitat_mode",
                (sTYPE): "Scene",
                sceneReversible: false
            ]
        ],
        devices: settings.modesToExpose.collect { mode ->
            [
                (sNM): mode,
                label: "${mode} Mode",
                id: "hubitat_mode_${mode}"
            ]
        },
    ]
}

private List<Map> deviceTypes() {
    if (state.nextDeviceTypeIndex == null) {
        state.nextDeviceTypeIndex = 0
    }

    List<Map> deviceTypes = []
    (0..<state.nextDeviceTypeIndex).each { i ->
        Map deviceType = deviceTypeFromSettings("deviceTypes.${i}")
        if (deviceType != null) {
            deviceTypes << deviceType
        }
    }
    return deviceTypes
}

private Map<String,Map<String,Object>> allKnownDevices() {
    Map<String,Map<String,Object>> knownDevices = [:]
    // Add "device" entries for exposed mode scenes
    (deviceTypes() + [modeSceneDeviceType()]).each { Map deviceType ->
        ((List)deviceType.devices).each { device ->
            knownDevices."${device.id}" = [
                device: device,
                deviceType: deviceType
            ]
        }
    }

    return knownDevices
}

private roundTo(number, Integer decimalPlaces) {
    Integer factor = Math.max(i1, 10 * decimalPlaces)
    try {
        return Math.round(number * factor) / factor
    } catch (NullPointerException e) {
        LOGGER.exception("Attempted to round null!", e)
        return null
    }
}

private static Integer googlePercentageToHubitat(percentage) {
    // Google is documented to provide percentages in the range [0..100], as
    // is Hubitat's SwitchLevel (setLevel), WindowBlind (setPosition).
    //
    // Just to be safe, clamp incoming values from Google to the range [0..100].
    return Math.max(iZ, Math.min(i100, percentage as Integer))
}

private static Integer hubitatPercentageToGoogle(percentage) {
    // Hubitat's driver capabilities for SwitchLevel (setLevel) and WindowBlind
    // (setPosition) are documented to use the range [0..100], but several
    // Z-Wave dimmer devices return values in the range [0..99].
    //
    // Rather than try to guess which is which, assume nobody will set a
    // device to 99% and map that specific value to 100.
    //
    // Clamp the value to ensure it's in the range [0..100], then map 99
    // to 100.
    Integer clamped = Math.max(iZ, Math.min(i100, percentage as Integer))
    return clamped == 99 ? i100 : clamped
}

@Field
private final Map<String,Closure> LOGGER = [
    debug: { if (gtSetB('debugLogging')) { doLog(sDBG,it) } },
    info: { doLog(sINFO,it) },
    warn: { doLog(sWARN,it) },
    error: { doLog(sERROR,it) },
    exception: { message, exception ->
        def relevantEntries = exception.stackTrace.findAll { entry -> entry.className.startsWith("user_app") }
        def line = relevantEntries[0].lineNumber
        def method = relevantEntries[0].methodName
        doLog(sERROR,"${message}: ${exception} at line ${line} (${method})")
        if (gtSetB('debugLogging')) {
            doLog(sDBG,"App exception stack trace:\n${relevantEntries.join("\n")}")
        }
    }
]

@Field static final String sERROR='error'
@Field static final String sINFO='info'
@Field static final String sWARN='warn'
@Field static final String sTRC='trace'
@Field static final String sDBG='debug'
@Field static final String sLTH='<'
@Field static final String sGTH='>'

void doLog(String mcmd, String msg, Boolean escapeHtml=true){
    String clr
    switch(mcmd){
        case sINFO:
            clr= '#0299b1'
            break
        case sTRC:
            clr= sCLRGRY
            break
        case sDBG:
            clr= 'purple'
            break
        case sWARN:
            clr= sCLRORG
            break
        case sERROR:
        default:
            clr= sCLRRED
    }
    String myMsg= escapeHtml ? msg.replaceAll(sLTH, '&lt;').replaceAll(sGTH, '&gt;') : msg
    log."$mcmd" escapeHtml ? span(myMsg,clr) : myMsg
}

@Field
private static final Map<String,String> HUBITAT_DEVICE_TYPES = [
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
private static final Map<String,String> GOOGLE_DEVICE_TYPES = [
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
private static final Map<String,String> GOOGLE_DEVICE_TRAITS = [
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
    OccupancySensing: "Occupancy Sensing",
    OnOff: "On/Off",
    OpenClose: "Open/Close",
    Reboot: "Reboot",
    Rotation: "Rotation",
    //RunCycle: "Run Cycle",
    SensorState: "Sensor State",
    Scene: "Scene",
    SoftwareUpdate: "Software Update",
    StartStop: "Start/Stop",
    //StatusReport: "Status Report",
    TemperatureControl: "Temperature Control",
    TemperatureSetting: "Temperature Setting",
    Timer: "Timer",
    Toggles: "Toggles",
    TransportControl: "Transport Control",
    Volume: "Volume",
]

@Field
private static final Map<String,String> GOOGLE_THERMOSTAT_MODES = [
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
private static final Map<String,Map> GOOGLE_SENSOR_STATES = [
    "AirQuality" :
    [
        "label" :                                "Air Quality",
        "descriptiveAttribute" :                 "airQualityDescriptive",
        "descriptiveState" :  [
            "healthy":                           "Healthy",
            "moderate":                          "Moderate",
            "unhealthy":                         "Unhealthy",
            "unhealthy for sensitive groups":    "Unhealthy for Sensitive Groups",
            "very unhealthy":                    "Very Unhealthy",
            "hazardous":                         "Hazardous",
            "good":                              "Good",
            "fair":                              "Fair",
            "poor":                              "Poor",
            "very poor":                         "Very Poor",
            "severe":                            "Severe",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      "airQualityValue",
        "numericUnits" :                         "AQI",
    ],
    "CarbonMonoxideLevel" :
    [
        "label" :                                "Carbon Monoxide Level",
        "descriptiveAttribute" :                 "carbonMonoxideDescriptive",
        "descriptiveState" :  [
            "carbon monoxide detected":          "Carbon Monoxide Detected",
            "high":                              "High",
            "no carbon monoxide detected":       "No Carbon Monoxide Detected",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      "carbonMonoxideValue",
        "numericUnits" :                         "PARTS_PER_MILLION",
    ],
    "SmokeLevel" :
    [
        "label" :                                "Smoke Level",
        "descriptiveAttribute" :                 "smokeLevelDescriptive",
        "descriptiveState" :  [
            "smoke detected":                    "Smoke Detected",
            "high":                              "High",
            "no smoke detected":                 "No Smoke Detected",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      "smokeLevelValue",
        "numericUnits" :                         "PARTS_PER_MILLION",
    ],
    "FilterCleanliness" :
    [
        "label" :                                "Filter Cleanliness",
        "descriptiveAttribute" :                 "filterCleanlinesDescriptive",
        "descriptiveState" :  [
            "clean":                             "Clean",
            "dirty":                             "Dirty",
            "needs replacement":                 "Needs Replacement",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      '',
        "numericUnits" :                         '',
    ],
    "WaterLeak" :
    [
        "label" :                                "Water Leak",
        "descriptiveAttribute" :                 "waterLeakDescriptive",
        "descriptiveState" :  [
            "leak":                              "Leak",
            "no leak":                           "No Leak",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      '',
        "numericUnits" :                         '',
    ],
    "RainDetection" :
    [
        "label" :                                "Rain Detection",
        "descriptiveAttribute" :                 "rainDetectionDescriptive",
        "descriptiveState" :  [
            "rain detected":                     "Rain Detected",
            "no rain detected":                  "No Rain Detected",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      '',
        "numericUnits" :                         '',
    ],
    "FilterLifeTime" :
    [
        "label" :                                "Filter Life Time",
        "descriptiveAttribute" :                 "filterLifeTimeDescriptive",
        "descriptiveState" :  [
            "new":                               "New",
            "good":                              "Good",
            "replace soon":                      "Replace Soon",
            "replace now":                       "Replace Now",
            "unknown":                           "Unknown",
        ],
        "numericAttribute":                      "filterLifeTimeValue",
        "numericUnits" :                         "PERCENTAGE",
    ],
    "PreFilterLifeTime" :
    [
        "label" :                                "Pre-Filter Life Time",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "preFilterLifeTimeValue",
        "numericUnits" :                         "PERCENTAGE",
    ],
    "HEPAFilterLifeTime" :
    [
        "label" :                                "HEPA Filter Life Time",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "HEPAFilterLifeTimeValue",
        "numericUnits" :                         "PERCENTAGE",
    ],
    "Max2FilterLifeTime" :
    [
        "label" :                                "Max2 Filter Life Time",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "max2FilterLifeTimeValue",
        "numericUnits" :                         "PERCENTAGE",
    ],
    "CarbonDioxideLevel" :
    [
        "label" :                                "Carbon Dioxide Level",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "carbonDioxideLevel",
        "numericUnits" :                         "PARTS_PER_MILLION",
    ],
    "PM2.5" :
    [
        "label" :                                "PM2.5",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "PM2_5Level",
        "numericUnits" :                         "MICROGRAMS_PER_CUBIC_METER",
    ],
    "PM10" :
    [
        "label" :                                "PM10",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "PM10Level",
        "numericUnits" :                         "MICROGRAMS_PER_CUBIC_METER",
    ],
    "VolatileOrganicCompounds" :
    [
        "label" :                                "Volatile Organic Compounds",
        "descriptiveAttribute" :                 '',
        "descriptiveState" :                     '',
        "numericAttribute":                      "volatileOrganicCompoundsLevel",
        "numericUnits" :                         "PARTS_PER_MILLION",
    ],
]

@Field
private static final Map<String,Map> THERMOSTAT_MODE_SETPOINT_COMMAND_PREFERENCES = [
    "off": null,
    "heat": [
        "name":  "setHeatingSetpointCommand",
        "title": "Set Heating Setpoint Command"
    ],
    "cool": [
        "name":  "setCoolingSetpointCommand",
        "title": "Set Cooling Setpoint Command"
    ],
].withDefault { mode ->
    [
        "name":  "set${mode.capitalize()}SetpointCommand",
        "title": "Set ${GOOGLE_THERMOSTAT_MODES[mode]} Setpoint Command"
    ]
} as Map<String, Map>

@Field
private static final Map<String,Map> THERMOSTAT_MODE_SETPOINT_ATTRIBUTE_PREFERENCES = [
    "off": null,
    "heat": [
        "name":  "heatingSetpointAttribute",
        "title": "Heating Setpoint Attribute"
    ],
    "cool": [
        "name":  "coolingSetpointAttribute",
        "title": "Cooling Setpoint Attribute"
    ]
].withDefault { mode ->
    [
        "name":  "${mode}SetpointAttribute",
        "title": "${GOOGLE_THERMOSTAT_MODES[mode]} Setpoint Attribute",
    ]
} as Map<String, Map>



static String myObj(obj){
    if(obj instanceof String)return 'String'
    else if(obj instanceof Map)return 'Map'
    else if(obj instanceof List)return 'List'
    else if(obj instanceof ArrayList)return 'ArrayList'
    else if(obj instanceof BigInteger)return 'BigInt'
    else if(obj instanceof Long)return 'Long'
    else if(obj instanceof Integer)return 'Int'
    else if(obj instanceof Boolean)return 'Bool'
    else if(obj instanceof BigDecimal)return 'BigDec'
    else if(obj instanceof Double)return 'Double'
    else if(obj instanceof Float)return 'Float'
    else if(obj instanceof Byte)return 'Byte'
    else if(obj instanceof Closure)return 'Closure'
    else if(obj instanceof com.hubitat.app.DeviceWrapper)return 'Device'
    else{
        //      if(eric()) log.error "object: ${describeObject(obj)}"
        return 'unknown'
    }
}

@Field static final String sSPCSB7='      │'
@Field static final String sSPCSB6='     │'
@Field static final String sSPCS6 ='      '
@Field static final String sSPCS5 ='     '
@Field static final String sSPCST='┌─ '
@Field static final String sSPCSM='├─ '
@Field static final String sSPCSE='└─ '
@Field static final String sNWL='\n'
@Field static final String sDBNL='\n\n • '

@CompileStatic
static String spanStr(Boolean html,String s){ return html ? span(s) : s }

@CompileStatic
static String doLineStrt(Integer level,List<Boolean>newLevel){
    String lineStrt; lineStrt=sNWL
    Boolean dB; dB=false
    Integer i
    for(i=iZ;i<level;i++){
        if(i+i1<level){
            if(!newLevel[i]){
                if(!dB){ lineStrt+=sSPCSB7; dB=true }
                else lineStrt+=sSPCSB6
            }else lineStrt+= !dB ? sSPCS6:sSPCS5
        }else lineStrt+= !dB ? sSPCS6:sSPCS5
    }
    return lineStrt
}

@CompileStatic
static String dumpListDesc(List data,Integer level,List<Boolean> lastLevel,String listLabel,Boolean html=false,Boolean reorder=true){
    String str; str=sBLK
    Integer n; n=i1
    List<Boolean> newLevel=lastLevel

    List list1=data?.collect{it}
    Integer sz=list1.size()
    for(Object par in list1){
        String lbl=listLabel+"[${n-i1}]".toString()
        if(par instanceof Map){
            Map<String,Object> newmap=[:]
            newmap[lbl]=(Map)par
            Boolean t1=n==sz
            newLevel[level]=t1
            str+=dumpMapDesc(newmap,level,newLevel,n,sz,!t1,html,reorder)
        }else if(par instanceof List || par instanceof ArrayList){
            Map<String,Object> newmap=[:]
            newmap[lbl]=par
            Boolean t1=n==sz
            newLevel[level]=t1
            str+=dumpMapDesc(newmap,level,newLevel,n,sz,!t1,html,reorder)
        }else{
            String lineStrt
            lineStrt=doLineStrt(level,lastLevel)
            lineStrt+=n==i1 && sz>i1 ? sSPCST:(n<sz ? sSPCSM:sSPCSE)
            str+=spanStr(html, lineStrt+lbl+": ${par} (${objType(par)})".toString() )
        }
        n+=i1
    }
    return str
}

@CompileStatic
static String dumpMapDesc(Map<String,Object> data,Integer level,List<Boolean> lastLevel,Integer listCnt=null,Integer listSz=null,Boolean listCall=false,Boolean html=false,Boolean reorder=true){
    String str; str=sBLK
    Integer n; n=i1
    Integer sz=data?.size()
    Map<String,Object> svMap,svLMap,newMap; svMap=[:]; svLMap=[:]; newMap=[:]
    for(Map.Entry<String,Object> par in data){
        String k=(String)par.key
        def v=par.value
        if(reorder && v instanceof Map){
            svMap+=[(k): v]
        }else if(reorder && (v instanceof List || v instanceof ArrayList)){
            svLMap+=[(k): v]
        }else newMap+=[(k):v]
    }
    newMap+=svMap+svLMap
    Integer lvlpls=level+i1
    for(Map.Entry<String,Object> par in newMap){
        String lineStrt
        List<Boolean> newLevel=lastLevel
        Boolean thisIsLast=n==sz && !listCall
        if(level>iZ)newLevel[(level-i1)]=thisIsLast
        Boolean theLast
        theLast=thisIsLast
        if(level==iZ)lineStrt=sDBNL
        else{
            theLast=theLast && thisIsLast
            lineStrt=doLineStrt(level,newLevel)
            if(listSz && listCnt && listCall)lineStrt+=listCnt==i1 && listSz>i1 ? sSPCST:(listCnt<listSz ? sSPCSM:sSPCSE)
            else lineStrt+=((n<sz || listCall) && !thisIsLast) ? sSPCSM:sSPCSE
        }
        String k=(String)par.key
        def v=par.value
        String objType=objType(v)
        if(v instanceof Map){
            str+=spanStr(html, lineStrt+"${k}: (${objType})".toString() )
            newLevel[lvlpls]=theLast
            str+=dumpMapDesc((Map)v,lvlpls,newLevel,null,null,false,html,reorder)
        }
        else if(v instanceof List || v instanceof ArrayList){
            str+=spanStr(html, lineStrt+"${k}: [${objType}]".toString() )
            newLevel[lvlpls]=theLast
            str+=dumpListDesc((List)v,lvlpls,newLevel,sBLK,html,reorder)
        }
        else{
            str+=spanStr(html, lineStrt+"${k}: (${v}) (${objType})".toString() )
        }
        n+=i1
    }
    return str
}

@CompileStatic
static String objType(obj){ return span(myObj(obj),sCLRORG) }

@CompileStatic
static String getMapDescStr(Map<String,Object> data,Boolean reorder=true, Boolean html=true){
    List<Boolean> lastLevel=[true]
    String str=dumpMapDesc(data,iZ,lastLevel,null,null,false,html,reorder)
    return str!=sBLK ? str:'No Data was returned'
}

@SuppressWarnings('unused')
def pageDump(){
    String message=getMapDescStr(allKnownDevices(),false)
    return dynamicPage((sNM):'pageDump',(sTIT):sBLK,uninstall:false){
        section('Devices dump'){
            paragraph message
        }
    }
}

@CompileStatic
static String span(String str,String clr=sNL,String sz=sNL,Boolean bld=false,Boolean br=false){
    return str ? sSPANS+"${(clr || sz || bld) ? sSTYLE+"'${clr ? sCLR+"${clr};":sBLK}${sz ? sFSZ+"${sz};":sBLK}${bld ? sFWT:sBLK}'":sBLK}>${str}</span>${br ? sLINEBR:sBLK}": sBLK
}

private String gtLbl(d){ return "${d?.label ?: d?.name ?: "no device name"}".toString() }
private String gtSetStr(String nm){ return (String)settings[nm] }
private Boolean gtSetB(String nm){ return (Boolean)settings[nm] }
//private Integer gtSetI(String nm){ return (Integer)settings[nm] }
private List gtSetList(String nm){ return (List)settings[nm] }

private String gtStStr(String nm){ return (String)state[nm] }

//private static Class DeviceWrapperClass(){ return 'hubitat.app.DeviceWrapper' as Class }

//@Field static final String sCLR4D9    = '#2784D9'
@Field static final String sCLRRED      = 'red'
//@Field static final String sCLRRED2   = '#cc2d3b'
@Field static final String sCLRGRY      = 'gray'
//@Field static final String sCLRGRN    = 'green'
//@Field static final String sCLRGRN2   = '#43d843'
@Field static final String sCLRORG      = 'orange'
@Field static final String sLINEBR      = '<br>'
@Field static final String sSPANS       = '<span'
@Field static final String sSTYLE       = ' style='
@Field static final String sCLR         = 'color: '
@Field static final String sFSZ         = 'font-size: '
@Field static final String sFWT         = 'font-weight: bold;'
//@Field static final String sIMPT        = 'px !important;'

@Field static final String sNL=(String)null
@Field static final String sBLK=''

@Field static final String sNM='name'
@Field static final String sREQ='required'
@Field static final String sTYPE='type'
@Field static final String sVAL='value'
@Field static final String sDEFVAL='defaultValue'
@Field static final String sTIT='title'
@Field static final String sBOOL='bool'
@Field static final String sTEXT='text'
@Field static final String sENUM='enum'
@Field static final String sOPTIONS='options'
@Field static final String sMULTIPLE='multiple'
@Field static final String sSUBONCHG='submitOnChange'

@Field static final String sON='on'
@Field static final String sOFF='off'
//@Field static final String sSWITCH='switch'

@Field static final Integer iZ=0
@Field static final Integer i1=1
@Field static final Integer i100=100
//@Field static final Integer i2=2
