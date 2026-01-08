package me.zhengjie.modules.flowable.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.flowable.service.FlowableService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import java.util.Map;

/**
 * Flowable Controller
 *
 * @author Zheng Jie
 * @date 2024-01-04
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flowable")
@Api(tags = "工作流管理")
public class FlowableController {

    private final FlowableService flowableService;

    @ApiOperation("启动流程实例")
    @PostMapping("/process/start")
    public ResponseEntity<Object> startProcess(@RequestParam String processDefinitionKey,
            @RequestParam(required = false) String name,
            @RequestBody(required = false) Map<String, Object> variables) {
        String processInstanceId = flowableService.startProcess(processDefinitionKey, name, variables);
        return new ResponseEntity<>(processInstanceId, HttpStatus.CREATED);
    }

    @ApiOperation("获取当前用户的任务")
    @GetMapping("/task")
    public ResponseEntity<Object> getMyTasks(Pageable pageable) {
        // Simple pagination conversion (Spring Data Pageable to Flowable API)
        // Note: Pageable is 0-indexed
        return new ResponseEntity<>(flowableService.getMyTasks(pageable.getPageNumber(), pageable.getPageSize()),
                HttpStatus.OK);
    }

    @ApiOperation("获取我的已办")
    @GetMapping("/task/finished")
    public ResponseEntity<Object> getFinishedTasks(Pageable pageable) {
        String currentUsername = me.zhengjie.utils.SecurityUtils.getCurrentUsername();
        return new ResponseEntity<>(
                flowableService.getFinishedTasks(currentUsername, pageable.getPageNumber(), pageable.getPageSize()),
                HttpStatus.OK);
    }

    @ApiOperation("完成任务")
    @PostMapping("/task/complete")
    public ResponseEntity<Object> completeTask(@RequestParam String taskId,
            @RequestBody(required = false) Map<String, Object> variables) {
        flowableService.completeTask(taskId, variables);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @ApiOperation("拒绝任务")
    @PostMapping("/task/reject")
    public ResponseEntity<Object> rejectTask(@RequestBody Map<String, String> params) {
        String taskId = params.get("taskId");
        String comment = params.get("comment");
        flowableService.rejectTask(taskId, comment);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @ApiOperation("获取流程定义列表")
    @GetMapping("/definition")
    public ResponseEntity<Object> getProcessDefinitions(Pageable pageable) {
        // We'll wrap the list in a Page object if needed, or just return the list for
        // now
        // Ideally map to a DTO, but for simplicity returning Flowable's POJO (caution
        // with serialization)
        // To avoid serialization issues like with Tasks, it is better to map to DTOs or
        // use a converter.
        // For this step, we'll return raw list and see if FastJson handles
        // ProcessDefinition better than Task
        // If not, we will creat a DTO. Flowable ProcessDefinition is an interface,
        // usually cleaner than Task.
        return new ResponseEntity<>(
                flowableService.getProcessDefinitions(pageable.getPageNumber(), pageable.getPageSize()), HttpStatus.OK);
    }

    @ApiOperation("部署流程")
    @PostMapping(value = "/definition/deploy")
    public ResponseEntity<Object> deploy(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) String name) throws java.io.IOException {
        if (name == null) {
            name = file.getOriginalFilename();
        }
        flowableService.deployProcess(name, file.getOriginalFilename(), file.getInputStream());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @ApiOperation("激活或挂起流程定义")
    @PutMapping("/definition/{id}")
    public ResponseEntity<Object> updateState(@PathVariable String id, @RequestParam int state) {
        flowableService.updateDefinitionState(id, state);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @ApiOperation("删除部署")
    @DeleteMapping("/deployment/{id}")
    public ResponseEntity<Object> deleteDeployment(@PathVariable String id) {
        flowableService.deleteDeployment(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @ApiOperation("读取流程资源(XML)")
    @GetMapping("/definition/xml/{id}")
    public void readSystemXml(@PathVariable String id, javax.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        java.io.InputStream inputStream = flowableService.getProcessDefinitionXml(id);
        if (inputStream == null) {
            throw new RuntimeException("Resource not found");
        }

        response.setContentType("text/xml");
        org.apache.commons.io.IOUtils.copy(inputStream, response.getOutputStream());
    }

    @ApiOperation("读取流程资源(图片)")
    @GetMapping("/definition/image/{id}")
    public void readResource(@PathVariable String id, javax.servlet.http.HttpServletResponse response)
            throws Exception {
        java.io.InputStream inputStream = flowableService.getProcessDefinitionImage(id);
        if (inputStream == null) {
            throw new RuntimeException("Image not found");
        }

        response.setContentType("image/png");
        org.apache.commons.io.IOUtils.copy(inputStream, response.getOutputStream());
    }

    @ApiOperation("获取流程实例追踪图")
    @GetMapping("/instance/image/{id}")
    public void readInstanceResource(@PathVariable String id, javax.servlet.http.HttpServletResponse response)
            throws Exception {
        java.io.InputStream inputStream = flowableService.generateProcessInstanceImage(id);
        if (inputStream == null) {
            throw new RuntimeException("Image not found");
        }

        response.setContentType("image/png");
        org.apache.commons.io.IOUtils.copy(inputStream, response.getOutputStream());
    }

    @ApiOperation("获取流程变量")
    @GetMapping("/instance/variables/{processInstanceId}")
    public ResponseEntity<Object> getProcessVariables(@PathVariable String processInstanceId) {
        return new ResponseEntity<>(flowableService.getProcessVariables(processInstanceId), HttpStatus.OK);
    }

    @ApiOperation("获取流程实例历史节点")
    @GetMapping("/instance/history/{id}")
    public ResponseEntity<Object> getHistory(@PathVariable String id) {
        return new ResponseEntity<>(flowableService.getFlowableHistory(id), HttpStatus.OK);
    }

    @ApiOperation("获取流程实例列表")
    @GetMapping("/instance")
    public ResponseEntity<Object> getProcessInstances(Pageable pageable) {
        return new ResponseEntity<>(
                flowableService.getProcessInstances(pageable.getPageNumber(), pageable.getPageSize()), HttpStatus.OK);
    }

    @ApiOperation("删除流程实例")
    @DeleteMapping("/instance/{id}")
    public ResponseEntity<Object> deleteProcessInstance(@PathVariable String id,
            @RequestParam(required = false) String deleteReason) {
        flowableService.deleteProcessInstance(id, deleteReason);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @ApiOperation("激活或挂起流程实例")
    @PutMapping("/instance/{id}")
    public ResponseEntity<Object> updateInstanceState(@PathVariable String id, @RequestParam int state) {
        flowableService.updateInstanceState(id, state);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // --- Model Management ---

    @ApiOperation("获取流程模型列表")
    @GetMapping("/model")
    public ResponseEntity<Object> getModels(Pageable pageable) {
        return new ResponseEntity<>(flowableService.getModels(pageable.getPageNumber(), pageable.getPageSize()),
                HttpStatus.OK);
    }

    @ApiOperation("保存流程模型")
    @PostMapping("/model")
    public ResponseEntity<Object> saveModel(@RequestBody Map<String, String> data) {
        String name = data.get("name");
        String key = data.get("key");
        String xml = data.get("xml");
        return new ResponseEntity<>(flowableService.saveModel(name, key, xml), HttpStatus.OK);
    }

    @ApiOperation("部署流程模型")
    @PostMapping("/model/deploy/{id}")
    public ResponseEntity<Object> deployModel(@PathVariable String id) {
        flowableService.deployModel(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("删除流程模型")
    @DeleteMapping("/model/{id}")
    public ResponseEntity<Object> deleteModel(@PathVariable String id) {
        flowableService.deleteModel(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("读取模型XML")
    @GetMapping("/model/xml/{id}")
    public void getModelXml(@PathVariable String id, javax.servlet.http.HttpServletResponse response)
            throws IOException {
        String xml = flowableService.getModelXml(id);
        if (xml == null) {
            throw new RuntimeException("Model XML not found");
        }
        response.setContentType("text/xml;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(xml);
    }
}
