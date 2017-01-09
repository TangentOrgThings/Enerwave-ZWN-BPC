// vim :set filetype=groovy ts=2 sw=2 sts=2 expandtab smarttab :
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

def getDriverVersion()
{
	return "v1.11"
}

def getDefaultMotionTimeout()
{
	return 5
}

preferences 
{
	input("testMode", "boolean", title: "Enable Test Mode", defaultValue: false)		 
	input("motionTimeout", "number", title: "Motion timeout in minutes (default 5 minutes)", defaultValue: getDefaultMotionTimeout())
}

metadata {
	definition (name: "Enerwave ZWN BPC", namespace: "TangentOrgThings", author: "Brian Aker") {
		capability "Battery"
		capability "Configuration"
		capability "Motion Sensor"
		capability "Sensor"

		attribute "MSR", "string"
		attribute "driverVersion", "string"
		attribute "motionTimeout", "number"

		fingerprint mfr: "011A", prod: "0601", model: "0901", cc: "30,70,72,80,84,85,86", ccOut: "20", deviceJoinName: "Enerwave Motion Sensor"  // Enerwave ZWN-BPC
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
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state("battery", label:'${currentValue}% battery', unit:"")
		}
		standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat") 
		{
			state "default", label:"", action:"configuration.configure", icon:"st.secondary.configure"
		}

		main "motion"
		details(["motion", "battery", "configure"])
	}
}

def parse(String description)
{
	def result = null
	if (description == "updated") {
		result = createEvent(descriptionText:description)
	}
	else if (description.startsWith("Err")) 
	{
		result = createEvent(descriptionText:description)
	} 
	else 
	{
		def cmd = zwave.parse(description)
		if (cmd)
		{
			result = zwaveEvent(cmd)
		}
		else
		{
			result = createEvent(value: description, descriptionText: description, isStateChange: false)
		}

		if (!reset)
		{
			result = createEvent(value: description, descriptionText: "$device.displayName error on $description", isStateChange: false)
		}
	}

	return result
}

def installed()
{	
	def events = []

	setMotionTimeout()
	events << createEvent([name: "motionTimeout", value: getDefaultMotionTimeout(), isStateChange: true])
	events << createEvent([name: "driverVersion", value: getDriverVersion(), isStateChange: true])

	return events
}

def updated()
{
	def events = []

	setMotionTimeout()
	events << createEvent([name: "motionTimeout", value: state.NextMotionDuration, isStateChange: true])
	events << createEvent([name: "driverVersion", value: getDriverVersion(), isStateChange: true])
	events << response(zwave.configurationV1.configurationGet(parameterNumber: 0))

	return events
}

def configure()
{
	setMotionTimeout()

	delayBetween([

	// Set device association for motion commands
	zwave.associationV1.associationSet(groupingIdentifier: 1, nodeId:zwaveHubNodeId).format(),

	// Set motion sensor timeout 
	zwave.configurationV2.configurationSet(configurationValue: [state.NextMotionDuration], parameterNumber: 0, size: 1).format(),
	zwave.configurationV2.configurationGet(parameterNumber: 0).format(),

	// Set the wake up to 30 minutes (default)
	// zwave.wakeUpV1.wakeUpIntervalSet(seconds: 21600, nodeid:zwaveHubNodeId).format(),

	// Get initial battery report
	zwave.batteryV1.batteryGet().format()

	],600)	
}

def setMotionTimeout() {
	if (testMode == "true")
	{
		state.NextMotionDuration = 250
	}
	else
	{
		state.NextMotionDuration = motionTimeout ?: getDefaultMotionTimeout()
	}
}

def sensorValueEvent(value)
{
	if (value)
	{
		createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion")
	}
	else
	{
		createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{   
	sensorValueEvent(cmd.value)
} 

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{   
	sensorValueEvent(cmd.sensorValue)
} 


def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
	def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

	if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) 
	{
		result << response(zwave.batteryV1.batteryGet())
	}

	if (state.NextMotionDuration)
	{
		logMessage("Reprogramming motion timeout")
		result << response(delayBetween(
			[zwave.configurationV.configurationSet(configurationValue: [state.NextMotionDuration], parameterNumber: 0, size: 1).format(),
			zwave.configurationV2.configurationGet(parameterNumber: 0).format()]
			, 600))

		state.NextMotionDuration = null
	}

	return result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) 
{    
	boolean did_batterylevel_change = state.batteryLevel != cmd.batteryLevel

	def result = [createEvent([name: "battery", unit: "%", value: cmd.batteryLevel, descriptionText: "Battery level", isStateChange: did_batterylevel_change])]

	if (cmd.batteryLevel == 255)
	{
		result << createEvent([descriptionText: "Replace Batteries", isStateChange: did_batterylevel_change])
	}

	return result
}


def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	createEvent(descriptionText: "$device.displayName: unknown command $cmd", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd)
{
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	updateDataValue("MSR", msr)

	createEvent([name: "MSR", value: "$msr", descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd)
{
	def result = []

	if (cmd.parameterNumber == 0)
	{
		if (cmd.configurationValue > 240)
		{
			result << createEvent([descriptionText: "Set to test mode", isStateChange: false])
		}

		result << createEvent([name: "motionTimeout", value: "cmd.configurationValue"])
	}
	else
	{
		result << createEvent([descriptionText: "Unknown configuration request: $cmd", isStateChange: false])
	}

	return result
}
