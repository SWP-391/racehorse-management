package vn.racehorse.deploy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the built web client from the same origin as the API (TECH-107): hashed files cached for a year,
 * {@code index.html} always revalidated, and {@code index.html} returned for client-side routes.
 * Active only when a client is bundled, so local development and tests are unchanged.
 */
@Configuration
@Conditional(FrontendBundled.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FrontendConfig implements WebMvcConfigurer {

    private final String location;

    public FrontendConfig(@Value("${" + FrontendBundled.LOCATION_PROPERTY + ":" + FrontendBundled.DEFAULT_LOCATION + "}") String location) {
        this.location = FrontendBundled.withSlash(location);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/docs").setViewName("forward:/docs/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/models/**")
                .addResourceLocations(location + "models/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        registry.addResourceHandler("/docs/**")
                .addResourceLocations(location + "docs/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    /** A missing file without an extension, outside the API, is a client route: answer with index.html. */
    static final class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource found = super.getResource(resourcePath, location);
            if (found != null) {
                return found;
            }
            if (isApiPath(resourcePath) || hasExtension(resourcePath)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        static boolean isApiPath(String path) {
            String p = path.startsWith("/") ? path.substring(1) : path;
            return p.equals("api") || p.startsWith("api/") || p.equals("actuator") || p.startsWith("actuator/");
        }

        static boolean hasExtension(String path) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            return last.contains(".");
        }
    }
}
