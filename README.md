# 尚硅谷听书（tingshu）

基于 Spring Cloud Alibaba 的听书类微服务项目，包含后端微服务集群与 uni-app 微信小程序前端。

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 核心框架 | Spring Boot 3.0.5、Spring Cloud 2022.0.2、Spring Cloud Alibaba 2022.0.0.0-RC1 |
| 注册中心 / 配置中心 | Nacos 2.x |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 持久层 | MyBatis-Plus 3.5.3.1 + MySQL 8 |
| 缓存 / 分布式锁 | Redis + Redisson |
| 消息队列 | RabbitMQ（Spring Cloud Stream / Bus） |
| 搜索 | Elasticsearch |
| 对象存储 | MinIO、腾讯云 COS |
| 音视频 | 腾讯云点播 VOD |
| 登录 | 微信小程序登录（weixin-java-miniapp） |
| 接口文档 | Knife4j（OpenAPI 3） |

## 目录结构

```
.
├── tingshu-parent/                 # 后端（Maven 多模块）
│   ├── common/                     # 公共模块
│   │   ├── common-util/            # 通用工具、返回结果、异常、常量、登录上下文
│   │   ├── service-util/           # 登录切面、Redis / MyBatis-Plus / Knife4j 配置
│   │   └── rabbit-util/            # RabbitMQ 封装
│   ├── model/                      # 实体、VO、Query 对象
│   ├── service-client/             # Feign 客户端（account / album / order / user）
│   ├── service/                    # 业务微服务
│   │   ├── service-album/          # 专辑、声音、分类、标签、文件上传（8080）
│   │   ├── service-user/           # 用户、微信登录、VIP（8503）
│   │   ├── service-search/         # 搜索（8502）
│   │   ├── service-order/          # 订单（8504）
│   │   ├── service-account/        # 账户
│   │   ├── service-payment/        # 支付
│   │   └── service-dispatch/       # 调度
│   └── server-gateway/             # 网关（8500）
└── mp-weixin/                      # 微信小程序前端（uni-app 编译产物，开发者工具直接打开）
```

## 已实现功能

**专辑**
- 三级分类列表、专辑标签列表
- 保存专辑、专辑分页列表、专辑详情、编辑专辑、删除专辑
- 专辑封面图片上传（MinIO）

**声音**
- 音频上传至腾讯云点播（返回 fileId 与播放地址，并回填时长 / 大小）
- 保存声音、分页列表、详情、编辑、删除
- 新增 / 删除时自动维护专辑的 `include_track_count` 集数

**用户与登录**
- 微信小程序登录：`code` 换 openid，用户不存在则自动注册，签发 token
- 登录态存 Redis，`@GuiGuLogin` 切面统一拦截未登录请求（响应 208）
- 当前用户信息查询与修改（昵称、头像）

## 快速开始

1. **准备中间件**：Nacos、MySQL 8、Redis、RabbitMQ、Elasticsearch、MinIO
2. **导入 Nacos 配置**：各服务读取 `service-{name}-dev.yaml` 与共享配置 `common.yaml`（同在 `DEFAULT_GROUP`），
   内容包含数据源、Redis、RabbitMQ、腾讯云点播密钥、微信小程序 appid / secret 等
3. **建库**：`tingshu_album`、`tingshu_user` 等
4. **启动顺序**：先起 Nacos → `server-gateway` → 各 `service-*`
5. **访问**：网关 `http://localhost:8500`，接口文档 `http://localhost:{port}/doc.html`

> 注意：项目**没有本地 `application.yml`**，配置全部从 Nacos 拉取，因此 Nacos 不可用时服务无法启动。
