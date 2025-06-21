package com.openapispecs.generator.plugin;

import com.openapispecs.generator.plugin.parser.OpenApiBuilder;
import com.openapispecs.generator.plugin.parser.mixin.MediaTypeMixin;
import com.openapispecs.generator.plugin.parser.mixin.SchemaMixin;
import com.openapispecs.generator.plugin.scanner.AnnotationScanner;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateSpecMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "openapi.basePackage", required = true)
    private String basePackage;

    @Parameter(property = "openapi.outputFileName", defaultValue = "openapi.yaml")
    private String outputFileName;

    @Parameter(property = "openapi.api.title", defaultValue = "${project.name}")
    private String apiTitle;

    @Parameter(property = "openapi.api.version", defaultValue = "${project.version}")
    private String apiVersion;

    @Parameter(property = "openapi.api.description", defaultValue = "${project.description}")
    private String apiDescription;

    @Parameter(property = "openapi.skip", defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("OpenAPI specification generation is skipped.");
            return;
        }

        try {
            // 1. Get the project's classpath elements
            List<String> classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());

            List<URL> projectClasspathList = new ArrayList<>();
            for (String element : classpathElements) {
                try {
                    projectClasspathList.add(new File(element).toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException(element + " is an invalid classpath element", e);
                }
            }

            // 2. Create a classloader with the project's full classpath
            URLClassLoader classLoader = new URLClassLoader(
                    projectClasspathList.toArray(new URL[0]),
                    this.getClass().getClassLoader());

            // 3. Scan for controllers and controller advice beans
            AnnotationScanner scanner = new AnnotationScanner(basePackage, classLoader);
            Set<Class<?>> controllers = scanner.findRestControllers();
            Set<Class<?>> controllerAdvices = scanner.findControllerAdvice();

            if (controllers.isEmpty()) {
                getLog().warn("No @RestController or @Controller classes found in package: " + basePackage);
                return;
            }
            getLog().info("Found " + controllers.size() + " controller(s).");

            // 4. Build OpenAPI model
            OpenApiBuilder builder = new OpenApiBuilder();
            OpenAPI openAPI = builder.build(apiTitle, apiVersion, apiDescription, controllers, controllerAdvices);

            // 5. Write to YAML file in the target directory
            File outputFile = new File(project.getBuild().getDirectory(), outputFileName);

            ObjectMapper yamlMapper = new ObjectMapper(
                    new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
            yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            yamlMapper.addMixIn(io.swagger.v3.oas.models.media.Schema.class, SchemaMixin.class);
            yamlMapper.addMixIn(io.swagger.v3.oas.models.media.MediaType.class, MediaTypeMixin.class);
            yamlMapper.writeValue(outputFile, openAPI);

            getLog().info("OpenAPI specification generated successfully at: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            getLog().error("Error generating OpenAPI specification", e);
            throw new MojoExecutionException("Error generating OpenAPI specification", e);
        }
    }
}
