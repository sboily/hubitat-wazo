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
}

metadata {
    definition (name: "Wazo driver", namespace: "quintana", author: "Sylvain Boily", importUrl: "https://github.com/") {
        capability "Initialize"
        capability "Notification"
        capability "Actuator"
        command "connectWazo"
        command "disconnectWazo"
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
    def apiParams = buildMessage(message)

    def params = [
        uri: "https://${ip}:${port}/api/chatd/1.0/messages",
		requestContentType: 'application/json',
		contentType: 'application/json',
		body: apiParams
    ]
    
    asynchttpPost('myPostResponse', params)
}

def myPostResponse(response, data) {
    if(response.status != 200) {
        log.error "WAZO log: Received HTTP error ${response.status}..."
    } else {
        if(logEnable) log.debug "WAZO log: Message Received by Join API Server ${data}"
    }
}

def buildMessage(String message) {
    return { message: message }
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
    log.info "Not implemented..."
    // sendNotification(message)
}
