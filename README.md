# 云帆商城 · 微服务电商后端（YunFan Mall）

> 一个基于 Spring Cloud Alibaba 的分布式电商后端系统，覆盖商品 / 检索 / 购物车 / 下单 / 库存 / 秒杀 / 认证等完整交易链路。
>

---

## 系统概览

```
                    ┌──────────────────────────────┐
   客户端/浏览器 ───►│   yunfan-gateway  网关       │
                    │  统一路由 · CORS · 鉴权入口    │
                    └──────┬───────────────────────┘
             ┌─────────────┼──────────────┬───────────┐
             ▼             ▼              ▼           ▼
      ┌───────────┐ ┌───────────┐ ┌────────────┐ ┌─────────┐
      │ product   │ │ search    │ │  order     │ │ member  │
      │ 商品服务  │ │ ES 检索    │ │ 订单服务    │ │ 会员服务 │
      └───────────┘ └───────────┘ └────────────┘ └─────────┘
             │             │             │            │
      ┌───────────┐ ┌───────────┐ ┌────────────┐ ┌────────────┐
      │  cart     │ │ seckill   │ │   ware     │ │  coupon    │
      │ 购物车     │ │ 秒杀       │ │  仓储库存   │ │ 营销/券    │
      └───────────┘ └───────────┘ └────────────┘ └────────────┘
      所有服务注册到 Nacos 并依赖 yunfan-common；第三方服务独立，供 OSS/短信等场景调用
```

## 核心模块

| 模块 | 职责 |
| --- | --- |
| `yunfan-common` | 通用模块：统一返回 `R` / 异常 / Feign / 常量与公共依赖 |
| `yunfan-gateway` | 网关：统一路由、跨域、鉴权入口 |
| `yunfan-product` | 商品服务：品牌 / 分类 / 属性(组) / SPU / SKU、商品上下架 |
| `yunfan-search` | 检索服务：基于 Elasticsearch 的商品上架、检索与筛选，支持自然语言 AI 检索 |
| `yunfan-cart` | 购物车服务：Redis 购物车、临时/登录态购物车合并 |
| `yunfan-order` | 订单服务：结算下单、订单状态机、RabbitMQ 延时关单、支付对接 |
| `yunfan-ware` | 仓储服务：库存、采购、库存锁定 / 解锁、出库 |
| `yunfan-member` | 会员服务：会员、积分、社交登录 |
| `yunfan-coupon` | 营销服务：优惠券 / 秒杀场次等 |
| `yunfan-seckill` | 秒杀服务：Redis 信号量预减 + RabbitMQ 异步下单 |
| `yunfan-auth-server` | 认证中心：验证码注册登录、社交登录 OAuth2、SSO 演示 |
| `yunfan-third-party` | 第三方服务：阿里云 OSS 上传、短信验证码 |
| `yunfan-test-sso-client` / `yunfan-test-sso-server` | SSO 单点登录演示端 |
| `renren-fast` / `renren-generator` | 后台管理系统与代码生成器（人人开源脚手架） |
| `db/` | 六库建表 SQL（admin / oms / pms / sms / ums / wms） |

## ✨ AI 亮点：自然语言商品检索

`yunfan-search` 提供 `/ai/search` 接口，用一句日常话就能搜商品，例如：

```bash
curl -X POST http://localhost:12001/ai/search \
  -H 'Content-Type: application/json' \
  -d '{"question":"5000元以内的红色华为手机，要现货","summarize":true}'
```

实现思路（纯 Java 直调大模型，不依赖任何 AI 框架）：

1. **意图解析**：把自然语言发给 DeepSeek（OpenAI 兼容协议 + JSON schema 约束），抽出 `keyword / brand / category / 价格区间 / 现货 / 排序` 等结构化条件；
2. **名称桥接**：品牌名 / 分类名通过 ES 商品索引自带字段反查成内部 ID，再复用既有 `MallSearchService` 的聚合检索与分页，不重复造 DSL；
3. **可控兜底**：模型输出不可控，解析失败自动降级为整句关键词检索；0 命中自动放宽硬条件；生成导购语失败不影响主结果——保证接口永远稳定返回。

> 🔑 调用需要 DeepSeek Key，仅通过环境变量注入、**不落库**：
> ```bash
> set DEEPSEEK_API_KEY=sk-xxxx        # Windows
> export DEEPSEEK_API_KEY=sk-xxxx     # Linux / macOS
> ```
> 未配置时 `/ai/search` 返回明确错误，不影响既有普通检索与全部网页端功能。

## 技术栈

- **微服务框架**：Spring Boot · Spring Cloud Alibaba（Nacos 注册/配置中心、OpenFeign、Gateway、Sentinel）
- **数据 / 中间件**：MySQL 8 · MyBatis-Plus · Redis / Redisson · RabbitMQ · Elasticsearch
- **其他**：Spring Session · 阿里云 OSS / 短信 · 支付宝 / 微信支付（沙箱 demo）

## 快速开始

### 环境前提

- 中间件：MySQL 8、Redis、Nacos（2.x）、RabbitMQ；`yunfan-search` 另需 Elasticsearch。
- 数据库：导入 `db/` 下六个 SQL（`yunfan_admin / oms / pms / sms / ums / wms`）。
- Nacos 配置中心：各服务通过 `bootstrap.yml` 按 namespace 拉取 `oss.yml` 等扩展配置，需先在 Nacos 建好对应命名空间与配置（课程标准做法）。
- 各服务 `application.yml` 中的 MySQL / Redis 等地址需替换为你本机配置。

### 启动

```bash
mvn clean install -DskipTests                     # 先构建 yunfan-common 等公共模块
mvn -pl yunfan-order -am spring-boot:run        # 单服务启动示例（服务逐个启动）
```

> 🔐 仓库内第三方云服务密钥（阿里云 OSS / 短信、微信 appsecret、支付宝商户私钥等）均已替换为 `REDACTED_*` 占位符，对接真实功能时替换为你自己的凭证。

