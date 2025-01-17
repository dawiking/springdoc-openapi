package org.springdoc.core;

import static org.springdoc.core.Constants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.ReflectionUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Component
public class GeneralInfoBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(GeneralInfoBuilder.class);
	private ApplicationContext context;
	private SecurityParser securityParser;
	private String serverBaseUrl;
	private Map<HandlerMethod, String> springdocTags = new HashMap<>();

	public GeneralInfoBuilder(ApplicationContext context, SecurityParser securityParser) {
		super();
		this.context = context;
		this.securityParser = securityParser;
	}

	public void build(OpenAPI openAPI) {
		Optional<OpenAPIDefinition> apiDef = getOpenAPIDefinition();
		if (apiDef.isPresent()) {
			buildOpenAPIWithOpenAPIDefinition(openAPI, apiDef.get());
		}
		// Set default info
		else if (openAPI.getInfo() == null) {
			Info infos = new Info().title(DEFAULT_TITLE).version(DEFAULT_VERSION);
			openAPI.setInfo(infos);
		}

		// default server value
		if (CollectionUtils.isEmpty(openAPI.getServers())) {
			Server server = new Server().url(serverBaseUrl).description(DEFAULT_SERVER_DESCRIPTION);
			openAPI.addServersItem(server);
		}

		// add security schemes
		this.calculateSecuritySchemes(openAPI.getComponents());
	}

	public Operation buildTags(HandlerMethod handlerMethod, Operation operation, OpenAPI openAPI) {

		// class tags
		List<io.swagger.v3.oas.annotations.tags.Tag> classTags = ReflectionUtils
				.getRepeatableAnnotations(handlerMethod.getBeanType(), io.swagger.v3.oas.annotations.tags.Tag.class);

		// method tags
		List<io.swagger.v3.oas.annotations.tags.Tag> methodTags = ReflectionUtils
				.getRepeatableAnnotations(handlerMethod.getMethod(), io.swagger.v3.oas.annotations.tags.Tag.class);

		List<io.swagger.v3.oas.annotations.tags.Tag> allTags = new ArrayList<>();
		Set<String> tagsStr = new HashSet<>();

		if (!CollectionUtils.isEmpty(methodTags)) {
			tagsStr.addAll(methodTags.stream().map(Tag::name).collect(Collectors.toSet()));
			allTags.addAll(methodTags);
		}

		if (!CollectionUtils.isEmpty(classTags)) {
			tagsStr.addAll(classTags.stream().map(Tag::name).collect(Collectors.toSet()));
			allTags.addAll(classTags);
		}

		if (springdocTags.containsKey(handlerMethod))
			tagsStr.add(springdocTags.get(handlerMethod));

		Optional<Set<io.swagger.v3.oas.models.tags.Tag>> tags = AnnotationsUtils
				.getTags(allTags.toArray(new Tag[allTags.size()]), true);

		if (tags.isPresent()) {
			Set<io.swagger.v3.oas.models.tags.Tag> tagsSet = tags.get();
			// Existing tags
			List<io.swagger.v3.oas.models.tags.Tag> openApiTags = openAPI.getTags();
			if (!CollectionUtils.isEmpty(openApiTags))
				tagsSet.addAll(openApiTags);
			openAPI.setTags(new ArrayList<>(tagsSet));
		}

		// Handle SecurityRequirement at operation level
		Optional<io.swagger.v3.oas.annotations.security.SecurityRequirement[]> securityRequirement = securityParser
				.getSecurityRequirements(handlerMethod);
		if (securityRequirement.isPresent()) {
			securityParser.buildSecurityRequirement(securityRequirement.get(), operation);
		}

		if (!CollectionUtils.isEmpty(tagsStr))
			operation.setTags(new ArrayList<String>(tagsStr));

		return operation;
	}

	public void setServerBaseUrl(String serverBaseUrl) {
		this.serverBaseUrl = serverBaseUrl;
	}

	private Optional<OpenAPIDefinition> getOpenAPIDefinition() {
		// Look for OpenAPIDefinition in a spring managed bean
		Map<String, Object> openAPIDefinitionMap = context.getBeansWithAnnotation(OpenAPIDefinition.class);
		OpenAPIDefinition apiDef = null;
		if (openAPIDefinitionMap.size() > 1)
			LOGGER.warn(
					"found more than one OpenAPIDefinition class. springdoc-openapi will be using the first one found.");
		if (openAPIDefinitionMap.size() > 0) {
			Map.Entry<String, Object> entry = openAPIDefinitionMap.entrySet().iterator().next();
			Class<?> objClz = entry.getValue().getClass();
			apiDef = ReflectionUtils.getAnnotation(objClz, OpenAPIDefinition.class);
		}

		// Look for OpenAPIDefinition in the spring classpath
		else {
			ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
					false);
			scanner.addIncludeFilter(new AnnotationTypeFilter(OpenAPIDefinition.class));
			if (AutoConfigurationPackages.has(context)) {
				List<String> packagesToScan = AutoConfigurationPackages.get(context);
				apiDef = getApiDefClass(scanner, packagesToScan);
			}

		}
		return Optional.ofNullable(apiDef);
	}

	private void buildOpenAPIWithOpenAPIDefinition(OpenAPI openAPI, OpenAPIDefinition apiDef) {
		// info
		AnnotationsUtils.getInfo(apiDef.info()).ifPresent(openAPI::setInfo);
		// OpenApiDefinition security requirements
		securityParser.getSecurityRequirements(apiDef.security()).ifPresent(openAPI::setSecurity);
		// OpenApiDefinition external docs
		AnnotationsUtils.getExternalDocumentation(apiDef.externalDocs()).ifPresent(openAPI::setExternalDocs);
		// OpenApiDefinition tags
		AnnotationsUtils.getTags(apiDef.tags(), false).ifPresent(tags -> openAPI.setTags(new ArrayList<>(tags)));
		// OpenApiDefinition servers
		openAPI.setServers(AnnotationsUtils.getServers(apiDef.servers()).orElse(null));
		// OpenApiDefinition extensions
		if (apiDef.extensions().length > 0) {
			openAPI.setExtensions(AnnotationsUtils.getExtensions(apiDef.extensions()));
		}
	}

	private void calculateSecuritySchemes(Components components) {
		// Look for OpenAPIDefinition in a spring managed bean
		Map<String, Object> securitySchemeBeans = context
				.getBeansWithAnnotation(io.swagger.v3.oas.annotations.security.SecurityScheme.class);
		if (securitySchemeBeans.size() > 0) {
			for (Map.Entry<String, Object> entry : securitySchemeBeans.entrySet()) {
				Class<?> objClz = entry.getValue().getClass();
				List<io.swagger.v3.oas.annotations.security.SecurityScheme> apiSecurityScheme = ReflectionUtils
						.getRepeatableAnnotations(objClz, io.swagger.v3.oas.annotations.security.SecurityScheme.class);
				this.addSecurityScheme(apiSecurityScheme, components);
			}
		}

		// Look for OpenAPIDefinition in the spring classpath
		else {
			ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
					false);
			scanner.addIncludeFilter(
					new AnnotationTypeFilter(io.swagger.v3.oas.annotations.security.SecurityScheme.class));
			if (AutoConfigurationPackages.has(context)) {
				List<String> packagesToScan = AutoConfigurationPackages.get(context);
				List<io.swagger.v3.oas.annotations.security.SecurityScheme> apiSecurityScheme = getSecuritySchemesClasses(
						scanner, packagesToScan);
				this.addSecurityScheme(apiSecurityScheme, components);
			}

		}
	}

	private void addSecurityScheme(List<io.swagger.v3.oas.annotations.security.SecurityScheme> apiSecurityScheme,
			Components components) {
		for (io.swagger.v3.oas.annotations.security.SecurityScheme securitySchemeAnnotation : apiSecurityScheme) {
			Optional<SecuritySchemePair> securityScheme = securityParser.getSecurityScheme(securitySchemeAnnotation);
			if (securityScheme.isPresent()) {
				Map<String, SecurityScheme> securitySchemeMap = new HashMap<>();
				if (StringUtils.isNotBlank(securityScheme.get().getKey())) {
					securitySchemeMap.put(securityScheme.get().getKey(), securityScheme.get().getSecurityScheme());
					if (!CollectionUtils.isEmpty(components.getSecuritySchemes())) {
						components.getSecuritySchemes().putAll(securitySchemeMap);
					} else {
						components.setSecuritySchemes(securitySchemeMap);
					}
				}
			}
		}
	}

	private OpenAPIDefinition getApiDefClass(ClassPathScanningCandidateComponentProvider scanner,
			List<String> packagesToScan) {
		for (String pack : packagesToScan) {
			for (BeanDefinition bd : scanner.findCandidateComponents(pack)) {
				// first one found is ok
				try {
					return AnnotationUtils.findAnnotation(Class.forName(bd.getBeanClassName()),
							OpenAPIDefinition.class);
				} catch (ClassNotFoundException e) {
					LOGGER.error("Class Not Found in classpath : {}", e.getMessage());
				}
			}
		}
		return null;
	}

	private List<io.swagger.v3.oas.annotations.security.SecurityScheme> getSecuritySchemesClasses(
			ClassPathScanningCandidateComponentProvider scanner, List<String> packagesToScan) {
		List<io.swagger.v3.oas.annotations.security.SecurityScheme> apiSecurityScheme = new ArrayList<>();
		for (String pack : packagesToScan) {
			for (BeanDefinition bd : scanner.findCandidateComponents(pack)) {
				try {
					apiSecurityScheme.add(AnnotationUtils.findAnnotation(Class.forName(bd.getBeanClassName()),
							io.swagger.v3.oas.annotations.security.SecurityScheme.class));
				} catch (ClassNotFoundException e) {
					LOGGER.error("Class Not Found in classpath : {}", e.getMessage());
				}
			}
		}
		return apiSecurityScheme;
	}

	public void addTag(Set<HandlerMethod> handlerMethods, String tagName) {
		handlerMethods.forEach(handlerMethod -> springdocTags.put(handlerMethod, tagName));
	}

	public Map<String, Object> getRestControllersMap() {
		return context.getBeansWithAnnotation(RestController.class);
	}

	public Map<String, Object> getRequestMappingMap() {
		return context.getBeansWithAnnotation(RequestMapping.class);
	}

	public Map<String, Object> getControllerAdviceMap() {
		Map<String, Object> controllerAdviceMap = context.getBeansWithAnnotation(ControllerAdvice.class);
		return Stream.of(controllerAdviceMap).flatMap(mapEl -> mapEl.entrySet().stream()).filter(
				controller -> (AnnotationUtils.findAnnotation(controller.getValue().getClass(), Hidden.class) == null))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));
	}

}
