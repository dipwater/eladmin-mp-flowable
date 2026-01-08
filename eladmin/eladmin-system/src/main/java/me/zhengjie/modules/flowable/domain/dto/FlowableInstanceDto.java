package me.zhengjie.modules.flowable.domain.dto;

import lombok.Data;
import java.util.Date;

@Data
// DTO for Flowable Instance
public class FlowableInstanceDto {
    private String id;
    private String name;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private String startUserId;
    private Date startTime;
    private Date endTime; // For history if we query history later
    private boolean suspended;
    private String businessKey;
    private String startUserName;
}
