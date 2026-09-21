# 装修公司管理系统

面向装修企业内部使用的轻量级运营管理平台，集成后端接口、内置前端、登录鉴权、角色权限和 H2 持久化，适合快速部署、演示和继续扩展。

## 功能概览

系统覆盖三条主线：客户与线索、项目与施工、供应链与收款。当前版本已包含：

- 经营看板
- 客户管理
- 员工管理
- 账号管理
- 项目管理
- 施工阶段管理
- 供应商管理
- 材料采购管理
- 收款管理
- 登录、退出、当前用户、权限概览

关键业务机制：

- 员工账号由管理员统一开通，并可直接绑定到具体员工，避免重复开账号
- 供应商作为独立主数据维护，采购记录直接关联供应商，便于统计和后续扩展
- 前端按权限显示菜单和操作入口，账号管理仅对管理员开放
- 系统不开放外部自助注册，适合企业内部使用

## 角色权限

| 角色 | 编码 | 说明 |
| --- | --- | --- |
| 管理员 | `ADMIN` | 拥有全部模块管理权限 |
| 项目经理 | `MANAGER` | 可维护客户、项目、施工阶段、供应商、采购 |
| 财务 | `FINANCE` | 可维护收款 |
| 设计师 | `DESIGNER` | 可维护客户、项目、施工阶段 |

视图与操作规则：

- 所有角色都可以查看经营看板
- 前端导航和页面视图会按读权限显示，未授权模块不会出现在侧边栏
- 表单、新增、编辑、删除等操作会按写权限显示；只有可写角色才会看到对应操作入口
- 账号管理视图仅管理员可见，普通角色不会看到该菜单

各角色的权限与视图范围如下：

- `ADMIN`
  - 可见视图：经营看板、客户管理、员工管理、账号管理、项目管理、施工阶段管理、供应商管理、材料采购管理、收款管理
  - 可执行操作：对全部业务模块拥有新增、编辑、删除权限；可创建账号、绑定员工、重置密码
- `MANAGER`
  - 可见视图：经营看板、客户管理、员工管理、项目管理、施工阶段管理、供应商管理、材料采购管理、收款管理
  - 可执行操作：可维护客户、项目、施工阶段、供应商、材料采购
  - 只读视图：员工管理、收款管理
- `FINANCE`
  - 可见视图：经营看板、项目管理、收款管理
  - 可执行操作：可维护收款记录
  - 只读视图：项目管理
- `DESIGNER`
  - 可见视图：经营看板、客户管理、项目管理、施工阶段管理
  - 可执行操作：可维护客户、项目、施工阶段

对应到权限编码：

- 读权限：`dashboard:read`、`customers:read`、`employees:read`、`users:read`、`projects:read`、`stages:read`、`suppliers:read`、`materials:read`、`payments:read`
- 写权限：`customers:write`、`employees:write`、`users:write`、`projects:write`、`stages:write`、`suppliers:write`、`materials:write`、`payments:write`
- 删除权限：与写权限同模块发放，即拥有写权限时同时具备对应 `:delete` 权限

## 技术栈

- 运行环境：`Java 17`
- 后端基础：`JDK HttpServer`、`JDBC`
- 数据存储：`H2 2.2.224` 文件数据库
- 前端：`HTML5`、`CSS3`、原生 `JavaScript`
- 接口通信：浏览器侧通过 `fetch` 调用 REST 风格接口
- 构建与打包：`Maven`、`maven-compiler-plugin`、`maven-jar-plugin`、`maven-shade-plugin`

当前实现特点：

- 不依赖 Spring Boot 或外部应用服务器，服务由 `org.example.Main` 直接启动
- 后端通过 `ApiHandler` 和 `PageHandler` 同时提供 API 与静态页面
- 数据通过 JDBC 写入本地 H2 文件，适合教学、演示和小型内部部署
- 前端为单页式原生脚本实现，部署简单，调试成本低

## 快速运行

推荐直接使用项目自带脚本：

```bash
bash start.sh
```

脚本会自动创建 `out` 目录、编译 Java 源码，并以完整 classpath 启动服务。默认访问地址为 `http://localhost:8080/`，也可以通过环境变量指定端口：

```bash
PORT=9090 bash start.sh
```

如果希望手动启动：

```bash
mkdir -p out
javac -encoding UTF-8 -cp lib/h2-2.2.224.jar -d out $(find src/main/java -name "*.java")
java -cp out:src/main/resources:lib/h2-2.2.224.jar org.example.Main
```

如果本地已安装 Maven，也可以打包后运行：

```bash
mvn package
java -jar target/ManageSystem-1.0-SNAPSHOT.jar
```

## 数据库默认账号

首次启动时，系统会在数据库 `users` 表中自动写入以下默认账号，可直接用于登录：

| 用户名 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `admin123` | `ADMIN` | 管理员，拥有全部模块管理权限 |
| `manager` | `manager123` | `MANAGER` | 项目经理，可查看员工并维护客户、项目、施工阶段、供应商、采购 |
| `finance` | `finance123` | `FINANCE` | 财务，可查看项目并维护收款记录 |
| `designer` | `designer123` | `DESIGNER` | 设计师，可维护客户、项目、施工阶段 |

这些账号由数据库初始化逻辑自动插入，对应代码见 [`DatabaseManager.java`](/Users/birliigant/百度云同步空间/Code/ManageSystem/src/main/java/org/example/db/DatabaseManager.java) 中的 `insertUser(...)` 调用。

## 数据持久化

系统使用 H2 文件数据库，默认数据文件为 `data/manage-system.mv.db`。

- 首次启动会自动建表
- 首次启动会自动注入初始化账号和基础业务数据
- 后续新增、修改、删除操作会写入数据库文件
- 重启服务后数据会保留

## 项目结构

```text
ManageSystem
├── .mvn
├── data
├── docs
│   ├── class
│   ├── 数据库表结构.md
│   └── 接口文档.md
├── lib
│   └── h2-2.2.224.jar
├── out
├── pom.xml
├── README.md
├── start.sh
└── src
    └── main
        ├── java
        │   └── org
        │       └── example
        │           ├── db
        │           ├── domain
        │           ├── security
        │           ├── service
        │           ├── util
        │           ├── web
        │           └── Main.java
        └── resources
            └── static
                ├── app.js
                ├── index.html
                └── styles.css
```

各目录内容与作用如下：

- `.mvn`：Maven Wrapper 相关目录，用于统一 Maven 运行环境
- `data`：运行时生成的 H2 数据库文件目录，默认保存 `manage-system.mv.db`
- `docs`：项目文档目录
- `docs/class`：课程相关材料，如封皮、要求说明、指导书和选题文档
- `docs/数据库表结构.md`：数据库表设计、字段定义和外键关系说明
- `docs/接口文档.md`：后端接口说明文档
- `lib`：本地依赖目录，当前主要存放 `h2-2.2.224.jar`
- `out`：通过 `start.sh` 或手动 `javac` 编译后生成的 `.class` 输出目录
- `src/main/java`：后端 Java 源码主目录
- `src/main/resources`：运行时资源目录
- `src/main/resources/static`：前端静态页面资源，浏览器直接访问的页面、样式和脚本都在这里
- `src/test`：测试代码目录，当前仓库下暂未放入测试文件
- `target`：Maven 构建输出目录，包含编译结果和打包产物

`src/main/java/org/example` 按职责继续划分为：

- `Main.java`：应用入口，负责初始化数据库、服务、鉴权模块并启动 HTTP 服务
- `db`：数据库访问与初始化
  - `DatabaseManager.java`：建表、初始数据、连接与 SQL 执行入口
  - `SqlConsumer.java`、`SqlFunction.java`：数据库操作使用的函数式接口
- `domain`：领域模型对象
  - 包含 `Customer`、`Employee`、`Project`、`ProjectStage`、`MaterialPurchase`、`PaymentRecord` 等业务实体
- `security`：认证与权限
  - `AuthService.java`：登录、token、权限判断
  - `AuthUser.java`：当前登录用户与权限信息载体
- `service`：业务编排层
  - `DecorationManagementService.java`：聚合客户、员工、项目、采购、收款、看板等核心业务逻辑
- `util`：通用工具
  - `AppException.java`：业务异常封装
  - `HttpUtils.java`：HTTP 请求/响应辅助方法
  - `JsonUtils.java`：JSON 序列化与反序列化
  - `PasswordUtils.java`：密码处理工具
- `web`：Web 层入口
  - `ApiHandler.java`：统一处理 `/api/**` 接口路由
  - `PageHandler.java`：处理首页和静态资源访问

`src/main/resources/static` 目录内容：

- `index.html`：系统主页面和基础布局
- `styles.css`：页面样式定义
- `app.js`：前端状态管理、登录流程、表单交互、数据渲染与接口调用

## 文档入口

- 项目说明：`README.md`
- 接口文档：[`docs/接口文档.md`](docs/接口文档.md)
- 数据库表结构：[`docs/数据库表结构.md`](docs/数据库表结构.md)

接口文档包含以下内容：

- 认证接口
- 经营看板接口
- 选项接口
- 客户、员工、账号、项目、施工阶段接口
- 供应商、材料采购、收款接口

## 适用场景

适合中小型装修企业的内部数字化管理，也适合作为轻量级 Java Web 管理系统示例，后续可平滑演进到更完整的 Spring Boot、MySQL、JWT 或前后端分离方案。
