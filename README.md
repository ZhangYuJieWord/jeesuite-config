**交流群(提供开发视频教程)**：230192763🈵 ，61859839

### 分布式配置中心(Distributed configuration center)
#### 为什么要造轮子？
之前试用过几个开源的配置中心，各有弊端(或者是不满足我们的需求)、譬如部署复杂、不支持全局配置、不支持敏感配置加密，对springboot支持不友好等等。所以从2015年开始就有了这个项目。
目前已经在生产环境多个公司生产环境稳定运行，欢迎点赞。

#### 实现功能
   1. 支持全局配置、多应用共享配置
   2. 支持配置文件、配置项、json配置支持
   3. 支持加密配置(server自动加密，client自动解密)
   4. 支持spring、springboot无缝对接
   5. 支持占位符引用
   6. 支持环境+项目维度精细化权限控制
   7. 支持http和zookeeper方式配置实时同步
   8. 支持在线查看应用当前运行时配置（配置中心与本地合并后的最终配置）
   9. 支持查看配置历史版本、一键回滚、版本对比
   10. 安全功能支持:可选开启安全ip功能与内网拉取配置限制
   
#### 在线演示
http://47.93.239.14:19992/admin.html
账号密码：admin/admin123
 
#### 文档
 - [部署文档](http://www.jeesuite.com/docs/quickstart/confcenter.html) 
 - [使用文档](http://www.jeesuite.com/docs/integration/confcenter.html) 

#### 关联项目
 - 基础库
  - [http://git.oschina.net/vakinge/jeesuite-libs](http://git.oschina.net/vakinge/jeesuite-libs)
  - [https://github.com/vakinge/jeesuite-libs](https://github.com/vakinge/jeesuite-libs)
  - 应用开发平台
  - [https://gitee.com/vakinge/oneplatform](https://gitee.com/vakinge/oneplatform)
  - [https://github.com/vakinge/oneplatform](https://github.com/vakinge/oneplatform)


#### 页面截图
页面基于`layui-beginner_admin`构建。

![image](http://ojmezn0eq.bkt.clouddn.com/cc_profiles.png)
![image](http://ojmezn0eq.bkt.clouddn.com/cc_apps.png)
![image](http://ojmezn0eq.bkt.clouddn.com/cc_configs.png)
![image](http://ojmezn0eq.bkt.clouddn.com/cc_config_add.png)
![image](http://ojmezn0eq.bkt.clouddn.com/cc_config_jm.png)
![image](http://ojmezn0eq.bkt.clouddn.com/cc_app_secret.png)
![image](http://ojmezn0eq.bkt.clouddn.com/cc_show_active.png)



