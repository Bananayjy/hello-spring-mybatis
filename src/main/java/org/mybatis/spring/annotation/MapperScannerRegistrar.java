/*
 * Copyright 2010-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * @MapperScann 的注册器
 * 将扫描到的 Mapper 接口，注册成 beanClass 为 MapperFactoryBean 的 BeanDefinition 对象，从而实现创建 Mapper 对象
 *
 * A {@link ImportBeanDefinitionRegistrar} to allow annotation configuration of MyBatis mapper scanning. Using
 * an @Enable annotation allows beans to be registered via @Component configuration, whereas implementing
 * {@code BeanDefinitionRegistryPostProcessor} will work for XML configuration.
 * {@link ImportBeanDefinitionRegistrar}允许MyBatis映射器扫描的注释配置。使用@Enable注释允许通过@Component配置注册bean，
 * 而实现{@code BeanDefinitionRegistryPostProcessor}将适用于XML配置。
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 * @author Putthiphong Boonphong
 *
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 *
 * @since 1.2.0
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

  // ResourceLoader 对象
  // 因为实现了 ResourceLoaderAware 接口，所以 resourceLoader 属性，能够被注入
  // Note: Do not move resourceLoader via cleanup
  private ResourceLoader resourceLoader;

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  /**
   * registerBeanDefinitions 方法是 ImportBeanDefinitionRegistrar 接口的核心方法，用于动态注册 Bean 定义
   * @param importingClassMetadata 提供访问 导入该配置类的注解元数据 的能力（即解析 @Import 注解的源头类的注解信息）
   * @param registry 提供 动态注册/管理 Bean定义 的接口
   */
  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    // 获得 @MapperScan 注解信息,并封装到AnnotationAttributes对象mapperScanAttrs中
    var mapperScanAttrs = AnnotationAttributes
        .fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));
    if (mapperScanAttrs != null) {
      // 扫描包，将扫描到的 Mapper 接口，注册成 beanClass 为 MapperFactoryBean 的 BeanDefinition 对象
      registerBeanDefinitions(importingClassMetadata, mapperScanAttrs, registry,
          generateBaseBeanName(importingClassMetadata, 0));
    }
  }

  void registerBeanDefinitions(AnnotationMetadata annoMeta, AnnotationAttributes annoAttrs,
      BeanDefinitionRegistry registry, String beanName) {

    // 注册MapperScannerConfigurer的beanDefinition对象
    var builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
    builder.addPropertyValue("processPropertyPlaceHolders", annoAttrs.getBoolean("processPropertyPlaceHolders"));

    Class<? extends Annotation> annotationClass = annoAttrs.getClass("annotationClass");
    if (!Annotation.class.equals(annotationClass)) {
      builder.addPropertyValue("annotationClass", annotationClass);
    }

    Class<?> markerInterface = annoAttrs.getClass("markerInterface");
    if (!Class.class.equals(markerInterface)) {
      builder.addPropertyValue("markerInterface", markerInterface);
    }

    Class<? extends BeanNameGenerator> generatorClass = annoAttrs.getClass("nameGenerator");
    if (!BeanNameGenerator.class.equals(generatorClass)) {
      builder.addPropertyValue("nameGenerator", BeanUtils.instantiateClass(generatorClass));
    }

    Class<? extends MapperFactoryBean> mapperFactoryBeanClass = annoAttrs.getClass("factoryBean");
    if (!MapperFactoryBean.class.equals(mapperFactoryBeanClass)) {
      builder.addPropertyValue("mapperFactoryBeanClass", mapperFactoryBeanClass);
    }

    var sqlSessionTemplateRef = annoAttrs.getString("sqlSessionTemplateRef");
    if (StringUtils.hasText(sqlSessionTemplateRef)) {
      builder.addPropertyValue("sqlSessionTemplateBeanName", annoAttrs.getString("sqlSessionTemplateRef"));
    }

    var sqlSessionFactoryRef = annoAttrs.getString("sqlSessionFactoryRef");
    if (StringUtils.hasText(sqlSessionFactoryRef)) {
      builder.addPropertyValue("sqlSessionFactoryBeanName", annoAttrs.getString("sqlSessionFactoryRef"));
    }

    List<String> basePackages = new ArrayList<>(Arrays.stream(annoAttrs.getStringArray("basePackages"))
        .filter(StringUtils::hasText).collect(Collectors.toList()));

    basePackages.addAll(Arrays.stream(annoAttrs.getClassArray("basePackageClasses")).map(ClassUtils::getPackageName)
        .collect(Collectors.toList()));

    if (basePackages.isEmpty()) {
      basePackages.add(getDefaultBasePackage(annoMeta));
    }

    var excludeFilterArray = annoAttrs.getAnnotationArray("excludeFilters");
    if (excludeFilterArray.length > 0) {
      List<TypeFilter> typeFilters = new ArrayList<>();
      List<Map<String, String>> rawTypeFilters = new ArrayList<>();
      for (AnnotationAttributes excludeFilters : excludeFilterArray) {
        if (excludeFilters.getStringArray("pattern").length > 0) {
          // in oder to apply placeholder resolver
          rawTypeFilters.addAll(parseFiltersHasPatterns(excludeFilters));
        } else {
          typeFilters.addAll(typeFiltersFor(excludeFilters));
        }
      }
      builder.addPropertyValue("excludeFilters", typeFilters);
      builder.addPropertyValue("rawExcludeFilters", rawTypeFilters);
    }

    var lazyInitialization = annoAttrs.getString("lazyInitialization");
    if (StringUtils.hasText(lazyInitialization)) {
      builder.addPropertyValue("lazyInitialization", lazyInitialization);
    }

    var defaultScope = annoAttrs.getString("defaultScope");
    if (!AbstractBeanDefinition.SCOPE_DEFAULT.equals(defaultScope)) {
      builder.addPropertyValue("defaultScope", defaultScope);
    }

    builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(basePackages));

    // for spring-native
    builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

    registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

  }

  /**
   * Parse excludeFilters which FilterType is REGEX or ASPECTJ
   *
   * @param filterAttributes
   *          AnnotationAttributes of excludeFilters
   *
   * @since 3.0.4
   */
  private List<Map<String, String>> parseFiltersHasPatterns(AnnotationAttributes filterAttributes) {

    List<Map<String, String>> rawTypeFilters = new ArrayList<>();
    FilterType filterType = filterAttributes.getEnum("type");
    var expressionArray = filterAttributes.getStringArray("pattern");
    for (String expression : expressionArray) {
      switch (filterType) {
        case REGEX:
        case ASPECTJ:
          Map<String, String> typeFilter = new HashMap<>(16);
          typeFilter.put("type", filterType.name().toLowerCase());
          typeFilter.put("expression", expression);
          rawTypeFilters.add(typeFilter);
          break;
        default:
          throw new IllegalArgumentException("Cannot specify the 'pattern' attribute if use the " + filterType
              + " FilterType in exclude filter of @MapperScan");
      }
    }
    return rawTypeFilters;
  }

  /**
   * Parse excludeFilters which FilterType is ANNOTATION ASSIGNABLE or CUSTOM
   *
   * @param filterAttributes
   *          AnnotationAttributes of excludeFilters
   *
   * @since 3.0.4
   */
  private List<TypeFilter> typeFiltersFor(AnnotationAttributes filterAttributes) {

    List<TypeFilter> typeFilters = new ArrayList<>();
    FilterType filterType = filterAttributes.getEnum("type");

    for (Class<?> filterClass : filterAttributes.getClassArray("value")) {
      switch (filterType) {
        case ANNOTATION:
          Assert.isAssignable(Annotation.class, filterClass,
              "Specified an unsupported type in 'ANNOTATION' exclude filter of @MapperScan");
          @SuppressWarnings("unchecked")
          var annoClass = (Class<Annotation>) filterClass;
          typeFilters.add(new AnnotationTypeFilter(annoClass));
          break;
        case ASSIGNABLE_TYPE:
          typeFilters.add(new AssignableTypeFilter(filterClass));
          break;
        case CUSTOM:
          Assert.isAssignable(TypeFilter.class, filterClass,
              "An error occured when processing a @ComponentScan " + "CUSTOM type filter: ");
          typeFilters.add(BeanUtils.instantiateClass(filterClass, TypeFilter.class));
          break;
        default:
          throw new IllegalArgumentException("Cannot specify the 'value' or 'classes' attribute if use the "
              + filterType + " FilterType in exclude filter of @MapperScan");
      }
    }
    return typeFilters;
  }

  private static String generateBaseBeanName(AnnotationMetadata importingClassMetadata, int index) {
    return importingClassMetadata.getClassName() + "#" + MapperScannerRegistrar.class.getSimpleName() + "#" + index;
  }

  private static String getDefaultBasePackage(AnnotationMetadata importingClassMetadata) {
    return ClassUtils.getPackageName(importingClassMetadata.getClassName());
  }

  /**
   * 是 MapperScannerRegistrar 的内部静态类，继承 MapperScannerRegistrar 类，@MapperScans 的注册器
   * 即RegisterBeanDefinitions循环版
   * A {@link MapperScannerRegistrar} for {@link MapperScans}.
   *
   * @since 2.0.0
   */
  static class RepeatingRegistrar extends MapperScannerRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
      // 获得 @MapperScans 注解信息
      var mapperScansAttrs = AnnotationAttributes
          .fromMap(importingClassMetadata.getAnnotationAttributes(MapperScans.class.getName()));
      if (mapperScansAttrs != null) {
        var annotations = mapperScansAttrs.getAnnotationArray("value");
        // 遍历 @MapperScans 的值，调用 `#registerBeanDefinitions(mapperScanAttrs, registry)` 方法，循环扫描处理
        for (var i = 0; i < annotations.length; i++) {
          registerBeanDefinitions(importingClassMetadata, annotations[i], registry,
              generateBaseBeanName(importingClassMetadata, i));
        }
      }
    }
  }

}
