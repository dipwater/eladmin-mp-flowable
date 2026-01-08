package me.zhengjie.modules.flowable.domain.dto;

import lombok.Data;

@Data
public class FlowableDefinitionDto {
    private String id;
    private String key;
    private String name;
    private int version;
    private String category;
    private String deploymentId;
    private String resourceName;
    private boolean suspended; // Derived from isSuspended()
}
