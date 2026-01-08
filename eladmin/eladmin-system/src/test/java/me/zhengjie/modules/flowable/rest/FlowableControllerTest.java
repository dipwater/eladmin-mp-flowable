package me.zhengjie.modules.flowable.rest;

import me.zhengjie.AppRun;
import me.zhengjie.modules.flowable.service.FlowableService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AppRun.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class FlowableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlowableService flowableService;

    @Test
    @WithMockUser(username = "admin")
    public void testStartProcess() throws Exception {
        Mockito.when(flowableService.startProcess(anyString(), anyString(), any())).thenReturn("1001");

        mockMvc.perform(post("/api/flowable/process/start")
                .param("processDefinitionKey", "testProcess")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin")
    public void testGetTasks() throws Exception {
        Mockito.when(flowableService.getMyTasks(0, 10)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/flowable/task")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk());
    }
}
