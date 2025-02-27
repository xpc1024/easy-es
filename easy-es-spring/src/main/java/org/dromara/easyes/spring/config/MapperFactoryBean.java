package org.dromara.easyes.spring.config;

import org.dromara.easyes.annotation.EsDS;
import org.dromara.easyes.annotation.Intercepts;
import org.dromara.easyes.common.enums.ProcessIndexStrategyEnum;
import org.dromara.easyes.common.property.EasyEsProperties;
import org.dromara.easyes.common.property.GlobalConfig;
import org.dromara.easyes.common.strategy.AutoProcessIndexStrategy;
import org.dromara.easyes.common.utils.LogUtils;
import org.dromara.easyes.common.utils.RestHighLevelClientUtils;
import org.dromara.easyes.common.utils.TypeUtils;
import org.dromara.easyes.core.biz.EntityInfo;
import org.dromara.easyes.core.cache.BaseCache;
import org.dromara.easyes.core.cache.GlobalConfigCache;
import org.dromara.easyes.core.proxy.EsMapperProxy;
import org.dromara.easyes.core.toolkit.EntityInfoHelper;
import org.dromara.easyes.extension.context.Interceptor;
import org.dromara.easyes.extension.context.InterceptorChain;
import org.dromara.easyes.extension.context.InterceptorChainHolder;
import org.dromara.easyes.spring.factory.IndexStrategyFactory;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.dromara.easyes.common.constants.BaseEsConstants.ZERO;
import static org.dromara.easyes.common.utils.RestHighLevelClientUtils.DEFAULT_DS;

/**
 * 代理类
 * <p>
 * Copyright © 2021 xpc1024 All Rights Reserved
 **/
public class MapperFactoryBean<T> implements FactoryBean<T> {

    private Class<T> mapperInterface;

    @Autowired
    private ApplicationContext applicationContext;

    public MapperFactoryBean() {
    }

    public MapperFactoryBean(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    @Override
    public T getObject() throws Exception {

        EsMapperProxy<T> esMapperProxy = new EsMapperProxy<>(mapperInterface, new ConcurrentHashMap<>());

        // 获取实体类
        Class<?> entityClass = TypeUtils.getInterfaceT(mapperInterface, ZERO);
        EasyEsProperties easyEsProperties = this.applicationContext.getBean(EasyEsProperties.class);
        GlobalConfig globalConfig = easyEsProperties.getGlobalConfig();
        // 初始化缓存
        GlobalConfigCache.setGlobalConfig(globalConfig);

        //获取动态数据源 若未配置多数据源,则使用默认数据源
        String restHighLevelClientId = Optional.ofNullable(mapperInterface.getAnnotation(EsDS.class))
                .map(EsDS::value).orElse(DEFAULT_DS);
        RestHighLevelClient client = this.applicationContext.getBean(RestHighLevelClientUtils.class)
                .getClient(restHighLevelClientId);
        BaseCache.initCache(mapperInterface, entityClass, client);

        // 创建代理
        T t = (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[]{mapperInterface}, esMapperProxy);

        // 初始化拦截器链
        InterceptorChain interceptorChain = this.initInterceptorChain();

        // 异步处理索引创建/更新/数据迁移等
        if (!ProcessIndexStrategyEnum.MANUAL.equals(globalConfig.getProcessIndexMode())) {
            // 父子类型,仅针对父类型创建索引,子类型不创建索引
            EntityInfo entityInfo = EntityInfoHelper.getEntityInfo(entityClass);
            boolean isChild = entityInfo.isChild();
            if (!isChild) {
                AutoProcessIndexStrategy autoProcessIndexService = this.applicationContext.getBean(IndexStrategyFactory.class)
                        .getByStrategyType(globalConfig.getProcessIndexMode().getStrategyType());
                autoProcessIndexService.processIndexAsync(entityClass, client);
            }
        } else {
            LogUtils.info("===> manual index mode activated");
        }
        return interceptorChain.pluginAll(t);
    }

    @Override
    public Class<?> getObjectType() {
        return this.mapperInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private InterceptorChain initInterceptorChain() {
        InterceptorChainHolder interceptorChainHolder = InterceptorChainHolder.getInstance();
        InterceptorChain interceptorChain = interceptorChainHolder.getInterceptorChain();
        if (interceptorChain == null) {
            synchronized (this) {
                interceptorChainHolder.initInterceptorChain();
                Map<String, Object> beansWithAnnotation = this.applicationContext.getBeansWithAnnotation(Intercepts.class);
                beansWithAnnotation.forEach((key, val) -> {
                    if (val instanceof Interceptor) {
                        Interceptor interceptor = (Interceptor) val;
                        interceptorChainHolder.addInterceptor(interceptor);
                    }
                });
            }
        }
        return interceptorChainHolder.getInterceptorChain();
    }

}
