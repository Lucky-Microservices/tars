package com.alibaba.csp.sentinel.dashboard.repository.metric;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import org.opentsdb.client.OpenTSDBClient;
import org.opentsdb.client.bean.request.Point;
import org.opentsdb.client.bean.request.Query;
import org.opentsdb.client.bean.request.SubQuery;
import org.opentsdb.client.bean.response.QueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * OpenTSDB {@link MetricEntity} implement
 *
 * @author <a href="mailto:jiashuai.xie01@gmail.com">Xiejiashuai</a>
 * @since 1.6.3-ext.RELEASE
 */
@SuppressWarnings("all")
@Repository("openTSDBMetricsRepository")
public class OpenTSDBMetricsRepository implements MetricsRepository<MetricEntity> {

	private static String OPEN_TSDB_TAG_TYPE = "type";

	@Autowired
	private OpenTSDBClient openTSDBClient;

	/**
	 * key:app value:资源集合
	 */
	private Cache<String, Set<String>> cache = CacheBuilder.newBuilder()
			.maximumSize(100000)
			// 设置缓存时间 1 小时
			.expireAfterWrite(1, TimeUnit.HOURS).build();

	@Override
	public void save(MetricEntity metric) {

		if (null == metric) {
			return;
		}

		// 缓存app 和资源的映射关系
		cacheAppResourceMapping(metric);

		// 构建metricName
		String metricName = buildMetricName(metric.getApp(), metric.getResource());

		// 时间戳
		long time = metric.getTimestamp().getTime();

		List<Point> points = new ArrayList<>();

		Stream.of(OpenTSDBTypeEnum.values()).forEach(openTSDBTypeEnum -> {

			Point.MetricBuilder metricBuilder = Point.metric(metricName);

			// 构建 tags
			metricBuilder.tag(OPEN_TSDB_TAG_TYPE, openTSDBTypeEnum.name());

			// 构建metric值
			setMetricValue(metric, metricBuilder, time, openTSDBTypeEnum);

			Point point = metricBuilder.build();

			openTSDBClient.put(point);
		});

	}

	@Override
	public void saveAll(Iterable<MetricEntity> metrics) {
		if (metrics == null) {
			return;
		}
		metrics.forEach(this::save);
	}

	@Override
	public List<MetricEntity> queryByAppAndResourceBetween(String app, String resource,
			long startTime, long endTime) {

		Query query = Query.begin(startTime).end(endTime)
				.sub(SubQuery.metric(buildMetricName(app, resource))
						.aggregator(SubQuery.Aggregator.NONE).build())
				.build();
		try {

			List<QueryResult> results = openTSDBClient.query(query);

			results = results.stream()
					.filter(result -> !CollectionUtils.isEmpty(result.getDps()))
					.collect(Collectors.toList());

			if (CollectionUtils.isEmpty(results)) {
				return new ArrayList<>();
			}

			// QueryResult 代表一个metric name 下的指定tag的所有时间戳结果
			// 比如 metricName = order-/create tag:type=passQps
			// 从1564544135到1564544494(秒)下的统计结果
			// dps = [("1564544135":30),("1564544399": 30),("1564544494": 30)]
			// 换言之 dps的数量代表着指定时间段内MetricEntity的数量
			int size = results.get(0).getDps().size();
			List<MetricEntity> metricEntities = new ArrayList<>(size);

			for (int i = 0; i < size; i++) {
				MetricEntity metricEntity = new MetricEntity();
				metricEntity.setApp(app);
				metricEntity.setResource(resource);
				metricEntities.add(metricEntity);
			}

			ArrayList<Long> timestamps = new ArrayList<>(
					results.get(0).getDps().keySet());

			for (int i = 0; i < size; i++) {

				MetricEntity metricEntity = metricEntities.get(i);

				for (QueryResult queryResult : results) {

					String type = queryResult.getTags().get(OPEN_TSDB_TAG_TYPE);

					Long timestamp = timestamps.get(i);
					metricEntity.setGmtCreate(new Date(timestamp * 1000));
					metricEntity.setTimestamp(new Date(timestamp * 1000));

					Number value = queryResult.getDps().get(timestamp);
					setMetricEntityValue(type, value, metricEntity);
				}
			}

			return metricEntities;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public List<String> listResourcesOfApp(String app) {
		Set<String> resources = cache.getIfPresent(app);
		return resources == null ? new ArrayList<>() : new ArrayList<>(resources);
	}

	@PreDestroy
	public void destroy() throws IOException {
		// 优雅关闭
		openTSDBClient.gracefulClose();
	}

	/**
	 * 把资源和应用缓存起来
	 *
	 * @param metric 统计指标实体
	 */
	private void cacheAppResourceMapping(MetricEntity metric) {
		Set<String> resources = cache.getIfPresent(metric.getApp());

		if (resources == null) {
			synchronized (cache) {
				resources = cache.getIfPresent(metric.getApp());
				if (resources == null) {
					cache.put(metric.getApp(), new HashSet<>());
				}
				resources = cache.getIfPresent(metric.getApp());
			}
		}

		resources.add(metric.getResource());

		cache.put(metric.getApp(), resources);
	}

	private void setMetricEntityValue(String type, Number v, MetricEntity metricEntity) {
		switch (OpenTSDBTypeEnum.valueOf(type)) {
		case passQps:
			metricEntity.setPassQps(Long.valueOf(String.valueOf(v)));
			break;
		case rt:
			metricEntity.setRt(Double.valueOf(String.valueOf(v)));
			break;
		case successQps:
			metricEntity.setSuccessQps(Long.valueOf(String.valueOf(v)));
			break;
		case count:
			metricEntity.setCount(Integer.valueOf(String.valueOf(v)));
			break;
		case blockQps:
			metricEntity.setBlockQps(Long.valueOf(String.valueOf(v)));
			break;
		case exceptionQps:
			metricEntity.setExceptionQps(Long.valueOf(String.valueOf(v)));
			break;
		default:
			break;
		}
	}

	/**
	 * 构建metric名称
	 * 
	 * @param app 应用名字
	 * @param resource sentinel资源名字
	 * @return metric名称
	 */
	private String buildMetricName(String app, String resource) {
		return app + "-" + resource;
	}

	/**
	 * 给OpenTSDB指标设置指定时间戳上的值
	 * 
	 * @param metric sentinel指标
	 * @param metricBuilder OpenTSDB指标构建器
	 * @param time 时间戳
	 * @param openTSDBTypeEnum OpenTSDB指标类型
	 */
	private void setMetricValue(MetricEntity metric, Point.MetricBuilder metricBuilder,
			long time, OpenTSDBTypeEnum openTSDBTypeEnum) {
		switch (openTSDBTypeEnum) {
		case passQps:
			metricBuilder.value(time, metric.getPassQps());
			break;
		case rt:
			metricBuilder.value(time, metric.getRt());
			break;
		case successQps:
			metricBuilder.value(time, metric.getSuccessQps());
			break;
		case count:
			metricBuilder.value(time, metric.getCount());
			break;
		case blockQps:
			metricBuilder.value(time, metric.getBlockQps());
			break;
		case exceptionQps:
			metricBuilder.value(time, metric.getExceptionQps());
			break;
		default:
			break;
		}
	}

	/**
	 * {@link MetricEntity}存储到OpenTSDB中的数据类型
	 */
	public enum OpenTSDBTypeEnum {

		/**
		 * 总QPS
		 */
		passQps,

		/**
		 * 通过QPS
		 */
		successQps,

		/**
		 * 拒绝QPS
		 */
		blockQps,

		/**
		 * 异常QPS
		 */
		exceptionQps,

		/**
		 * 成功访问总响应时间
		 */
		rt,

		/**
		 * 聚合条数
		 */
		count;

	}

}
