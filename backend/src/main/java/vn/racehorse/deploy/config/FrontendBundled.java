package vn.racehorse.deploy.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True when the built web client is inside the service (the Docker image), false in local development and tests. */
public class FrontendBundled implements Condition {

    static final String LOCATION_PROPERTY = "app.web.frontend-location";
    static final String DEFAULT_LOCATION = "classpath:/static/";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String location = context.getEnvironment().getProperty(LOCATION_PROPERTY, DEFAULT_LOCATION);
        return new DefaultResourceLoader(context.getClassLoader()).getResource(withSlash(location) + "index.html").exists();
    }

    static String withSlash(String location) {
        return location.endsWith("/") ? location : location + "/";
    }
}
