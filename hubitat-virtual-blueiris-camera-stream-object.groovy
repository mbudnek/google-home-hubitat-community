/**
 *  Copyright 2022 Lyle Pakula
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
 *  Date: 2022-06-14
 */

preferences {
    final GOOGLE_CAMERA_STREAM_SUPPORTED_PROTOCOLS = [
        "progressive_mp4":         "Progressive MP4",
        "hls":                     "HLS",
        "dash":                    "Dash",
        "smooth_stream":           "Smooth Stream",
//      "webrtc":                  "WebRTC",    // requires extra development
    ]
    input "deviceIP", "text", title: "Webserver HTTP URL:Port", required: true
    input "deviceName", "text", title: "Camera Short Name", required: true
    input "deviceUser", "text", title: "Webserver Username (Optional)", required: false
    input "devicePWD", "text", title: "Webserver Password (Optional)", required: false
    input "sourceProtocol", "enum", title: "Camera Stream Protocol",
        options: GOOGLE_CAMERA_STREAM_SUPPORTED_PROTOCOLS, multiple: false, required: true
}

metadata {
    definition(name: "Virtual BlueIris Camera Stream Object", namespace: "lpakula", author: "Lyle Pakula") {
        capability  "VideoCamera"

        attribute   "camera", "enum"
        attribute   "mute", "enum"
        attribute   "streamURL", "JSON_OBJECT"
        attribute   "streamProtocol", "enum"
        attribute   "statusMessage", "string"
        attribute   "supportedProtocols", "string"
    }
}

def installed() {
    updated()
}

def updated() {
    log.info "${device.label}: Updated"
    sendEvent(name: "camera", value: "on")
    sendEvent(name: "mute", value: "off")
    // check if a user and password was entered, and add it to the URL, otherwise just create the URL
    if (deviceUser && devicePWD) {
        sendEvent(name: "streamURL",
        value: "http://${deviceIP}/h264/${deviceName}/temp.m3u8?user=${deviceUser}&pw=${devicePWD}")
    } else {
        sendEvent(name: "streamURL", value: "http://${deviceIP}/h264/${deviceName}/temp.m3u8")
    }
    sendEvent(name: "streamProtocol", value: "${sourceProtocol}")
    sendEvent(name: "supportedProtocols", value: "${sourceProtocol}")
    sendEvent(name: "statusMessage", value: "SUCCESS")
}

def on(supportedStreamProtocols) {
    log.debug "${device.label}: on() ${supportedStreamProtocols}"
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
