package org.simpleframework.inject;

import java.lang.reflect.Field;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.core.BeanContainer;
import org.simpleframework.inject.annotation.Autowired;
import org.simpleframework.util.ClassUtil;
import org.simpleframework.util.ValidationUtil;

/**
 * @Description: 依赖注入实现类
 * @Date 2020/08/22 18:04
 * @auther Draymonder
 */
@Slf4j
public class DependencyInjector {

  /**
   * bean容器
   */
  private BeanContainer beanContainer;
  /**
   * base包名
   */
  @Getter
  private String basePackageName;

  private DependencyInjector() {
  }

  public DependencyInjector(String basePackageName) {
    // 单例模式创建bean容器
    beanContainer = BeanContainer.getInstance();
    this.basePackageName = basePackageName;
  }

  /**
   * 预先加载bean
   */
  public void loadBeans() {
    if (!beanContainer.isLoaded()) {
      beanContainer.loadBeans(this.basePackageName);
    }
  }

  /**
   * 控制反转, 将Autowired配置的变量注入相应的值
   */
  public void doIoC() {
    int dependencyCount = 0;
    loadBeans();
    Set<Class<?>> classSet = beanContainer.getClassSet();
    if (ValidationUtil.isEmpty(classSet)) {
      log.error("bean container classSet is empty");
      return;
    }
    for (Class<?> clazz : classSet) {
      Field[] fields = clazz.getDeclaredFields();
      if (ValidationUtil.isEmpty(fields)) {
        continue;
      }
      for (Field field : fields) {
        if (field.isAnnotationPresent(Autowired.class)) {
          // 获取注解里面的Autowired的值
          Autowired autowired = field.getAnnotation(Autowired.class);
          String autowiredValue = autowired.value();

          /**
           * AServiceImpl implements AService
           *
           * @Autowired
           * private AService aSerivce;
           *
           */

          // 这个字段上有Autowired
          // 读取key的class
          Class<?> fieldKeyClass = field.getType();
          // 读取value的class
          Class<?> fieldValueClass = getBeanClassByType(fieldKeyClass, autowiredValue);
          // 根据value的class拿到对应的bean
          Object valueBean = getBean(fieldValueClass);

          // 对clazz的field的key设置为value
          Object currentOjbect = getBean(clazz);
          ClassUtil.setFieldValue(field, currentOjbect, valueBean, true);
          log.info("Object {} field {} has bean injected Object {}", currentOjbect, field.getName(),
              valueBean);
          dependencyCount++;
        }
      }
    }
    log.info("ioc has been end, and dedendency inject {} times", dependencyCount);
  }

  /**
   * 根据field的类去获取field的具体的实现类 举例如: `aService` 去获取 `aService` or `aServiceImpl`
   *
   * @param fieldClass     字段
   * @param autowiredValue @Autowired的value值
   * @return 获取到的beanClass类
   */
  private Class<?> getBeanClassByType(Class<?> fieldClass, String autowiredValue) {
    Object currentBean = beanContainer.getBeanByClass(fieldClass);
    if (ValidationUtil.isEmpty(currentBean)) {
      Set<Class<?>> subBeanClassSet = beanContainer
          .getBeansBySuperClassOrInterface(fieldClass);
      if (ValidationUtil.isEmpty(subBeanClassSet)) {
        log.error("can not find filed class [{}] val", fieldClass);
        throw new RuntimeException(
            String.format("the field class [%s] has not implement", fieldClass));
      } else if (subBeanClassSet.size() == 1) {
        return subBeanClassSet.iterator().next();
      } else {
        for (Class<?> subClass : subBeanClassSet) {
          if (autowiredValue.equals(subClass.getSimpleName())) {
            return subClass;
          }
        }
        throw new RuntimeException(
            String
                .format("the field class [%s] has multi implements, can not find the specific one",
                    fieldClass));
      }
    } else {
      return fieldClass;
    }
  }

  /**
   * 获取拥有filed字段的对象 例如: class A { int b; }, 获取`Field b`调用的是 getFieldObject(A.class);
   *
   * @param clazz 拥有field字段的类
   * @return clazz类对应的实例化对象
   */
  public Object getBean(Class<?> clazz) {
    return beanContainer.getBeanByClass(clazz);
  }

}
