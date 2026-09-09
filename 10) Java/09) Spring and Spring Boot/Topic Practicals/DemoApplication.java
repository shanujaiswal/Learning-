/**
 * DemoApplication.java
 *
 * Illustrates:
 *     1. A @SpringBootApplication entry-point class (the meta-annotation combo:
 *        @Configuration + @EnableAutoConfiguration + @ComponentScan)
 *     2. A @Component / @RestController picked up automatically by component
 *        scanning, with @Value property injection
 *     3. A @Configuration class showing conditional bean registration via
 *        @ConditionalOnProperty
 *     4. Example application.yml content (as a comment block) that the
 *        @Value / @ConditionalOnProperty usage below corresponds to
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/02 Spring Boot Fundamentals.md
 *
 * IMPORTANT -- THIS FILE DOES NOT COMPILE OR RUN ON ITS OWN.
 *     It is illustrative only. To actually run this code you need a real
 *     Spring Boot project (generated via https://start.spring.io / Spring
 *     Initializr) with, at minimum, the "spring-boot-starter-web" dependency
 *     on the classpath (plus a Spring Boot parent/BOM providing versions --
 *     see the Theory chapter's "Starters" section). Copy/paste and adapt the
 *     classes below into such a project's src/main/java tree (each class
 *     would normally live in its own .java file matching its class name).
 *
 * Run (once pasted into a real Spring Boot Maven project):
 *     mvn spring-boot:run
 *
 *     -- or, after packaging --
 *     mvn clean package
 *     java -jar target/demo-app-0.0.1-SNAPSHOT.jar
 *
 *     -- or override config at launch, without touching any file --
 *     mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090 --greeting.enabled=false"
 */

// ============================================================================
// Example application.yml (would live at src/main/resources/application.yml
// in the real project) -- shown here as a comment block since this is a
// single illustrative .java file, not a real project tree.
// ============================================================================
/*
server:
  port: 8081

spring:
  application:
    name: demo-app

# Custom property read via @Value("${greeting.message}") in GreetingComponent below
greeting:
  message: "Hello from application.yml!"
  enabled: true          # drives the @ConditionalOnProperty example in AppConfig

logging:
  level:
    root: INFO
    com.example.demo: DEBUG
*/


package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


// =============================================================================
// 1) THE ENTRY POINT -- @SpringBootApplication
// =============================================================================
// @SpringBootApplication is shorthand for stacking THREE annotations:
//   @Configuration          -- this class may itself declare @Bean methods
//   @EnableAutoConfiguration -- auto-register beans based on the classpath
//                                (e.g. spring-boot-starter-web on the classpath
//                                triggers DispatcherServletAutoConfiguration,
//                                an embedded Tomcat, Jackson JSON converters...)
//   @ComponentScan           -- scan THIS package and sub-packages for
//                                @Component/@Service/@Repository/@RestController
//
// Placing this class at the ROOT package (com.example.demo) means everything
// below it (com.example.demo.*) gets picked up automatically -- move it to a
// sibling/deeper package by mistake and component scanning silently misses
// classes outside its subtree (see Theory file's Gotchas section).
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        // SpringApplication.run() bootstraps the ApplicationContext, triggers
        // auto-configuration, starts the embedded server (Tomcat by default
        // when spring-boot-starter-web is present), and blocks the main
        // thread keeping the app alive.
        SpringApplication.run(DemoApplication.class, args);
    }
}


// =============================================================================
// 2) A COMPONENT PICKED UP BY COMPONENT SCANNING, USING @Value INJECTION
// =============================================================================
// Because this class is annotated @Component and lives in a package at or
// below com.example.demo (the root package above), @ComponentScan finds it
// automatically -- no manual registration needed anywhere.
//
// @Value("${greeting.message}") pulls the value of the "greeting.message"
// key straight out of application.yml (or application.properties, or an
// environment variable / command-line override -- see the property
// precedence order in the Theory file). A typo here (e.g. "greting.message")
// would NOT fail startup -- it would just fail to resolve unless a default
// is supplied, which is exactly the "silent property typo" gotcha.
@Component
class GreetingComponent {

    // ":default value" syntax supplies a fallback if the property is absent,
    // which also protects against the exact typo gotcha described above.
    @Value("${greeting.message:Hello, default greeting!}")
    private String message;

    public String getMessage() {
        return message;
    }
}


// A @RestController is a specialization of @Component (so it's ALSO found by
// component scanning) that additionally tells Spring MVC "serialize return
// values directly to the HTTP response body" (JSON, via the Jackson
// converter that spring-boot-starter-web auto-configured) instead of
// resolving a view template.
@RestController
class GreetingController {

    private final GreetingComponent greetingComponent;

    // Constructor injection -- Spring sees the single constructor and
    // auto-wires GreetingComponent from the ApplicationContext, no
    // @Autowired annotation required on a single-constructor class
    // (as of Spring 4.3+).
    GreetingController(GreetingComponent greetingComponent) {
        this.greetingComponent = greetingComponent;
    }

    @GetMapping("/greeting")
    public String greeting() {
        return greetingComponent.getMessage();
    }
}


// =============================================================================
// 3) A @Configuration CLASS DEMONSTRATING CONDITIONAL BEAN REGISTRATION
// =============================================================================
// This mirrors exactly how Spring Boot's OWN auto-configuration classes work
// internally (e.g. DataSourceAutoConfiguration, DispatcherServletAutoConfiguration)
// -- a @Bean method wrapped in a @Conditional annotation that only registers
// the bean when some condition about the classpath, existing beans, or
// properties holds true.
@Configuration
class AppConfig {

    // @ConditionalOnProperty says: only register this bean if the property
    // "greeting.enabled" is present AND equal to "true" (matchIfMissing
    // controls what happens when the key is absent entirely -- here, false,
    // meaning the bean is skipped by default unless the property explicitly
    // opts in).
    //
    // Toggle this by changing greeting.enabled in application.yml (see the
    // comment block above), or by overriding it at launch with:
    //     --greeting.enabled=false
    @Bean
    @ConditionalOnProperty(
            name = "greeting.enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public FeatureFlagBean greetingFeatureFlag() {
        // In a real app this might be a service that changes behavior based
        // on a feature flag, an alternate implementation of an interface, or
        // a bean that only makes sense in certain environments (e.g. a mock
        // mail sender registered only when "mail.enabled=false" for local dev).
        return new FeatureFlagBean("greeting feature is ON");
    }

    // NOTE: Spring Boot's real auto-configuration classes almost always pair
    // @ConditionalOnProperty / @ConditionalOnClass with @ConditionalOnMissingBean,
    // e.g.:
    //
    //     @Bean
    //     @ConditionalOnMissingBean(DataSource.class)
    //     public DataSource dataSource() { ... }
    //
    // so that auto-configuration backs off the instant a developer supplies
    // their OWN bean of that type -- the mechanism that makes auto-configuration
    // "smart defaults you can always override" rather than a rigid framework.
}


// A trivial marker type just so AppConfig above has something concrete to
// return -- in a real project this would be a meaningful service interface
// or implementation, not a bare holder class.
class FeatureFlagBean {
    private final String description;

    FeatureFlagBean(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "FeatureFlagBean{" + description + "}";
    }
}
