<p align="center">
  <a href="https://en.easy-es.cn/">
   <img alt="East-Es-Logo" src="https://iknow.hs.net/042dd639-5bfa-410f-968f-8bbceb8d8ca7.png">
  </a>
</p>

<p align="center">
  Born To Simplify Development
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

## What is Easy-Es?

Easy-Es is a powerfully enhanced toolkit of RestHighLevelClient for simplify development. This toolkit provides some efficient, useful, out-of-the-box features for ElasticSearch. By using Easy-Es, you can use MySQL syntax to complete Es queries. Use it can effectively save your development time.

## Official website

**easy-es website**  https://en.easy-es.cn/

**easy-es gitcode** https://gitcode.com/dromara/easy-es

**easy-es gitee** https://gitee.com/dromara/easy-es

**easy-es github** https://github.com/dromara/easy-es

**dromara website** https://dromara.org/

**dromara gitee homepage** https://gitee.com/dromara/

## Links

- [中文版](https://github.com/xpc1024/easy-es/blob/main/README-ZH.md)
- [Samples](https://github.com/xpc1024/easy-es/tree/main/easy-es-sample)
- [Demo in Springboot](https://en.easy-es.cn/pages/658abb/#_2-pom)

## Features

-   Automatically create and update indexes, automatically migrate data, and process zero downtime
-   Auto configuration on startup
-   Out-of-the-box interfaces for operate es
-   Powerful and flexible where condition wrapper
-   Lambda-style API
-   Automatic paging operation
-   Support high-level syntax such as highlighting and weighting and Geo etc
-   ...

## Compare

> Demand: Query all documents with title equals "Hi" and author equals "Guy"



```java
// Use Easy-Es to complete the query with only 3 lines of code
LambdaEsQueryWrapper<Document> wrapper = new LambdaEsQueryWrapper<>();
        wrapper.eq(Document::getTitle, "Hi").eq(Document::getCreator, "Guy");
        List<Document> documents = documentMapper.selectList(wrapper);
```

```java
// Query with RestHighLevelClient requires 11 lines of code, not including parsing JSON code
String indexName = "document";
        SearchRequest searchRequest = new SearchRequest(indexName);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        TermQueryBuilder titleTerm = QueryBuilders.termQuery("title", "Hi");
        TermsQueryBuilder creatorTerm = QueryBuilders.termsQuery("creator", "Guy");
        boolQueryBuilder.must(titleTerm);
        boolQueryBuilder.must(creatorTerm);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);
        try {
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        // Then parse the DocumentList from searchResponse in various ways, omitting these codes...
        } catch (IOException e) {
        e.printStackTrace();
        }
```

> The above is just a simple query demonstration. The more complex the actual query scene, the better the effect, which can save 3-5 times the amount of code on average.
## Getting started

-   Add Easy-Es dependency
    - Latest Version: [![Maven Central](https://img.shields.io/github/v/release/xpc1024/easy-es?include_prereleases&logo=xpc&style=plastic)](https://search.maven.org/search?q=g:io.github.xpc1024%20a:easy-*)
    - Maven:
      ```xml
      <dependency>
        <groupId>org.dromara.easy-es</groupId>
        <artifactId>easy-es-boot-starter</artifactId>
        <version>Latest Version</version>
      </dependency>
      ```
    - Gradle
      ```groovy
      compile group: 'org.dromara.easy-es', name: 'easy-es-boot-starter', version: 'Latest Version'
      ```
-   Add mapper file extends BaseEsMapper interface

    ```java
    public interface DocumentMapper extends BaseMapper<User> {
    }
    ```

- Use it
  ``` java
  LambdaEsQueryWrapper<Document> wrapper = new LambdaEsQueryWrapper<>();
  wrapper.eq(Document::getTitle,"Hello World")
         .eq(Document::getCreator,"Guy");
  List<Document> documentList = documentMapper.selectList();
  
  ```
  Easy-Es will execute the following Query:
    ```json
    {"query":{"bool":{"must":[{"term":{"title":{"value":"Hello World","boost":1.0}}},{"term":{"creator":{"value":"Guy","boost":1.0}}}],"adjust_pure_negative":true,"boost":1.0}}}
    ```

  The syntax of this query in MySQL is:
  ```sql
   SELECT * FROM document WHERE title = 'Hello World' AND creator = 'Guy'
  ```

> This showcase is just a small part of Easy-Es features. If you want to learn more, please refer to the [documentation](https://easy-es.cn/#/en/).

## Architecture

![Architecture](https://iknow.hs.net/27fb40b8-22d4-45c2-92e0-1471112d5102.jpg)

## MySQL Easy-Es and Es syntax comparison table

| MySQL | Easy-Es | Es-DSL/Es java api|
| --- | --- |--- |
| and | and |must|
| or | or | should|
| = | eq | term|
| != | ne | boolQueryBuilder.mustNot(queryBuilder)|
| > | gt | QueryBuilders.rangeQuery('es field').gt()|
| >= | ge | .rangeQuery('es field').gte()|
| < | lt | .rangeQuery('es field').lt() |
| <= | le | .rangeQuery('es field').lte()| 
| like '%field%' | like | QueryBuilders.wildcardQuery(field,*value*)|
| not like '%field%' | notLike | must not wildcardQuery(field,*value*)|
| like '%field' | likeLeft | QueryBuilders.wildcardQuery(field,*value)|
| like 'field%' | likeRight | QueryBuilders.wildcardQuery(field,value*)|
| between | between | QueryBuilders.rangeQuery('es field').from(xx).to(xx) |
| notBetween | notBetween | must not QueryBuilders.rangeQuery('es field').from(xx).to(xx)|
| is null | isNull | must not QueryBuilders.existsQuery(field) |
| is notNull | isNotNull | QueryBuilders.existsQuery(field)|
| in | in | QueryBuilders.termsQuery(" xx es field", xx)|
| not in | notIn | must not QueryBuilders.termsQuery(" xx es field", xx)|
| group by | groupBy | AggregationBuilders.terms()|
| order by | orderBy | fieldSortBuilder.order(ASC/DESC)|
| min | min | AggregationBuilders.min|
| max | max |AggregationBuilders.max|
| avg | avg |AggregationBuilders.avg|
| sum | sum |AggregationBuilders.sum| 
| order by xxx asc | orderByAsc | fieldSortBuilder.order(SortOrder.ASC)|
| order by xxx desc | orderByDesc |fieldSortBuilder.order(SortOrder.DESC)|
| - | match |matchQuery|
| - | matchPhrase |QueryBuilders.matchPhraseQuery|
| - | matchPrefix |QueryBuilders.matchPhrasePrefixQuery|
| - | queryStringQuery |QueryBuilders.queryStringQuery|
| select * | matchAllQuery |QueryBuilders.matchAllQuery()|
| - | highLight |HighlightBuilder.Field |
| ... | ... | ...|
---

## Advertising provider
<a href="https://www.mingdao.com?s=utm_70&utm_source=easy-es&utm_medium=banner&utm_campaign=gitee&utm_content=IT%E8%B5%8B%E8%83%BD%E4%B8%9A%E5%8A%A1
">
  <img alt="ad" src="https://iknow.hs.net/00b4a54c-6505-4776-9232-f0a9d9768fac.jpg">
</a>

</br>

<a href="https://www.misboot.com/?from=easy-es">
  <img alt="ad" src="https://iknow.hs.net/68963214-7a61-4f38-b5b5-a068c07a35f1.png">
</a>

</a>

## Donate

[Donate Easy-Es](https://en.easy-es.cn/pages/fb291d/)


## License

Easy-Es is under the Apache 2.0 license. See the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0) file for details.
