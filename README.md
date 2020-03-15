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
9. Navigate to "Apps" in Hubitat
10. Click "Add User App" and select "Google Home Community"
11. Make a note of the app's ID.  This can be found in your web browser's URL bar.
    - The URL should be "http://{your hub IP}/installedapp/configure/{app ID}/mainPreferences"
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
3. Enter a name for the project and click "Create project".
4. Select "Smart Home"
5. Click the "Develop" tab.
6. On the "Invocation" screen, give your Action a name
7. Click "Actions" in the menu
8. Enter the following as the Fulfillment URL:
    - `https://cloud.hubitat.com/api/{your hub ID}/apps/{app ID}/action`
9. Click "Account linkig" in the menu
10. Enter the Client ID and Client Secret you got when enabling OAuth for the Google Home Community app
11. Enter `https://oauth.cloud.hubitat.com/oauth/authorize` as the Authorization URL
12. Enter `https://oauth.cloud.hubitat.com/oauth/token` as the Token URL
13. Click "Next"
14. In the "Configure your client (optional)" section, enter "app" in the Scopes box
15. Click "Save"
16. Click the "Test" tab
17. In the top-right of the page, hover your mouse over the icon that looks like a phone in front of a laptop, you should see "Testing on Device: Enabled"
    - Note: You may need to wait a few minutes at this point for Google's backend to get itself sorted out.  If after several minutes you get a quick loading screen and bounced back to the list after step 22 below, you may need to make a trivial change (such as removing and re-adding a character from the Action's display name) and save the Action to force it to figure its life out.
18. Open the Google Home app on your phone or tablet
19. Tap the "+" in the top-left corner
20. Tap "Set up device"
21. Tap "Have something already set up?"
    - If you have the official Hubitat integration linked, unlink it before proceeding
22. In the list, find the entry `[test] {your action name}`
23. Enter your Hubitat account credentials and click "Sign In"
24. Select your hub and tap "Select"
25. Make sure at least one device is selected to expose to Google Home
    - Note: If you do not select any devices linking process will fail.  If that happens, go back to the Google Home Community app in Hubitat and select at least one device and then try again starting from step 18 above.
26. Tap "Authorize"


# Configuring Devices

The first step to configuring a device to link to Google Home is to define a device type.  This will determine what kind of device Google Home sees devices as, what commands are available to interact with them, and how those commands will translate to Hubitat commands.

## Defining a Device Type

1. Navigate to "Apps" in Hubitat
2. Click on the "Google Home Community" app you created
3. Click "Define new device type"
4. Fill in the settings on the "Device Type Definition" page.  See [Device Type Settings](#device-type-settings) below.
5. Select one or more device traits to add to this device type.  Device traits determine what commands are available for a device type and how those commands translate to Hubitat commands.  See below for more information about individual device traits.
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


## Device Traits

Google Home defines a number of device traits that you can mix-and-match to define the functionality of any particular type of device.  Not all traits are currently supported by Google Home Community.  The following are the currently supported device traits and their configuration parameters:

### Brightness

The Brightness trait is primarily used for devices like dimmer switches and light bulbs, but can be used for any device that can be set to a level between 0% and 100%.  It can be controlled by saying things like "Hey Google, set {device} to {level}" and queried by saying things like "Hey Google, what's the level of {device}?".  It has the following configuration parameters:

- Current Brightness Attribute: The device attribute used to query the current brightness level of the device.  Should be in the range 0-100.  Mapps to the `level` attribute by default.
- Set Brightness Command: A device command used to set the brightness of the device.  Should accept a brightness level in the range 0-100.  Mapps to the `setLevel` command by default.

### Fan Speed

The Fan Speed trait is primarily used for fan controllers with multiple speed settings.  It can be controlled by saying things like "Hey Google, set {device} to {speed}" and queried by saying things like "Hey Google, what's the {device} speed?".  It has the following configuration parameters:

- Current Speed Attribute: The device attribute used to query the current fan speed of the device.  Maps to the `speed` attribute by default.
- Set Speed Command: A device command used to set the fan speed of the device.  Should accept one of the supported fan speeds.  Mapps to the `setSpeed` command by default.
- Supported Fan Speeds: The set of fan speed settings that this type of device supports.  If multiple settings will set the device to the same fan speed, you should only select one of them.
- Google Home Level Names for {speed} - A comma-separated list of names that you will use to reference this fan speed when interacting with the Google Assistant.  By default, the name of the speed in Hubitat is used.
- Reversible: Select this if the fan direction can be reversed
- Reverse Command: Only available if "Reversible" is selected.  A device command that can be used to reverse the device's fan direction.

### On/Off

The On/Off trait is used for devices that have discreet on and off states such as switches or lights.  It can be controlled by saying things like "Hey Google, turn on {device}" and queried by saying things like "Hey Google, is {device} on?".  It has the following configuration parameters:

- On/Off Attribute: The device attribute used to query the current state of the device.  Should always be either On Value or Off Value.  Maps to the `switch` attribute by default.
- On Value: The value that the On/Off Attribute will report when the device is on.  Optional if Off Value is specified.  Defaults to "on".
- Off Value: The value that the On/Off Attribute will report when the device is off.  Optional if On Value is specified.  Defaults to "off".
- Control Type: This parameter determines how this device is controlled.  Either with a single command that accepts different parameters for "on" and "off" or two different commands for "on" and "off".
    - Separate Commands
        - On Command: A device command used to turn the device on.  Should not require any parameters.  Mapps to `on` by default.
        - Off Command: A device command used to turn the device off.  Should not require any parameters. Mapps to `off` by default.
    - Single Command
        - On/Off Command: A device command used to turn the device on or off.  Should accept one parameter.
        - On Parameter: The parameter to pass to the On/Off Command to turn the device on.
        - Off Parameter: The parameter to pass to the On/Off Command to turn the device off.

### Open/Close

The Open/Close trait is used for devices that can be opened and closed such as doors, blinds, vents, or valves.  This trait supports both devices that can only be fully opened or closed and devices that can be partially opened.  It can be controlled by saying things like "Hey Google, open {device}" or "Hey Google, open {device} 50%" and queried by saying things like "Hey Google, is {device} open?".  It has the following configuration parameters:

- Query Only Open/Close: Should be set if this device can only be queried but not controlled (a contact sensor, for example).
- Discrete Only Open/Close: Should be left unset if this device can be partially opened and set if the device can only be fully opened or closed.  The other configuration parameters change depending on if this is set or not:
    - Unset:
        - Open/Close Attribute: The device attribute used to query the current state of the device.  Should be in the range 0-100 with 0 being fully closed and 100 being fully open.
        - Open/Close Command: Only available if Query Only Open/Close is unset.  A device command used to open or close the device.  Should accept a parameter in the range 0-100 representing the percentage of the way to open the device.  Mapped to `setPosition` by default.
    - Set:
        - Open/Close Attribute: The device attribute used to query the current state of the device.  Should always be either Open Value or Closed Value.
        - Open Value: The value that the Open/Close Attribute will report when the device is open.  Defaults to "open".
        - Closed Value: The value that the Open/Close Attribute will report when the device is closed.  Defaults to "closed".
        - Open Command: Only available if Query Only Open/Close is unset.  A device command used to open the device.  Should not require any parameters.  Mapps to `open` by default.
        - Close Command: Only available if Query Only Open/Close is unset.  A device command used to close the device.  Should not require any parameters.  Mapps to `close` by default.

### Scene

This is used for controlling scenes, and should generally only be used with the "Scene" device type.  It can be controlled by saying things like "Hey Google, activate {scene}" or "Hey Google, deactivate {scene}".  It cannot be queried.  It has the following configuration parameters:

- Activate Command: A device command used to activate this scene.  Mapps to `on` by default.
- Can this scene be deactivated?: Should be left unset if this scene can only be activated and set if this scene can be both activated and deactivated.
- Deactivate Command: A device command used to deactivate this scene.  Only available if the scene can be deactivated.  Mapps to `off` by default.

### Temperature Setting

This trait is primarily used for thermostats.  It can be controlled by saying things like "Hey Google, set {device} to 75 degrees" or "Hey Google, set {device} to heat mode" and can be queried by saying things like "Hey Google, what's the temperature of {device}?".  It has the following configuration parameters:

- Temperature Unit: The unit that this device reports temperature in, either Fahrenheit or Celcius.  Defaults to your hub's default temperature unit.
- Supported Modes: The operating modes that this device supports.
- Current Temperature Attribute: The device attribute used to query the current temperature reading of the device.  Mapps to `temperature` by default.
- Setpoint Attribute: The device attribute used to query the device's current setpoint when in modes other than Heat/Cool mode.  Not available unless a mode other than Heat/Cool is supported.  Mapps to `thermostatSetpoint` by default.
- Set Setpoint Command:  A device command used to set the device's setpoint when in modes other than Heat/Cool mode.  Not available unless a mode other than Heat/Cool is supported.  Mapps to `setCoolingSetpoint` by default.
- Heating Setpoint Attribute: The device attribute used to query the device's current heating setpoint when in  Heat/Cool mode.  Not available unless Heat/Cool mode is supported.  Mapps to `heatingSetpoint` by default.
- Set Heating Setpoint Command:  A device command used to set the device's heating setpoint when in Heat/Cool mode.  Not available unless Heat/Cool mode is supported.  Mapps to `setHeatingSetpoint` by default.
- Cooling Setpoint Attribute: The device attribute used to query the device's current cooling setpoint when in  Heat/Cool mode.  Not available unless Heat/Cool mode is supported.  Mapps to `coolingSetpoint` by default.
- Set Cooling Setpoint Command:  A device command used to set the device's cooling setpoint when in Heat/Cool mode.  Not available unless Heat/Cool mode is supported.  Mapps to `setCoolingSetpoint` by default.
- Temperature Buffer: The minimum offset between the heating and cooling setpoints when in Heat/Cool mode.  Not available unless Heat/Cool mode is supported.  Optional.
- {Mode} Hubitat Mode: The value passed to the Set Mode Command to set the device to this mode and reported by the Current Mode Attribute when the device is in this mode.
- Set Mode Command: A device command used to set the current operating mode of the device.  Should accept any of the {Mode} Hubitat Mode values.
- Current Mode Attribute: The device attribute used to query the device's current operating mode.  Should always report one of the {Mode} Hubitat Mode values.
- Minimum Setpoint: The minimum allowed value for the device's setpoint.  Optional, but must be specified if Maximum Setpoint is specified.
- Maximum Setpoint: The maximum allowed value for the device's setpoint.  Optional, but must be specified if Minimum Setpoint is specified.

### Toggles

This trait is used for devices that have one or more independently togglable on/off settings.  For example, a manual override of a thermostat schedule or an engery saving mode on a dryer.  It can be controlled by saying things like "Hey Google, turn on {toggle} on {device}" or "Hey Google, turn off {device} {toggle}" and can be queried by saying things like "Hey Google, is {device} {toggle} on?".

Multiple toggles may be defined for a device type, each has all of the parameters defined for the [On/Off trait](#onoff), as well as the following:

- Toggle Names: A comma-separated list of names that can be used to control or query this toggle.  The Google Assistant will accept any of the defined names, but will always respond with the first name in the list.
