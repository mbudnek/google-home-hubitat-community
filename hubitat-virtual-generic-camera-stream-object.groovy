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
    input "sourceHLSURL", "text", title: "Camera HLS stream HTTP URL", required: false
    input "sourceMP4URL", "text", title: "Camera MP4 stream HTTP URL", required: false
    input "sourceDashURL", "text", title: "Camera dash stream HTTP URL", required: false
    input "sourceSmoothStreamURL", "text", title: "Camera smooth stream HTTP URL", required: false
//    input "sourceWebRTCURL", "text", title: "Camera WebRTC HTTP URL", required: false    // requires extra development
}

metadata {
    definition(name: "Virtual Generic Camera Stream Object", namespace: "lpakula", author: "Lyle Pakula") {
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
    sendEvent(name: "statusMessage", value: "SUCCESS")

    def sourceProtocols = new StringBuilder()
    sourceProtocols = addProtocols(sourceHLSURL, "hls", sourceProtocols)
    sourceProtocols = addProtocols(sourceMP4URL, "progressive_mp4", sourceProtocols)
    sourceProtocols = addProtocols(sourceDashURL, "dash", sourceProtocols)
    sourceProtocols = addProtocols(sourceSmoothStreamURL, "smooth_stream", sourceProtocols)
//    sourceProtocols = addProtocols(sourceWebRTCURL, '"webrtc"', sourceProtocols)

    if (sourceProtocols.length() == 0) {
        log.error "${device.label}: At least one URL needs to be configured."
        sendEvent(name: "supportedProtocols", value: "")
    } else {
        sendEvent(name: "supportedProtocols", value: "${sourceProtocols}")
    }
}

def addProtocols(sourceURL, sourceProtocol, sourceProtocolList) {
    if (verifyURL(sourceURL)) {
        if (sourceProtocolList.length() != 0) {
            sourceProtocolList.append(",")
        }
        sourceProtocolList.append(sourceProtocol)
    }
    return(sourceProtocolList)
}

def verifyURL(sourceURL) {
    def trimmedURL = sourceURL?.trim()
    return trimmedURL != null && trimmedURL?.length() != 0 ? 1 : 0
}

def validateProtcol(sourceURL, sourceProtocol, supportedProtocols) {
    if (verifyURL(sourceURL)) {
        if (supportedProtocols.find { protocol -> protocol == "hls" }) {
            sendEvent(name: "streamURL", value: "${sourceURL}")
            sendEvent(name: "streamProtocol", value: "${sourceProtocol}")

            log.debug "${device.label}: on() ${sourceURL}, Protocol: ${sourceProtocol}"
            return(1)
        }
    }
    return(0)
}

def on(supportedStreamProtocols) {
    if (validateProtcol(sourceHLSURL, "hls", supportedStreamProtocols)) {
        return
    }
    if (validateProtcol(sourceMP4URL, "progressive_mp4", supportedStreamProtocols)) {
        return
    }
    if (validateProtcol(sourceDashURL, "dash", supportedStreamProtocols)) {
        return
    }
    if (validateProtcol(sourceSmoothStreamURL, "smooth_stream", supportedStreamProtocols)) {
        return
    }
    if (validateProtcol(sourceWebRTCURL, "webrtc", supportedStreamProtocols)) {
        return
    }

    sendEvent(name: "streamURL", value: "")
    sendEvent(name: "streamProtocol", value: "")

    log.error "${device.label}: At least one URL needs to be configured."
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
