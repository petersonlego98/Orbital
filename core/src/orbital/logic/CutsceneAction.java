package orbital.logic;

public enum CutsceneAction{
    active,
    pan,
    zoom,
    stop,
    shake,
    getHud,
    setHud;

    public static final CutsceneAction[] all = values();
}
