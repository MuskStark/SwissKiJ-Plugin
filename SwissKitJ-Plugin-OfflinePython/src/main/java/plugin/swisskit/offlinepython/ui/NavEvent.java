package plugin.swisskit.offlinepython.ui;

import javafx.event.Event;
import javafx.event.EventType;

/** Fired by panels (e.g. DepsPanel "保存并去构建") to request the shell switch nav. */
public class NavEvent extends Event {
    public static final EventType<NavEvent> NAV = new EventType<>(Event.ANY, "OPB_NAV");
    private final String target;
    public NavEvent(String target) { super(NAV); this.target = target; }
    public String target() { return target; }
}
