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
 *  Date: 2021-09-14
 */

preferences {
    input "sourceHLSURL", "text", title: "Camera HLS stream HTTP URL", required: false
    input "sourceMP4URL", "text", title: "Camera MP4 stream HTTP URL", required: false
    input "sourceDashURL", "text", title: "Camera dash stream HTTP URL", required: false
    input "sourceSmoothStreamURL", "text", title: "Camera smooth stream HTTP URL", required: false
//    input "sourceWebRTCURL", "text", title: "Camera WebRTC HTTP URL", required: false    // requires extra development
}

metadata {
    definition (name: "Virtual Generic Camera Stream Object", namespace: "lpakula", author: "Lyle Pakula") {
        capability  "VideoCamera"

        attribute   "camera", "enum"
        attribute   "mute", "enum"
        attribute   "streamURL", "JSON_OBJECT"
        attribute   "streamProtocol", "enum"
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
    sendEvent(name: "statusMessage", value: "SUCCESS")
    if ((sourceHLSURL == null) || (sourceMP4URL == null) || (sourceDashURL == null) || (sourceSmoothStreamURL == null)
//        || (sourceWebRTCURL == "")
        ) { 
        log.error "${device.label}: At least one URL needs to be configured."
    }
}

def on(supportedStreamProtocols) {
    if (sourceHLSURL != null) {
        if (supportedStreamProtocols.find { it == "hls" }) {
            sourceURL = sourceHLSURL
            sourceProtocol = "hls"
        }
    } else if (sourceMP4URL != null) {
        if (supportedStreamProtocols.find { it == "progressive_mp4" }) {
            sourceURL = sourceMP4URL
            sourceProtocol = "progressive_mp4"
        }
    } else if (sourceDashURL != null) {
        if (supportedStreamProtocols.find { it == "dash" }) {
            sourceURL = sourceDashURL
            sourceProtocol = "dash"
        }
    } else if (sourceSmoothStreamURL != null) {
        if (supportedStreamProtocols.find { it == "smooth_stream" }) {
            sourceURL = sourceSmoothStreamURL
            sourceProtocol = "smooth_stream"
        }
    } else if (sourceWebRTCURL != null) {
        if (supportedStreamProtocols.find { it == "webrtc" }) {
            sourceURL = sourceWebRTCURL
            sourceProtocol = "webrtc"
        }
    } else {
        sourceURL = ""
        sourceProtocol = ""
        log.error "${device.label}: At least one URL needs to be configured."
    }

    sendEvent(name: "streamURL", value: "${sourceURL}")
    sendEvent(name: "streamProtocol", value: "${sourceProtocol}")

    log.debug "${device.label}: on() ${sourceURL}, Protocol: ${sourceProtocol}"
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
