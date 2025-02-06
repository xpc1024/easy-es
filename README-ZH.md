<p align="center">
  <a href="https://easy-es.cn/">
   <img alt="East-Es-Logo" src="https://iknow.hs.net/042dd639-5bfa-410f-968f-8bbceb8d8ca7.png">
  </a>
</p>

<p align="center">
  您的Star是我继续前进的动力，如果喜欢EE请右上角帮忙点亮星星⭐!
</p>

<p align="center">
  <a href="https://search.maven.org/search?q=g:io.github.xpc1024%20a:easy-*">
    <img alt="maven" src="https://img.shields.io/github/v/release/xpc1024/easy-es?include_prereleases&logo=xpc&style=plastic">
  </a>
  <a href="https://www.murphysec.com/dr/htY0sMYDQaDn4X8iXp" alt="OSCS Status"><img src="https://www.oscs1024.com/platform/badge/dromara/easy-es.git.svg?size=small"/></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img alt="code style" src="https://img.shields.io/badge/license-Apache%202-4EB1BA.svg?style=flat-square">
  </a>
</p>

# 官方地址 | Official website

**easy-es官网** https://easy-es.cn/

**easy-es官方gitee** https://gitee.com/dromara/easy-es

**easy-es官方github** https://github.com/dromara/easy-es

**开源社区dromara** https://dromara.org/

**开源社区码云首页** https://gitee.com/dromara/

> **Tip:** 官网是vue单页面应用，首次访问加载可能比较慢🐢，主公们请耐心等待一下，后续会很快🏹，如偶遇打不开可刷新多尝试几次.

# 简介 | Intro

Easy-Es是一款简化ElasticSearch搜索引擎操作的开源框架,全自动智能索引托管.

目前功能丰富度和易用度及性能已全面领先SpringData-Elasticsearch.

简化`CRUD`及其它高阶操作,可以更好的帮助开发者减轻开发负担

底层采用Es官方提供的RestHighLevelClient,保证其原生性能及拓展性.

技术讨论 QQ 群 ：729148550 群内可在群文件中免费领取 颈椎保护 | 增肌 | 减脂 等健身计划 无套路

微信群请先添加作者微信,由作者拉入 (亦可咨询健身问题,作者是健身教练)

项目推广初期,还望大家能够不吝点点三连:⭐Star,👀Watch,fork📌

支持一下国产开源,让更多人看到和使用本项目,非常感谢!

# 优点 | Advantages

- **全自动索引托管:** 全球开源首创的索引托管模式,开发者无需关心索引的创建更新及数据迁移等繁琐步骤,索引全生命周期皆可托管给框架,由框架自动完成,过程零停机,用户无感知,彻底解放开发者
- **智能字段类型推断:** 根据索引类型和当前查询类型上下文综合智能判断当前查询是否需要拼接.keyword后缀,减少小白误用的可能
- **屏蔽语言差异:** 开发者只需要会MySQL语法即可使用Es
- **代码量极少:** 与直接使用RestHighLevelClient相比,相同的查询平均可以节省3-8倍左右的代码量
- **零魔法值:** 字段名称直接从实体中获取,无需输入字段名称字符串这种魔法值
- **零额外学习成本:** 开发者只要会国内最受欢迎的Mybatis-Plus语法,即可无缝迁移至Easy-Es
- **降低开发者门槛:** 即便是只了解ES基础的初学者也可以轻松驾驭ES完成绝大多数需求的开发
- **功能强大:** 支持MySQL的几乎全部功能,且对ES特有的分词,权重,高亮,嵌套,地理位置Geo,Ip地址查询等功能都支持
- **语法优雅:** 所有条件构造器均支持Lambda风格链式编程，编程体验和代码可读性大幅提升
- **安全可靠:** 墨菲安全扫描零风险,且代码单元测试综合覆盖率高达95%以上.
- **完善的中英文文档:** 提供了中英文双语操作文档,文档全面可靠,帮助您节省更多时间
- **...**

# 对比 | Compare

> 需求:查询出文档标题为 "传统功夫"且作者为"码保国"的所有文档
```java
    // 使用Easy-Es仅需1行代码即可完成查询
    List<Document> documents = documentMapper.selectList(EsWrappers.lambdaQuery(Document.class).eq(Document::getTitle, "传统功夫").eq(Document::getCreator, "码保国"));
```


```java
    // 传统方式, 直接用RestHighLevelClient进行查询 需要19行代码,还不包含下划线转驼峰,自定义字段处理及_id处理等代码
    String indexName = "document";
    SearchRequest searchRequest = new SearchRequest(indexName);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    TermQueryBuilder titleTerm = QueryBuilders.termQuery("title", "传统功夫");
    TermsQueryBuilder creatorTerm = QueryBuilders.termsQuery("creator", "码保国");
    boolQueryBuilder.must(titleTerm);
    boolQueryBuilder.must(creatorTerm);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(boolQueryBuilder);
    searchRequest.source(searchSourceBuilder);
    try {
         SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
         List<Document> documents = Optional.ofNullable(searchResponse)
                .map(SearchResponse::getHits)
                .map(SearchHits::getHits)
                .map(hit->Document document = JSON.parseObject(hit.getSourceAsString(),Document.class))
                .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
```
> * 以上只是简单查询演示,实际使用场景越复杂,效果就越好,平均可节省至少3-8倍代码量
> * 传统功夫,点到为止! 上述功能仅供演示,仅为Easy-Es支持功能的冰山一角,Easy-Es就是这么Easy到不讲武德💪,不用的请耗子尾汁.

# 架构 | Architecture

![Architecture](https://iknow.hs.net/27fb40b8-22d4-45c2-92e0-1471112d5102.jpg)

## 功能结构图 | Functional structure diagram

![Function](https://iknow.hs.net/5fad565b-8b4e-4274-ab59-a74c3492ac9d.png)


# 相关链接 | Links

- [Switch To English](https://gitee.com/easy-es/easy-es/blob/master/README_EN.md)
- [功能示例](https://gitee.com/dromara/easy-es/tree/master/easy-es-sample)
- [Springboot集成Demo](https://gitee.com/easy-es/easy-es-springboot-demo)

# Latest Version: [![Maven Central](https://img.shields.io/github/v/release/xpc1024/easy-es?include_prereleases&logo=xpc&style=plastic)](https://search.maven.org/search?q=g:io.github.xpc1024%20a:easy-*)
---
**Maven:**
``` xml
<dependency>
    <groupId>org.dromara.easy-es</groupId>
    <artifactId>easy-es-boot-starter</artifactId>
    <version>Latest Version</version>
</dependency>
```
**Gradle:**
```groovy
compile group: 'org.dromara.easy-es', name: 'easy-es-boot-starter', version: 'Latest Version'
```

# 荣誉 | Honour

> Easy-Es是一个持续成长和精进的开源框架,感谢大家一路支持,也感谢多方平台多我们努力的认可,我们会继续努力,用更好的产品力回报每一位支持者!


<img alt="zsxq" src="https://iknow.hs.net/1b003ee7-dbfb-4cb8-9b30-de6136218faf.jpg">


# 其他开源项目 | Other Project

- [健身计划一键生成系统](https://gitee.com/easy-es/fit-plan)

# 期望 | Futures
---

> 欢迎提出更好的意见，帮助完善 Easy-Es

# 版权 | License
---

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

# 关注我 | About Me

[CSDN博客](https://blog.csdn.net/lovexiaotaozi?spm=3001.5343)

QQ | 微信:252645816

# 知识星球 | Planet Of Knowledge

<img alt="zsxq" src="https://iknow.hs.net/9038b7ab-c0d9-4a87-9492-e839907a8978.png">

# 捐赠 | Donate


[捐赠记录,感谢你们的支持！](https://easy-es.cn/pages/b52ac5/)

> 您的支持是鼓励我们前行的动力，无论金额多少都足够表达您这份心意。

> 如果您愿意捐赠本项目,推荐直接在右下方通过Gitee直接捐赠.

# 广告商 | Advertising provider

> 我们的广告投放商,如果您期望Easy-Es能够走得更远,不妨点击下图,支持一下我们的广告商Thanks♪(･ω･)ﾉ

<a href="https://www.misboot.com/?from=easy-es">
  <img alt="ad" src="https://iknow.hs.net/68963214-7a61-4f38-b5b5-a068c07a35f1.png">
</a>

</br>

<a href="https://www.mingdao.com?s=utm_70&utm_source=easy-es&utm_medium=banner&utm_campaign=gitee&utm_content=IT%E8%B5%8B%E8%83%BD%E4%B8%9A%E5%8A%A1">
  <img alt="ad" src="https://iknow.hs.net/00b4a54c-6505-4776-9232-f0a9d9768fac.jpg">
</a>


# 赞助商 | Sponsor

> 如果您想支持我们,奈何囊中羞涩,没事,您可以花30秒借花献佛,点击下方链接进入注册,则该赞助商会代您捐赠一笔小钱给社区开发者们买包辣条。

<a href="http://apifox.cn/a103easyse">
  <img alt="ad" src="https://iknow.hs.net/a26897a9-d408-4985-9ed6-b3180ea6ed98.png">
</a>


# 周边好物 | Good things


> 老汉为社区量身打造的专属T恤,感兴趣的老板们可以点击下图链接了解详情

<a href="https://www.easy-es.cn/pages/1a810c/">
  <img alt="ad" src="https://iknow.hs.net/ac784337-0b57-4885-9875-e1879bff47dc.jpg">
</a>
