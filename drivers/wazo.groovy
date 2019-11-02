/**
*  Wazo driver
*
*  2019 Sylvain Boily
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
*/

def version() {
    state.appName = "Wazo"
    state.version = "v1.0.20190924" 
    state.dwInfo = "${state.appName}:${state.version}"
}

preferences {
    input("ip", "text", title: "Wazo Server Ip Address", description: "", required:true)
    input("port", "text", title: "Wazo Server Port", description: "", required:true)
    input("token", "text", title: "Wazo Server token", description: "", required:true)
    input("logEnable", "bool", title: "Enable Debug Logging?:", required: true)
    input("roomName", "text", title: "Notification Room", required: true, defaultValue: "Hubitat BOT")
    input("roomUuid", "text", title: "Notification Room UUID", required: false)

}

metadata {
    definition (name: "Wazo driver", namespace: "quintana", author: "Sylvain Boily", importUrl: "https://github.com/") {
        capability "Initialize"
        capability "Notification"
        capability "Actuator"
        command "connectWazo"
        command "disconnectWazo"
        command "checkMyRoom"
    }
}

def installed() {
     sendEvent(name: "DriverAuthor", value: "Sylvain Boily", displayed: true)
     sendEvent(name: "DriverVersion", value: state.version, displayed: true)
}

def updated() {
    initialize()
}

def initialize() {
    if (logEnable) log.debug "WAZO log: Initialize..."
    version()
    state.started = false
    websocketConnect()
}

def websocketConnect() {
    if (logEnable) log.debug "WAZO log: Websocket connection to ${ip}:${port} with token ${token}"
    interfaces.webSocket.connect("wss://${ip}:${port}/api/websocketd/?token=${token}", headers: ["Content-Type":"application/json"])
}

def parse(String message) {
    data = parseJson(message)    
    if (logEnable) log.debug "WAZO log: State websocket: ${state.started}"
    
    if (state.started) {
        msg = parseWazoMessage(data.data)
        sendEvent(name: "${data.name}", value: "${msg}", displayed: true)
        
        return msg
    }
    
    if (logEnable) log.debug "WAZO log: ${data}"
    switch (data.op) {
        case "init":
            if (logEnable) log.debug "WAZO log: websocket is init mode"
            subscribe("*")
            start()
            break
        case "start":
            state.started = true
            if (logEnable) log.debug "WAZO log: waiting for messages"
            break
    }
}

def subscribe(String eventName) {
    def msg = '{"op": "subscribe", "data": {"event_name": "' + eventName + '"}}'
    sendMsg(msg)
}

def start() {
    def msg = '{"op": "start"}'
    sendMsg(msg)
}

def parseWazoMessage(data) {
    if (logEnable) log.debug "WAZO log: ${data}"
    
    return data
}

def sendMsg(String msg) {
    if (logEnable) log.debug "WAZO log: send message ${msg}"
    interfaces.webSocket.sendMessage(msg)
}

def webSocketStatus(String status) {
    if (logEnable) log.debug "WAZO log: webSocketStatus is ${status}"
}

def sendNotification(String message) {
    data = buildMessage(message)
    uri = "https://${ip}:${port}/api/chatd/1.0/messages"

    postHttp(myPostResponse, uri, data)
}

def postHttp(callback, uri, data) {
    def params = [
        uri: uri,
		requestContentType: 'application/json',
		contentType: 'application/json',
        headers: ['X-Auth-Token': token],
		body: data
    ]

    asynchttpPost(callback, params)
}

def getHttp(callback, uri) {
    def params = [
        uri: uri,
		requestContentType: 'application/json',
		contentType: 'application/json',
        headers: ['X-Auth-Token': token]
    ]
    
    asynchttpGet(callback, params)
}

def getMyInfoFromToken() {
    if(logEnable) log.debug "WAZO log: Get Token Info: ${token}"
    uri = "https://${ip}:${port}/api/auth/0.1/token/${token}"
    getHttp('getTokenInfo', uri)
}

def getTokenInfo(response, data) {
    tenant_uuid = response.json.data.metadata.tenant_uuid
    user_uuid = response.json.data.metadata.user_uuid
    wazo_uuid = response.json.data.metadata.xivo_uuid

    if(logEnable) log.debug "WAZO log: Callback get token: ${tenant_uuid}, ${user_uuid}, ${wazo_uuid}"

    createHubitatRoomNotification(tenant_uuid, user_uuid, wazo_uuid)
}

def checkMyRoom() {
    if(logEnable) log.debug "WAZO log: Check My Notification Room: ${roomName}"

    uri = "https://${ip}:${port}/api/chatd/1.0/users/me/rooms"
    getHttp('myGetResponse', uri)
}

def myGetResponse(response, data) {

    if (response.status == 401) {
        if(logEnable) log.debug "WAZO log: Authentication failed..."
        return false
    }

    else if (response.status == 200) {
        if (isMyRoomExist(response.json) == true) {
            if(logEnable) log.debug "WAZO log: Notification Room already exist: ${roomName}"
            return true
        }
        else {
            if(logEnable) log.debug "WAZO log: Notification Room doesn't exist: ${roomName}"
            getMyInfoFromToken()
        }
    }

    else {
        if(logEnable) log.debug "WAZO log: Error to check room: ${response.status}"
    }
}

def isMyRoomExist(data) {
    isExist = false
    data.items.each{
        if (it.name == roomName) {
            isExist = true
            device.updateSetting("roomUuid", it.uuid)
        }
    }
    return isExist
}

def createHubitatRoomNotification(tenant_uuid, user_uuid, wazo_uuid) {
    if(logEnable) log.debug "WAZO log: Create Notification Room: ${roomName}"

    data = [
        name: "Hubitat BOT",
        users: [
            [
                tenant_uuid: tenant_uuid,
                uuid: "00fbed1c-3ac0-4757-ba70-5709c1aa3024",
                wazo_uuid: wazo_uuid
            ],
            [
                tenant_uuid: tenant_uuid,
                uuid: user_uuid,
                wazo_uuid: wazo_uuid
            ]
        ]
    ]
    uri = "https://${ip}:${port}/api/chatd/1.0/users/me/rooms"

    postHttp('myPostResponse', uri, data)
}

def sendMessage(message) {
    data = [
        alias: 'Hubitat BOT',
        content: message
    ]
    uri = "https://${ip}:${port}/api/chatd/1.0/users/me/rooms/${roomUuid}/messages"
    postHttp('myPostResponse', uri, data)
}

def myPostResponse(response, data) {
    if(response.status != 201) {
        log.error "WAZO log: Received HTTP error ${response.status}..."
    } else {
        if(logEnable) log.debug "WAZO log: Message Received by Join API Server ${data}"
    }
}

def close() {
    state.started = false
    interfaces.webSocket.close()
}

def disconnectWazo() {
    close()
    sendEvent(name: "Wazo driver", value: "stop", isStateChange: true)
}

def connectWazo() {
    initialize()
    sendEvent(name: "Wazo driver", value: "start", isStateChange: true)

}

def deviceNotification(message) {
    sendMessage(message)
}
