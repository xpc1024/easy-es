```java
/**
 * 设置别名信息
 * @param aliasName
 */
createAlias(String aliasName)
```

>**Tip:** 无论您目前是否会使用别名,我们都强烈建议您在生产环境创建索引时给该索引取一个有意义的别名,这样未来如果您有索引变更计划时,可以通过别名平滑过渡,不影响生产环境的服务,可通过别名平滑迁移,迁移过程如失败,可轻松完成回滚.