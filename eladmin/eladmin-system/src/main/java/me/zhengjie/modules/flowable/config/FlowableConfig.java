package me.zhengjie.modules.flowable.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Configuration;

/**
 * Flowable Config
 *
 * @author Zheng Jie
 * @date 2024-01-04
 */
@Configuration
public class FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    @Override
    public void configure(SpringProcessEngineConfiguration engineConfiguration) {
        engineConfiguration.setActivityFontName("wqy-microhei");
        engineConfiguration.setLabelFontName("wqy-microhei");
        engineConfiguration.setAnnotationFontName("wqy-microhei");
    }
}
