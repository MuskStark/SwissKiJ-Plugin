package plugin.swisskit.hpl.service;

import fan.summer.api.log.LoggerFactory;
import fan.summer.api.log.PluginLogger;
import javafx.application.Platform;
import javafx.concurrent.Task;
import plugin.swisskit.hpl.dto.PeriodDataRU;
import plugin.swisskit.hpl.dto.UserSearchResp;

public class HappyLearningService {

    private static final HappyLearningService INSTANCE = new HappyLearningService();

    public static HappyLearningService getInstance() {
        return INSTANCE;
    }

    private static final PluginLogger log = LoggerFactory.getLogger(HappyLearningService.class);

    private final CourseQueryService queryService;
    private final CourseLearningService learningService;

    private Task<Void> currentTask;
    private String key;
    private String token;
    private int majorGoal;
    private int electiveGoal;
    private volatile String currentStatusKey = "plugin.hpl.idle";

    private HappyLearningService() {
        this.queryService = new CourseQueryService();
        this.learningService = new CourseLearningService(queryService);
    }

    public Long getCurrentLessonId() {
        return learningService.getCurrentLessonId();
    }

    public String getCurrentLessonName() {
        return learningService.getCurrentLessonName();
    }

    public Float getClassHours() {
        return learningService.getClassHours();
    }

    public UserSearchResp getPersonInfo(String cookie, String token) {
        return queryService.getPersonInfo(cookie, token);
    }

    public void setSkipSignal(boolean skipSignal) {
        learningService.setSkipSignal(skipSignal);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getToken() {
        return token;
    }

    public int getMajorGoal() {
        return majorGoal;
    }

    public int getElectiveGoal() {
        return electiveGoal;
    }

    public String getCurrentStatusKey() {
        return currentStatusKey;
    }

    public boolean isRunning() {
        return currentTask != null && !currentTask.isDone();
    }

    public void setGoals(PeriodDataRU period) {
        this.majorGoal = period.getGroupLearningGoal().intValue();
        this.electiveGoal = period.getSelfLearningGoal().intValue();
    }

    public void startLearning(String lessonType, String token) {
        this.token = token;
        this.currentStatusKey = "plugin.hpl.learning";

        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                learningService.autoLearning(lessonType, token, key);
                return null;
            }
        };

        currentTask.setOnSucceeded(e -> {
            currentStatusKey = "plugin.hpl.completed";
            log.info("[Service] Learning task completed successfully");
        });

        currentTask.setOnFailed(e -> {
            currentStatusKey = "plugin.hpl.error";
            Throwable ex = currentTask.getException();
            log.error("[Service] Learning task failed", ex);
        });

        currentTask.setOnCancelled(e -> {
            currentStatusKey = "plugin.hpl.stopped";
            log.info("[Service] Learning task cancelled by user");
        });

        Thread thread = new Thread(currentTask, "HappyLearning-Worker");
        thread.setDaemon(true);
        thread.start();

        log.info("[Service] Learning task started, lessonType: {}", lessonType);
    }

    public void stopLearning() {
        if (currentTask != null && !currentTask.isDone()) {
            log.info("[Service] Stopping learning task");
            currentTask.cancel(true);
        }
    }
}
