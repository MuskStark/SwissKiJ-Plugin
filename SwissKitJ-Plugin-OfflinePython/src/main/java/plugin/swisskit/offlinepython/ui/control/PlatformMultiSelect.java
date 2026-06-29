package plugin.swisskit.offlinepython.ui.control;

import fan.summer.api.MdiIconUtil;
import javafx.scene.control.MenuButton;
import javafx.scene.control.CheckMenuItem;
import plugin.swisskit.offlinepython.domain.PlatformCatalog;
import plugin.swisskit.offlinepython.ui.OpbStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-select target-platform dropdown: a MenuButton of CheckMenuItems backed by a plain
 * selection list. Selection bookkeeping (summary, at-least-one guard) is delegated to
 * {@link PlatformCatalog}; this class is the JavaFX wiring only.
 */
public class PlatformMultiSelect extends MenuButton {

    private final List<String> selected = new ArrayList<>(List.of("win_amd64"));
    private boolean updating = false;
    private Consumer<List<String>> onChange;

    public PlatformMultiSelect() {
        super(PlatformCatalog.summary(List.of("win_amd64")));
        setStyle("-fx-background-color: " + OpbStyle.GLASS_BG + "; -fx-text-fill: " + OpbStyle.TEXT_PRIMARY
                + "; -fx-border-color: " + OpbStyle.GLASS_BORDER + "; -fx-background-radius: 8; -fx-cursor: hand;");
        rebuildMenu();
    }

    private void rebuildMenu() {
        getItems().clear();
        for (PlatformCatalog.Entry e : PlatformCatalog.ALL) {
            CheckMenuItem mi = new CheckMenuItem(PlatformCatalog.labelOf(e.tag()) + "  (" + e.tag() + ")");
            mi.setGraphic(MdiIconUtil.createIcon(PlatformCatalog.iconOf(e.tag()), 14, OpbStyle.TEXT_PRIMARY));
            mi.setSelected(selected.contains(e.tag()));
            mi.selectedProperty().addListener((o, ov, nv) -> {
                if (updating) return;
                updating = true;
                try {
                    List<String> next = PlatformCatalog.toggle(selected, e.tag());
                    if (next.equals(selected)) { mi.setSelected(true); return; } // refused: keep at least one
                    selected.clear();
                    selected.addAll(next);
                    setText(PlatformCatalog.summary(selected));
                    if (onChange != null) onChange.accept(getSelected());
                } finally {
                    updating = false;
                }
            });
            getItems().add(mi);
        }
    }

    /** Current selection (defensive copy). */
    public List<String> getSelected() { return List.copyOf(selected); }

    /** Primary platform (first selected) used for size estimates; never empty. */
    public String primaryPlatform() { return selected.isEmpty() ? "win_amd64" : selected.get(0); }

    /** Compact summary text (delegates to PlatformCatalog). */
    public String summary() { return PlatformCatalog.summary(selected); }

    /** Replace the selection (at least one platform enforced). */
    public void setSelected(List<String> sel) {
        selected.clear();
        if (sel == null || sel.isEmpty()) selected.add("win_amd64");
        else selected.addAll(sel);
        setText(PlatformCatalog.summary(selected));
        rebuildMenu();
        if (onChange != null) onChange.accept(getSelected());
    }

    /** Notified after each successful selection change. */
    public void setOnChange(Consumer<List<String>> cb) { this.onChange = cb; }
}
