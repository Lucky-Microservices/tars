package com.alibaba.csp.sentinel.dashboard.config;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.http.nio.reactor.IOReactorException;
import org.opentsdb.client.OpenTSDBClient;
import org.opentsdb.client.OpenTSDBClientFactory;
import org.opentsdb.client.OpenTSDBConfig;
import org.opentsdb.client.bean.request.Point;
import org.opentsdb.client.bean.response.DetailResult;
import org.opentsdb.client.http.callback.BatchPutHttpResponseCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * OpenTSDB 配置类
 *
 * @author <a href="mailto:jiashuai.xie01@gmail.com">Xiejiashuai</a>
 * @since 1.6.3-ext.RELEASE
 */
@Configuration
@Validated
@ConfigurationProperties(prefix = "sentinel.open-tsdb")
public class OpenTSDBClientConfig {

	private static Logger logger = LoggerFactory.getLogger(OpenTSDBClientConfig.class);

	@NotBlank(message = "host can not be blank")
	private String host;

	@NotNull(message = "port can not be null")
	private Integer port;

	@Bean
	public OpenTSDBConfig openTSDBConfig() {
		return OpenTSDBConfig.address(host, port)
				// 每批数据提交完成后回调
				.batchPutCallBack(new BatchPutHttpResponseCallback.BatchPutCallBack() {
					@Override
					public void response(List<Point> points, DetailResult result) {
						// 在请求完成并且response code成功时回调
					}

					@Override
					public void responseError(List<Point> points, DetailResult result) {
						// 在response code失败时回调
					}

					@Override
					public void failed(List<Point> points, Exception e) {
						// 在发生错误是回调
					}
				}).config();
	}

	@Bean
	public OpenTSDBClient openTSDBClient(OpenTSDBConfig config)
			throws IOReactorException {
		return OpenTSDBClientFactory.connect(config);
	}

}
