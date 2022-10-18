# Community Maintained Google Home Integration

A full-featured and highly configurable integration between Hubitat Elevation and Google Home.

# Installation

This integration consists of two parts:

- A Hubitat app that will be installed on your Hubitat Elevation hub
- A Google smart home Action

## Installing the Hubitat App

To install the Hubitat App:

1. Navigate to "Apps Code" in Hubitat
2. Click "New App"
3. Paste the code from [google-home-community.groovy](google-home-community.groovy) into the editor and click save
4. Click "OAuth"
5. In the popup dialog, click "Enable OAuth in App"
6. Click "Update"
7. Click "OAuth" again
8. Make a note of the values in the "Client ID" and "Client Secret" fields
    - Note: Using keyboard shortcuts to copy from the Client ID and Client Secret fields doesn't work in some browsers (notably Google Chrome).  You may need to either right-click and copy from the context menu or type the values out manually.
9. Navigate to "Apps" in Hubitat
10. Click "Add User App" and select "Google Home Community"
11. Make a note of the app's ID.  This can be found in your web browser's URL bar.
    - The URL should be `http://{your hub IP}/installedapp/configure/{app ID}/mainPreferences`
    - The number between "configure/" and "/mainPreferences" is your app's ID
    - For example, if the URL for the app is `http://192.168.0.123/installedapp/configure/12345/mainPreferences` then your app ID is `12345`
12. Configure at least one device type.  See [Configuring Devices](#configuring-devices) below for more information.
13. Click "Done".

## Creating the Google smart home Action

Before creating your Google smart home Action, you will need your Hubitat hub's ID:

1. Navigate to https://portal.hubitat.com
2. Log in
3. Navigate to "My Hubs"
4. Click on the Hubitat logo above your hub
5. Find "Hub ID" in the dialog that pops up, and make a note of its value

To create your Google smart home Action:

1. Navigate to https://console.actions.google.com
2. Click "New project"
3. Enter a name for the project and click "Create project"
4. Select "Smart Home" and click "Start Building"
5. Click the "Develop" tab.
6. On the "Invocation" screen, give your Action a name
7. Click "Actions" in the menu
8. Enter the following as the Fulfillment URL:
    - `https://cloud.hubitat.com/api/{your hub ID}/apps/{app ID}/action`
    - For example:
        - Your app ID from step 11 of the "Installing the Hubitat App" section above is `12345`
        - Your Hub ID from step 5 of the previous section is `b97ac781-df30-43cd-98a4-e8a9a040bada`
        - Your Fulfillment URL would be `https://cloud.hubitat.com/api/b97ac781-df30-43cd-98a4-e8a9a040bada/apps/12345/action`
9. Click "Account linking" in the menu
10. Enter the Client ID and Client Secret you got when enabling OAuth for the Google Home Community app
11. Enter `https://oauth.cloud.hubitat.com/oauth/authorize` as the Authorization URL
12. Enter `https://oauth.cloud.hubitat.com/oauth/token` as the Token URL
13. Click "Next"
14. Leave everything unchecked in the "Use your app for account linking (optional)" section and click "Next"
15. In the "Configure your client (optional)" section, enter "app" in the Scopes box
16. Click "Save"
17. Click the "Test" tab
18. In the top-right of the page, click "Settings" and ensure "On device testing" is enabled
    - Note: You may need to wait a few minutes at this point for Google's backend to get itself sorted out.  If after several minutes you get a quick loading screen and bounced back to the list after step 23 below, you may need to make a trivial change (such as removing and re-adding a character from the Action's display name) and save the Action to force it to figure its life out.
19. Open the Google Home app on your phone or tablet
20. Tap the "+" in the top-left corner
21. Tap "Set up device"
22. Tap "Works with Google"
23. In the list, find the entry `[test] {your action name}`
24. Enter your Hubitat account credentials and click "Sign In"
25. Select your hub and tap "Select"
26. Make sure at least one device is selected to expose to Google Home
    - Note: If you do not select any devices or your selected devices have no traits defined, the linking process will fail.  If that happens, go back to the Google Home Community app in Hubitat and select at least one device and then try again starting from step 18 above.
27. Tap "Authorize"


# Configuring Devices

The first step to configuring a device to link to Google Home is to define a device type.  This will determine what kind of device Google Home sees devices as, what commands are available to interact with them, and how those commands will translate to Hubitat commands.

## Defining a Device Type

1. Navigate to "Apps" in Hubitat
2. Click on the "Google Home Community" app you created
3. Click "Define new device type"
4. Fill in the settings on the "Device Type Definition" page.  See [Device Type Settings](#device-type-settings) below.
5. Select one or more device traits to add to this device type.  Device traits determine what commands are available for a device type and how those commands translate to Hubitat commands.  All device types must have at least one trait defined or Google Home will fail to sync your devices.  See below for more information about individual device traits.
6. Click "Next"
7. Back on the main preferences page, click "{device type} devices" and select which devices you would like to link to Google Home using this device type
8. Click "Done"
9. Either pull down to refresh in the Google Home app or say "Hey Google, sync my smart home devices"
10. The selected devices should appear in your Google Home app and be available to control using the Google assistant

Note: The same device should not be selected for multiple device types.

## Device Type Settings

Each device type has the following settings:

- Device type name:  This is the display name for this device type.  It will be shown in the device selector once the device type is defined.
- Device type: A Hubitat capability that will determine which devices are available to select for this device type.
- Google Home device type: The type of device that Google Home will see the selected devices as.  Controls the icon and available controls in the Google Home app and on Google Assistant devices with screens.  The "Scene" device type is special.  Devices with the "Scene" type are not shown in the main Google Home app interface, but may be controlled by voice and using routines.
- Device traits: The traits defined for this device.  See [Device Traits](#device-traits) below.
- Actions to Confirm:  Only visible if the device has one or more traits with actions.  The Google Assistant will ask for confirmation before performing these actions.  This is primarily useful to prevent the assistant from triggering an action accidentally.
- Actions requiring PIN:  Only visible if the device has one or more traits with actions.  The Google Assistant will request a PIN code before performing these actions.  Useful for security-critical actions such as unlocking a lock or opening a garage door.
- PIN Codes:  Only visible if one or more actions are configured to require a PIN code.  Allows you to manage the PIN codes that will be accepted for this device type.
- Use device pincodes: Set to retrieve the pincodes from the device.  Set to false to use the app pincodes.  Defaults to 'false'.
  NOTE: Global pincodes are always useable.
	- Device pin code attribute: The device attribute used to query the device pincodes JSON map.  Maps to the `lockCodes` attribute by default.
	- Device pin code value: The key value in the JSON map pointing to the user pincode.  Maps to the `code` value by default.

### Arm/Disarm

The Arm/Disarm trait is user to control security systems.  It can be controlled by saying things like "Hey Google, arm {device} to {level}" or "Hey Google, disarm {device}}" and queried by saying things like "Hey Google, is the {device} armed?".  It has the following configuration parameters:

- Armed/Disarmed Attribute: The device attribute used to query the current alarm status of the device.  Maps to the `securityKeypad` attribute by default.
- Current Arm Level Attribute: The device attribute used to query the current alarm level of the device.  Maps to the `securityKeypad` attribute by default.
- Exit Delay Value Attribute: The device attribute used to indicate how long the user has until the alarm is set.  Maps to the `exitAllowance` attribute by default.
- Supported Alarm Levels: The device attribute used to indicate which alarm levels are available.  Available selections are "Disarm, Home, Night, Away".  For each setting the following settings are available:
	- Google Home Level Names for Disarm: Comma separated list of synonyms that Google can send to set this level.  Maps to the `Disarm` attribute by default.
	- Hubitat Command for Disarm: The device command used to set this level.  Maps to the `disarm` attribute by default.
	- Hubitat Value for Home: The device value returned when the device is set to this level.  Maps to the `disarmed` attribute by default.
	- Google Home Level Names for Night: Comma separated list of synonyms that Google can send to set this level.  Maps to the `Night` attribute by default.
	- Hubitat Command for Night: The device command used to set this level.  Maps to the `armNight` attribute by default.
	- Hubitat Value for Night: The device value returned when the device is set to this level.  Maps to the `armed night` attribute by default.
	- Google Home Level Names for Home: Comma separated list of synonyms that Google can send to set this level.  Maps to the `Home` attribute by default.
	- Hubitat Command for Home: The device command used to set this level.  Maps to the `armHome` attribute by default.
	- Hubitat Value for Home: The device value returned when the device is set to this level.  Maps to the `armed home` attribute by default.
	- Google Home Level Names for Away: Comma separated list of synonyms that Google can send to set this level.  Maps to the `Away` attribute by default.
	- Hubitat Command for Away: The device command used to set this level.  Maps to the `armAway` attribute by default.
	- Hubitat Value for Away: The device value returned when the device is set to this level.  Maps to the `armed away` attribute by default.
	- Return user index: If selected, during a successful pincode challenge, the user index is returned with the Arm/Disarm commands. Defaults to 'false'.
	  NOTE: Device must support parameters in the arm / disarm commands.



## Device Traits

Google Home defines a number of device traits that you can mix-and-match to define the functionality of any particular type of device.  Not all traits are currently supported by Google Home Community.  The following are the currently supported device traits and their configuration parameters:

### Brightness

The Brightness trait is primarily used for devices like dimmer switches and light bulbs, but can be used for any device that can be set to a level between 0% and 100%.  It can be controlled by saying things like "Hey Google, set {device} to {level}" and queried by saying things like "Hey Google, what's the level of {device}?".  It has the following configuration parameters:

- Current Brightness Attribute: The device attribute used to query the current brightness level of the device.  Should be in the range 0-100.  Maps to the `level` attribute by default.
- Set Brightness Command: A device command used to set the brightness of the device.  Should accept a brightness level in the range 0-100.  Maps to the `setLevel` command by default.

### CameraStream

The CameraStream trait is used to map compatible video streams for viewing on ChromeCast enabled devices (Nest Hubs, ChromeCast, etc).

It requires a driver that maps the stream URL to the Camera Stream URL Attribute setting.  Maps to `settings` by default.

- This can be accomplished using one or both of the supplied drivers:
    -  Hubitat Virtual Generic Camera Stream Object.groovy: Enter the complete stream URLs of all camera supported protocols in the `Camera HLS stream HTTP URL`, `Camera MP4 stream HTTP URL`, `Camera dash stream HTTP URL`, and/or `Camera smooth stream HTTP URL` input fields.
	   NOTE:  At least ONE URL must be set.  The driver will serve up the supported streams based on the display device request.
    -  Hubitat Virtual BlueIris Camera Stream Object.groovy: For use with the Blue Iris DVR. Enter the `Webserver HTTP URL:Port` (omit the http://), `Camera Short Name`, `Webserver Username (Optional)` and `Webserver Password  (Optional)`.
    -  For both of the above drivers, select the protocol for the stream.

NOTE: These drivers offer no other functionality other than a placeholder for the stream URL.  The buttons are non-functional.

### Color Setting

The Color Setting trait is intended primarily for smart lights that can have their color set.  It supports both full-spectrum color and color temperature.  It can be controlled by saying things like "Hey Goolge, set {device} to blue" or "Hey Google, set {device} to 3500K".  Color cannot currently be queried.  The Color Setting trait has the following configuration parameters:

NOTE: At least one of "Full-Spectrum Color Control" and/or "Color Temperature Control" **must** be set.

- Full-Spectrum Color Control: Set this if the device can be set to any color.  If set, the following settings become available:
    - Hue Attribute: The device attribute used to query the current hue of the device.  Maps to `hue` by default.
    - Saturation Attribute: The device attribute used to query the current saturation of the device.  Maps to `saturation` by default.
    - Level Attribute: The device attribute used to query the current level/value of the device.  Maps to `level` by default.
    - Set Color Command: A device command used to set the color of the device.  Should accept a map with the keys `hue`, `saturaton`, and `level`.  Maps to `setColor` by default.
- Color Temperature Control: Set this if the device can have its color temperature set.  If set, the following settings become available:
    - Minimum Color Temperature: The minimum color temperature to which the device can be set.  Default is 2200.
    - Maximum Color Temperature: The maximum color temperature to which the device can be set.  Default is 6500.
    - Color Temperature Attribute: The device attribute used to query the current color temperature of the device.  Maps to `colorTemperature` by default.
    - Set Color Temperature Command: A device command used to set the color temperature of the device.  Should accept an integer in the range [Minimum Color Temperature, Maximum Color Temperature].  Maps to `setColorTemperature` by default.

If both "Full-Spectrum Color Control" and "Color Temperature Control" are set, the following settings become available:

- Color Mode Attribute: The device attribute used to determine if the device's color is currently set to a full-spectrum color or a color temperature.  Maps to `colorMode` by default.
- Full-Spectrum Mode Value: The value reported by the "Color Mode Attribute" when the device has been set to a full-spectrum color.  Default is "RGB".
- Color Temperature Mode Value: The value reported by the "Color Mode Attribute" when the device has been set to a color temperature.  Default is "CT".

### Dock

The Dock trait is intended to be used for devices such as robot vacuum cleaners that can be commanded to return to their charging dock.  It can be controlled by saying things like "Hey Google, tell {device} to return to its dock" and queried by saying things like "Hey Google, is {device} docked?".  The Dock trait has the following configuration paramters:

- Dock Attribute: The device attribute used to determine if the device is currently docked or not.  Maps to `status` by default.
- Docked Value: The value of the "Docked Attribute" that indicates that the device is currently docked.  Defaults to `docked`.
- Dock Command: A device command used to tell the device to return to its dock.  Maps to `returnToDock` by default.

### Energy Storage

The Energy Storage trait is used for any device that can report energy capacity and optionally start charging.  It can be controlled by saying things like "Hey Google, charge {device}" and queried by saying things like "Hey Google, is {device} charging?", "Hey Google, What is {device} battery level?",
"Hey Google, What is {device} battery percentage?", or "Hey Google, How long until {device} is fully charged?"  This trait has the following configuration parameters:

- Rechargeable: Set to indicate that this device is rechargeable.  Defaults to 'false'.
- Query Only Energy Storage: Set to indicate that this device can only be queried for energy storage and not controlled.  If 'true', the following command is available:
    - Charge Command: A device command used to charge the device.
- Capacity Remaining Value Attribute: The device attribute used to query the current capacity of the device.  Maps to the `battery` attribute by default.
- Capacity Remaining Unit: The unit represented by the Capacity Remaining Value Attribute.  Defaults to 'PERCENTAGE'.
- Capacity Until Full Value Attribute: The device attribute used to query the capacity until full of the device.
- Capacity Until Full Unit: The unit represented by the Capacity Until Full Value Value Attribute.
- Descriptive Capacity Remaining Attribute: The device attribute used to query the current capacity of the device with descriptive text should no number capacity remaining value be reported.
- Charging Attribute: The device attribute used to query the current charging state of the device.
- Charging Value: The value that the Charging attribute will report when the device is charging.
- Plugged In Attribute: The device attribute used to query the current plugged in state of the device.
- Plugged In Value: The value that the Plugged In attribute will report when the device is plugged in.

### Fan Speed

The Fan Speed trait is primarily used for fan controllers with multiple speed settings.  It can be controlled by saying things like "Hey Google, set {device} to {speed}" and queried by saying things like "Hey Google, what's the {device} speed?".  It has the following configuration parameters:

- Current Speed Attribute: The device attribute used to query the current fan speed of the device.  Maps to the `speed` attribute by default.
- Set Speed Command: A device command used to set the fan speed of the device.  Should accept one of the supported fan speeds.  Maps to the `setSpeed` command by default.
- Supported Fan Speeds: The set of fan speed settings that this type of device supports.  If multiple settings will set the device to the same fan speed, you should only select one of them.
- Google Home Level Names for {speed} - A comma-separated list of names that you will use to reference this fan speed when interacting with the Google Assistant.  By default, the name of the speed in Hubitat is used.
- Reversible: Select this if the fan direction can be reversed
- Reverse Command: Only available if "Reversible" is selected.  A device command that can be used to reverse the device's fan direction.
- Supports Percentage Settings: Select this if you want to command the fan using percentages
- Current Fan Speed Percentage Attribute: The device attribute used to query the current fan speed percentage of the device.  Maps to the `level` attribute by default.
- Fan Speed Percent Command: A device command used to set the fan speed of the device i percentage. Maps to the `setLevel` command by default.

### Humidity Setting

The Humidity Setting trait is used for devices that can sense and/or control the ambient humidity, such as a humidity sensor, humidifier, or dehumidifier.  It can be controlled by saying things like "Hey Google, set {device} to 60%" and queried by saying things like "Hey Google, what's the humidity of {device}?" or "Hey Google, what's the humidity in {room}?".  It has the following configuration parameters:

- Humidity Attribute: The device attribute used to query the current humidity.  Should return an integer between 0 and 100.  Maps to the `humidity` attribute by default.
- Query Only Humidity: Set to indicate that this device can only be queried for humidity and not controlled.

The following settings are only available if the "Query Only Humidity" setting is unset:
- Humidity Setpoint Attribute: The device attribute that reports the current humidity setpoint.
- Set Humidity Command: A device command that sets the desired humidity.  Should accept an integer in the range [Minimum Setpoint, Maximum Setpoint].
- Minimum Humidity Setpoint: The minimum humidity setpoint supported for this device type.  Attempting to set the desired humidiy lower will set it to this value instead.  Required if "Maximum Humidity Setpoint" is specified.
- Maximum Humidity Setpoint: The maximum humidity setpoint supported for this device type.  Attempting to set the desired humidiy higher will set it to this value instead.  Required if "Minimum Humidity Setpoint" is specified.

### Locator

The Locator trait is used for finding devices that have audible or visual indicators, such as vacuums, or other devices that beep, ring or flash.  It can be controlled by saying things like "Hey Google, where is {device}" or "Hey Google, find my {device}".  Locator trait has the following configuration parameters:

- Locator Command: A device command used to locate the device.  Should not require any parameters.  Maps to `locate` by default.

### Lock/Unlock

The Lock/Unlock trait is used for anything that can lock and unlock, such as doors and windows.  It can be controlled by saying things like "Hey Google, lock {device}" and queried by saying things like "Hey Google, is {device} locked?".  Since locks are often security-sensitive, it is recommended, though not required, that PIN code support be configured for device types implementing this trait.  The Lock/Unlock trait has the following configuration parameters:

- Locked/Unlocked Attribute: The device attribute used to query the current state of the device.  Maps to the `lock` attribute by default.
- Locked Value: The value that the Locked/Unlocked attribute will report when the device is locked.  Defaults to "locked".
- Lock Command: A device command used to lock the device.  Should not require any parameters.  Maps to `lock` by default.
- Lock Command: A device command used to unlock the device.  Should not require any parameters.  Maps to `unlock` by default.
- Return user index: If selected, during a successful pincode challenge, the user index is returned with the Arm/Disarm commands. Defaults to 'false'.
	  NOTE: Device must support parameters in the lock / unlock commands.

### Media State

The Media State trait is used for reporting the current playback and activity state of a media device.  Query command is unknown at this time.  The trait has the following configuration parameters:

- Support Activity State: Should be set if this device can report the current activity state.  Defaults to 'false'.  If 'true', the following state is available:
    - Activity State Attribute: The device attribute used to query the current activity state of the device.
- Support Playback State: Should be set if this device can report the current playback state.  Defaults to 'false'.  If 'true', the following state is available:
    - Playback State Attribute: The device attribute used to query the current playback state of the device.  Maps to the `status` attribute by default.

### On/Off

The On/Off trait is used for devices that have discreet on and off states such as switches or lights.  It can be controlled by saying things like "Hey Google, turn on {device}" and queried by saying things like "Hey Google, is {device} on?".  It has the following configuration parameters:

- On/Off Attribute: The device attribute used to query the current state of the device.  Should always be either On Value or Off Value.  Maps to the `switch` attribute by default.
- On Value: The value that the On/Off Attribute will report when the device is on.  Optional if Off Value is specified.  Defaults to "on".
- Off Value: The value that the On/Off Attribute will report when the device is off.  Optional if On Value is specified.  Defaults to "off".
- Control Type: This parameter determines how this device is controlled.  Either with a single command that accepts different parameters for "on" and "off" or two different commands for "on" and "off".
    - Separate Commands
        - On Command: A device command used to turn the device on.  Should not require any parameters.  Maps to `on` by default.
        - Off Command: A device command used to turn the device off.  Should not require any parameters. Maps to `off` by default.
    - Single Command
        - On/Off Command: A device command used to turn the device on or off.  Should accept one parameter.
        - On Parameter: The parameter to pass to the On/Off Command to turn the device on.
        - Off Parameter: The parameter to pass to the On/Off Command to turn the device off.

### Open/Close

The Open/Close trait is used for devices that can be opened and closed such as doors, blinds, vents, or valves.  This trait supports both devices that can only be fully opened or closed and devices that can be partially opened.  It can be controlled by saying things like "Hey Google, open {device}" or "Hey Google, open {device} 50%" and queried by saying things like "Hey Google, is {device} open?".  It has the following configuration parameters:

- Query Only Open/Close: Should be set if this device can only be queried but not controlled (a contact sensor, for example).
- Discrete Only Open/Close: Should be left unset if this device can be partially opened and set if the device can only be fully opened or closed.  The other configuration parameters change depending on if this is set or not:
    - Unset:
        - Open/Close Attribute: The device attribute used to query the current state of the device.  Should be in the range 0-100.
        - Reverse Direction: Normally the Open/Close Attribute uses 0 to indicate fully closed and 100 to indicate fully open.  Set this toggle to reverse that and use 100 to indicate fully closed and 0 to indicate fully open.
        - Open/Close Command: Only available if Query Only Open/Close is unset.  A device command used to open or close the device.  Should accept a parameter in the range 0-100 representing the percentage of the way to open the device.  Mapped to `setPosition` by default.
    - Set:
        - Open/Close Attribute: The device attribute used to query the current state of the device.  Should always be either Open Value or Closed Value.
        - Open Value: The value that the Open/Close Attribute will report when the device is open.  Defaults to "open".
        - Closed Value: The value that the Open/Close Attribute will report when the device is closed.  Defaults to "closed".
        - Open Command: Only available if Query Only Open/Close is unset.  A device command used to open the device.  Should not require any parameters.  Maps to `open` by default.
        - Close Command: Only available if Query Only Open/Close is unset.  A device command used to close the device.  Should not require any parameters.  Maps to `close` by default.

### Reboot

The Reboot trait is used for devices that can be rebooted.  It can be controlled by saying things like "Hey Google, reboot {device}".  It has the following configuration parameters:

- Reboot Command: A device command to reboot the device.

### Rotation

The Rotation trait is used for devices that can be rotated to a specific position such as slat blinds or a pivoting fan.  It can be controlled by saying things like "Hey Google, rotate {device} to 30%" and queried by saying things like "Hey Google, how far is {device} rotated?".  It has the following configuration parameters:

- Current Rotation Attribute: The device attribute used to determine how far the device is currently rotated.  Should be a number in the range 0-99 with 0 being rotated fully counter-clockwise and 99 being rotated fully clockwise.
- Set Rotation Command: A device command used to rotate the device to a new position.  Should accept a parameter in the range 0-99 where 0 will rotate the device fully counter-clockwise and 99 will rotate it fully clockwise.
- Supports Continuous Rotation: This parameter should be set if the device can rotate continuously.  That is, if it can continue rotating clockwise past its position 99 to get back to position 0.

### Scene

This is used for controlling scenes, and should generally only be used with the "Scene" device type.  It can be controlled by saying things like "Hey Google, activate {scene}" or "Hey Google, deactivate {scene}".  It cannot be queried.  It has the following configuration parameters:

- Activate Command: A device command used to activate this scene.  Maps to `on` by default.
- Can this scene be deactivated?: Should be left unset if this scene can only be activated and set if this scene can be both activated and deactivated.
- Deactivate Command: A device command used to deactivate this scene.  Only available if the scene can be deactivated.  Maps to `off` by default.

### Sensor State

This is used for reporting sensor states such as air quality, smoke level, carbon monoxide level, filter cleanliness, water leak, etc.  It can be controlled by saying things like "Hey Google, what is the filter lifetime of (device)" or "Hey Google, what is the carbon dioxide level of (device)".  It has the following configuration parameters:

- Google Home Available States: Comma separated list of available sensor states.
- Hubitat Descriptive State Attribute: The device attribute used to query the current descriptive value of the device.
- Google Numeric Units: Units for the sensor state if the sensor supports numerical output.
- Hubitat Numeric Attribute: The device attribute used to query the current numerical value of the device.

### SoftwareUpdate

The Software Update trait is used for devices that can have a software update.  It can be controlled by saying things like "Hey Google, Software update {device}." and queried by saying things like "Hey Google, When was {device} last updated?".  It has the following configuration parameters:

- Last Software Update Unix Time Stamp Attribute: The device attribute used to indicate the last Unix time (in seconds) that the update occurred.
- Software Update Command: A device command used to start a software update on the device.

### Start/Stop

This trait is used for devices that support starting, stopping, and optionally pausing operation.  It has the following configuration parameters:

- Start/Stop Attribute: The device attribute used to determine if the is currently running.  Maps to `status` by default.
- Start Value: The value of the "Start/Stop Attribute" that indicates that the device is running.  Defaults to "running".
- Stop Value: The value of the "Start/Stop Attribute" that indicates that the device is currently stopped.
- Start Command: A device command to start the device running.  Maps to `start` by default.
- Stop Command: A device command to stop the device.
- Pausable: Turn this on if the device is capable of pausing and resuming operation.  This is distinct from stopping and starting in that a device that has been paused will resume its operation from where it was when it was paused, while a device that is stopped will begin anew from the beginning of its operation.  If this parameter is enabled, the following parameters become available:
    - Pause/UnPause Attribute: The device attribute used to determine if the device is currently paused.  Maps to `status` by default.
    - Pause Value: The value of the "Pause/UnPause Attribute" that indicates that the device is currently paused.  Defaults to "paused".
    - Pause Command: A device command used to pause the device.  Maps to `pause` by default.

### Temperature Control

This trait is intended for devices that can sense or control their own internal temperature such as a water heater or an oven.  For devices that sense or control the ambient temperature, use [Temperature Setting](#temperature-setting).  It can be controlled by saying things like "Hey Google, set {device} to 100 degrees" and queried by saying things like "Hey Google, what's the temperature of {device}?".  The temperature of these devices will not be reported when asking the Google Assistant about the temperature of a room.  The Temperature Control trait has the following configuration parameters:

- Temperature Unit: The unit that this device reports temperature in, either Fahrenheit or Celsius.  Defaults to your hub's default temperature unit.
- Current Temperature Attribute: The device attribute used to query the current temperature reading of the device.  Maps to `temperature` by default.
- Query Only Temperature Control: Should be set if this device can only be queried for its current temperature, and not controlled.  If this setting is not set, the following configuration parameters become available:
    - Current Temperature Setpoint Attribute: The device attribute used to query the device's current setpoint.
    - Set Temperature Command: A device command used to set the desired temperature of the device.
    - Minimum Temperature Setting: The minimum temperature to which the device can be set.
    - Maximum Temperature Setting: The maximum temperature to which the device can be set.
    - Temperature Step: The amount that the desired temperature will be raised or lowered when you say "Hey Google, turn up/down {device}".

### Temperature Setting

This trait is primarily used for thermostats and ambient temperature sensors.  For devices that sense or control their own internal temperature, use the [Temperature Control](#temperature-control) trait.  It can be controlled by saying things like "Hey Google, set {device} to 75 degrees" or "Hey Google, set {device} to heat mode" and can be queried by saying things like "Hey Google, what's the temperature of {device}?".  The current temperature of all devices in a room with this trait will be reported when asking the Google Assistant for the temperature of the room.  It has the following configuration parameters:

- Temperature Unit: The unit that this device reports temperature in, either Fahrenheit or Celsius.  Defaults to your hub's default temperature unit.
- Current Temperature Attribute: The device attribute used to query the current temperature reading of the device.  Maps to `temperature` by default.
- Query Only Temperature Setting: Set to indicate that this device can only be queried for temperature, not set.

The following settings are only available if "Query Only Temperature Setting" is unset:
- Supported Modes: The operating modes that this device supports.  For each mode, the following settings are available:
    - {Mode} Setpoint Attribute: The device attribute used to query the device's current setpoint when in {mode}.  Maps to `{mode}Setpoint` by default. If Heat/Cool mode is selected, the settings for both Heat mode and Cool mode are available.
    - Set {Mode} Setpoint Command:  A device command used to set the device's setpoint when in {mode}.  Maps to `set{Mode}Setpoint` by default. If Heat/Cool mode is selected, the settings for both Heat mode and Cool mode are available.
    - {Mode} Hubitat Mode: The value passed to the Set Mode Command to set the device to this mode and reported by the Current Mode Attribute when the device is in this mode.
- Set Mode Command: A device command used to set the current operating mode of the device.  Should accept any of the {Mode} Hubitat Mode values.
- Current Mode Attribute: The device attribute used to query the device's current operating mode.  Should always report one of the {Mode} Hubitat Mode values.
- Temperature Buffer: The minimum offset between the heating and cooling setpoints when in Heat/Cool mode.  Not available unless Heat/Cool mode is supported.  Optional.
- Minimum Setpoint: The minimum allowed value for the device's setpoint.  Optional, but must be specified if Maximum Setpoint is specified.
- Maximum Setpoint: The maximum allowed value for the device's setpoint.  Optional, but must be specified if Minimum Setpoint is specified.

### Timer

The Timer trait can be used to control a built-in timer on devices, such as starting a new timer as well as pausing and canceling a running timer, and asking how much time is remaining.  It can be controlled by saying things like "Hey Google, Run {device} timer for five minutes.", "Hey Google, Add one minute to {device} timer.", "Hey Google, Pause the {device} timer.", "Hey Google, Resume the {device} timer.", or "Hey Google, Stop the {device} timer."  NOTE:  Control command phrases are unconfirmed.
And queried by saying things like "Hey Google, How much time is left on {device} timer?".  This trait has the following configuration parameters:

- Maximum Timer Duration (seconds): The maximum seconds a timer can be set for.  Defaults to '86400'. (24 hours)
- Command Only (No Query): Set to indicate that this device can only accept commands and not be queried for status.  Defaults to 'false'.  If 'true', the following parameters are available:
    - Time Remaining Attribute: The device attribute used to query the current timer remaining of the device.  Will be -1 (timer stopped) to Maximum Timer Duration (seconds).  Maps to the `timeRemaining` attribute by default.
    - Timer Paused Attribute: The device attribute used to query if the device is paused.  Maps to the `sessionStatus` attribute by default.
    - Timer Paused Value: The value that the Timer Paused Attribute will report when the device is paused.  Defaults to `paused`.
- Timer Start Command: A device command used to start the device timer.  A parameter of timerTimeSec with the number of seconds between 0 and Maximum Timer Duration (seconds) will be supplied.  Maps to `start` by default.
- Timer Adjust Command: A device command used to adjust the device timer.  A parameter of timerTimeSec with the number of +/- seconds to adjust the timer to will supplied.  Maps to `setTimeRemaining` by default.
- Timer Cancel Command: A device command used to cancel the device timer.  Maps to `cancel` by default.
- Timer Pause Command: A device command used to pause the device timer.  Maps to `pause` by default.
- Timer Resume Command: A device command used to resume the device timer.  Maps to `start` by default.

### Toggles

This trait is used for devices that have one or more independently togglable on/off settings.  For example, a manual override of a thermostat schedule or an energy saving mode on a dryer.  It can be controlled by saying things like "Hey Google, turn on {toggle} on {device}" or "Hey Google, turn off {device} {toggle}" and can be queried by saying things like "Hey Google, is {device} {toggle} on?".

Multiple toggles may be defined for a device type, each has all of the parameters defined for the [On/Off trait](#onoff), as well as the following:

- Toggle Names: A comma-separated list of names that can be used to control or query this toggle.  The Google Assistant will accept any of the defined names, but will always respond with the first name in the list.

### Transport Control

This trait is used for devices which are able to control media playback such as Music Players. It can be controlled by saying things like "Hey Google, pause {device}.", "Hey Google, resume {device}.", "Hey Google, stop {device}.", "Hey Google, next on {device}.", "Hey Google, previous on {device}.". It supports the following commands (based on the Hubitat Music Player device capability):

- Next Command: A device command used to skip to next media item. Maps to `nextTrack` by default.
- Pause Command: A device command used to pause media playback. Maps to `pause` by default.
- Previous Command: A device command used to skip to previous media item. Maps to `previousTrack` by default.
- Resume Command: A device command used to resume media playback. Maps to `play` by default.
- Stop Command: A device command used to stop media playback. Maps to `stop` by default.


### Volume

This trait is used for devices that can have their volume controlled such as TVs or speakers.  It can be controlled by saying things like "Hey Google, set {device} volume to 65%" or "Hey Google, turn up {device}".  It has the following configuration parameters:

- Current Volume Attribute: The device attribute used to query the current volume of the device.  Should be an integer in the range 0-100.  Maps to `volume` by default.
- Set Volume Command: A device command used to set the desired volume.  Should accept an integer parameter in the range 0-100.  Maps to `setVolume` by default.
- Volume Level Step:  The amount by which to raise or lower the volume when requested to turn up or down the volume without requesting a specific amount.  Defaults to `1`.
- Supports Mute And Unmute:  Set unset this if your device cannot be muted separately from setting its volume to 0.  If set, the following configuration parameters are available:
    - Mute State Attribute:  The device attribute used to determine if the device is muted or not.  Maps to `mute` by default.
    - Muted Value:  The value that the Mute State Attribute will report when the device is muted.  Defaults to `muted`.
    - Unmuted Value:  The value that the Mute State Attribute will report when the device is unmuted.  Defaults to `unmuted`.
    - Mute Command:  A device command used to mute the device.  Should accept no parameters.  Maps to `mute` by default.
    - Unmute Command:  A device command used to unmute the device.  Should accept no parameters.  Maps to `unmute` by default.

# Global Settings

## Global PIN Codes

These PIN Codes apply to **all** device types that have **Actions requireng PIN** set.
They function in the same manner as the per-device-type PIN Codes.
This allows a single user/pin combination to apply to all security actions.
- IE: If you have both a Door Lock Device Type and a Garage Door Device Type that have the unlock and the open actions secured respectivly, these pins will apply to both device types. This removes the need to maintain the same user and pin combination for the two device types seperately.

Note that using the Global PIN Codes does not preclude the use of the per-device-type codes; that is, you can use both.
If you have users that should have access to control everything, then use the Global PIN Codes; for the other users, you can set them up on the specific device type
- IE: Mr. Smith should have access to the entire system - setup a Global PIN; Johnny Jr. should have access to only the Door Locks - setup a user/pin on the Door Lock Device Type
