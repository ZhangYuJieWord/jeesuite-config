/**
 * Confidential and Proprietary Copyright 2019 By 卓越里程教育科技有限公司 All Rights Reserved
 */
package com.jeesuite.confcenter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.jeesuite.common.http.HttpResponseEntity;
import com.jeesuite.common.http.HttpUtils;
import com.jeesuite.common.json.JsonUtils;
import com.jeesuite.common.util.ResourceUtils;

/**
 * 
 * <br>
 * Class Name   : InternalConfigChangeListener
 *
 * @author jiangwei
 * @version 1.0.0
 * @date 2019年7月18日
 */
public class InternalConfigChangeListener {

	private final static Logger logger = LoggerFactory.getLogger("com.jeesuite.confcenter");

	private final static String ROOT_PATH = "/confcenter";
	private ScheduledExecutorService hbScheduledExecutor;
	private ZkClientProxy zkClient;
	
	private String syncType;

	public InternalConfigChangeListener(String zkServers) {
		try {
			Class.forName("org.I0Itec.zkclient.ZkClient");
			if(StringUtils.isNotBlank(zkServers)){				
				zkClient = new ZkClientProxy(zkServers, 5000);
			}
		} catch (Exception e) {}
		
		ConfigcenterContext context = ConfigcenterContext.getInstance();
		if(zkClient != null && zkClient.isAvailable()){
			resisterZkListener(context);
			syncType = "zookeepr";
		}else{
			resisterHttpListener(context);
			syncType = "http";
		}
		logger.info("register ConfigChangeListener OK ,type:{}",syncType);
	}
	
	/**
	 * @return the syncType
	 */
	public String getSyncType() {
		return syncType;
	}



	public void close(){
		if(zkClient !=  null)zkClient.close();
		if(hbScheduledExecutor != null)hbScheduledExecutor.shutdown();
	}

	/**
	 * @param context
	 */
	private void resisterHttpListener(ConfigcenterContext context) {
		hbScheduledExecutor = Executors.newScheduledThreadPool(1);

		final String[] syncStatusUrls = new String[context.getApiBaseUrls().length];
		for (int i = 0; i < context.getApiBaseUrls().length; i++) {
			syncStatusUrls[i] = context.getApiBaseUrls()[i] + "/api/sync_status";
		}
		
		final Map<String, String> params = new HashMap<>();
		params.put("nodeId", context.getNodeId());
		params.put("appName", context.getApp());
		params.put("env", context.getEnv());
		hbScheduledExecutor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				// 由于初始化的时候还拿不到spring.cloud.client.ipAddress，故在同步过程上送
				if (context.isSpringboot()) {
					String serverip = ResourceUtils.getProperty("spring.cloud.client.ipAddress");
					if (StringUtils.isNotBlank(serverip)) {
						params.put("serverip", serverip);
					}
				}

				boolean updated = false;
				for (String url : syncStatusUrls) {
					url = ConfigcenterContext.getInstance().buildTokenParameter(url);
					HttpResponseEntity response = HttpUtils.postJson(url, JsonUtils.toJson(params),
							HttpUtils.DEFAULT_CHARSET);
					if(updated)return;
					// 刷新服务端更新的配置
					if (updated = response.isSuccessed()) {
						JsonNode jsonNode = JsonUtils.getNode(response.getBody(), "data");
						Map map = JsonUtils.toObject(jsonNode.toString(), Map.class);
						context.updateConfig(map);
					}
				}

			}
		}, 5, context.getSyncIntervalSeconds(), TimeUnit.SECONDS);
	}

	/**
	 * 
	 */
	private void resisterZkListener(ConfigcenterContext context) {
		String appParentPath = ROOT_PATH + "/" + context.getEnv() + "/" + context.getApp() + "/nodes";
		if(!zkClient.exists(appParentPath)){
			zkClient.createPersistent(appParentPath, true);
		}
		
		String appNodePath = appParentPath + "/" + context.getNodeId();
		
		if(zkClient.exists(appNodePath)){
			logger.info("node path[{}] exists",appNodePath);
			return;
		}
		//创建node节点
		zkClient.createEphemeral(appNodePath);
		zkClient.subscribeDataChanges(context, appNodePath);
		
	}

	
}
