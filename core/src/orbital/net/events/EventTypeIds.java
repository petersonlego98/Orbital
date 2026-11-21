package orbital.net.events;

/**
 * Stable wire IDs for the game events that are worth recording.
 *
 * <p>IDs are never renumbered: a log must stay readable across builds. Adding an event appends a
 * new constant. Event payloads are written by the game layer (see {@code EventRecorder}) with the
 * varint helpers in {@link EventCodec}, so this class only fixes the vocabulary.</p>
 */
public final class EventTypeIds{
    //session
    public static final int SESSION_START = 0;
    public static final int SESSION_END = 1;
    public static final int PLAYER_JOIN = 2;
    public static final int PLAYER_LEAVE = 3;

    //world mutations
    public static final int TILE_CHANGE = 10;
    public static final int TILE_FLOOR_CHANGE = 11;
    public static final int TILE_OVERLAY_CHANGE = 12;
    public static final int BUILD_BEGIN = 13;
    public static final int BUILD_END = 14;
    public static final int BUILD_DESTROYED = 15;
    public static final int BUILD_TEAM_CHANGE = 16;
    public static final int BUILD_HEALTH = 17;
    public static final int CONFIG = 18;

    //entities
    public static final int UNIT_CREATE = 20;
    public static final int UNIT_SPAWN = 21;
    public static final int UNIT_DESTROY = 22;
    public static final int BULLET_CREATE = 23;
    public static final int UNIT_COMMAND = 24;
    public static final int UNIT_STANCE = 25;

    //items and economy
    public static final int ITEM_WITHDRAW = 30;
    public static final int ITEM_DEPOSIT = 31;
    public static final int CORE_ITEMS = 32;

    //logic
    public static final int WAVE = 40;
    public static final int GAME_OVER = 41;
    public static final int CHAT = 42;
    public static final int KEYFRAME = 50;

    private EventTypeIds(){
    }

    /** @return the localized-ish name of an id, for logs and debugging. */
    public static String name(int id){
        return switch(id){
            case SESSION_START -> "session.start";
            case SESSION_END -> "session.end";
            case PLAYER_JOIN -> "player.join";
            case PLAYER_LEAVE -> "player.leave";
            case TILE_CHANGE -> "tile.change";
            case TILE_FLOOR_CHANGE -> "tile.floor";
            case TILE_OVERLAY_CHANGE -> "tile.overlay";
            case BUILD_BEGIN -> "build.begin";
            case BUILD_END -> "build.end";
            case BUILD_DESTROYED -> "build.destroyed";
            case BUILD_TEAM_CHANGE -> "build.team";
            case BUILD_HEALTH -> "build.health";
            case CONFIG -> "build.config";
            case UNIT_CREATE -> "unit.create";
            case UNIT_SPAWN -> "unit.spawn";
            case UNIT_DESTROY -> "unit.destroy";
            case BULLET_CREATE -> "bullet.create";
            case UNIT_COMMAND -> "unit.command";
            case UNIT_STANCE -> "unit.stance";
            case ITEM_WITHDRAW -> "item.withdraw";
            case ITEM_DEPOSIT -> "item.deposit";
            case CORE_ITEMS -> "core.items";
            case WAVE -> "wave";
            case GAME_OVER -> "game.over";
            case CHAT -> "chat";
            case KEYFRAME -> "keyframe";
            default -> "unknown(" + id + ")";
        };
    }
}
