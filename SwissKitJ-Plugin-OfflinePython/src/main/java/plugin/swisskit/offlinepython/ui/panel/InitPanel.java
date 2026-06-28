package plugin.swisskit.offlinepython.ui.panel;

import fan.summer.api.component.UiUtils;
import fan.summer.api.i18n.I18n;
import plugin.swisskit.offlinepython.ui.LogConsole;
import plugin.swisskit.offlinepython.ui.ProjectContext;

public class InitPanel extends CommandPanel {
    public InitPanel(LogConsole log, ProjectContext project) {
        super(log, project);
        getChildren().add(titleNode());
        getChildren().add(UiUtils.subLabel("在顶栏点「新建」创建项目，或「打开」现有项目目录。"));
        var note = UiUtils.subLabel("init 会在项目目录生成 config.json、requirements.txt、README.md。");
        getChildren().add(note);
    }
    @Override public String title() { return I18n.get("opb.init.title"); }
}
