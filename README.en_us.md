# Aero FE Thrusters

[简体中文](README.md) | [English](README.en_us.md)

Aero FE Thrusters is a Create: Aeronautics / Sable addon. It adds an FE-powered Electric Thruster which applies propulsion force to Aeronautics physics structures. The thruster can be configured with an in-game UI, controlled by redstone, integrated with CC: Tweaked computers for thrust control and physics telemetry, and connected to Applied Energistics 2 ME cables for AE energy exchange.

## Requirements

Required:

- Minecraft `1.21.1`
- NeoForge `21.1.219` or newer
- Create `6.0.10` or newer
- Sable `1.2.1` or newer
- Create: Aeronautics `1.2.1` or newer

Optional:

- CC: Tweaked `1.118.0` or newer, for computer control and telemetry.
- CC: Sable is not required by this mod. If installed, its `sublevel` / `aero` APIs can still be used alongside this mod. Aero FE Thrusters adds extra force telemetry through the thruster peripheral.
- Applied Energistics 2 `19.2.17` or newer, for powering the thruster from ME cables or feeding the thruster's buffer back into an ME network.

## Block Content

### Electric Thruster

The Electric Thruster is an FE-powered thruster for Create: Aeronautics physics structures.

Base values:

- Configurable max thrust: `0` to `4096` pN
- Default max thrust: `256` pN
- Internal energy capacity: `1,000,000` FE
- Max receive rate: `64,000` FE/t
- Energy cost: `2 FE` per `1 pN` of thrust per tick

Crafting recipe:

```text
C I C
R F R
C I C
```

Materials:

- `C`: Copper Ingot
- `I`: Iron Ingot
- `R`: Redstone Dust
- `F`: Create Precision Mechanism

## Usage

1. Place the Electric Thruster on a Create: Aeronautics / Sable physics structure.
2. Provide FE power to the thruster. If AE2 is installed, ME cables can connect to any side for AE/FE energy exchange.
3. Right-click the thruster with an empty hand to open the configuration UI.
4. Set the max thrust in the center input box. Valid range: `0 <= thrust <= 4096`.
5. Use the slider below the input box to select the redstone control mode.
6. Right-click with the Create wrench to rotate the thruster like a normal Create block.

## Applied Energistics 2 Energy Compatibility

When AE2 is installed, the thruster is exposed as an ME network node and public AE power storage.

- The ME network can inject AE into the thruster; energy is converted through AE2's normal unit conversion and stored in the internal FE buffer.
- The ME network can also extract energy from the thruster buffer to power AE2 devices.
- The normal NeoForge FE capability remains receive-only. Bidirectional extraction is only exposed through the AE2 compatibility layer requested by this mod.

## Redstone Control Modes

| Lua name | UI name | Behavior |
| --- | --- | --- |
| `ignore` | Ignore redstone signal | Outputs the configured thrust while energy is available. |
| `work_when_powered` | Work when receiving redstone | Outputs no thrust by default; outputs configured thrust when signal is above `0`. |
| `stop_when_powered` | Stop when receiving redstone | Outputs configured thrust by default; stops when signal is above `0`. |
| `proportional` | Proportional to signal | Outputs `maxThrust * signal / 15`. |
| `inverse` | Inverse to signal | Outputs `maxThrust * (15 - signal) / 15`. |

For example, with max thrust set to `150` pN and mode set to `proportional`:

- Redstone signal `0`: outputs `0` pN
- Redstone signal `1`: outputs `10` pN
- Redstone signal `2`: outputs `20` pN
- Redstone signal `15`: outputs `150` pN

## CC: Tweaked Quick Start

Place a CC: Tweaked computer next to the thruster, then run:

```lua
local t = peripheral.find("aero_fe_thruster")
if not t then error("Cannot find aero_fe_thruster") end

t.setMaxThrust(512)
t.setRedstoneMode("ignore")

print(t.getCurrentThrust())
print(t.getEnergy(), t.getEnergyCapacity())
```

If the output is too long for the terminal, write it to a file:

```lua
local t = peripheral.find("aero_fe_thruster")
local report = textutils.serialize(t.getInfo())
local file = fs.open("thruster_report.txt", "w")
file.write(report)
file.close()
print("Wrote thruster_report.txt")
```

## Document: CC:T Peripheral

Peripheral type:

```lua
"aero_fe_thruster"
```

Get the peripheral:

```lua
local t = peripheral.find("aero_fe_thruster")
```

All structure telemetry functions require the thruster to be part of a Sable physics structure. If the thruster is still a normal world block, these functions throw:

```text
thruster is not part of a physics structure
```

Force telemetry is provided by Sable. Calling `enableForceTelemetry()`, `getForceGroups()`, or `getForces()` enables individual force tracking. After enabling it for the first time, wait until the next physics tick before expecting force data.

### Thrust And Basic State

| Function | Description | Usage | Example output |
| --- | --- | --- | --- |
| `setThrust(thrust)` | Sets configured max thrust. Alias of `setMaxThrust`. Range: `0..4096`. | `t.setThrust(512)` | `512` |
| `setMaxThrust(thrust)` | Sets configured max thrust. Throws a Lua error outside `0..4096`. | `t.setMaxThrust(1024)` | `1024` |
| `getThrust()` | Returns configured max thrust. Alias of `getMaxThrust`. | `t.getThrust()` | `1024` |
| `getMaxThrust()` | Returns configured max thrust. | `t.getMaxThrust()` | `1024` |
| `getCurrentThrust()` | Returns current actual output thrust, affected by redstone and FE availability. | `t.getCurrentThrust()` | `768.0` |
| `getTargetThrust()` | Returns redstone-adjusted target thrust. This does not account for FE shortage. | `t.getTargetThrust()` | `1024.0` |
| `getInfo()` | Returns a basic thruster info table. | `t.getInfo()` | See below |

`getInfo()` example output:

```lua
{
  maxThrust = 1024,
  currentThrust = 1024.0,
  targetThrust = 1024.0,
  redstoneMode = "ignore",
  redstoneSignal = 0,
  energy = 998000,
  energyCapacity = 1000000
}
```

### Redstone Control

| Function | Description | Usage | Example output |
| --- | --- | --- | --- |
| `getRedstoneSignal()` | Returns the strongest neighboring redstone signal, from `0` to `15`. | `t.getRedstoneSignal()` | `12` |
| `setRedstoneMode(mode)` | Sets redstone mode. The argument must be a valid Lua mode name. | `t.setRedstoneMode("proportional")` | `"proportional"` |
| `getRedstoneMode()` | Returns the current redstone mode. | `t.getRedstoneMode()` | `"ignore"` |
| `getRedstoneModes()` | Returns all available redstone modes. | `t.getRedstoneModes()` | See below |

`getRedstoneModes()` example output:

```lua
{
  [1] = "ignore",
  [2] = "work_when_powered",
  [3] = "stop_when_powered",
  [4] = "proportional",
  [5] = "inverse"
}
```

### FE Energy

| Function | Description | Usage | Example output |
| --- | --- | --- | --- |
| `getEnergy()` | Returns stored FE. | `t.getEnergy()` | `998000` |
| `getEnergyCapacity()` | Returns internal FE capacity. | `t.getEnergyCapacity()` | `1000000` |
| `hasEnergy()` | Returns whether the thruster has any FE stored. | `t.hasEnergy()` | `true` |

### Physics Structure Telemetry

| Function | Description | Usage | Example output |
| --- | --- | --- | --- |
| `hasStructure()` | Returns whether the thruster belongs to a Sable physics structure. | `t.hasStructure()` | `true` |
| `getStructureInfo()` | Returns combined structure information: mass, center of mass, velocities, and force telemetry state. | `t.getStructureInfo()` | See below |
| `getStructureMass()` | Returns total structure mass. | `t.getStructureMass()` | `245.5` |
| `getStructureCenterOfMass()` | Returns center of mass. | `t.getStructureCenterOfMass()` | `{ x = 3.5, y = 5.0, z = -1.0 }` |
| `getStructureLinearVelocity()` | Returns structure linear velocity. | `t.getStructureLinearVelocity()` | `{ x = 0.0, y = 0.2, z = -1.4 }` |
| `getStructureAngularVelocity()` | Returns structure angular velocity. | `t.getStructureAngularVelocity()` | `{ x = 0.0, y = 0.01, z = 0.0 }` |

`getStructureInfo()` example output:

```lua
{
  id = "6f0ef816-3f4d-4c72-9e3b-98a748bcb49b",
  runtimeId = 12,
  name = "Airship",
  mass = 245.5,
  inverseMass = 0.0040733197556008,
  centerOfMass = { x = 3.5, y = 5.0, z = -1.0 },
  linearVelocity = { x = 0.0, y = 0.2, z = -1.4 },
  angularVelocity = { x = 0.0, y = 0.01, z = 0.0 },
  forceTelemetryEnabled = true,
  forceCount = 3
}
```

### Force Telemetry

| Function | Description | Usage | Example output |
| --- | --- | --- | --- |
| `enableForceTelemetry()` | Enables Sable individual point-force tracking. | `t.enableForceTelemetry()` | `true` |
| `isForceTelemetryEnabled()` | Returns whether force tracking is enabled. | `t.isForceTelemetryEnabled()` | `true` |
| `getForceGroups()` | Returns total force and torque grouped by force group. Automatically enables force tracking. | `t.getForceGroups()` | See below |
| `getForces()` | Returns every recorded point force. Automatically enables force tracking. | `t.getForces()` | See below |

`getForceGroups()` example output:

```lua
{
  [1] = {
    id = "sable:propulsion",
    name = "Propulsion",
    description = "",
    color = 16753920,
    defaultDisplayed = true,
    forceCount = 1,
    totalForce = { x = 0.0, y = 0.0, z = -512.0 },
    totalForceMagnitude = 512.0,
    totalTorque = { x = 0.0, y = 128.0, z = 0.0 },
    totalTorqueMagnitude = 128.0
  }
}
```

`getForces()` example output:

```lua
{
  [1] = {
    groupId = "sable:propulsion",
    groupName = "Propulsion",
    point = { x = 4.5, y = 3.5, z = 8.5 },
    force = { x = 0.0, y = 0.0, z = -512.0 },
    magnitude = 512.0
  },
  [2] = {
    groupId = "sable:drag",
    groupName = "Drag",
    point = { x = 3.0, y = 4.0, z = 1.5 },
    force = { x = 0.0, y = 0.0, z = 42.7 },
    magnitude = 42.7
  }
}
```

### Full Report Script

The script below writes every function output to `thruster_report.txt`:

```lua
local outputPath = "thruster_report.txt"
local t = peripheral.find("aero_fe_thruster")
if not t then error("Cannot find adjacent aero_fe_thruster") end

local lines = {}
local function writeLine(text) lines[#lines + 1] = tostring(text) end
local function show(name, value)
  writeLine("")
  writeLine("== " .. name .. " ==")
  writeLine(textutils.serialize(value))
end

local maxThrust = t.getMaxThrust()
local mode = t.getRedstoneMode()

show("getInfo()", t.getInfo())
show("getThrust()", t.getThrust())
show("getMaxThrust()", t.getMaxThrust())
show("setThrust(current)", t.setThrust(maxThrust))
show("setMaxThrust(current)", t.setMaxThrust(maxThrust))
show("getCurrentThrust()", t.getCurrentThrust())
show("getTargetThrust()", t.getTargetThrust())
show("getRedstoneSignal()", t.getRedstoneSignal())
show("getRedstoneMode()", t.getRedstoneMode())
show("getRedstoneModes()", t.getRedstoneModes())
show("setRedstoneMode(current)", t.setRedstoneMode(mode))
show("getEnergy()", t.getEnergy())
show("getEnergyCapacity()", t.getEnergyCapacity())
show("hasEnergy()", t.hasEnergy())
show("hasStructure()", t.hasStructure())

if t.hasStructure() then
  show("getStructureInfo()", t.getStructureInfo())
  show("getStructureMass()", t.getStructureMass())
  show("getStructureCenterOfMass()", t.getStructureCenterOfMass())
  show("getStructureLinearVelocity()", t.getStructureLinearVelocity())
  show("getStructureAngularVelocity()", t.getStructureAngularVelocity())
  show("enableForceTelemetry()", t.enableForceTelemetry())
  sleep(0.1)
  show("isForceTelemetryEnabled()", t.isForceTelemetryEnabled())
  show("getForceGroups()", t.getForceGroups())
  show("getForces()", t.getForces())
end

local file = fs.open(outputPath, "w")
file.write(table.concat(lines, "\n"))
file.close()
print("Wrote " .. outputPath)
```
