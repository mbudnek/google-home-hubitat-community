/**
 *  Copyright 2020 Lyle Pakula
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Camera Stream Object
 *
 *  Driver does nothing but bridge an HTTP stream into Google Home to display as a video camera.
 *  Saving the preferences creates the video URL link which will be passed to a Chromecast device:
 *      http://${deviceIP}/h264/${deviceName}/temp.m3u8?user=${deviceUser}&pw=${devicePWD}
 *
 *  NOTE:  Chromecast requires H.264 video, and AAC audio enabled in the above stream
 *      (regardless if the camera source supplies audio)
 *
 *  Author: Lyle Pakula (wir3z)
 *  Date: 2020-08-02
 */

preferences {
    input "deviceURL", "text", title: "Camera stream HTTP URL", required: true
}

metadata {
    definition (name: "Virtual Generic Camera Stream Object", namespace: "lpakula", author: "Lyle Pakula") {
        capability  "VideoCamera"

        attribute   "camera", "enum"
        attribute   "mute", "enum"
        attribute   "settings", "JSON_OBJECT"
        attribute   "statusMessage", "string"
     }
}

def installed() {
    updated()
}

def updated() {
    log.info "${device.label}: Updated"
    sendEvent(name: "camera", value: "on")
    sendEvent(name: "mute", value: "off")
    sendEvent(name: "settings", value: "${deviceURL}")
    sendEvent(name: "statusMessage", value: "SUCCESS")
}

def on() {
    log.debug "${device.label}: on()"
}

def off() {
    log.debug "${device.label}: off()"
}

def mute() {
    log.debug "${device.label}: mute()"
}

def unmute() {
    log.debug "${device.label}: unmute()"
}

def flip() {
    log.debug "${device.label}: flip()"
}
