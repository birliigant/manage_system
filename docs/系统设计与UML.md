# 系统设计与 UML

本文件中的图均使用 Mermaid 编写，GitHub 和支持 Mermaid 的 Markdown 编辑器可以直接渲染。图中的类、方法和关系根据当前 Java 源码整理；省略了重复的 CRUD 方法和实现细节，以保持图表可读性。

## 1. 模块图

```mermaid
flowchart LR
    Auth[认证与权限]
    Dashboard[经营看板]
    Customer[客户管理]
    Employee[员工管理]
    User[账号管理]
    Project[项目管理]
    Stage[施工阶段]
    Supplier[供应商管理]
    Material[材料采购]
    Payment[收款管理]
    DB[(H2 数据库)]

    Auth --> User
    Dashboard --> Project
    Customer --> Project
    Employee --> Project
    Project --> Stage
    Project --> Material
    Project --> Payment
    Supplier --> Material
    Auth --> DB
    Dashboard --> DB
    Customer --> DB
    Employee --> DB
    User --> DB
    Project --> DB
    Stage --> DB
    Supplier --> DB
    Material --> DB
    Payment --> DB
```

## 2. 包图

```mermaid
flowchart TB
    Main[org.example.Main]
    Web[org.example.web]
    Security[org.example.security]
    Service[org.example.service]
    Domain[org.example.domain]
    DB[org.example.db]
    Util[org.example.util]
    Static[src/main/resources/static]

    Main --> Web
    Main --> Security
    Main --> Service
    Main --> DB
    Web --> Security
    Web --> Service
    Web --> Util
    Security --> DB
    Security --> Domain
    Service --> DB
    Service --> Domain
    Service --> Util
    DB --> Util
    Static --> Web
```

## 3. 核心类图

```mermaid
classDiagram
    direction LR

    class Main {
        +main(String[] args)
    }

    class PageHandler {
        +handle(HttpExchange exchange)
    }

    class ApiHandler {
        -DecorationManagementService service
        -AuthService authService
        +handle(HttpExchange exchange)
        -routeAuth()
        -routeCustomers()
        -routeEmployees()
        -routeUsers()
        -routeProjects()
        -routeStages()
        -routeSuppliers()
        -routeMaterials()
        -routePayments()
    }

    class AuthService {
        -DatabaseManager databaseManager
        +login(String username, String password)
        +currentUser(HttpExchange exchange)
        +logout(HttpExchange exchange)
        +requireUser(HttpExchange exchange)
        +requireReadPermission(AuthUser user, String resource)
        +requireWritePermission(AuthUser user, String resource)
        +permissionOverview(AuthUser user)
    }

    class AuthUser {
        <<record>>
        +long id
        +String username
        +String displayName
        +String role
        +List~String~ permissions
    }

    class DecorationManagementService {
        -DatabaseManager databaseManager
        +dashboardSummary()
        +options()
        +listCustomers()
        +listEmployees()
        +listUsers()
        +listProjects()
        +listStages()
        +listSuppliers()
        +listMaterials()
        +listPayments()
        +createResource()
        +updateResource()
        +deleteResource()
    }

    class DatabaseManager {
        -String jdbcUrl
        +initialize()
        +getConnection()
        +query()
        +insert()
        +update()
        +transaction()
    }

    class Customer {
        <<record>>
        +long id
        +String name
        +String phone
        +String source
        +String level
        +String intention
        +String address
        +String notes
        +LocalDate createdDate
    }

    class Employee {
        <<record>>
        +long id
        +String name
        +String role
        +String phone
        +String specialty
        +String status
        +LocalDate hireDate
        +String notes
    }

    class Project {
        <<record>>
        +long id
        +long customerId
        +long managerId
        +String status
        +BigDecimal contractAmount
        +LocalDate startDate
        +LocalDate expectedEndDate
    }

    class ProjectStage {
        <<record>>
        +long id
        +long projectId
        +String stageName
        +String owner
        +String status
        +LocalDate plannedDate
        +LocalDate actualDate
    }

    class MaterialPurchase {
        <<record>>
        +long id
        +long projectId
        +String materialName
        +String category
        +String supplier
        +BigDecimal amount
        +LocalDate purchaseDate
    }

    class PaymentRecord {
        <<record>>
        +long id
        +long projectId
        +String type
        +BigDecimal amount
        +String status
        +LocalDate paymentDate
        +String payer
    }

    class HttpUtils
    class JsonUtils
    class PasswordUtils
    class AppException

    Main --> DatabaseManager : creates
    Main --> DecorationManagementService : creates
    Main --> AuthService : creates
    Main --> ApiHandler : registers
    Main --> PageHandler : registers
    ApiHandler --> AuthService : authenticates
    ApiHandler --> DecorationManagementService : delegates
    ApiHandler --> HttpUtils : responds
    ApiHandler --> JsonUtils : parses
    AuthService --> DatabaseManager : queries sessions/users
    AuthService --> AuthUser : creates
    AuthService --> PasswordUtils : hashes/verifies
    DecorationManagementService --> DatabaseManager : persists
    DecorationManagementService --> Customer
    DecorationManagementService --> Employee
    DecorationManagementService --> Project
    DecorationManagementService --> ProjectStage
    DecorationManagementService --> MaterialPurchase
    DecorationManagementService --> PaymentRecord
    DatabaseManager --> AppException : wraps database failures
```

## 4. 登录时序图

```mermaid
sequenceDiagram
    actor User as 用户
    participant UI as 前端 app.js
    participant API as ApiHandler
    participant Auth as AuthService
    participant DB as DatabaseManager

    User->>UI: 输入用户名和密码
    UI->>API: POST /api/auth/login
    API->>Auth: login(username, password)
    Auth->>DB: 查询 users
    DB-->>Auth: 用户、密码哈希和角色
    Auth->>Auth: 校验密码和角色权限
    Auth->>DB: 写入 user_sessions
    Auth-->>API: token、用户信息和权限
    API-->>UI: 200 JSON
    UI->>UI: 保存 token 并加载看板
```

## 5. 业务 CRUD 协作图

```mermaid
sequenceDiagram
    actor Manager as 管理员或业务角色
    participant UI as 前端页面
    participant API as ApiHandler
    participant Auth as AuthService
    participant Service as DecorationManagementService
    participant DB as DatabaseManager

    Manager->>UI: 填写业务表单
    UI->>API: POST/PUT /api/{resource}
    API->>Auth: requireUser()
    Auth-->>API: AuthUser
    API->>Auth: requireWritePermission()
    Auth-->>API: 权限通过
    API->>Service: create/update(resource)
    Service->>Service: 校验字段和业务关联
    Service->>DB: insert/update + transaction
    DB-->>Service: 持久化结果
    Service-->>API: 业务对象或结果
    API-->>UI: JSON 响应
    UI-->>Manager: 刷新列表和提示结果
```

## 6. 角色用例图

```mermaid
flowchart LR
    Admin([管理员])
    Manager([项目经理])
    Finance([财务])
    Designer([设计师])

    subgraph System[装修公司管理系统]
        Login((登录/退出))
        Dashboard((查看经营看板))
        Customers((客户管理))
        Employees((员工管理))
        Users((账号管理))
        Projects((项目管理))
        Stages((施工阶段管理))
        Suppliers((供应商管理))
        Materials((材料采购管理))
        Payments((收款管理))
    end

    Admin --> Login
    Admin --> Dashboard
    Admin --> Customers
    Admin --> Employees
    Admin --> Users
    Admin --> Projects
    Admin --> Stages
    Admin --> Suppliers
    Admin --> Materials
    Admin --> Payments
    Manager --> Login
    Manager --> Dashboard
    Manager --> Customers
    Manager --> Employees
    Manager --> Projects
    Manager --> Stages
    Manager --> Suppliers
    Manager --> Materials
    Manager --> Payments
    Finance --> Login
    Finance --> Dashboard
    Finance --> Projects
    Finance --> Payments
    Designer --> Login
    Designer --> Dashboard
    Designer --> Customers
    Designer --> Projects
    Designer --> Stages
```

## 7. 项目状态图

```mermaid
stateDiagram-v2
    [*] --> 待启动
    待启动 --> 进行中: 开工
    进行中 --> 已暂停: 暂停
    已暂停 --> 进行中: 恢复
    进行中 --> 已完成: 完工
    待启动 --> 已取消: 取消
    进行中 --> 已取消: 取消
    已暂停 --> 已取消: 取消
    已完成 --> [*]
    已取消 --> [*]
```

项目实际可用状态值由业务层和初始化数据共同决定，提交报告时应以数据库中的当前枚举为准。

## 8. 项目管理活动图

```mermaid
flowchart TD
    Start([开始]) --> Login{是否已登录}
    Login -- 否 --> DoLogin[登录并创建会话]
    DoLogin --> Permission{是否有项目写权限}
    Login -- 是 --> Permission
    Permission -- 否 --> ReadOnly[查看项目和关联数据]
    Permission -- 是 --> Action{选择操作}
    Action --> Create[新建项目]
    Action --> Edit[编辑项目]
    Action --> Delete[删除项目]
    Create --> Validate[校验客户、负责人、金额和日期]
    Edit --> Validate
    Delete --> Confirm{确认删除}
    Confirm -- 否 --> ReadOnly
    Confirm -- 是 --> Persist[事务写入数据库]
    Validate --> Persist
    Persist --> Refresh[返回 JSON 并刷新列表]
    ReadOnly --> End([结束])
    Refresh --> End
```

## 9. 部署图

```mermaid
flowchart LR
    Client[浏览器客户端]
    JVM[Java 17 进程<br/>org.example.Main<br/>localhost:8080]
    Static[classpath 静态资源]
    H2[(H2 文件数据库<br/>data/manage-system.mv.db)]

    Client -->|HTTP| JVM
    JVM --> Static
    JVM -->|JDBC| H2
```

## 10. 数据库 ER 图

```mermaid
 erDiagram
    USERS ||--o{ USER_SESSIONS : owns
    EMPLOYEES ||--o{ PROJECTS : manages
    CUSTOMERS ||--o{ PROJECTS : owns
    PROJECTS ||--o{ PROJECT_STAGES : contains
    PROJECTS ||--o{ MATERIAL_PURCHASES : has
    PROJECTS ||--o{ PAYMENT_RECORDS : receives
    SUPPLIERS ||--o{ MATERIAL_PURCHASES : supplies
    EMPLOYEES ||--o{ USERS : binds

    USERS {
        bigint id PK
        varchar username UK
        varchar role
        bigint employee_id
    }
    USER_SESSIONS {
        bigint id PK
        bigint user_id FK
        varchar token UK
        timestamp expires_at
    }
    CUSTOMERS {
        bigint id PK
        varchar name
        varchar phone
        varchar level
    }
    EMPLOYEES {
        bigint id PK
        varchar name
        varchar role
        varchar status
    }
    PROJECTS {
        bigint id PK
        bigint customer_id FK
        bigint manager_id FK
        varchar status
        decimal contract_amount
    }
    PROJECT_STAGES {
        bigint id PK
        bigint project_id FK
        varchar stage_name
        varchar status
    }
    SUPPLIERS {
        bigint id PK
        varchar name UK
        varchar status
    }
    MATERIAL_PURCHASES {
        bigint id PK
        bigint project_id FK
        bigint supplier_id
        decimal amount
    }
    PAYMENT_RECORDS {
        bigint id PK
        bigint project_id FK
        decimal amount
        date payment_date
    }
```

## 11. 图与源码对应关系

| 图 | 主要源码依据 |
| --- | --- |
| 模块图、包图 | `Main.java`、`web`、`security`、`service`、`db`、`domain`、`util` |
| 类图 | `ApiHandler`、`PageHandler`、`AuthService`、`DecorationManagementService`、`DatabaseManager` 和领域 record |
| 登录时序图 | `ApiHandler.routeAuth`、`AuthService.login`、`DatabaseManager` |
| CRUD 协作图 | `ApiHandler.route*`、`DecorationManagementService.create/update/delete*` |
| 用例图 | `AuthService` 的角色权限和前端菜单控制 |
| ER 图 | `DatabaseManager.initialize` 与[数据库表结构](数据库表结构.md) |