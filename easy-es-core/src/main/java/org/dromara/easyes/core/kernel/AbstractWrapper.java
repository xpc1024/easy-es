package org.dromara.easyes.core.kernel;

import org.apache.lucene.search.join.ScoreMode;
import org.dromara.easyes.annotation.rely.FieldType;
import org.dromara.easyes.common.enums.AggregationTypeEnum;
import org.dromara.easyes.common.enums.EsQueryTypeEnum;
import org.dromara.easyes.common.enums.OrderTypeEnum;
import org.dromara.easyes.common.utils.*;
import org.dromara.easyes.core.biz.*;
import org.dromara.easyes.core.conditions.function.*;
import org.dromara.easyes.core.toolkit.EntityInfoHelper;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.geometry.Geometry;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.time.ZoneId;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.dromara.easyes.common.constants.BaseEsConstants.WILDCARD_SIGN;
import static org.dromara.easyes.common.enums.EsQueryTypeEnum.*;
import static org.dromara.easyes.common.enums.OrderTypeEnum.CUSTOMIZE;

/**
 * 抽象Lambda表达式父类
 * <p>
 * Copyright © 2021 xpc1024 All Rights Reserved
 **/
public abstract class AbstractWrapper<T, R, Children extends AbstractWrapper<T, R, Children>> extends Wrapper<T>
        implements Compare<Children, R>, Nested<Children, Children>, Func<Children, R>, Join<Children>, Geo<Children, R>
        , Query<Children, T, R>, Update<Children, R>, Index<Children, R> {

    protected final Children typedThis = (Children) this;
    /**
     * 存放树的高度
     */
    protected int level;
    /**
     * 全局父节点 每次指向nested条件后
     */
    protected String parentId;
    /**
     * 上一节点类型
     */
    protected EsQueryTypeEnum prevQueryType;
    /**
     * 栈 存放父id
     */
    protected Stack<String> parentIdStack;
    /**
     * 队列 存放上一节点类型
     */
    protected LinkedList<EsQueryTypeEnum> prevQueryTypeQueue;

    public Children setEntity(T entity) {
        this.entity = entity;
        this.initEntityClass();
        return typedThis;
    }

    public Children setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.initEntityClass();
        return typedThis;
    }

    protected void initEntityClass() {
        if (this.entityClass == null && this.entity != null) {
            this.entityClass = (Class<T>) entity.getClass();
        }
    }

    protected Class<T> getCheckEntityClass() {
        Assert.notNull(entityClass, "entityClass must not null,please set entity before use this method!");
        return entityClass;
    }

    /**
     * 必要的初始化
     */
    protected final void initNeed() {
        baseSortParams = new ArrayList<>();
        aggregationParamList = new ArrayList<>();
        paramQueue = new LinkedList<>();
        prevQueryType = NESTED_AND;
        parentIdStack = new Stack<>();
        prevQueryTypeQueue = new LinkedList<>();
    }

    @Override
    public <V> Children allEq(boolean condition, Map<String, V> params) {
        if (condition && CollectionUtils.isNotEmpty(params)) {
            params.forEach(this::eq);
        }
        return typedThis;
    }

    @Override
    public <V> Children allEq(boolean condition, BiPredicate<String, V> filter, Map<String, V> params) {
        if (condition && CollectionUtils.isNotEmpty(params)) {
            params.forEach((k, v) -> {
                if (filter.test(k, v)) {
                    if (StringUtils.checkValNotNull(v)) {
                        eq(k, v);
                    }
                }
            });
        }
        return typedThis;
    }

    @Override
    public Children eq(boolean condition, String column, Object val, Float boost) {
        return addParam(condition, TERM, column, val, boost);
    }

    @Override
    public Children and(boolean condition, Consumer<Children> consumer) {
        return addNested(condition, NESTED_AND, consumer);
    }

    @Override
    public Children or(boolean condition, Consumer<Children> consumer) {
        return addNested(condition, NESTED_OR, consumer);
    }

    @Override
    public Children or(boolean condition) {
        // 需要将其前一同level节点prevQueryType修改为or_should
        for (int i = paramQueue.size() - 1; i >= 0; i--) {
            Param param = paramQueue.get(i);
            if (Objects.equals(level, param.getLevel())) {
                param.setPrevQueryType(NESTED_OR);
                break;
            }
        }
        return addParam(condition, OR, null, null, null);
    }

    @Override
    public Children must(boolean condition, Consumer<Children> consumer) {
        return addNested(condition, NESTED_AND, consumer);
    }

    @Override
    public Children should(boolean condition, Consumer<Children> consumer) {
        return addNested(condition, NESTED_OR, consumer);
    }

    @Override
    public Children filter(boolean condition, Consumer<Children> consumer) {
        return addNested(condition, NESTED_FILTER, consumer);
    }

    @Override
    public Children filter(boolean condition) {
        return addParam(condition, FILTER, null, null, null);
    }

    @Override
    public Children not(boolean condition) {
        return addParam(condition, NOT, null, null, null);
    }

    @Override
    public Children not(boolean condition, Consumer<Children> consumer) {
        return addNested(condition, NESTED_NOT, consumer);
    }

    @Override
    public Children nested(boolean condition, String path, Consumer<Children> consumer, ScoreMode scoreMode) {
        return addNested(condition, path, scoreMode, consumer);
    }

    @Override
    public Children match(boolean condition, String column, Object val, Float boost) {
        return addParam(condition, MATCH, column, val, boost);
    }

    @Override
    public Children hasChild(boolean condition, String type, Consumer<Children> consumer, ScoreMode scoreMode) {
        return addJoin(condition, HAS_CHILD, type, scoreMode, consumer);
    }

    @Override
    public Children hasParent(boolean condition, String parentType, Consumer<Children> consumer, boolean score) {
        return addJoin(condition, HAS_PARENT, parentType, score, consumer);
    }

    @Override
    public Children parentId(boolean condition, Object parentId, String type, Float boost) {
        if (condition) {
            Assert.notNull(parentId, "parentId could not be null");
        }
        return addParam(condition, PARENT_ID, type, parentId, boost);
    }

    @Override
    public Children matchPhrase(boolean condition, String column, Object val, Float boost) {
        return addParam(condition, MATCH_PHRASE, column, val, boost);
    }

    @Override
    public Children matchAllQuery(boolean condition, Float boost) {
        return addParam(condition, MATCH_ALL, null, null, boost);
    }

    @Override
    public Children matchPhrasePrefixQuery(boolean condition, String column, Object val, int maxExpansions, Float boost) {
        return addParam(condition, MATCH_PHRASE_PREFIX, column, val, maxExpansions, null, boost);
    }

    @SafeVarargs
    @Override
    public final Children multiMatchQuery(boolean condition, Object val, Operator operator, int minimumShouldMatch, Float boost, String... columns) {
        return addParam(condition, val, operator, minimumShouldMatch, boost, columns);
    }

    @Override
    public Children queryStringQuery(boolean condition, String queryString, Float boost) {
        return addParam(condition, QUERY_STRING, queryString, null, boost);
    }

    @Override
    public Children prefixQuery(boolean condition, String column, String prefix, Float boost) {
        if (condition) {
            Assert.notBlank(prefix, "prefix can't be blank");
        }
        return addParam(condition, PREFIX, column, prefix, boost);
    }


    @Override
    public Children gt(boolean condition, String column, Object val, ZoneId timeZone, String format, Float boost) {
        return addParam(condition, GT, column, val, timeZone, format, boost);
    }

    @Override
    public Children ge(boolean condition, String column, Object val, ZoneId timeZone, String format, Float boost) {
        return addParam(condition, GE, column, val, timeZone, format, boost);
    }

    @Override
    public Children lt(boolean condition, String column, Object val, ZoneId timeZone, String format, Float boost) {
        return addParam(condition, LT, column, val, timeZone, format, boost);
    }

    @Override
    public Children le(boolean condition, String column, Object val, ZoneId timeZone, String format, Float boost) {
        return addParam(condition, LE, column, val, timeZone, format, boost);
    }

    @Override
    public Children between(boolean condition, String column, Object from, Object to, ZoneId timeZone, String format, Float boost) {
        return addParam(condition, column, from, to, timeZone, format, boost);
    }

    @Override
    public Children like(boolean condition, String column, Object val, Float boost) {
        val = Optional.ofNullable(val)
                .map(v -> WILDCARD_SIGN + v + WILDCARD_SIGN)
                .orElse(WILDCARD_SIGN);
        return addParam(condition, WILDCARD, column, val, boost);
    }

    @Override
    public Children likeLeft(boolean condition, String column, Object val, Float boost) {
        val = Optional.ofNullable(val)
                .map(v -> WILDCARD_SIGN + v)
                .orElse(WILDCARD_SIGN);
        return addParam(condition, WILDCARD, column, val, boost);
    }

    @Override
    public Children likeRight(boolean condition, String column, Object val, Float boost) {
        val = Optional.ofNullable(val)
                .map(v -> v + WILDCARD_SIGN)
                .orElse(WILDCARD_SIGN);
        return addParam(condition, WILDCARD, column, val, boost);
    }

    @Override
    public Children in(boolean condition, String column, Collection<?> coll, Float boost) {
        if (CollectionUtils.isEmpty(coll)) {
            return typedThis;
        }
        return addParam(condition, TERMS, column, coll, boost);
    }

    @Override
    public Children exists(boolean condition, String column, Float boost) {
        return addParam(condition, EXISTS, column, null, boost);
    }

    @Override
    public Children geoBoundingBox(boolean condition, String column, String topLeft, String bottomRight, Float boost) {
        if (condition) {
            Assert.notBlank(topLeft, "TopLeft must not be null in geoBoundingBox query");
            Assert.notBlank(bottomRight, "BottomRight must not be null in geoBoundingBox query");
            return geoBoundingBox(true, column, new GeoPoint(topLeft), new GeoPoint(bottomRight), boost);
        }
        return typedThis;
    }

    @Override
    public Children geoBoundingBox(boolean condition, String column, GeoPoint topLeft, GeoPoint bottomRight, Float boost) {
        if (condition) {
            Assert.notNull(topLeft, "TopLeft point must not be null in geoBoundingBox query");
            Assert.notNull(bottomRight, "BottomRight point must not be null in geoBoundingBox query");
        }
        return addParam(condition, GEO_BOUNDING_BOX, column, null, topLeft, bottomRight, boost);
    }

    @Override
    public Children geoDistance(boolean condition, String column, Double distance, DistanceUnit distanceUnit, GeoPoint centralGeoPoint, Float boost) {
        if (condition) {
            Assert.notNull(distance, "Distance must not be null in geoDistance query");
            Assert.notNull(distanceUnit, "Distance unit must not be null in geoDistance query");
            Assert.notNull(centralGeoPoint, "CentralGeoPoint must not be null in geoDistance query");
        }
        return addParam(condition, GEO_DISTANCE, column, distance, distanceUnit, centralGeoPoint, boost);
    }

    @Override
    public Children geoDistance(boolean condition, String column, Double distance, DistanceUnit distanceUnit, String centralGeoPoint, Float boost) {
        if (condition) {
            Assert.notBlank(centralGeoPoint, "centralGeoPoint must not be null in geoDistance query");
            return geoDistance(true, column, distance, distanceUnit, new GeoPoint(centralGeoPoint), boost);
        }
        return typedThis;
    }

    @Override
    public Children geoDistance(boolean condition, String column, String distance, GeoPoint centralGeoPoint, Float boost) {
        if (condition) {
            Assert.notBlank(distance, "Distance must not be null in geoDistance query");
            Assert.notNull(centralGeoPoint, "CentralGeoPoint must not be null in geoDistance query");
        }
        return addParam(condition, GEO_DISTANCE, column, distance, null, centralGeoPoint, boost);
    }

    @Override
    public Children geoDistance(boolean condition, String column, String distance, String centralGeoPoint, Float boost) {
        if (condition) {
            Assert.notBlank(centralGeoPoint, "centralGeoPoint must not be null in geoDistance query");
            return geoDistance(true, column, distance, new GeoPoint(centralGeoPoint), boost);
        }
        return typedThis;
    }

    @Override
    public Children geoPolygon(boolean condition, String column, List<GeoPoint> geoPoints, Float boost) {
        if (condition) {
            Assert.notEmpty(geoPoints, "GeoPoints must not be null in geoPolygon query");
        }
        return addParam(condition, GEO_POLYGON, column, geoPoints, boost);
    }

    @Override
    public Children geoShape(boolean condition, String column, String indexedShapeId, Float boost) {
        if (condition) {
            Assert.notNull(indexedShapeId, "IndexedShapeId must not be null in geoShape query");
        }
        return addParam(condition, GEO_SHAPE_ID, column, indexedShapeId, boost);
    }

    @Override
    public Children geoShape(boolean condition, String column, Geometry geometry, ShapeRelation shapeRelation, Float boost) {
        if (condition) {
            Assert.notNull(geometry, "Geometry must not be null in geoShape query");
            Assert.notNull(geometry, "ShapeRelation must not be null in geoShape query");
        }
        return addParam(condition, GEO_SHAPE, column, geometry, shapeRelation, null, boost);
    }

    @Override
    public final Children orderBy(boolean condition, boolean isAsc, String... columns) {
        if (ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }

        if (condition) {
            Arrays.stream(columns)
                    .forEach(column -> {
                        BaseSortParam baseSortParam = BaseSortParam.builder()
                                .sortField(column)
                                .sortOrder(isAsc ? SortOrder.ASC : SortOrder.DESC)
                                .orderTypeEnum(OrderTypeEnum.FIELD)
                                .build();
                        baseSortParams.add(baseSortParam);
                    });
        }
        return typedThis;
    }

    @Override
    public Children orderBy(boolean condition, List<OrderByParam> orderByParams) {
        if (condition && CollectionUtils.isNotEmpty(orderByParams)) {
            this.orderByParams = orderByParams;
        }
        return typedThis;
    }

    @Override
    public Children orderByDistanceAsc(boolean condition, String column, DistanceUnit unit, GeoDistance geoDistance, GeoPoint... geoPoints) {
        if (ArrayUtils.isNotEmpty(geoPoints)) {
            if (condition) {
                BaseSortParam baseSortParam = BaseSortParam.builder()
                        .sortField(column)
                        .sortOrder(SortOrder.ASC)
                        .orderTypeEnum(OrderTypeEnum.GEO)
                        .geoPoints(geoPoints)
                        .unit(unit)
                        .geoDistance(geoDistance)
                        .build();
                baseSortParams.add(baseSortParam);
            }
        }
        return typedThis;
    }

    @Override
    public Children orderByDistanceDesc(boolean condition, String column, DistanceUnit unit, GeoDistance geoDistance, GeoPoint... geoPoints) {
        if (ArrayUtils.isNotEmpty(geoPoints)) {
            if (condition) {
                BaseSortParam baseSortParam = BaseSortParam.builder()
                        .sortField(column)
                        .sortOrder(SortOrder.DESC)
                        .orderTypeEnum(OrderTypeEnum.GEO)
                        .geoPoints(geoPoints)
                        .unit(unit)
                        .geoDistance(geoDistance)
                        .build();
                baseSortParams.add(baseSortParam);
            }
        }
        return typedThis;
    }

    @Override
    public Children sort(boolean condition, List<SortBuilder<?>> sortBuilders) {
        if (CollectionUtils.isEmpty(sortBuilders)) {
            return typedThis;
        }
        if (condition) {
            sortBuilders.forEach(sortBuilder -> {
                BaseSortParam baseSortParam = BaseSortParam
                        .builder()
                        .orderTypeEnum(CUSTOMIZE)
                        .sortBuilder(sortBuilder)
                        .build();
                baseSortParams.add(baseSortParam);
            });
        }
        return typedThis;
    }

    @Override
    public Children sortByScore(boolean condition, SortOrder sortOrder) {
        if (condition) {
            BaseSortParam baseSortParam = BaseSortParam.builder()
                    .sortOrder(sortOrder)
                    .orderTypeEnum(OrderTypeEnum.SCORE)
                    .build();
            baseSortParams.add(baseSortParam);
        }
        return typedThis;
    }


    @Override
    public final Children groupBy(boolean condition, boolean enablePipeline, String... columns) {
        if (!condition || ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }
        Arrays.stream(columns).forEach(column -> doIt(condition, enablePipeline, AggregationTypeEnum.TERMS, column));
        return typedThis;
    }

    @Override
    public Children termsAggregation(boolean condition, boolean enablePipeline, String... columns) {
        if (!condition || ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }
        Arrays.stream(columns).forEach(column -> doIt(condition, enablePipeline, AggregationTypeEnum.TERMS, column));
        return typedThis;
    }

    @Override
    public Children avg(boolean condition, boolean enablePipeline, String... columns) {
        if (!condition || ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }
        Arrays.stream(columns).forEach(column -> doIt(condition, enablePipeline, AggregationTypeEnum.AVG, column));
        return typedThis;
    }

    @Override
    public Children min(boolean condition, boolean enablePipeline, String... columns) {
        if (!condition || ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }
        Arrays.stream(columns).forEach(column -> doIt(condition, enablePipeline, AggregationTypeEnum.MIN, column));
        return typedThis;
    }

    @Override
    public Children max(boolean condition, boolean enablePipeline, String... columns) {
        if (!condition || ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }
        Arrays.stream(columns).forEach(column -> doIt(condition, enablePipeline, AggregationTypeEnum.MAX, column));
        return typedThis;
    }

    @Override
    public Children sum(boolean condition, boolean enablePipeline, String... columns) {
        if (!condition || ArrayUtils.isEmpty(columns)) {
            return typedThis;
        }
        Arrays.stream(columns).forEach(column -> doIt(condition, enablePipeline, AggregationTypeEnum.SUM, column));
        return typedThis;
    }

    @Override
    public Children distinct(boolean condition, String column) {
        if (condition) {
            this.distinctField = column;
        }
        return typedThis;
    }

    @Override
    public Children from(Integer from) {
        this.from = from;
        return typedThis;
    }

    @Override
    public Children size(Integer size) {
        this.size = size;
        return typedThis;
    }

    @Override
    public Children limit(Integer m) {
        this.size = m;
        return typedThis;
    }

    @Override
    public Children limit(Integer m, Integer n) {
        this.from = m;
        this.size = n;
        return typedThis;
    }

    @Override
    public Children index(boolean condition, String... indexNames) {
        if (condition) {
            if (ArrayUtils.isEmpty(indexNames)) {
                throw ExceptionUtils.eee("indexNames can not be empty");
            }
            this.indexNames = indexNames;
        }
        return typedThis;
    }

    @Override
    public Children routing(boolean condition, String routing) {
        if (condition) {
            this.routing = routing;
        }
        return typedThis;
    }

    @Override
    public Children preference(boolean condition, String preference) {
        if (condition) {
            if (StringUtils.isBlank(preference)) {
                return typedThis;
            }
            this.preference = preference;
        }
        return typedThis;
    }

    @Override
    public Children setSearchSourceBuilder(boolean condition, SearchSourceBuilder searchSourceBuilder) {
        if (condition) {
            this.searchSourceBuilder = searchSourceBuilder;
        }
        return typedThis;
    }

    @Override
    public Children mix(boolean condition, QueryBuilder queryBuilder) {
        return addParam(condition, queryBuilder);
    }

    @Override
    public Children bucketOrder(boolean condition, List<BucketOrder> bucketOrders) {
        if (condition) {
            this.bucketOrders = bucketOrders;
        }
        return typedThis;
    }

    @Override
    public Children select(String... columns) {
        this.include = columns;
        return typedThis;
    }

    @Override
    public Children select(Predicate<EntityFieldInfo> predicate) {
        return select(entityClass, predicate);
    }

    @Override
    public Children select(Class<T> entityClass, Predicate<EntityFieldInfo> predicate) {
        this.entityClass = entityClass;
        List<String> list = EntityInfoHelper.getEntityInfo(getCheckEntityClass()).chooseSelect(predicate);
        include = list.toArray(include);
        return typedThis;
    }

    @Override
    public Children minScore(Float score) {
        minScore = score;
        return typedThis;
    }

    @Override
    public Children trackScores() {
        trackScores = Boolean.TRUE;
        return typedThis;
    }

    @Override
    public Children notSelect(String... columns) {
        this.exclude = columns;
        return typedThis;
    }


    @Override
    public Children set(boolean condition, String column, Object val) {
        if (condition) {
            EsUpdateParam esUpdateParam = new EsUpdateParam();
            esUpdateParam.setField(column);
            esUpdateParam.setValue(val);
            updateParamList.add(esUpdateParam);
        }
        return typedThis;
    }

    @Override
    public Children indexName(String... indexNames) {
        if (ArrayUtils.isEmpty(indexNames)) {
            throw new RuntimeException("indexNames can not be empty");
        }
        this.indexNames = indexNames;
        return typedThis;
    }

    @Override
    public Children maxResultWindow(Integer maxResultWindow) {
        Optional.ofNullable(maxResultWindow).ifPresent(max -> this.maxResultWindow = maxResultWindow);
        return typedThis;
    }

    @Override
    public Children settings(Integer shards, Integer replicas) {
        if (Objects.nonNull(shards)) {
            this.shardsNum = shards;
        }
        if (Objects.nonNull(replicas)) {
            this.replicasNum = replicas;
        }
        return typedThis;
    }

    @Override
    public Children settings(Settings settings) {
        this.settings = settings;
        return typedThis;
    }

    @Override
    public Children mapping(Map<String, Object> mapping) {
        this.mapping = mapping;
        return typedThis;
    }

    @Override
    public Children mapping(String column, FieldType fieldType, String analyzer, String searchAnalyzer, String dateFormat, Boolean fieldData, Float boost) {
        addEsIndexParam(column, fieldType, analyzer, searchAnalyzer, dateFormat, fieldData, boost);
        return typedThis;
    }

    @Override
    public Children createAlias(String aliasName) {
        if (ArrayUtils.isEmpty(indexNames)) {
            throw new RuntimeException("indexNames can not be empty");
        }
        if (StringUtils.isEmpty(aliasName)) {
            throw new RuntimeException("aliasName can not be empty");
        }
        this.aliasName = aliasName;
        return typedThis;
    }

    @Override
    public Children join(String column, String parentName, String childName) {
        EsIndexParam esIndexParam = new EsIndexParam();
        esIndexParam.setFieldName(column);
        esIndexParam.setFieldType(FieldType.JOIN.getType());
        esIndexParamList.add(esIndexParam);
        return typedThis;
    }

    /**
     * 子类返回一个自己的新对象
     *
     * @return wrapper
     */
    protected abstract Children instance();


    /**
     * 追加基础查询参数
     *
     * @param param         被追加的新参数
     * @param queryTypeEnum 查询类型
     * @param column        列
     * @param val           值
     * @param boost         权重
     */
    private void addBaseParam(Param param, EsQueryTypeEnum queryTypeEnum, String column, Object val, Float boost) {
        // 基本值设置
        param.setId(UUID.randomUUID().toString());
        Optional.ofNullable(parentId).ifPresent(param::setParentId);
        param.setPrevQueryType(prevQueryType);
        param.setQueryTypeEnum(queryTypeEnum);
        param.setVal(val);
        param.setColumn(column);
        param.setBoost(boost);
        param.setLevel(level);

        // 入队之前需要先对MP中的拼接类型特殊处理
        processJoin(param);
        paramQueue.add(param);
    }

    /**
     * 追加基础嵌套 整个框架最难的点之一
     *
     * @param param         参数
     * @param queryTypeEnum 查询类型
     * @param consumer      消费者
     */
    private void addBaseNested(Param param, EsQueryTypeEnum queryTypeEnum, Consumer<Children> consumer) {
        // 基本值设置
        param.setId(UUID.randomUUID().toString());
        Optional.ofNullable(parentId).ifPresent(param::setParentId);
        param.setQueryTypeEnum(queryTypeEnum);
        param.setLevel(level);
        param.setPrevQueryType(queryTypeEnum);

        // 入队之前需要先对MP中的拼接类型特殊处理
        processJoin(param);

        paramQueue.add(param);
        this.parentId = param.getId();
        parentIdStack.push(parentId);
        level++;
        consumer.accept(instance());
        // 深度优先在consumer条件消费完后会来执行这里 此时parentId需要重置 至于为什么,比较烧脑 可断点打在consumer前后观察一波
        level--;
        if (!parentIdStack.isEmpty()) {
            // 丢弃栈顶 当前id
            parentIdStack.pop();

            if (parentIdStack.isEmpty()) {
                // 仙人板板 根节点
                parentId = null;
            } else {
                // 非根节点 取其上一节点id作为爸爸id
                this.parentId = parentIdStack.peek();
            }
        }
    }


    /**
     * 特殊处理拼接
     *
     * @param param 参数
     */
    private void processJoin(Param param) {
        // 重置前一节点类型
        if (!paramQueue.isEmpty()) {
            Param prev = paramQueue.peekLast();
            if (OR.equals(prev.getQueryTypeEnum())) {
                // 上一节点是拼接or() 需要重置其prevQueryType类型,让其走should查询
                param.setPrevQueryType(NESTED_OR);
            } else if (NOT.equals(prev.getQueryTypeEnum())) {
                // 上一节点是拼接not() 需要重置其prevQueryType类型,让其走must_not查询
                param.setPrevQueryType(NESTED_NOT);
            } else if (FILTER.equals(prev.getPrevQueryType())) {
                // 上一节点是拼接filter() 需要重置其prevQueryType类型,让其走filter查询
                param.setPrevQueryType(NESTED_FILTER);
            }
        }
    }

    /**
     * 追加查询参数
     *
     * @param condition    执行条件
     * @param queryBuilder 原生查询参数
     * @return wrapper
     */
    private Children addParam(boolean condition, QueryBuilder queryBuilder) {
        if (condition) {
            Param param = new Param();
            param.setQueryBuilder(queryBuilder);
            addBaseParam(param, MIX, null, null, null);
        }
        return typedThis;
    }

    /**
     * 追加查询参数
     *
     * @param condition     条件
     * @param queryTypeEnum 查询类型
     * @param column        列
     * @param val           值
     * @param boost         权重
     * @return wrapper
     */
    private Children addParam(boolean condition, EsQueryTypeEnum queryTypeEnum, String column, Object val, Float boost) {
        if (condition) {
            Param param = new Param();
            addBaseParam(param, queryTypeEnum, column, val, boost);
        }
        return typedThis;
    }

    /**
     * 重载，追加拓展参数
     *
     * @param condition     条件
     * @param queryTypeEnum 查询类型
     * @param column        列
     * @param val           值
     * @param var1          拓展字段1
     * @param var2          拓展字段2
     * @param boost         权重
     * @return wrapper
     */
    private Children addParam(boolean condition, EsQueryTypeEnum queryTypeEnum, String column, Object val, Object var1, Object var2, Float boost) {
        if (condition) {
            Param param = new Param();
            param.setExt1(var1);
            param.setExt2(var2);
            addBaseParam(param, queryTypeEnum, column, val, boost);
        }
        return typedThis;
    }


    /**
     * 重载，追加拓展参数
     *
     * @param condition 条件
     * @param column    列
     * @param var1      拓展字段1
     * @param var2      拓展字段2
     * @param var3      拓展字段3
     * @param var4      拓展字段4
     * @param boost     权重
     * @return wrapper
     */
    private Children addParam(boolean condition, String column, Object var1, Object var2, Object var3, Object var4, Float boost) {
        if (condition) {
            Assert.notNull(var1, "from must not be null in between query");
            Assert.notNull(var2, "to must not be null in between query");
            Param param = new Param();
            param.setExt1(var1);
            param.setExt2(var2);
            param.setExt3(var3);
            param.setExt4(var4);
            addBaseParam(param, BETWEEN, column, null, boost);
        }
        return typedThis;
    }

    /**
     * 重载，追加拓展参数
     *
     * @param condition          条件
     * @param val                值
     * @param operator           操作符
     * @param minimumShouldMatch 最小匹配值
     * @param boost              权重
     * @param columns            列数组
     * @return wrapper
     */
    private Children addParam(boolean condition, Object val, Operator operator, int minimumShouldMatch, Float boost, String... columns) {
        if (condition) {
            Param param = new Param();
            param.setExt1(operator);
            param.setExt2(minimumShouldMatch);
            param.setColumns(columns);
            addBaseParam(param, MULTI_MATCH, null, val, boost);
        }
        return typedThis;
    }


    /**
     * 重载，追加嵌套条件
     *
     * @param condition     条件
     * @param queryTypeEnum 查询类型
     * @param consumer      消费者
     * @return wrapper
     */
    private Children addNested(boolean condition, EsQueryTypeEnum queryTypeEnum, Consumer<Children> consumer) {
        if (condition) {
            Param param = new Param();
            addBaseNested(param, queryTypeEnum, consumer);
        }
        return typedThis;
    }

    /**
     * 重载，追加加嵌套类型查询条件
     *
     * @param condition 条件
     * @param path      路径
     * @param ext       评分模式 或 是否计算评分
     * @param consumer  消费者
     * @return wrapper
     */
    private Children addJoin(boolean condition, EsQueryTypeEnum queryTypeEnum, String path, Object ext, Consumer<Children> consumer) {
        if (condition) {
            Param param = new Param();
            param.setColumn(path);
            param.setVal(ext);
            addBaseNested(param, queryTypeEnum, consumer);
        }
        return typedThis;
    }

    /**
     * 重载，追加加嵌套类型查询条件
     *
     * @param condition 条件
     * @param path      路径
     * @param scoreMode 评分模式
     * @param consumer  消费者
     * @return wrapper
     */
    private Children addNested(boolean condition, String path, ScoreMode scoreMode, Consumer<Children> consumer) {
        if (condition) {
            Param param = new Param();
            param.setColumn(path);
            param.setVal(scoreMode);
            addBaseNested(param, NESTED, consumer);
        }
        return typedThis;
    }


    /**
     * 封装查询参数 聚合类
     *
     * @param condition           条件
     * @param enablePipeline      是否管道聚合
     * @param aggregationTypeEnum 聚合类型
     * @param column              列
     * @return wrapper
     */
    private Children doIt(boolean condition, boolean enablePipeline, AggregationTypeEnum aggregationTypeEnum, String column) {
        if (condition) {
            AggregationParam aggregationParam = new AggregationParam();
            aggregationParam.setEnablePipeline(enablePipeline);
            aggregationParam.setName(column);
            aggregationParam.setField(column);
            aggregationParam.setAggregationType(aggregationTypeEnum);
            aggregationParamList.add(aggregationParam);
        }
        return typedThis;
    }

    /**
     * 添加索引参数
     *
     * @param fieldName      字段名
     * @param fieldType      字段类型
     * @param analyzer       查询分词器
     * @param searchAnalyzer 索引分词器
     * @param dateFormat     日期格式化规则
     * @param fieldData      是否将text类型字段添加fieldData,fieldData为true时,则text字段也支持聚合
     * @param boost          权重
     */
    private void addEsIndexParam(String fieldName, FieldType fieldType, String analyzer, String searchAnalyzer, String dateFormat, Boolean fieldData, Float boost) {
        EsIndexParam esIndexParam = new EsIndexParam();
        esIndexParam.setFieldName(fieldName);
        esIndexParam.setFieldType(fieldType.getType());
        esIndexParam.setAnalyzer(analyzer);
        esIndexParam.setSearchAnalyzer(searchAnalyzer);
        esIndexParam.setDateFormat(dateFormat);
        esIndexParam.setFieldData(fieldData);
        esIndexParam.setBoost(boost);
        esIndexParamList.add(esIndexParam);
    }

}
