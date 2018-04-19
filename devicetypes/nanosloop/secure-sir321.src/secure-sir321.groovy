/**
 *  Secure SIR321
 *
 *  Copyright 2018 Matthew King
 *
 */
metadata
{
    definition (name: "Secure SIR321", namespace: "nanosloop", author: "Matthew King")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Switch"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Thermostat Schedule"
        capability "Temperature Measurement"
        fingerprint deviceId:"0x1000", inClusters: "0x72 0x86 0x25 0x20 0x85 0x53 0x70 0x31"
    }

    simulator
    {
        status "on": "command: 2003, payload: FF"
        status "off": "command: 2003, payload: 00"

        // reply messages
        reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
        reply "200100,delay 100,2502": "command: 2503, payload: 00"
    }

    // tile definitions
    tiles(scale: 2)
    {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true)
        {
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL")
            {
                attributeState "on", label: "Heating", action: "switch.off", icon: "st.Bath.bath4.on", backgroundColor: "#79b821"
                attributeState "off", label: "Off", action: "switch.on", icon: "st.Bath.bath4.off", backgroundColor: "#ffffff"
            }
            tileAttribute("device.temperature", key: "SECONDARY_CONTROL")
            {
                attributeState("default", label:'${currentValue} C', unit:"C")
            }
        }

        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") 
        {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "switch"
        details(["switch","temperature","refresh"])
    }
}

def parse(String description)
{
    log.debug "parse description: $description"
    def result
    def cmd = zwave.parse(description, [0x20:1, 0x25:1, 0x86:1, 0x70:1, 0x72:1, 0x31:1, 0x53:1, 0x86:1])
    if (cmd)
    {
    result = createEvent(zwaveEvent(cmd))
    }
    
    if (result?.name == "hail" && hubFirmwareLessThan("000.011.00602"))
    {
    result = [result, response(zwave.basicV1.basicGet())]
    log.debug "Was hailed: requesting state update"
    }
    else
    {
    log.debug "Parse returned ${result?.descriptionText}"
    }
    
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
    def result = []
    result << createevent(name: "switch", value: cmd.value ? "on" : "off")
    result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
    [name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
    [name: "switch", value: cmd.value ? "on" : "off", type: "digital"]
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd)
{
    [name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd)
{
    if (state.manufacturer != cmd.manufacturerName)
    {
        updateDataValue("manufacturer", cmd.manufacturerName)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd)
{
    def result = []
    def map = [:]
    map.value = cmd.scaledSensorValue.toString()
    map.unit = cmd.scale == 1 ? "F" : "C"
    map.name = "temperature"
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd)
{
    // Handles all Z-Wave commands we aren"t interested in
    [:]
}

def on()
{
    delayBetween([
        zwave.basicV1.basicSet(value: 0xFF).format(),
        zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def off()
{
    delayBetween([
        zwave.basicV1.basicSet(value: 0x00).format(),
        zwave.switchBinaryV1.switchBinaryGet().format()
    ])
}

def poll() 
{
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.manufacturerSpecificV1.manufacturerSpecificGet().format(),
        zwave.sensorMultilevelV1.sensorMultilevelGet().format()
    ])
}

def refresh()
{
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.manufacturerSpecificV1.manufacturerSpecificGet().format(),
        zwave.sensorMultilevelV1.sensorMultilevelGet().format()
    ])
}

def configure()
{
log.debug "configure"

// Normally this won"t do anything as the thermostat is asleep, 
// but do this in case it helps with the initial config
//Set Temp Scale
zwave.configurationV1.configurationSet(configurationValue: [0xff], parameterNumber: 2, size: 1).format()
//Setup Group 2
zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format()
//Set Report Time
zwave.configurationV1.configurationSet(configurationValue: [0x3c], parameterNumber: 3, size: 2).format()
zwave.configurationV1.configurationSet(configurationValue: [0x3c], parameterNumber: 4, size: 2).format()
zwave.configurationV1.configurationSet(configurationValue: [0x12c], parameterNumber: 5, size: 2).format()

}