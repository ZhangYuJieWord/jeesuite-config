package com.jeesuite.admin.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jeesuite.common.json.JsonUtils;
import com.jeesuite.common.util.DateUtils;

@Component
public class ConfigStateHolder implements InitializingBean{
	
	private static final String NOTIFY_UPLOAD_CMD = "upload";

	private static Logger logger = LoggerFactory.getLogger("configcenter");
	
	private final static String ZK_ROOT_PATH = "/confcenter";
	public static final String SYNC_TYPE_ZK = "zookeeper";
	
	private @Autowired Environment environment;
	
	private static Map<String, List<ConfigState>> configStates = new ConcurrentHashMap<>();
	
	private static ZkClient zkClient;

	public void afterPropertiesSet() throws Exception {
		try {
			String zkServers = environment.getProperty("cc.sync.zkServers");
			if(StringUtils.isNotBlank(zkServers)){				
				ZkConnection zkConnection = new ZkConnection(zkServers);
				zkClient = new ZkClient(zkConnection, 3000);
				logger.info("config_sync_zookeeper {} init finish",zkServers);
				
				syncConfigIfMiss();
				
			}
		} catch (Exception e) {
			zkClient = null;
			logger.error("init config_sync_zookeeper error",e);
		}
	}
	
	private void syncConfigIfMiss(){
		if(!zkClient.exists(ZK_ROOT_PATH)){
			return;
		}
		
		List<String> envs = zkClient.getChildren(ZK_ROOT_PATH);
		
		String parentPath;
		List<String> apps;
		for (String env : envs) {
			parentPath = ZK_ROOT_PATH + "/" + env;
			apps = zkClient.getChildren(parentPath);
			for (String app : apps) {
				parentPath = ZK_ROOT_PATH + "/" + env + "/" + app + "/nodes";
				
				int activeNodeCount = zkClient.countChildren(parentPath);
				if(activeNodeCount == 0)continue;
				List<ConfigState> localCacheConfigs = configStates.get(app + "#" + env);
				
				if(activeNodeCount > 0 && (localCacheConfigs == null || localCacheConfigs.size() < activeNodeCount)){
					zkClient.writeData(parentPath, NOTIFY_UPLOAD_CMD);
					logger.info("send cmd[{}] on path[{}]",NOTIFY_UPLOAD_CMD,parentPath);
				}
			}
		}
	}
	
	public void add(ConfigState cs){
		String key = cs.getAppName() + "#" + cs.getEnv();
		List<ConfigState> list = configStates.get(key);
		if(list == null){
			list = new ArrayList<>();
			configStates.put(key, list);
			list.add(cs);
		}else{
			if(!list.contains(cs)){
				list.add(cs);
			}
		}
		logger.info("New node[{}-{}-{}] registered!",cs.getAppName(),cs.getEnv(),cs.getNodeId());
	}
	
	public List<ConfigState> get(String env){
		
		List<ConfigState> result = new ArrayList<>();
		Set<String> keys = configStates.keySet();
		
		for (String key : keys) {
			if(key.endsWith(env)){
				//
				clearExpireNodes(key);
				List<ConfigState> list = configStates.get(key);
				result.addAll(list);
			}
		}
		return result;
	}
	
	public List<ConfigState> get(String appName,String env){
		List<ConfigState> clist = configStates.get(appName + "#" + env);
		return clist == null ? new ArrayList<>() : clist;
	}
	
	private static void clearExpireNodes(String appNameAnaEnvKey){
		try {
			List<ConfigState> sameAppNodes = configStates.get(appNameAnaEnvKey);
			if(sameAppNodes == null || sameAppNodes.isEmpty())return;
			String syncType = sameAppNodes.get(0).syncType;
			String zkPath = sameAppNodes.get(0).zkPath;
			
			Date nowTime = new Date();
			if(SYNC_TYPE_ZK.equals(syncType)){
				List<String> activeNodes = zkClient.getChildren(zkPath);
				if(activeNodes == null)return;
				if(activeNodes.isEmpty()){
					sameAppNodes.clear();
					return;
				}
				Iterator<ConfigState> iterator = sameAppNodes.iterator();
				while (iterator.hasNext()) {  
					ConfigState c = iterator.next();
		            if (!activeNodes.contains(c.getNodeId())) {  
		            	logger.info("remove expire node:{}",c.getNodeId());
		            	iterator.remove();  
		            }else{
		            	c.setSyncTime(nowTime);
		            } 
		        } 
			}else{
				synchronized (sameAppNodes) {				
					Iterator<ConfigState> iter = sameAppNodes.iterator();  
					while (iter.hasNext()) {
						ConfigState s = iter.next(); 
						if (DateUtils.getDiffSeconds(nowTime, s.syncTime) > s.syncIntervalSeconds * 2) {  
							iter.remove();  
						}  
					}  
				}
			}
		} catch (Exception e) {
			logger.error("clearExpireNodes:"+ appNameAnaEnvKey,e);
		}
	}

	public static class ConfigState{
		
		private String nodeId;
		private String appName;
		private String env;
		private String version;
		private boolean springboot;
		private String serverip;
		private int serverport;
		private int syncIntervalSeconds = 90;
		private String syncType = "http";
		private Date syncTime = new Date();
		private String zkPath;
		
		@JsonIgnore
		private Map<String, String> configs = new TreeMap<>();
		
		private Map<String, String> waitingSyncConfigs = new HashMap<>();
		
		
		public ConfigState(Map<String, String> allMaps) {
			this.nodeId = allMaps.remove("nodeId");
			this.appName = allMaps.remove("appName");
			this.env = allMaps.remove("env");
			this.version = allMaps.remove("version");
			if(allMaps.containsKey("syncType"))syncType = allMaps.remove("syncType");
			if(allMaps.containsKey("syncIntervalSeconds"))this.syncIntervalSeconds = Integer.parseInt(allMaps.remove("syncIntervalSeconds"));
			springboot = Boolean.parseBoolean(allMaps.remove("springboot"));
			if(allMaps.containsKey("serverport")){
				serverport = Integer.parseInt(allMaps.remove("serverport"));
			}
			if(allMaps.containsKey("serverip")){
				serverip = allMaps.remove("serverip");
			}
			
			ArrayList<String> keys = new ArrayList<>(allMaps.keySet());
			Collections.sort(keys);
			for (String key : keys) {
				configs.put(key, allMaps.get(key));
			}
			
			zkPath = ZK_ROOT_PATH + "/" + env + "/" + appName + "/nodes";
			
			if(SYNC_TYPE_ZK.equals(syncType)){
				if(zkClient == null){
					logger.warn("Zookeeper client not init,skip");
					return ;
				}
				
				if(!zkClient.exists(zkPath))return;
				//
				clearExpireNodes(appName + "#" + env);
				
				logger.info("int ConfigState ok");
			}
		}
		
		public void update(String nodeId,Map<String, String> datas){
			if(datas.containsKey("serverip")){
				serverip = datas.get("serverip");
			}
			syncTime = new Date();
			//清理失效节点
			//clearExpireNodes(appName + "#" + env);
		}
		
		public void onConfigSyncSuccess(){
			if(waitingSyncConfigs.isEmpty())return;
			configs.putAll(waitingSyncConfigs);
			waitingSyncConfigs.clear();
		}

		public String getNodeId() {
			return nodeId;
		}
		public void setNodeId(String nodeId) {
			this.nodeId = nodeId;
		}
		public String getAppName() {
			return appName;
		}
		public void setAppName(String appName) {
			this.appName = appName;
		}
		public String getEnv() {
			return env;
		}
		public void setEnv(String env) {
			this.env = env;
		}
		public String getVersion() {
			return version;
		}
		public void setVersion(String version) {
			this.version = version;
		}

		public Map<String, String> getConfigs() {
			return configs;
		}
		public void setConfigs(Map<String, String> configs) {
			this.configs = configs;
		}

		public Date getSyncTime() {
			return syncTime;
		}

		public void setSyncTime(Date syncTime) {
			this.syncTime = syncTime;
		}

		public boolean isSpringboot() {
			return springboot;
		}

		public String getServerip() {
			return serverip;
		}

		public void setServerip(String serverip) {
			this.serverip = serverip;
		}

		public int getServerport() {
			return serverport;
		}

		public void setServerport(int serverport) {
			this.serverport = serverport;
		}

		public void setSpringboot(boolean springboot) {
			this.springboot = springboot;
		}

		public Map<String, String> getWaitingSyncConfigs() {
			return waitingSyncConfigs;
		}
		
		public String getSyncType() {
			return syncType;
		}

		public void publishChangeConfig(Map<String, String> changeConfigs){
			if(changeConfigs.isEmpty())return;
			if(SYNC_TYPE_ZK.equals(syncType)){
				if(zkClient == null){
					logger.warn("Zookeeper client not init,skip");
					return ;
				}
				if(zkClient.countChildren(zkPath) == 0){
					return;
				}
				zkClient.writeData(zkPath, JsonUtils.toJson(changeConfigs));
			}else{
				waitingSyncConfigs.putAll(changeConfigs);
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			if(StringUtils.isNotBlank(serverip) && serverport > 0){				
				result = prime * result + ((serverip == null) ? 0 : serverip.hashCode());
				result = prime * result + serverport;
			}else{
				result = prime * result + ((nodeId == null) ? 0 : nodeId.hashCode());
			}
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ConfigState other = (ConfigState) obj;
			if(StringUtils.isNotBlank(serverip) && serverport > 0){	
				if (!serverip.equals(other.serverip))return false;
				if (serverport != other.serverport)return false;
			}else{
				if (!nodeId.equals(other.nodeId))return false;
			}
			return true;
		}

		
	}

	
}
