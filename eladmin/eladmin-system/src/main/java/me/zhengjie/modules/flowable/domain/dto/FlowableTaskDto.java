package me.zhengjie.modules.flowable.domain.dto;

import lombok.Data;
import java.util.Date;

@Data
// DTO for Flowable Task
public class FlowableTaskDto {
    private String id;
    private String name;
    private String assignee;
    private Date createTime;
    private String processInstanceId;
    private String processDefinitionId;
    private String processName;
    private Date endTime;
    private Long duration;
    private String assigneeName;
    private String startUserId;
    private String startUserName;
    private String processStatus;
}
