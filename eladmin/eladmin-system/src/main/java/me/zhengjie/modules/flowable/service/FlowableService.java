package me.zhengjie.modules.flowable.service;

import lombok.RequiredArgsConstructor;
import me.zhengjie.utils.SecurityUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flowable Service
 *
 * @author Zheng Jie
 * @date 2024-01-04
 */
@Service
@RequiredArgsConstructor
public class FlowableService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final org.flowable.engine.HistoryService historyService;

    private final org.flowable.engine.RepositoryService repositoryService;
    private final org.flowable.engine.ProcessEngine processEngine;
    private final org.flowable.engine.IdentityService identityService;
    private final me.zhengjie.modules.system.mapper.UserMapper userMapper;

    /**
     * Start a process instance
     *
     * @param processDefinitionKey Process Definition Key
     * @param variables            Process Variables
     * @return ProcessInstanceId
     */
    @Transactional(rollbackFor = Exception.class)
    public String startProcess(String processDefinitionKey, String name, Map<String, Object> variables) {
        String currentUsername = me.zhengjie.utils.SecurityUtils.getCurrentUsername();
        identityService.setAuthenticatedUserId(currentUsername);

        // Name is optional, but helps in UI
        org.flowable.engine.runtime.ProcessInstanceBuilder builder = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(processDefinitionKey)
                .name(name)
                .variable("initiator", currentUsername);

        if (variables != null) {
            builder.variables(variables);
        }

        org.flowable.engine.runtime.ProcessInstance processInstance = builder.start();

        // Auto-complete the first task if the assignee is the initiator (common
        // scenario)
        // Or if we just want to skip the "Draft" phase.
        // For simple workflows, the first task is often "Fill Application", which is
        // already done.
        org.flowable.task.api.Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId())
                .singleResult();
        if (task != null && currentUsername.equals(task.getAssignee())) {
            taskService.addComment(task.getId(), processInstance.getId(), "提交申请"); // Add default comment
            taskService.complete(task.getId(), variables);
        }

        return processInstance.getId();
    }

    /**
     * Get tasks for a specific user
     *
     * @param assignee User ID (username)
     * @param page     Page number (0-indexed)
     * @param size     Page size
     * @return List of FlowableTaskDto
     */
    public List<me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto> getTasks(String assignee, int page, int size) {
        // Fetch User ID and Jobs (Groups)
        me.zhengjie.modules.system.domain.User user = userMapper.findByUsername(assignee);
        List<String> candidateGroups = new java.util.ArrayList<>();
        if (user != null) {
            Set<Long> jobIds = userMapper.selectJobIdsByUserId(user.getId());
            if (jobIds != null && !jobIds.isEmpty()) {
                for (Long jobId : jobIds) {
                    candidateGroups.add(String.valueOf(jobId)); // Assuming GroupId stored in Flowable is JobId string
                }
            }
        }

        TaskQuery query = taskService.createTaskQuery()
                .active()
                .orderByTaskCreateTime().desc();

        if (candidateGroups.isEmpty()) {
            query.taskCandidateOrAssigned(assignee);
        } else {
            query.or()
                    .taskCandidateOrAssigned(assignee)
                    .taskCandidateGroupIn(candidateGroups)
                    .endOr();
        }

        List<org.flowable.task.api.Task> tasks = query.listPage(page * size, size);

        return tasks.stream().map(task -> {
            me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto dto = new me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto();
            dto.setId(task.getId());
            dto.setName(task.getName());
            dto.setAssignee(task.getAssignee());
            dto.setAssigneeName(getNickName(task.getAssignee())); // Nickname
            dto.setCreateTime(task.getCreateTime());
            dto.setProcessInstanceId(task.getProcessInstanceId());
            dto.setProcessDefinitionId(task.getProcessDefinitionId());

            // Fetch Process Instance Name & Initiator(StartUserId) for Nickname
            // We need to fetch ProcessInstance (Runtime) or HistoricProcessInstance (if for
            // some reason it's gone from runtime but task exists?)
            // Tasks in getTasks are active, so Runtime ProcessInstance should exist.
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (processInstance != null) {
                dto.setProcessName(processInstance.getName());
                dto.setStartUserId(processInstance.getStartUserId());
                dto.setStartUserName(getNickName(processInstance.getStartUserId())); // Initiator Nickname
            }
            // Active task implies process is active (unless suspended, but we can call it
            // 'Processing')
            dto.setProcessStatus("进行中");
            return dto;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get finished tasks for specific user
     */
    public List<me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto> getFinishedTasks(String assignee, int page,
            int size) {
        org.flowable.task.api.history.HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(assignee)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc();

        List<org.flowable.task.api.history.HistoricTaskInstance> tasks = query.listPage(page * size, size);

        return tasks.stream().map(task -> {
            me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto dto = new me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto();
            dto.setId(task.getId());
            dto.setName(task.getName());
            dto.setAssignee(task.getAssignee());
            dto.setAssigneeName(getNickName(task.getAssignee())); // Nickname
            dto.setCreateTime(task.getCreateTime());
            dto.setEndTime(task.getEndTime());
            dto.setDuration(task.getDurationInMillis());
            dto.setProcessInstanceId(task.getProcessInstanceId());
            dto.setProcessDefinitionId(task.getProcessDefinitionId());

            // Populate Process Name from History
            org.flowable.engine.history.HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (pi != null) {
                dto.setProcessName(pi.getName());
                dto.setStartUserId(pi.getStartUserId());
                dto.setStartUserName(getNickName(pi.getStartUserId())); // Initiator Nickname

                // Determine Process Status
                if (pi.getEndTime() != null) {
                    if (pi.getDeleteReason() != null &&
                            (pi.getDeleteReason().contains("不同意") || pi.getDeleteReason().contains("reject"))) {
                        dto.setProcessStatus("已拒绝");
                    } else {
                        dto.setProcessStatus("已结束");
                    }
                } else {
                    dto.setProcessStatus("进行中");
                }
            }

            return dto;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get tasks for current user
     */
    public List<me.zhengjie.modules.flowable.domain.dto.FlowableTaskDto> getMyTasks(int page, int size) {
        return getTasks(SecurityUtils.getCurrentUsername(), page, size);
    }

    /**
     * Complete a task
     *
     * @param taskId    Task ID
     * @param variables Variables to update
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, Map<String, Object> variables) {
        org.flowable.task.api.Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null) {
            // Claim task if not assigned (e.g. candidate group task)
            if (task.getAssignee() == null) {
                try {
                    taskService.claim(taskId, SecurityUtils.getCurrentUsername());
                } catch (Exception e) {
                    // Ignore if already claimed or other issue, complete might still work if user
                    // is candidate
                    // But claiming ensures it shows in history
                }
            }

            if (variables != null && variables.containsKey("comment")) {
                String comment = (String) variables.get("comment");
                taskService.addComment(taskId, task.getProcessInstanceId(), comment);
            }
            taskService.complete(taskId, variables);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(String taskId, String comment) {
        org.flowable.task.api.Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null) {
            // Claim task if not assigned (e.g. candidate group task) to ensure it shows in
            // history
            if (task.getAssignee() == null) {
                try {
                    taskService.claim(taskId, SecurityUtils.getCurrentUsername());
                } catch (Exception e) {
                    // Ignore
                }
            }

            if (comment != null) {
                taskService.addComment(taskId, task.getProcessInstanceId(), comment);
            }
            // Delete process instance with reason. This makes it 'completed' (ended) but
            // with a specific delete reason.
            runtimeService.deleteProcessInstance(task.getProcessInstanceId(),
                    "不同意: " + (comment != null ? comment : ""));
        }
    }

    /**
     * Get process definitions
     * 
     * @param page page number
     * @param size page size
     * @return List of FlowableDefinitionDto
     */
    public List<me.zhengjie.modules.flowable.domain.dto.FlowableDefinitionDto> getProcessDefinitions(int page,
            int size) {
        List<org.flowable.engine.repository.ProcessDefinition> definitions = repositoryService
                .createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc()
                .listPage(page * size, size);

        return definitions.stream().map(def -> {
            me.zhengjie.modules.flowable.domain.dto.FlowableDefinitionDto dto = new me.zhengjie.modules.flowable.domain.dto.FlowableDefinitionDto();
            dto.setId(def.getId());
            dto.setKey(def.getKey());
            dto.setName(def.getName());
            dto.setVersion(def.getVersion());
            dto.setCategory(def.getCategory());
            dto.setDeploymentId(def.getDeploymentId());
            dto.setResourceName(def.getResourceName());
            dto.setSuspended(def.isSuspended());
            return dto;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Deploy process definition
     *
     * @param name         Deployment Name
     * @param resourceName Resource Name (e.g. process.bpmn)
     * @param inputStream  File Input Stream
     */
    @Transactional(rollbackFor = Exception.class)
    public void deployProcess(String name, String resourceName, java.io.InputStream inputStream) {
        repositoryService.createDeployment()
                .name(name)
                .addInputStream(resourceName, inputStream)
                .deploy();
    }

    /**
     * Update process definition state
     * 
     * @param id    Process Definition ID
     * @param state 1: Activate, 2: Suspend
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDefinitionState(String id, int state) {
        if (state == 1) {
            repositoryService.activateProcessDefinitionById(id, true, null);
        } else if (state == 2) {
            repositoryService.suspendProcessDefinitionById(id, true, null);
        }
    }

    /**
     * Get process instances
     * 
     * @param page page number
     * @param size page size
     * @return List of FlowableInstanceDto
     */
    private String getNickName(String username) {
        if (username == null) {
            return null;
        }
        me.zhengjie.modules.system.domain.User user = userMapper.findByUsername(username);
        return user != null ? user.getNickName() : username;
    }

    public List<me.zhengjie.modules.flowable.domain.dto.FlowableInstanceDto> getProcessInstances(int page, int size) {
        List<org.flowable.engine.history.HistoricProcessInstance> instances = historyService
                .createHistoricProcessInstanceQuery()
                .orderByProcessInstanceStartTime().desc()
                .listPage(page * size, size);

        return instances.stream().map(inst -> {
            me.zhengjie.modules.flowable.domain.dto.FlowableInstanceDto dto = new me.zhengjie.modules.flowable.domain.dto.FlowableInstanceDto();
            dto.setId(inst.getId());
            dto.setName(inst.getName());
            dto.setProcessDefinitionId(inst.getProcessDefinitionId());
            dto.setProcessDefinitionKey(inst.getProcessDefinitionKey());
            dto.setProcessDefinitionName(inst.getProcessDefinitionName());
            dto.setProcessDefinitionVersion(inst.getProcessDefinitionVersion());
            dto.setStartUserId(inst.getStartUserId());
            dto.setStartUserName(getNickName(inst.getStartUserId())); // Populate Nickname
            dto.setStartTime(inst.getStartTime());
            dto.setEndTime(inst.getEndTime());

            if (inst.getEndTime() == null) {
                ProcessInstance runtimeInst = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(inst.getId()).singleResult();
                if (runtimeInst != null) {
                    dto.setSuspended(runtimeInst.isSuspended());
                }
            } else {
                dto.setSuspended(false);
            }

            dto.setBusinessKey(inst.getBusinessKey());
            return dto;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Delete Process Instance
     * 
     * @param instanceId   Instance ID
     * @param deleteReason Reason
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcessInstance(String instanceId, String deleteReason) {
        // Try to delete runtime instance if it exists
        try {
            runtimeService.deleteProcessInstance(instanceId, deleteReason);
        } catch (org.flowable.common.engine.api.FlowableObjectNotFoundException e) {
            // Instance might be already finished or not found in runtime
        }
        // Always try to delete history
        historyService.deleteHistoricProcessInstance(instanceId);
    }

    /**
     * Update Process Instance State
     * 
     * @param instanceId Instance ID
     * @param state      1: Activate, 2: Suspend
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateInstanceState(String instanceId, int state) {
        if (state == 1) {
            runtimeService.activateProcessInstanceById(instanceId);
        } else if (state == 2) {
            runtimeService.suspendProcessInstanceById(instanceId);
        }
    }

    /**
     * Delete Deployment
     * ·
     * 
     * @param deploymentId Deployment ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeployment(String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    /**
     * Get Process Diagram
     *
     * @param id Process Definition ID
     * @return InputStream of image
     */
    public java.io.InputStream getProcessDefinitionImage(String id) {
        return repositoryService.getProcessDiagram(id);
    }

    /**
     * Get Process XML
     *
     * @param id Process Definition ID
     * @return InputStream of XML
     */
    public java.io.InputStream getProcessDefinitionXml(String id) {
        org.flowable.engine.repository.ProcessDefinition def = repositoryService.getProcessDefinition(id);
        return repositoryService.getResourceAsStream(def.getDeploymentId(), def.getResourceName());
    }

    /**
     * Generate Process Instance Image with Highlighting
     */
    public java.io.InputStream generateProcessInstanceImage(String processInstanceId) {
        // 1. Get process instance
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (processInstance == null) {
            return null;
        }

        // 2. Get process definition
        org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService
                .getBpmnModel(processInstance.getProcessDefinitionId());

        // 3. Get active activity IDs
        List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);

        // 4. Generate diagram
        org.flowable.image.ProcessDiagramGenerator diagramGenerator = processEngine.getProcessEngineConfiguration()
                .getProcessDiagramGenerator();

        // Use default fonts to avoid font missing issues on Linux
        return diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                activeActivityIds,
                java.util.Collections.<String>emptyList(),
                "宋体",
                "宋体",
                "宋体",
                null,
                1.0,
                true);
    }

    // --- Model Management ---

    public Object getModels(int page, int size) {
        List<org.flowable.engine.repository.Model> models = repositoryService.createModelQuery()
                .orderByLastUpdateTime().desc()
                .listPage(page * size, size);
        long total = repositoryService.createModelQuery().count();

        List<Map<String, Object>> list = models.stream().map(m -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getName());
            map.put("key", m.getKey());
            map.put("version", m.getVersion());
            map.put("createTime", m.getCreateTime());
            map.put("lastUpdateTime", m.getLastUpdateTime());
            map.put("metaInfo", m.getMetaInfo());
            map.put("deploymentId", m.getDeploymentId());
            return map;
        }).collect(java.util.stream.Collectors.toList());

        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("content", list);
        ret.put("totalElements", total);
        return ret;
    }

    @Transactional(rollbackFor = Exception.class)
    public String saveModel(String name, String key, String xml) {
        org.flowable.engine.repository.Model model = repositoryService.createModelQuery().modelKey(key).latestVersion()
                .singleResult();
        if (model == null) {
            model = repositoryService.newModel();
            model.setKey(key);
            model.setVersion(1);
        } else {
            // Update content?
            // If previously published, this save makes it a "Draft" again?
            // Yes, user requested "Saved is unpublished".
            // So we clear deploymentId.
            model.setDeploymentId(null);
        }
        model.setName(name);

        repositoryService.saveModel(model);
        repositoryService.addModelEditorSource(model.getId(), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return model.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deployModel(String modelId) {
        org.flowable.engine.repository.Model model = repositoryService.getModel(modelId);
        byte[] bytes = repositoryService.getModelEditorSource(model.getId());
        if (bytes == null) {
            throw new RuntimeException("模型数据为空，请先设计流程");
        }

        String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        org.flowable.engine.repository.Deployment deployment = repositoryService.createDeployment()
                .name(model.getName())
                .addString(model.getName() + ".bpmn20.xml", xml)
                .deploy();

        // Update Model with Deployment ID -> Published
        model.setDeploymentId(deployment.getId());
        repositoryService.saveModel(model);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String id) {
        repositoryService.deleteModel(id);
    }

    public String getModelXml(String id) {
        byte[] bytes = repositoryService.getModelEditorSource(id);
        if (bytes == null)
            return null;
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Get process history for tracking
     */
    public List<Map<String, Object>> getFlowableHistory(String processInstanceId) {
        List<org.flowable.engine.history.HistoricActivityInstance> activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        return activities.stream().map(a -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("activityId", a.getActivityId());
            map.put("activityName", a.getActivityName());
            map.put("activityType", a.getActivityType());
            map.put("assignee", a.getAssignee());

            // Populate Nickname
            // Populate Nickname or Candidate Info
            if (a.getAssignee() != null) {
                me.zhengjie.modules.system.domain.User user = userMapper.findByUsername(a.getAssignee());
                if (user != null) {
                    map.put("assigneeName", user.getNickName());
                } else {
                    map.put("assigneeName", a.getAssignee());
                }
            } else if (a.getTaskId() != null) {
                // If assignee is null, check for candidate users/groups
                // Use historyService for better compatibility with finished/deleted tasks
                List<org.flowable.identitylink.api.history.HistoricIdentityLink> links = historyService
                        .getHistoricIdentityLinksForTask(a.getTaskId());
                if (links != null && !links.isEmpty()) {
                    List<String> candidates = new java.util.ArrayList<>();
                    for (org.flowable.identitylink.api.history.HistoricIdentityLink link : links) {
                        if ("candidate".equals(link.getType())) {
                            if (link.getUserId() != null) {
                                me.zhengjie.modules.system.domain.User user = userMapper
                                        .findByUsername(link.getUserId());
                                candidates.add(user != null ? user.getNickName() : link.getUserId());
                            } else if (link.getGroupId() != null) {
                                // Assuming GroupId is JobId
                                try {
                                    Long jobId = Long.parseLong(link.getGroupId());
                                    List<me.zhengjie.modules.system.domain.User> users = userMapper.findByJobId(jobId);
                                    if (users != null && !users.isEmpty()) {
                                        for (me.zhengjie.modules.system.domain.User u : users) {
                                            candidates.add(u.getNickName());
                                        }
                                    } else {
                                        candidates.add("岗位ID:" + link.getGroupId());
                                    }
                                } catch (NumberFormatException e) {
                                    candidates.add("岗位:" + link.getGroupId());
                                }
                            }
                        }
                    }
                    if (!candidates.isEmpty()) {
                        map.put("assigneeName", String.join(", ", candidates));
                    }
                }
            }

            // Populate Comments
            if (a.getTaskId() != null) {
                List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(a.getTaskId());
                if (comments != null && !comments.isEmpty()) {
                    map.put("comments", comments.stream().map(org.flowable.engine.task.Comment::getFullMessage)
                            .collect(java.util.stream.Collectors.toList()));
                }
            }

            map.put("startTime", a.getStartTime());
            map.put("endTime", a.getEndTime());
            map.put("duration", a.getDurationInMillis());
            map.put("deleteReason", a.getDeleteReason()); // Add deleteReason
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        List<org.flowable.variable.api.history.HistoricVariableInstance> variables = historyService
                .createHistoricVariableInstanceQuery().processInstanceId(processInstanceId).list();
        Map<String, Object> map = new java.util.HashMap<>();
        for (org.flowable.variable.api.history.HistoricVariableInstance v : variables) {
            map.put(v.getVariableName(), v.getValue());
        }
        return map;
    }
}