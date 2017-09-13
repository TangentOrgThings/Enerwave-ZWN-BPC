// vim :set ts=2 sw=2 sts=2 expandtab smarttab :
/**
 *  Enerwave ZWN BPC
 *
 *  Copyright 2016 Brian Aker
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

def getDriverVersion() { 
  return "1.92"
}

def getDefaultMotionTimeout() {
  return 240
}

def getDefaultWakeupInterval() {
  return 1800
}

def getParamater() {
  if (isNewerModel) {
    return 0x01
  }
  state.isNewerModel ? 0x01 : 0x00
}

def getAssociationGroup () {
  if ( ! state.isNewerModel ) {
    return 0x01
  } else if ( zwaveHubNodeId == 1 || state.isNewerModel ) {
    return 0x01
  } else if (state.isNewerModel) {
    return 0x03
  } 

  return 0x01 // Not really right
}

metadata {
  definition (name: "Enerwave ZWN BPC", namespace: "TangentOrgThings", author: "Brian Aker") {
    capability "Battery"
    capability "Configuration"
    capability "Motion Sensor"
    capability "Sensor"

    attribute "reset", "enum", ["false", "true"]
    attribute "Lifeline", "string"
    attribute "driverVersion", "number"
    attribute "FirmwareMdReport", "string"
    attribute "Manufacturer", "string"
    attribute "ManufacturerCode", "string"
    attribute "MSR", "string"
    attribute "ProduceTypeCode", "string"
    attribute "ProductCode", "string"
    attribute "WakeUp", "string"
    // fingerprint mfr: "011a", prod: "0601", model: "0901", cc: "30,70,72,80,84,85,86", ccOut: "20", deviceJoinName: "Enerwave Motion Sensor"  // Enerwave ZWN-BPC
    fingerprint type: "2001", mfr: "011A", prod: "0601", model: "0901", deviceJoinName: "Enerwave Motion Sensor ZWN-BPC"  // Enerwave ZWN-BPC
    // fingerprint type: "2001", mfr: "011A", prod: "00FF", model: "0700", deviceJoinName: "Enerwave Motion Sensor ZWN-BPC PLus"  // Enerwave ZWN-BPC
  }

  simulator {
    status "inactive": "command: 3003, payload: 00"
    status "active": "command: 3003, payload: FF"
  }

  tiles {
    standardTile("motion", "device.motion", width: 2, height: 2) {
      state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
      state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
    }
    valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
      state("battery", label:'${currentValue}% battery', unit:"")
    }
    valueTile("driverVersion", "device.driverVersion", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
      state "driverVersion", label:'${currentValue}'
    }
    standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
      state "default", label:"", action:"configuration.configure", icon:"st.secondary.configure"
    }
    valueTile("reset", "device.reset", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
      state "false", label:'', backgroundColor:"#ffffff"
      state "true", label:'reset', backgroundColor:"#e51426"
    }
    valueTile("lastActive", "state.lastActive", width:2, height:2, inactiveLabel: true, decoration: "flat") {
      state "default", label: '${currentValue}'
    }

    main "motion"
    details(["motion", "battery", "lastActive", "driverVersion", "reset", "configure"])
  }

  preferences 
  {
    input name: "testMode", type: "bool", title: "Enable Test Mode", description: "Enter true or false"
    input name: "motionTimeout", type: "number", title: "Motion timeout", description: "Motion timeout in minutes (default 5 minutes)", range: "1..240"
    input name: "wakeupInterval", type: "number", title: "Wakeup Interval", description: "Interval in seconds for the device to wakeup", range: "240..68400"
    input name: "isNewerModel", type: "bool", title: "Temp fix for model", description: "Enter true or false"
  }
}

private deviceCommandClasses () {
  if (state.NewModel) {
    return [
      0x20: 1, 0x30: 1, 0x70: 2, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 2, 0x86: 1
    ]
  } else {
    return [
      0x20: 1, 0x59: 1, 0x70: 2, 0x71: 3, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 2, 0x86: 1
    ]
  }
}

def parse(String description) {
  def result = null

  log.debug "PARSE: ${description}"
  if (description.startsWith("Err")) {
    if (description.startsWith("Err 106")) {
      if (state.sec) {
        log.debug description
      } else {
        result = createEvent(
          descriptionText: "This device failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
          eventType: "ALERT",
          name: "secureInclusion",
          value: "failed",
          isStateChange: true,
        )
      }
    } else {
      result = createEvent(value: description, descriptionText: description, isStateChange: true)
    }
  } else if (description != "updated") {
    def cmd = zwave.parse(description, [ 0x20: 1, 0x30: 1, 0x59: 1, 0x70: 2, 0x71: 3, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 2, 0x86: 1 ]) // , [0x20: 1, 0x30: 1, 0x70: 1, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 1, 0x86: 1]) // 30,70,72,80,84,85,86

    if (cmd)
    {
      result = zwaveEvent(cmd)

      if (!result)
      {
        log.warning "Parse Failed and returned ${result} for command ${cmd}"
        result = createEvent(value: description, descriptionText: description)
      } else {
        log.debug "RESULT: ${result}"
      }
    } else {
      log.info "zwave.parse() failed: ${description}"
      result = createEvent(value: description, descriptionText: description)
    }
  } else {
    log.error("Unknown: $description")
    result = createEvent(value: description, descriptionText: "Unknown: $description")
  }

  return result
}

def prepDevice() {
  [
    zwave.versionV1.versionGet(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
  ]
}

def installed() {
  log.debug "installed()"
  sendEvent(name: "driverVersion", value: getDriverVersion(), isStateChange: true)
  state.motionTimeout = motionTimeout ? motionTimeout : 240
  setMotionTimeout()
  state.isAssociated = false
  state.isConfigured = false
  state.newerModel = false

  sendCommands(prepDevice())
}

def updated() {
  log.debug "updated()"
  state.motionTimeout = motionTimeout ? motionTimeout : getDefaultMotionTimeout()
  setMotionTimeout()
  state.isAssociated = false
  state.isConfigured = false
  state.isNewerModel = isNewerModel
  sendEvent(name: "driverVersion", value: getDriverVersion(), isStateChange: true)
  sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName is being reset")
}

def configure() {
  setMotionTimeout()
  sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName is being reset")

  state.motionTimeout = motionTimeout ? motionTimeout : getDefaultMotionTimeout()

  def cmds = []

  // Set device association for motion commands
  if (! state.isAssociated ) {
    cmd << zwave.associationV1.associationGet(groupingIdentifier: getAssociationGroup())
  }

  // Set motion sensor timeout 
  cmds << zwave.configurationV1.configurationSet(configurationValue: [state.NextMotionDuration], parameterNumber: getParamater(), size: 1)
  if (! state.isConfigured) {
    cmds << zwave.configurationV1.configurationGet(parameterNumber: getParamater())
  }

  if (getDataValue("MSR") == null) {
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
  }

  if ( zwaveHubNodeId == 0x01) {
    // Set the wake up interval if Smartthings is Primary
    cmds << zwave.wakeUpV2.wakeUpIntervalSet(seconds: getDefaultWakeupInterval(), nodeid:zwaveHubNodeId)
  }

  // Get initial battery report
  cmds << zwave.batteryV1.batteryGet()

  sendCommands(cmds, 600)
}

def setMotionTimeout() {
  if (testMode == "true" || motionTimeout == 0) {
    state.NextMotionDuration = 250
  } else if ( state.motionTimeout > 0 && state.motionTimeout <= 240) {
    state.NextMotionDuration = motionTimeout
  } else {
    state.NextMotionDuration = getDefaultMotionTimeout()
  }
}

def sensorValueEvent(value) {
  def result = []
  Boolean isActive

  if (value) {
    state.lastActive = new Date().time
    isActive = true
  } else {
    isActive = false
  }

  def cmds = []

  if ( zwaveHubNodeId == 1) {
    if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
      cmds << zwave.batteryV1.batteryGet()
    }
  }

  if (! state.isConfigured) {
    cmds << zwave.configurationV1.configurationGet(parameterNumber: getParamater())
  }

  if (! state.isAssociated ) {
    cmds << zwave.associationV1.associationGet(groupingIdentifier: 0x01)
  }

  if (cmds) {
    sendEvent(name: "motion", value: isActive ? "active" : "inactive")
    sendCommands(cmds)
  } else {
    [ createEvent(name: "motion", value: isActive ? "active" : "inactive") ]
  }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
  log.debug ("BasicSet() $cmd")
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
  log.debug ("BasicReport() $cmd")
  sensorValueEvent(cmd.value)
} 

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
  log.debug ("SensorBinaryReport() $cmd")
  sensorValueEvent(cmd.sensorValue)
} 

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
  log.debug "NotificationReport() $cmd"

    Boolean isActive = false

    if (cmd.notificationType) {
      switch (cmd.event) {
        case 8:
        isActive = cmd.notificationStatus ? true : false
          break;
        default:
        log.error "Unknown state"
      } 
    }

  state.newerModel = true
  log.debug ("NotificationReport() $cmd")
  sensorValueEvent(isActive ? 255 : 0)
} 

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  log.debug "WakeUpNotification() $cmd"
    def cmds = []

    if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
      cmds << zwave.batteryV1.batteryGet()
    }

  if (! state.isConfigured ) {
    cmds << zwave.configurationV1.configurationGet(parameterNumber: getParamater())
    state.NextMotionDuration = null
  }

  if (cmds) {
    sendCommands(cmds, 600)
  }

  createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
  log.debug "BatteryReport() $cmd"
  Boolean did_batterylevel_change = state.batteryLevel != cmd.batteryLevel

  if (cmd.batteryLevel == 255) {
    sendEvent(descriptionText: "Replace Batteries", isStateChange: did_batterylevel_change)
    [ createEvent(name: "battery", unit: "%", value: "1", descriptionText: "Battery needs replacing", isStateChange: true) ]
  } else {
    [ createEvent(name: "battery", unit: "%", value: cmd.batteryLevel, descriptionText: "Battery level", isStateChange: did_batterylevel_change) ]
  }

}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
  log.debug ("AssociationReport() $cmd")

  if (cmd.nodeId && cmd.groupingIdentifier == getParamater()) { // Lifeline
    def string_of_assoc = ""
    cmd.nodeId.each {
      string_of_assoc += "${it}, "
    }
    def lengthMinus2 = string_of_assoc.length() - 2
    def final_string = string_of_assoc.getAt(0..lengthMinus2)
    state.Lifeline = final_string

    if (cmd.nodeId.any { it == zwaveHubNodeId }) {
      Boolean isStateChange = state.isAssociated ?: false
      sendEvent(name: "Lifeline",
          value: "${final_string}", 
          descriptionText: "${final_string}",
          displayed: true,
          isStateChange: isStateChange)

      state.isAssociated = true
    } else {
      Boolean isStateChange = state.isAssociated ? true : false
      sendEvent(name: "Lifeline",
          value: "",
          descriptionText: "${final_string}",
          displayed: true,
          isStateChange: isStateChange)
    }
    state.isAssociated = false
  } else {
    Boolean isStateChange = state.isAssociated ? true : false
    sendEvent(name: "Lifeline",
        value: "misconfigured",
        descriptionText: "misconfigured group ${cmd.groupingIdentifier}",
        displayed: true,
        isStateChange: isStateChange)
  }

  if (! state.isAssociated ) {
    sendCommands([ 
      zwave.associationV1.associationSet(groupingIdentifier: getAssociationGroup(), nodeId: [zwaveHubNodeId]),
      zwave.associationV1.associationGet(groupingIdentifier: getAssociationGroup())
    ])
  } else {
    [createEvent(descriptionText: "$device.displayName assoc: $cmd", displayed: true)]
  }
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
  log.error("$device.displayName command not implemented: $cmd")
  [createEvent(descriptionText: "$device.displayName command not implemented: $cmd", displayed: true)]
}

def zwaveEvent(physicalgraph.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  state.reset = true
  [createEvent(name: "reset", value: state.reset, descriptionText: cmd.toString(), isStateChange: true, displayed: true)]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  log.debug ("ManufacturerSpecificReport() $cmd")

    String manufacturerCode = String.format("%04X", cmd.manufacturerId)
    String productTypeCode = String.format("%04X", cmd.productTypeId)
    String productCode = String.format("%04X", cmd.productId)

    state.manufacturer = cmd.manufacturerName ? cmd.manufacturerName : "Enerwave"

    sendEvent(name: "ManufacturerCode", value: manufacturerCode)
    sendEvent(name: "ProduceTypeCode", value: productTypeCode)
    sendEvent(name: "ProductCode", value: productCode)

    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    updateDataValue("manufacturer", state.manufacturer)

    sendEvent(name: "MSR", value: "$msr", descriptionText: "$device.displayName", isStateChange: false)

    [createEvent(name: "Manufacturer", value: "${state.manufacturer}", descriptionText: "$device.displayName", isStateChange: false)]
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
  int parameterNumber

  switch ( cmd.parameterNumber) {
    case 0:
    parameterNumber = 0
    break;
    case 1:
    parameterNumber = 1
    break;
    default:
    return [createEvent(descriptionText: "$device.displayName recieved unknown parameter $cmd.parameterNumber", isStateChange: false)]
  }



  if (cmd.configurationValue[parameterNumber] == state.motionTimeout) {
    state.NextMotionDuration = null
    state.isConfigured = true
  } else {
    setMotionTimeout()
    state.isConfigured = false
  }

  if (! state.isConfigured) {
    state.NextMotionDuration = null
    sendHubCommand([
      zwave.configurationV1.configurationSet(configurationValue: [state.NextMotionDuration], parameterNumber: getParamater(), size: 1),
      zwave.configurationV1.configurationGet(parameterNumber: getParamater())
    ])
  } else{
    [createEvent(descriptionText: "$device.displayName is not configured", isStateChange: false)]
  }
}

/*****************************************************************************************************************
 *  Private Helper Functions:
 *****************************************************************************************************************/

/**
 *  encapCommand(cmd)
 *
 *  Applies security or CRC16 encapsulation to a command as needed.
 *  Returns a physicalgraph.zwave.Command.
 **/
private encapCommand(physicalgraph.zwave.Command cmd) {
  if (state.sec) {
    return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd)
  } else if (state.useCrc16) {
    return zwave.crc16EncapV1.crc16Encap().encapsulate(cmd)
  } else {
    return cmd
  }
}

/**
 *  prepCommands(cmds, delay=200)
 *
 *  Converts a list of commands (and delays) into a HubMultiAction object, suitable for returning via parse().
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private prepCommands(cmds, delay=200) {
  return response(delayBetween(cmds.collect{ (it instanceof physicalgraph.zwave.Command ) ? encapCommand(it).format() : it }, delay))
}

/**
 *  sendCommands(cmds, delay=200)
 *
 *  Sends a list of commands directly to the device using sendHubCommand.
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private sendCommands(cmds, delay=200) {
  sendHubCommand( cmds.collect{ (it instanceof physicalgraph.zwave.Command ) ? response(encapCommand(it)) : response(it) }, delay)
}
