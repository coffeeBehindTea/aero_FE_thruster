# Aero FE Thrusters

[简体中文](README.md) | [English](README.en_us.md)

Aero FE Thrusters 是一个面向 Create: Aeronautics / Sable 物理结构的机械动力附属模组。它添加了一个电力推进器，使用 FE 能量作为燃料，并把推力施加到航空学物理结构上。推进器可以通过方块 UI 调整最大推力和红石控制模式，也可以通过 CC: Tweaked 电脑进行控制和读取物理遥测信息；安装 Applied Energistics 2 时，还可以接入 ME 线缆进行 AE 能量交换。

## 前置模组

必需：

- Minecraft `1.21.1`
- NeoForge `21.1.219` 或更高
- Create `6.0.10` 或更高
- Sable `1.2.1` 或更高
- Create: Aeronautics `1.2.1` 或更高

可选：

- CC: Tweaked `1.118.0` 或更高，用于电脑控制推进器和读取物理结构信息。
- CC: Sable 不是本模组的前置；如果安装了它，也可以继续使用它提供的 `sublevel` / `aero` API。本模组额外在推进器 peripheral 上提供力读取能力。
- Applied Energistics 2 `19.2.17` 或更高，用于通过 ME 线缆向推进器供能，或让推进器缓存为 ME 网络供能。

## 方块内容

### 电力推进器

电力推进器是一个 FE 驱动的 Create: Aeronautics 推进器。

基础参数：

- 最大可设置推力：`0` 到服务端配置上限，默认 `4096` pN
- 默认最大推力：`256` pN
- 推力精度：最多 `4` 位小数
- 内部能量容量：`1,000,000` FE
- 最大输入速率：`64,000` FE/t
- 能量消耗：每 `1 pN` 推力每 tick 消耗 `2 FE`

合成配方：

```text
C I C
R F R
C I C
```

材料：

- `C`：铜锭
- `I`：铁锭
- `R`：红石粉
- `F`：Create 精密构件

## 使用方法

1. 将电力推进器放在 Create: Aeronautics / Sable 物理结构上。
2. 给推进器接入 FE 能源；如果安装了 AE2，也可以把 ME 线缆接到推进器任意一面进行 AE/FE 能量交换。
3. 空手右键推进器打开配置 UI。
4. 在中间输入框设置最大推力，范围为 `0 <= 推力 <= 配置上限`。
5. 使用下方滑条选择红石控制模式。
6. 使用 Create 扳手右键推进器时，推进器会像普通机械动力方块一样旋转。

## Create 配置

打开 Create 的游戏内配置界面，找到 Aero FE Thrusters 的服务端配置。

- `thruster.maxConfigurableThrust`：推进器 UI 和 CC:T 可设置推力的上限。
- 默认值：`4096`
- 配置允许范围：`1` 到 `1.0E7`
- 单个推进器的推力值最多支持 `4` 位小数。

## Applied Energistics 2 能量兼容

安装 AE2 后，推进器会作为一个 ME 网络节点和公开能量存储被 AE2 线缆识别。

- ME 网络可以把 AE 能量注入推进器，能量会按 AE2 的单位换算写入推进器内部 FE 缓存。
- ME 网络也可以从推进器缓存中抽取能量，为 AE2 网络供能。
- 普通 NeoForge FE 能力仍然只允许外部输入；只有 AE2 兼容层可以按本功能需求从推进器缓存抽能。

## 红石控制模式

| Lua 名称 | UI 名称 | 行为 |
| --- | --- | --- |
| `ignore` | 忽略红石信号 | 只要有能量，就按设定推力输出。 |
| `work_when_powered` | 收到红石信号时工作 | 默认不输出推力；红石信号大于 `0` 时输出设定推力。 |
| `stop_when_powered` | 收到红石信号时停止 | 默认输出设定推力；红石信号大于 `0` 时停止输出。 |
| `proportional` | 与信号成正比 | 将最大推力分为 `15` 份，输出 `最大推力 * 信号强度 / 15`。 |
| `inverse` | 与信号成反比 | 输出 `最大推力 * (15 - 信号强度) / 15`。 |

例如最大推力为 `150` pN，模式为 `proportional` 时：

- 红石信号 `0`：输出 `0` pN
- 红石信号 `1`：输出 `10` pN
- 红石信号 `2`：输出 `20` pN
- 红石信号 `15`：输出 `150` pN

## CC: Tweaked 快速开始

把 CC: Tweaked 电脑放在推进器相邻位置，然后运行：

```lua
local t = peripheral.find("aero_fe_thruster")
if not t then error("找不到 aero_fe_thruster") end

t.setMaxThrust(512)
t.setRedstoneMode("ignore")

print(t.getCurrentThrust())
print(t.getEnergy(), t.getEnergyCapacity())
```

如果输出太多，可以写入文件：

```lua
local t = peripheral.find("aero_fe_thruster")
local report = textutils.serialize(t.getInfo())
local file = fs.open("thruster_report.txt", "w")
file.write(report)
file.close()
print("已写入 thruster_report.txt")
```

## Document: CC:T Peripheral

Peripheral 类型名：

```lua
"aero_fe_thruster"
```

获取 peripheral：

```lua
local t = peripheral.find("aero_fe_thruster")
```

所有结构遥测函数都需要推进器属于一个 Sable 物理结构。如果推进器还只是普通世界方块，这些函数会抛出：

```text
thruster is not part of a physics structure
```

力记录由 Sable 提供。调用 `enableForceTelemetry()`、`getForceGroups()` 或 `getForces()` 会开启单独力记录；第一次开启后通常需要等待下一个物理 tick 才能读到数据。

### 推力与基础状态

| Function | 作用 | 使用方法 | 示例输出 |
| --- | --- | --- | --- |
| `setThrust(thrust)` | 设置最大推力，是 `setMaxThrust` 的别名。范围 `0..配置上限`，最多 4 位小数。 | `t.setThrust(512.125)` | `512.125` |
| `setMaxThrust(thrust)` | 设置最大推力。超出配置上限会抛出 Lua 错误。 | `t.setMaxThrust(1024.0625)` | `1024.0625` |
| `getThrust()` | 读取配置的最大推力，是 `getMaxThrust` 的别名。 | `t.getThrust()` | `1024` |
| `getMaxThrust()` | 读取配置的最大推力。 | `t.getMaxThrust()` | `1024` |
| `getCurrentThrust()` | 读取当前实际输出推力。会受到红石模式和 FE 供能影响。 | `t.getCurrentThrust()` | `768.0` |
| `getTargetThrust()` | 读取红石模式计算出的目标推力。它不代表 FE 不足时的实际输出。 | `t.getTargetThrust()` | `1024.0` |
| `getInfo()` | 读取推进器基础信息表。 | `t.getInfo()` | 见下方示例 |

`getInfo()` 示例输出：

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

### 红石控制

| Function | 作用 | 使用方法 | 示例输出 |
| --- | --- | --- | --- |
| `getRedstoneSignal()` | 读取推进器附近收到的最高红石信号，范围 `0..15`。 | `t.getRedstoneSignal()` | `12` |
| `setRedstoneMode(mode)` | 设置红石模式。参数必须是合法 Lua 模式名。 | `t.setRedstoneMode("proportional")` | `"proportional"` |
| `getRedstoneMode()` | 读取当前红石模式。 | `t.getRedstoneMode()` | `"ignore"` |
| `getRedstoneModes()` | 返回所有可用红石模式。 | `t.getRedstoneModes()` | 见下方示例 |

`getRedstoneModes()` 示例输出：

```lua
{
  [1] = "ignore",
  [2] = "work_when_powered",
  [3] = "stop_when_powered",
  [4] = "proportional",
  [5] = "inverse"
}
```

### FE 能量

| Function | 作用 | 使用方法 | 示例输出 |
| --- | --- | --- | --- |
| `getEnergy()` | 读取当前储存的 FE。 | `t.getEnergy()` | `998000` |
| `getEnergyCapacity()` | 读取内部 FE 容量。 | `t.getEnergyCapacity()` | `1000000` |
| `hasEnergy()` | 推进器内是否还有 FE。 | `t.hasEnergy()` | `true` |

### 物理结构遥测

| Function | 作用 | 使用方法 | 示例输出 |
| --- | --- | --- | --- |
| `hasStructure()` | 判断推进器是否属于 Sable 物理结构。 | `t.hasStructure()` | `true` |
| `getStructureInfo()` | 读取结构综合信息，包括质量、重心、速度和力记录状态。 | `t.getStructureInfo()` | 见下方示例 |
| `getStructureMass()` | 读取结构总质量。 | `t.getStructureMass()` | `245.5` |
| `getStructureCenterOfMass()` | 读取结构重心。 | `t.getStructureCenterOfMass()` | `{ x = 3.5, y = 5.0, z = -1.0 }` |
| `getStructureLinearVelocity()` | 读取结构线速度。 | `t.getStructureLinearVelocity()` | `{ x = 0.0, y = 0.2, z = -1.4 }` |
| `getStructureAngularVelocity()` | 读取结构角速度。 | `t.getStructureAngularVelocity()` | `{ x = 0.0, y = 0.01, z = 0.0 }` |

`getStructureInfo()` 示例输出：

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

### 力遥测

| Function | 作用 | 使用方法 | 示例输出 |
| --- | --- | --- | --- |
| `enableForceTelemetry()` | 开启 Sable 单独点力记录。 | `t.enableForceTelemetry()` | `true` |
| `isForceTelemetryEnabled()` | 读取力记录是否已开启。 | `t.isForceTelemetryEnabled()` | `true` |
| `getForceGroups()` | 按力分组读取合力和合力矩。会自动开启力记录。 | `t.getForceGroups()` | 见下方示例 |
| `getForces()` | 读取每一个记录到的点力。会自动开启力记录。 | `t.getForces()` | 见下方示例 |

`getForceGroups()` 示例输出：

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

`getForces()` 示例输出：

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

### 完整报告脚本

下面的脚本会把所有函数输出写入 `thruster_report.txt`：

```lua
local outputPath = "thruster_report.txt"
local t = peripheral.find("aero_fe_thruster")
if not t then error("没有找到相邻的 aero_fe_thruster") end

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
print("已写入 " .. outputPath)
```
