package org.example.db;

import org.example.util.AppException;
import org.example.util.PasswordUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private final String jdbcUrl;

    public DatabaseManager() {
        this(Path.of("data", "manage-system"));
    }

    public DatabaseManager(Path dbFile) {
        this.jdbcUrl = "jdbc:h2:file:" + dbFile.toAbsolutePath() + ";AUTO_SERVER=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        loadDriver();
        initialize();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    public <T> T queryOne(String sql, SqlConsumer<PreparedStatement> binder, SqlFunction<ResultSet, T> mapper) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.accept(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapper.apply(resultSet);
            }
        } catch (SQLException ex) {
            throw new AppException(500, "数据库查询失败: " + ex.getMessage());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(500, "数据库查询失败: " + ex.getMessage());
        }
    }

    public <T> List<T> queryList(String sql, SqlConsumer<PreparedStatement> binder, SqlFunction<ResultSet, T> mapper) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.accept(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(mapper.apply(resultSet));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new AppException(500, "数据库查询失败: " + ex.getMessage());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(500, "数据库查询失败: " + ex.getMessage());
        }
    }

    public long insert(String sql, SqlConsumer<PreparedStatement> binder) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (binder != null) {
                binder.accept(statement);
            }
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
                return -1L;
            }
        } catch (SQLException ex) {
            throw new AppException(500, "数据库写入失败: " + ex.getMessage());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(500, "数据库写入失败: " + ex.getMessage());
        }
    }

    public int update(String sql, SqlConsumer<PreparedStatement> binder) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.accept(statement);
            }
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new AppException(500, "数据库更新失败: " + ex.getMessage());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(500, "数据库更新失败: " + ex.getMessage());
        }
    }

    public void transaction(SqlConsumer<Connection> callback) {
        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                callback.accept(connection);
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof AppException appException) {
                    throw appException;
                }
                throw new AppException(500, "数据库事务失败: " + ex.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw new AppException(500, "数据库事务失败: " + ex.getMessage());
        }
    }

    private void loadDriver() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            throw new AppException(500, "未找到 H2 数据库驱动，请确认已将 lib/h2-2.2.224.jar 加入运行时 classpath。");
        }
    }

    private void initialize() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists users (
                        id bigint auto_increment primary key,
                        username varchar(64) not null unique,
                        display_name varchar(64) not null,
                        password_hash varchar(256) not null,
                        password_salt varchar(256) not null,
                        role varchar(32) not null,
                        status varchar(32) not null,
                        created_at timestamp not null
                    )
                    """);
            statement.execute("alter table users add column if not exists employee_id bigint");
            statement.execute("""
                    create table if not exists user_sessions (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        token varchar(128) not null unique,
                        expires_at timestamp not null,
                        created_at timestamp not null,
                        constraint fk_sessions_user foreign key (user_id) references users(id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table if not exists customers (
                        id bigint auto_increment primary key,
                        name varchar(64) not null,
                        phone varchar(32) not null,
                        source varchar(64),
                        level varchar(32) not null,
                        intention varchar(32) not null,
                        address varchar(255),
                        notes clob,
                        created_date date not null
                    )
                    """);
            statement.execute("""
                    create table if not exists employees (
                        id bigint auto_increment primary key,
                        name varchar(64) not null,
                        role varchar(32) not null,
                        phone varchar(32) not null,
                        specialty varchar(128),
                        status varchar(32) not null,
                        hire_date date not null,
                        notes clob
                    )
                    """);
            statement.execute("""
                    create table if not exists projects (
                        id bigint auto_increment primary key,
                        name varchar(128) not null,
                        customer_id bigint not null,
                        manager_id bigint not null,
                        status varchar(32) not null,
                        style varchar(64),
                        address varchar(255) not null,
                        area decimal(10,2) not null,
                        contract_amount decimal(12,2) not null,
                        start_date date not null,
                        expected_end_date date not null,
                        notes clob,
                        constraint fk_projects_customer foreign key (customer_id) references customers(id),
                        constraint fk_projects_manager foreign key (manager_id) references employees(id)
                    )
                    """);
            statement.execute("""
                    create table if not exists project_stages (
                        id bigint auto_increment primary key,
                        project_id bigint not null,
                        stage_name varchar(64) not null,
                        owner varchar(64),
                        status varchar(32) not null,
                        planned_date date not null,
                        actual_date date,
                        notes clob,
                        constraint fk_stages_project foreign key (project_id) references projects(id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table if not exists material_purchases (
                        id bigint auto_increment primary key,
                        project_id bigint not null,
                        material_name varchar(128) not null,
                        category varchar(64) not null,
                        supplier varchar(128),
                        amount decimal(12,2) not null,
                        status varchar(32) not null,
                        purchase_date date not null,
                        notes clob,
                        constraint fk_materials_project foreign key (project_id) references projects(id) on delete cascade
                    )
                    """);
            statement.execute("alter table material_purchases add column if not exists supplier_id bigint");
            statement.execute("""
                    create table if not exists payment_records (
                        id bigint auto_increment primary key,
                        project_id bigint not null,
                        type varchar(32) not null,
                        amount decimal(12,2) not null,
                        status varchar(32) not null,
                        payment_date date not null,
                        payer varchar(64),
                        notes clob,
                        constraint fk_payments_project foreign key (project_id) references projects(id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table if not exists suppliers (
                        id bigint auto_increment primary key,
                        name varchar(128) not null unique,
                        contact_name varchar(64),
                        phone varchar(32),
                        category varchar(64),
                        address varchar(255),
                        status varchar(32) not null,
                        notes clob,
                        created_date date not null
                    )
                    """);
        } catch (SQLException ex) {
            throw new AppException(500, "初始化数据库失败: " + ex.getMessage());
        }
        seed();
        migrateSupplierLinks();
        migrateLegacyEmployeeRoles();
        migrateUserEmployeeLinks();
    }

    private void seed() {
        Integer userCount = queryOne("select count(*) from users", null, resultSet -> resultSet.getInt(1));
        if (userCount == null || userCount == 0) {
            insertUser("admin", "系统管理员", "admin123", "ADMIN");
            insertUser("manager", "项目经理账号", "manager123", "MANAGER");
            insertUser("finance", "财务账号", "finance123", "FINANCE");
            insertUser("designer", "设计师账号", "designer123", "DESIGNER");
        }

        Integer customerCount = queryOne("select count(*) from customers", null, resultSet -> resultSet.getInt(1));
        if (customerCount != null && customerCount > 0) {
            return;
        }

        transaction(connection -> {
            long customer1 = insert(connection,
                    "insert into customers(name, phone, source, level, intention, address, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "张先生");
                        statement.setString(2, "13800000001");
                        statement.setString(3, "抖音");
                        statement.setString(4, "重点客户");
                        statement.setString(5, "高");
                        statement.setString(6, "浦东新区锦绣路");
                        statement.setString(7, "偏好现代轻奢风格");
                        statement.setDate(8, Date.valueOf(LocalDate.now().minusDays(18)));
                    });
            long customer2 = insert(connection,
                    "insert into customers(name, phone, source, level, intention, address, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "李女士");
                        statement.setString(2, "13800000002");
                        statement.setString(3, "老客户转介绍");
                        statement.setString(4, "VIP客户");
                        statement.setString(5, "高");
                        statement.setString(6, "闵行区都市路");
                        statement.setString(7, "关注环保材料");
                        statement.setDate(8, Date.valueOf(LocalDate.now().minusDays(28)));
                    });

            long employee1 = insert(connection,
                    "insert into employees(name, role, phone, specialty, status, hire_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "王工");
                        statement.setString(2, "项目经理");
                        statement.setString(3, "13900000011");
                        statement.setString(4, "施工统筹");
                        statement.setString(5, "在职");
                        statement.setDate(6, Date.valueOf(LocalDate.now().minusYears(2)));
                        statement.setString(7, "负责工地排期");
                    });
            long employee2 = insert(connection,
                    "insert into employees(name, role, phone, specialty, status, hire_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "陈设计");
                        statement.setString(2, "设计师");
                        statement.setString(3, "13900000012");
                        statement.setString(4, "全案设计");
                        statement.setString(5, "在职");
                        statement.setDate(6, Date.valueOf(LocalDate.now().minusYears(1)));
                        statement.setString(7, "擅长现代风与奶油风");
                    });
            insert(connection,
                    "insert into employees(name, role, phone, specialty, status, hire_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "赵经理");
                        statement.setString(2, "项目经理");
                        statement.setString(3, "13900000013");
                        statement.setString(4, "质量验收");
                        statement.setString(5, "在职");
                        statement.setDate(6, Date.valueOf(LocalDate.now().minusMonths(16)));
                        statement.setString(7, "负责关键节点验收");
                    });

            long supplier1 = insert(connection,
                    "insert into suppliers(name, contact_name, phone, category, address, status, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "上海优家建材");
                        statement.setString(2, "刘经理");
                        statement.setString(3, "021-66886688");
                        statement.setString(4, "主材");
                        statement.setString(5, "上海市闵行区建材一路 18 号");
                        statement.setString(6, "合作中");
                        statement.setString(7, "瓷砖与卫浴长期合作商");
                        statement.setDate(8, Date.valueOf(LocalDate.now().minusMonths(6)));
                    });
            long supplier2 = insert(connection,
                    "insert into suppliers(name, contact_name, phone, category, address, status, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "绿筑供应链");
                        statement.setString(2, "周采购");
                        statement.setString(3, "021-66997788");
                        statement.setString(4, "辅材");
                        statement.setString(5, "上海市嘉定区材料大道 66 号");
                        statement.setString(6, "合作中");
                        statement.setString(7, "板材与环保辅材供应商");
                        statement.setDate(8, Date.valueOf(LocalDate.now().minusMonths(3)));
                    });

            long project1 = insert(connection,
                    "insert into projects(name, customer_id, manager_id, status, style, address, area, contract_amount, start_date, expected_end_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "张先生婚房装修");
                        statement.setLong(2, customer1);
                        statement.setLong(3, employee1);
                        statement.setString(4, "施工中");
                        statement.setString(5, "现代轻奢");
                        statement.setString(6, "浦东新区锦绣路 88 号");
                        statement.setBigDecimal(7, new BigDecimal("118.50"));
                        statement.setBigDecimal(8, new BigDecimal("268000.00"));
                        statement.setDate(9, Date.valueOf(LocalDate.now().minusDays(10)));
                        statement.setDate(10, Date.valueOf(LocalDate.now().plusDays(70)));
                        statement.setString(11, "包含客厅背景墙与全屋收纳");
                    });
            long project2 = insert(connection,
                    "insert into projects(name, customer_id, manager_id, status, style, address, area, contract_amount, start_date, expected_end_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setString(1, "李女士旧房翻新");
                        statement.setLong(2, customer2);
                        statement.setLong(3, employee2);
                        statement.setString(4, "设计中");
                        statement.setString(5, "原木奶油");
                        statement.setString(6, "闵行区都市路 66 号");
                        statement.setBigDecimal(7, new BigDecimal("92.00"));
                        statement.setBigDecimal(8, new BigDecimal("186000.00"));
                        statement.setDate(9, Date.valueOf(LocalDate.now().minusDays(5)));
                        statement.setDate(10, Date.valueOf(LocalDate.now().plusDays(90)));
                        statement.setString(11, "厨房和卫生间重点改造");
                    });

            insert(connection,
                    "insert into project_stages(project_id, stage_name, owner, status, planned_date, actual_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project1);
                        statement.setString(2, "水电交底");
                        statement.setString(3, "王工");
                        statement.setString(4, "已完成");
                        statement.setDate(5, Date.valueOf(LocalDate.now().minusDays(7)));
                        statement.setDate(6, Date.valueOf(LocalDate.now().minusDays(6)));
                        statement.setString(7, "客户已确认插座点位");
                    });
            insert(connection,
                    "insert into project_stages(project_id, stage_name, owner, status, planned_date, actual_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project1);
                        statement.setString(2, "泥木施工");
                        statement.setString(3, "赵经理");
                        statement.setString(4, "进行中");
                        statement.setDate(5, Date.valueOf(LocalDate.now().plusDays(5)));
                        statement.setNull(6, java.sql.Types.DATE);
                        statement.setString(7, "需要重点跟进瓷砖铺贴");
                    });
            insert(connection,
                    "insert into project_stages(project_id, stage_name, owner, status, planned_date, actual_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project2);
                        statement.setString(2, "方案定稿");
                        statement.setString(3, "陈设计");
                        statement.setString(4, "进行中");
                        statement.setDate(5, Date.valueOf(LocalDate.now().plusDays(2)));
                        statement.setNull(6, java.sql.Types.DATE);
                        statement.setString(7, "等待客户确认主卧配色");
                    });

            insert(connection,
                    "insert into material_purchases(project_id, material_name, category, supplier, supplier_id, amount, status, purchase_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project1);
                        statement.setString(2, "蒙娜丽莎瓷砖");
                        statement.setString(3, "主材");
                        statement.setString(4, "上海优家建材");
                        statement.setLong(5, supplier1);
                        statement.setBigDecimal(6, new BigDecimal("32500.00"));
                        statement.setString(7, "已下单");
                        statement.setDate(8, Date.valueOf(LocalDate.now().minusDays(3)));
                        statement.setString(9, "客厅与厨房通铺");
                    });
            insert(connection,
                    "insert into material_purchases(project_id, material_name, category, supplier, supplier_id, amount, status, purchase_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project2);
                        statement.setString(2, "兔宝宝生态板");
                        statement.setString(3, "辅材");
                        statement.setString(4, "绿筑供应链");
                        statement.setLong(5, supplier2);
                        statement.setBigDecimal(6, new BigDecimal("14800.00"));
                        statement.setString(7, "待采购");
                        statement.setDate(8, Date.valueOf(LocalDate.now().plusDays(1)));
                        statement.setString(9, "用于全屋柜体");
                    });

            insert(connection,
                    "insert into payment_records(project_id, type, amount, status, payment_date, payer, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project1);
                        statement.setString(2, "首付款");
                        statement.setBigDecimal(3, new BigDecimal("120000.00"));
                        statement.setString(4, "已到账");
                        statement.setDate(5, Date.valueOf(LocalDate.now().minusDays(9)));
                        statement.setString(6, "张先生");
                        statement.setString(7, "签约当天到账");
                    });
            insert(connection,
                    "insert into payment_records(project_id, type, amount, status, payment_date, payer, notes) values (?, ?, ?, ?, ?, ?, ?)",
                    statement -> {
                        statement.setLong(1, project2);
                        statement.setString(2, "首付款");
                        statement.setBigDecimal(3, new BigDecimal("60000.00"));
                        statement.setString(4, "待到账");
                        statement.setDate(5, Date.valueOf(LocalDate.now().plusDays(1)));
                        statement.setString(6, "李女士");
                        statement.setString(7, "方案确认后支付");
                    });
        });
    }

    private void insertUser(String username, String displayName, String password, String role) {
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);
        insert("insert into users(username, display_name, password_hash, password_salt, role, status, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, username);
                    statement.setString(2, displayName);
                    statement.setString(3, hash);
                    statement.setString(4, salt);
                    statement.setString(5, role);
                    statement.setString(6, "启用");
                    statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                });
    }

    private void migrateSupplierLinks() {
        Integer supplierCount = queryOne("select count(*) from suppliers", null, resultSet -> resultSet.getInt(1));
        if (supplierCount == null || supplierCount == 0) {
            List<String> supplierNames = queryList(
                    "select distinct supplier from material_purchases where supplier is not null and supplier <> ''",
                    null,
                    resultSet -> resultSet.getString(1)
            );
            for (String supplierName : supplierNames) {
                insert(
                        "insert into suppliers(name, contact_name, phone, category, address, status, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        statement -> {
                            statement.setString(1, supplierName);
                            statement.setString(2, "");
                            statement.setString(3, "");
                            statement.setString(4, "未分类");
                            statement.setString(5, "");
                            statement.setString(6, "合作中");
                            statement.setString(7, "由历史采购数据自动迁移生成");
                            statement.setDate(8, Date.valueOf(LocalDate.now()));
                        }
                );
            }
        }

        transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "select id, supplier from material_purchases where supplier_id is null and supplier is not null and supplier <> ''");
                 ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) {
                    long materialId = resultSet.getLong("id");
                    String supplierName = resultSet.getString("supplier");
                    try (PreparedStatement findSupplier = connection.prepareStatement("select id from suppliers where name = ?")) {
                        findSupplier.setString(1, supplierName);
                        try (ResultSet supplierResult = findSupplier.executeQuery()) {
                            if (supplierResult.next()) {
                                try (PreparedStatement update = connection.prepareStatement("update material_purchases set supplier_id = ? where id = ?")) {
                                    update.setLong(1, supplierResult.getLong(1));
                                    update.setLong(2, materialId);
                                    update.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void migrateUserEmployeeLinks() {
        transaction(connection -> {
            linkUserToEmployee(connection, "manager", "王工");
            linkUserToEmployee(connection, "designer", "陈设计");
        });
    }

    private void migrateLegacyEmployeeRoles() {
        transaction(connection -> {
            try (PreparedStatement updateMonitorRole = connection.prepareStatement(
                    "update employees set role = '项目经理' where role in ('监理', '采购')")) {
                updateMonitorRole.executeUpdate();
            }
            try (PreparedStatement updateBudgetRole = connection.prepareStatement(
                    "update employees set role = '财务' where role = '预算员'")) {
                updateBudgetRole.executeUpdate();
            }
            try (PreparedStatement renameEmployee = connection.prepareStatement(
                    "update employees set name = '赵经理' where name = '赵监理'")) {
                renameEmployee.executeUpdate();
            }
            try (PreparedStatement renameStageOwner = connection.prepareStatement(
                    "update project_stages set owner = '赵经理' where owner = '赵监理'")) {
                renameStageOwner.executeUpdate();
            }
        });
    }

    private void linkUserToEmployee(Connection connection, String username, String employeeName) throws SQLException {
        try (PreparedStatement userSelect = connection.prepareStatement(
                "select employee_id from users where username = ?")) {
            userSelect.setString(1, username);
            try (ResultSet userResult = userSelect.executeQuery()) {
                if (!userResult.next() || userResult.getObject("employee_id") != null) {
                    return;
                }
            }
        }

        Long employeeId = null;
        try (PreparedStatement employeeSelect = connection.prepareStatement(
                "select id from employees where name = ? order by id asc limit 1")) {
            employeeSelect.setString(1, employeeName);
            try (ResultSet employeeResult = employeeSelect.executeQuery()) {
                if (employeeResult.next()) {
                    employeeId = employeeResult.getLong(1);
                }
            }
        }

        if (employeeId == null) {
            return;
        }

        try (PreparedStatement update = connection.prepareStatement(
                "update users set employee_id = ? where username = ? and employee_id is null")) {
            update.setLong(1, employeeId);
            update.setString(2, username);
            update.executeUpdate();
        }
    }

    private long insert(Connection connection, String sql, SqlConsumer<PreparedStatement> binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            try {
                binder.accept(statement);
            } catch (AppException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AppException(500, "数据库写入失败: " + ex.getMessage());
            }
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
                return -1L;
            }
        }
    }
}
