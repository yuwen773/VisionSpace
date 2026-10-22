package com.yuwen.visionspace.acceptance;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=classpath:/acceptance.yml")
@ActiveProfiles("acceptance")
@ContextConfiguration(initializers = IsolatedApplicationInitializer.class)
@Import(RecordingStorageConfiguration.class)
public @interface IsolatedSpringBootTest { }
