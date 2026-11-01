//Generated class. Do not modify.

"use strict";

let scriptName = "base.js"
let modName = "none"

const log = (context, obj) => Vars.mods.scripts.log(context, String(obj))
const print = text => log(modName + "/" + scriptName, text)

const newFloats = cap => Vars.mods.getScripts().newFloats(cap);

//these are not strictly necessary, but are kept for edge cases
const run = method => new java.lang.Runnable(){run: method}
const boolf = method => new Boolf(){get: method}
const boolp = method => new Boolp(){get: method}
const floatf = method => new Floatf(){get: method}
const floatp = method => new Floatp(){get: method}
const cons = method => new Cons(){get: method}
const prov = method => new Prov(){get: method}
const func = method => new Func(){get: method}
const getRealClass = value => Vars.mods.getScripts().getClass(value);

const newEffect = (lifetime, renderer) => new Effect.Effect(lifetime, new Effect.EffectRenderer({render: renderer}))
Call = Packages.orbital.gen.Call

//js 'extend(Base, ..., {})' = java 'new Base(...) {}'
function extend(/*Base, ..., def*/){
    const Base = arguments[0]
    const def = arguments[arguments.length - 1]
    //swap order from Base, def, ... to Base, ..., def
    const args = [Base, def].concat(Array.from(arguments).splice(1, arguments.length - 2))

    //forward constructor arguments to new JavaAdapter
    const instance = JavaAdapter.apply(null, args)
    //JavaAdapter only overrides functions; set fields too
    for(var i in def){
        if(typeof(def[i]) != "function"){
            instance[i] = def[i]
        }
    }
    return instance
}

importPackage(Packages.java.lang)
const Arrays = Packages.java.util.Arrays
//commonly used inner classes that are moved out so JS mods can be more compatible with v8 and v9
const TextureFilter = Packages.arc.graphics.Texture.TextureFilter
const TextureWrap = Packages.arc.graphics.Texture.TextureWrap
importPackage(Packages.arc)
importPackage(Packages.arc.assets)
importPackage(Packages.arc.assets.loaders)
importPackage(Packages.arc.audio)
importPackage(Packages.arc.backend.headless)
importPackage(Packages.arc.files)
importPackage(Packages.arc.flabel)
importPackage(Packages.arc.freetype)
importPackage(Packages.arc.func)
importPackage(Packages.arc.fx)
importPackage(Packages.arc.fx.filters)
importPackage(Packages.arc.fx.util)
importPackage(Packages.arc.graphics)
importPackage(Packages.arc.graphics.g2d)
importPackage(Packages.arc.graphics.g3d)
importPackage(Packages.arc.graphics.gl)
importPackage(Packages.arc.input)
importPackage(Packages.arc.math)
importPackage(Packages.arc.math.geom)
importPackage(Packages.arc.mock)
importPackage(Packages.arc.net)
importPackage(Packages.arc.net.dns)
importPackage(Packages.arc.packer)
importPackage(Packages.arc.scene)
importPackage(Packages.arc.scene.actions)
importPackage(Packages.arc.scene.event)
importPackage(Packages.arc.scene.style)
importPackage(Packages.arc.scene.ui)
importPackage(Packages.arc.scene.ui.layout)
importPackage(Packages.arc.scene.utils)
importPackage(Packages.arc.struct)
importPackage(Packages.arc.util)
importPackage(Packages.arc.util.io)
importPackage(Packages.arc.util.noise)
importPackage(Packages.arc.util.pooling)
importPackage(Packages.arc.util.serialization)
importPackage(Packages.arc.util.viewport)
importPackage(Packages.orbital)
importPackage(Packages.orbital.ai)
importPackage(Packages.orbital.ai.types)
importPackage(Packages.orbital.async)
importPackage(Packages.orbital.audio)
importPackage(Packages.orbital.content)
importPackage(Packages.orbital.core)
importPackage(Packages.orbital.ctype)
importPackage(Packages.orbital.editor)
importPackage(Packages.orbital.editor.data)
importPackage(Packages.orbital.entities)
importPackage(Packages.orbital.entities.abilities)
importPackage(Packages.orbital.entities.bullet)
importPackage(Packages.orbital.entities.effect)
importPackage(Packages.orbital.entities.part)
importPackage(Packages.orbital.entities.pattern)
importPackage(Packages.orbital.entities.units)
importPackage(Packages.orbital.game)
importPackage(Packages.orbital.gen)
importPackage(Packages.orbital.graphics)
importPackage(Packages.orbital.graphics.g3d)
importPackage(Packages.orbital.input)
importPackage(Packages.orbital.io)
importPackage(Packages.orbital.io.versions)
importPackage(Packages.orbital.logic)
importPackage(Packages.orbital.maps)
importPackage(Packages.orbital.maps.filters)
importPackage(Packages.orbital.maps.generators)
importPackage(Packages.orbital.maps.planet)
importPackage(Packages.orbital.mod)
importPackage(Packages.orbital.mod.data)
importPackage(Packages.orbital.net)
importPackage(Packages.orbital.service)
importPackage(Packages.orbital.type)
importPackage(Packages.orbital.type.unit)
importPackage(Packages.orbital.type.weapons)
importPackage(Packages.orbital.type.weather)
importPackage(Packages.orbital.ui)
importPackage(Packages.orbital.ui.builder)
importPackage(Packages.orbital.ui.dialogs)
importPackage(Packages.orbital.ui.fragments)
importPackage(Packages.orbital.ui.layout)
importPackage(Packages.orbital.world)
importPackage(Packages.orbital.world.blocks)
importPackage(Packages.orbital.world.blocks.campaign)
importPackage(Packages.orbital.world.blocks.defense)
importPackage(Packages.orbital.world.blocks.defense.turrets)
importPackage(Packages.orbital.world.blocks.distribution)
importPackage(Packages.orbital.world.blocks.environment)
importPackage(Packages.orbital.world.blocks.heat)
importPackage(Packages.orbital.world.blocks.liquid)
importPackage(Packages.orbital.world.blocks.logic)
importPackage(Packages.orbital.world.blocks.payloads)
importPackage(Packages.orbital.world.blocks.power)
importPackage(Packages.orbital.world.blocks.production)
importPackage(Packages.orbital.world.blocks.sandbox)
importPackage(Packages.orbital.world.blocks.storage)
importPackage(Packages.orbital.world.blocks.units)
importPackage(Packages.orbital.world.consumers)
importPackage(Packages.orbital.world.draw)
importPackage(Packages.orbital.world.meta)
importPackage(Packages.orbital.world.modules)
const AdminRequestEvent = Packages.orbital.game.EventType.AdminRequestEvent
const PlayerIpUnbanEvent = Packages.orbital.game.EventType.PlayerIpUnbanEvent
const PlayerIpBanEvent = Packages.orbital.game.EventType.PlayerIpBanEvent
const PlayerUnbanEvent = Packages.orbital.game.EventType.PlayerUnbanEvent
const PlayerBanEvent = Packages.orbital.game.EventType.PlayerBanEvent
const PlayerLeave = Packages.orbital.game.EventType.PlayerLeave
const PlayerConnect = Packages.orbital.game.EventType.PlayerConnect
const PlayerJoin = Packages.orbital.game.EventType.PlayerJoin
const PlayerConnectionConfirmed = Packages.orbital.game.EventType.PlayerConnectionConfirmed
const ConnectPacketEvent = Packages.orbital.game.EventType.ConnectPacketEvent
const ConnectionEvent = Packages.orbital.game.EventType.ConnectionEvent
const UnitChangeEvent = Packages.orbital.game.EventType.UnitChangeEvent
const UnitUnloadEvent = Packages.orbital.game.EventType.UnitUnloadEvent
const UnitSpawnEvent = Packages.orbital.game.EventType.UnitSpawnEvent
const UnitCreateEvent = Packages.orbital.game.EventType.UnitCreateEvent
const UnitDrownEvent = Packages.orbital.game.EventType.UnitDrownEvent
const UnitDamageEvent = Packages.orbital.game.EventType.UnitDamageEvent
const UnitBulletDestroyEvent = Packages.orbital.game.EventType.UnitBulletDestroyEvent
const UnitDestroyEvent = Packages.orbital.game.EventType.UnitDestroyEvent
const BuildingBulletDestroyEvent = Packages.orbital.game.EventType.BuildingBulletDestroyEvent
const GeneratorPressureExplodeEvent = Packages.orbital.game.EventType.GeneratorPressureExplodeEvent
const BlockDestroyEvent = Packages.orbital.game.EventType.BlockDestroyEvent
const BuildSelectEvent = Packages.orbital.game.EventType.BuildSelectEvent
const BuildRotateEvent = Packages.orbital.game.EventType.BuildRotateEvent
const BlockBuildEndEvent = Packages.orbital.game.EventType.BlockBuildEndEvent
const BlockBuildBeginEvent = Packages.orbital.game.EventType.BlockBuildBeginEvent
const RulesLoadEvent = Packages.orbital.game.EventType.RulesLoadEvent
const ResearchEvent = Packages.orbital.game.EventType.ResearchEvent
const UnlockEvent = Packages.orbital.game.EventType.UnlockEvent
const StateChangeEvent = Packages.orbital.game.EventType.StateChangeEvent
const CoreChangeEvent = Packages.orbital.game.EventType.CoreChangeEvent
const BuildTeamChangeEvent = Packages.orbital.game.EventType.BuildTeamChangeEvent
const TileOverlayChangeEvent = Packages.orbital.game.EventType.TileOverlayChangeEvent
const TileFloorChangeEvent = Packages.orbital.game.EventType.TileFloorChangeEvent
const TileChangeEvent = Packages.orbital.game.EventType.TileChangeEvent
const TilePreChangeEvent = Packages.orbital.game.EventType.TilePreChangeEvent
const BulletCreateEvent = Packages.orbital.game.EventType.BulletCreateEvent
const BuildDamageEvent = Packages.orbital.game.EventType.BuildDamageEvent
const GameOverEvent = Packages.orbital.game.EventType.GameOverEvent
const BuildingCommandEvent = Packages.orbital.game.EventType.BuildingCommandEvent
const UnitControlEvent = Packages.orbital.game.EventType.UnitControlEvent
const PayloadDropEvent = Packages.orbital.game.EventType.PayloadDropEvent
const PickupEvent = Packages.orbital.game.EventType.PickupEvent
const TapEvent = Packages.orbital.game.EventType.TapEvent
const ConfigEvent = Packages.orbital.game.EventType.ConfigEvent
const DepositEvent = Packages.orbital.game.EventType.DepositEvent
const WithdrawEvent = Packages.orbital.game.EventType.WithdrawEvent
const SectorCaptureEvent = Packages.orbital.game.EventType.SectorCaptureEvent
const ClientChatEvent = Packages.orbital.game.EventType.ClientChatEvent
const PlayerChatEvent = Packages.orbital.game.EventType.PlayerChatEvent
const TextInputEvent = Packages.orbital.game.EventType.TextInputEvent
const MenuBuilderOptionChooseEvent = Packages.orbital.game.EventType.MenuBuilderOptionChooseEvent
const MenuOptionChooseEvent = Packages.orbital.game.EventType.MenuOptionChooseEvent
const ClientServerConnectEvent = Packages.orbital.game.EventType.ClientServerConnectEvent
const ClientPreConnectEvent = Packages.orbital.game.EventType.ClientPreConnectEvent
const SchematicCreateEvent = Packages.orbital.game.EventType.SchematicCreateEvent
const SectorLaunchLoadoutEvent = Packages.orbital.game.EventType.SectorLaunchLoadoutEvent
const SectorLaunchEvent = Packages.orbital.game.EventType.SectorLaunchEvent
const LaunchItemEvent = Packages.orbital.game.EventType.LaunchItemEvent
const SectorInvasionEvent = Packages.orbital.game.EventType.SectorInvasionEvent
const SectorLoseEvent = Packages.orbital.game.EventType.SectorLoseEvent
const SaveLoadEvent = Packages.orbital.game.EventType.SaveLoadEvent
const TextureStreamEvent = Packages.orbital.game.EventType.TextureStreamEvent
const DataPatchLoadEvent = Packages.orbital.game.EventType.DataPatchLoadEvent
const WorldLoadEndEvent = Packages.orbital.game.EventType.WorldLoadEndEvent
const WorldLoadBeginEvent = Packages.orbital.game.EventType.WorldLoadBeginEvent
const WorldLoadEvent = Packages.orbital.game.EventType.WorldLoadEvent
const FileTreeInitEvent = Packages.orbital.game.EventType.FileTreeInitEvent
const MusicRegisterEvent = Packages.orbital.game.EventType.MusicRegisterEvent
const ClientLoadEvent = Packages.orbital.game.EventType.ClientLoadEvent
const ModContentLoadEvent = Packages.orbital.game.EventType.ModContentLoadEvent
const AtlasPackEvent = Packages.orbital.game.EventType.AtlasPackEvent
const ContentInitEvent = Packages.orbital.game.EventType.ContentInitEvent
const BlockInfoEvent = Packages.orbital.game.EventType.BlockInfoEvent
const LineConfirmEvent = Packages.orbital.game.EventType.LineConfirmEvent
const TurnEvent = Packages.orbital.game.EventType.TurnEvent
const WaveEvent = Packages.orbital.game.EventType.WaveEvent
const HostEvent = Packages.orbital.game.EventType.HostEvent
const ResetEvent = Packages.orbital.game.EventType.ResetEvent
const PlayEvent = Packages.orbital.game.EventType.PlayEvent
const DisposeEvent = Packages.orbital.game.EventType.DisposeEvent
const ServerLoadEvent = Packages.orbital.game.EventType.ServerLoadEvent
const ClientCreateEvent = Packages.orbital.game.EventType.ClientCreateEvent
const SaveWriteEvent = Packages.orbital.game.EventType.SaveWriteEvent
const MapPublishEvent = Packages.orbital.game.EventType.MapPublishEvent
const MapMakeEvent = Packages.orbital.game.EventType.MapMakeEvent
const ResizeEvent = Packages.orbital.game.EventType.ResizeEvent
const LoseEvent = Packages.orbital.game.EventType.LoseEvent
const WinEvent = Packages.orbital.game.EventType.WinEvent
const Trigger = Packages.orbital.game.EventType.Trigger
