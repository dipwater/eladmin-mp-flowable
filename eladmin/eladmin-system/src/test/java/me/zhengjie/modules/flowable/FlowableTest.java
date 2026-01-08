package me.zhengjie.modules.flowable;

import me.zhengjie.AppRun;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = AppRun.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FlowableTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    public void contextLoads() {
        assertNotNull(processEngine);
        assertNotNull(repositoryService);
        assertNotNull(runtimeService);
        assertNotNull(taskService);
        System.out.println("Flowable Engine started successfully: " + processEngine.getName());
    }
}
