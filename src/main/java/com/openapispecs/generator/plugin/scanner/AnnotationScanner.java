package com.openapispecs.generator.plugin.scanner;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

public class AnnotationScanner {
    private final Reflections reflections;

    public AnnotationScanner(String basePackage, ClassLoader classLoader) {
        this.reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(basePackage, classLoader)
                .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated)
                .addClassLoaders(classLoader));
    }

    public Set<Class<?>> findRestControllers() {
        Set<Class<?>> restControllers = reflections.getTypesAnnotatedWith(RestController.class);
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(Controller.class);

        Set<Class<?>> allControllers = new HashSet<>();
        allControllers.addAll(restControllers);
        allControllers.addAll(controllers);

        return allControllers;
    }

    public Set<Class<?>> findControllerAdvice() {
        return reflections.getTypesAnnotatedWith(ControllerAdvice.class);
    }
}