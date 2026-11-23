package com.almaraama.app.web;

import com.almaraama.membership.api.PerimeterApi;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC settings of the application: the {@code Perimeter} controller parameter. */
@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

    private final PerimeterApi perimeters;

    /**
     * @param perimeters resolved lazily, because the membership module's beans are created after the web configuration
     */
    WebConfiguration(@Lazy PerimeterApi perimeters) {
        this.perimeters = perimeters;
    }

    /** Lets controllers declare a {@link com.almaraama.shared.perimeter.Perimeter} parameter (CF-11). */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PerimeterArgumentResolver(perimeters));
    }
}
