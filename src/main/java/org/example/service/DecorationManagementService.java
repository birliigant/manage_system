package org.example.service;

import org.example.db.DatabaseManager;
import org.example.util.AppException;
import org.example.util.PasswordUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DecorationManagementService {
    private static final List<String> USER_ROLES = List.of("ADMIN", "MANAGER", "FINANCE", "DESIGNER");
    private static final List<String> USER_STATUSES = List.of("启用", "停用");
    private static final List<String> EMPLOYEE_ROLES = List.of("项目经理", "设计师", "财务", "管理员");
    private static final List<String> SUPPLIER_STATUSES = List.of("合作中", "待评估", "暂停合作");
    private static final List<String> SUPPLIER_CATEGORIES = List.of("主材", "辅材", "定制", "软装", "未分类");

    private final DatabaseManager databaseManager;

    public DecorationManagementService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Map<String, Object> dashboardSummary() {
        BigDecimal contractAmount = databaseManager.queryOne(
                "select coalesce(sum(contract_amount), 0) from projects",
                null,
                resultSet -> resultSet.getBigDecimal(1)
        );
        BigDecimal receivedAmount = databaseManager.queryOne(
                "select coalesce(sum(amount), 0) from payment_records where status = '已到账'",
                null,
                resultSet -> resultSet.getBigDecimal(1)
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerCount", scalarCount("customers"));
        summary.put("employeeCount", scalarCount("employees"));
        summary.put("projectCount", scalarCount("projects"));
        summary.put("supplierCount", scalarCount("suppliers"));
        summary.put("activeProjectCount", databaseManager.queryOne(
                "select count(*) from projects where status <> '已完工'",
                null,
                resultSet -> resultSet.getLong(1)
        ));
        summary.put("pendingStageCount", databaseManager.queryOne(
                "select count(*) from project_stages where status <> '已完成'",
                null,
                resultSet -> resultSet.getLong(1)
        ));
        summary.put("pendingMaterialCount", databaseManager.queryOne(
                "select count(*) from material_purchases where status <> '已结算'",
                null,
                resultSet -> resultSet.getLong(1)
        ));
        summary.put("contractAmount", toMoney(contractAmount));
        summary.put("receivedAmount", toMoney(receivedAmount));
        summary.put("recentProjects", listProjects(null, null));
        summary.put("upcomingStages", databaseManager.queryList(
                """
                select s.id, s.project_id, p.name as project_name, s.stage_name, s.owner, s.status, s.planned_date, s.actual_date, s.notes
                from project_stages s
                join projects p on p.id = s.project_id
                where s.status <> '已完成'
                order by s.planned_date asc, s.id desc
                limit 5
                """,
                null,
                this::stageMap
        ));
        return summary;
    }

    public List<Map<String, Object>> listCustomers(String keyword) {
        String normalized = normalizeKeyword(keyword);
        return databaseManager.queryList(
                """
                select c.*,
                       (select count(*) from projects p where p.customer_id = c.id) as project_count
                from customers c
                where (? = '' or lower(c.name) like ? or lower(c.phone) like ? or lower(coalesce(c.source, '')) like ? or lower(coalesce(c.address, '')) like ?)
                order by c.created_date desc, c.id desc
                """,
                statement -> {
                    String likeKeyword = "%" + normalized + "%";
                    statement.setString(1, normalized);
                    statement.setString(2, likeKeyword);
                    statement.setString(3, likeKeyword);
                    statement.setString(4, likeKeyword);
                    statement.setString(5, likeKeyword);
                },
                this::customerMap
        );
    }

    public Map<String, Object> getCustomer(long id) {
        Map<String, Object> customer = databaseManager.queryOne(
                """
                select c.*,
                       (select count(*) from projects p where p.customer_id = c.id) as project_count
                from customers c
                where c.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::customerMap
        );
        if (customer == null) {
            throw new AppException(404, "客户不存在。");
        }
        return customer;
    }

    public Map<String, Object> createCustomer(Map<String, Object> payload) {
        long id = databaseManager.insert(
                "insert into customers(name, phone, source, level, intention, address, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setString(2, requiredText(payload, "phone"));
                    statement.setString(3, optionalText(payload, "source"));
                    statement.setString(4, defaultText(payload, "level", "普通客户"));
                    statement.setString(5, defaultText(payload, "intention", "中"));
                    statement.setString(6, optionalText(payload, "address"));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setDate(8, Date.valueOf(parseDate(payload.get("createdDate"), LocalDate.now())));
                });
        return getCustomer(id);
    }

    public Map<String, Object> updateCustomer(long id, Map<String, Object> payload) {
        getCustomer(id);
        databaseManager.update(
                "update customers set name = ?, phone = ?, source = ?, level = ?, intention = ?, address = ?, notes = ?, created_date = ? where id = ?",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setString(2, requiredText(payload, "phone"));
                    statement.setString(3, optionalText(payload, "source"));
                    statement.setString(4, defaultText(payload, "level", "普通客户"));
                    statement.setString(5, defaultText(payload, "intention", "中"));
                    statement.setString(6, optionalText(payload, "address"));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setDate(8, Date.valueOf(parseDate(payload.get("createdDate"), LocalDate.now())));
                    statement.setLong(9, id);
                });
        return getCustomer(id);
    }

    public void deleteCustomer(long id) {
        Long count = databaseManager.queryOne(
                "select count(*) from projects where customer_id = ?",
                statement -> statement.setLong(1, id),
                resultSet -> resultSet.getLong(1)
        );
        if (count != null && count > 0) {
            throw new AppException(400, "该客户已关联项目，无法删除。");
        }
        int affected = databaseManager.update("delete from customers where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "客户不存在。");
        }
    }

    public List<Map<String, Object>> listEmployees(String keyword) {
        String normalized = normalizeKeyword(keyword);
        return databaseManager.queryList(
                """
                select e.*,
                       (select count(*) from projects p where p.manager_id = e.id) as managed_project_count,
                       u.id as linked_user_id,
                       u.username as linked_username,
                       u.status as linked_user_status
                from employees e
                left join users u on u.employee_id = e.id
                where (? = '' or lower(e.name) like ? or lower(e.role) like ? or lower(e.phone) like ? or lower(coalesce(e.specialty, '')) like ? or lower(coalesce(u.username, '')) like ?)
                order by e.hire_date desc, e.id desc
                """,
                statement -> {
                    String likeKeyword = "%" + normalized + "%";
                    statement.setString(1, normalized);
                    statement.setString(2, likeKeyword);
                    statement.setString(3, likeKeyword);
                    statement.setString(4, likeKeyword);
                    statement.setString(5, likeKeyword);
                    statement.setString(6, likeKeyword);
                },
                this::employeeMap
        );
    }

    public Map<String, Object> getEmployee(long id) {
        Map<String, Object> employee = databaseManager.queryOne(
                """
                select e.*,
                       (select count(*) from projects p where p.manager_id = e.id) as managed_project_count,
                       u.id as linked_user_id,
                       u.username as linked_username,
                       u.status as linked_user_status
                from employees e
                left join users u on u.employee_id = e.id
                where e.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::employeeMap
        );
        if (employee == null) {
            throw new AppException(404, "员工不存在。");
        }
        return employee;
    }

    public Map<String, Object> createEmployee(Map<String, Object> payload) {
        long id = databaseManager.insert(
                "insert into employees(name, role, phone, specialty, status, hire_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setString(2, normalizeEmployeeRole(requiredText(payload, "role")));
                    statement.setString(3, requiredText(payload, "phone"));
                    statement.setString(4, optionalText(payload, "specialty"));
                    statement.setString(5, defaultText(payload, "status", "在职"));
                    statement.setDate(6, Date.valueOf(parseDate(payload.get("hireDate"), LocalDate.now())));
                    statement.setString(7, optionalText(payload, "notes"));
                });
        return getEmployee(id);
    }

    public Map<String, Object> updateEmployee(long id, Map<String, Object> payload) {
        getEmployee(id);
        databaseManager.update(
                "update employees set name = ?, role = ?, phone = ?, specialty = ?, status = ?, hire_date = ?, notes = ? where id = ?",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setString(2, normalizeEmployeeRole(requiredText(payload, "role")));
                    statement.setString(3, requiredText(payload, "phone"));
                    statement.setString(4, optionalText(payload, "specialty"));
                    statement.setString(5, defaultText(payload, "status", "在职"));
                    statement.setDate(6, Date.valueOf(parseDate(payload.get("hireDate"), LocalDate.now())));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setLong(8, id);
                });
        return getEmployee(id);
    }

    public void deleteEmployee(long id) {
        Long projectCount = databaseManager.queryOne(
                "select count(*) from projects where manager_id = ?",
                statement -> statement.setLong(1, id),
                resultSet -> resultSet.getLong(1)
        );
        if (projectCount != null && projectCount > 0) {
            throw new AppException(400, "该员工仍负责项目，无法删除。");
        }
        Long userCount = databaseManager.queryOne(
                "select count(*) from users where employee_id = ?",
                statement -> statement.setLong(1, id),
                resultSet -> resultSet.getLong(1)
        );
        if (userCount != null && userCount > 0) {
            throw new AppException(400, "该员工已绑定系统账号，请先删除或解绑账号。");
        }
        int affected = databaseManager.update("delete from employees where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "员工不存在。");
        }
    }

    public List<Map<String, Object>> listUsers(String keyword) {
        String normalized = normalizeKeyword(keyword);
        return databaseManager.queryList(
                """
                select u.id, u.username, u.display_name, u.role, u.status, u.created_at, u.employee_id,
                       e.name as employee_name, e.role as employee_role
                from users u
                left join employees e on e.id = u.employee_id
                where (? = '' or lower(u.username) like ? or lower(u.display_name) like ? or lower(u.role) like ? or lower(coalesce(e.name, '')) like ?)
                order by u.created_at desc, u.id desc
                """,
                statement -> {
                    String likeKeyword = "%" + normalized + "%";
                    statement.setString(1, normalized);
                    statement.setString(2, likeKeyword);
                    statement.setString(3, likeKeyword);
                    statement.setString(4, likeKeyword);
                    statement.setString(5, likeKeyword);
                },
                this::userMap
        );
    }

    public Map<String, Object> getUser(long id) {
        Map<String, Object> user = databaseManager.queryOne(
                """
                select u.id, u.username, u.display_name, u.role, u.status, u.created_at, u.employee_id,
                       e.name as employee_name, e.role as employee_role
                from users u
                left join employees e on e.id = u.employee_id
                where u.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::userMap
        );
        if (user == null) {
            throw new AppException(404, "账号不存在。");
        }
        return user;
    }

    public Map<String, Object> createUser(Map<String, Object> payload) {
        String username = requiredText(payload, "username");
        ensureUsernameAvailable(username, null);

        Long employeeId = optionalLongValue(payload, "employeeId");
        ensureEmployeeBindable(employeeId, null);

        String displayName = resolveDisplayName(payload, employeeId);
        String role = normalizeUserRole(defaultText(payload, "role", "MANAGER"));
        String status = normalizeUserStatus(defaultText(payload, "status", "启用"));
        String rawPassword = requiredText(payload, "password");
        validatePassword(rawPassword);
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(rawPassword, salt);

        long id = databaseManager.insert(
                "insert into users(username, display_name, password_hash, password_salt, role, status, created_at, employee_id) values (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), ?)",
                statement -> {
                    statement.setString(1, username);
                    statement.setString(2, displayName);
                    statement.setString(3, hash);
                    statement.setString(4, salt);
                    statement.setString(5, role);
                    statement.setString(6, status);
                    bindNullableLong(statement, 7, employeeId);
                }
        );
        return getUser(id);
    }

    public Map<String, Object> updateUser(long id, Map<String, Object> payload) {
        Map<String, Object> existing = getUser(id);
        String username = requiredText(payload, "username");
        ensureUsernameAvailable(username, id);

        Long employeeId = optionalLongValue(payload, "employeeId");
        ensureEmployeeBindable(employeeId, id);

        String displayName = resolveDisplayName(payload, employeeId);
        String role = normalizeUserRole(defaultText(payload, "role", String.valueOf(existing.get("role"))));
        String status = normalizeUserStatus(defaultText(payload, "status", String.valueOf(existing.get("status"))));
        String password = optionalText(payload, "password");

        if (password.isBlank()) {
            databaseManager.update(
                    "update users set username = ?, display_name = ?, role = ?, status = ?, employee_id = ? where id = ?",
                    statement -> {
                        statement.setString(1, username);
                        statement.setString(2, displayName);
                        statement.setString(3, role);
                        statement.setString(4, status);
                        bindNullableLong(statement, 5, employeeId);
                        statement.setLong(6, id);
                    }
            );
        } else {
            validatePassword(password);
            String salt = PasswordUtils.generateSalt();
            String hash = PasswordUtils.hashPassword(password, salt);
            databaseManager.update(
                    "update users set username = ?, display_name = ?, password_hash = ?, password_salt = ?, role = ?, status = ?, employee_id = ? where id = ?",
                    statement -> {
                        statement.setString(1, username);
                        statement.setString(2, displayName);
                        statement.setString(3, hash);
                        statement.setString(4, salt);
                        statement.setString(5, role);
                        statement.setString(6, status);
                        bindNullableLong(statement, 7, employeeId);
                        statement.setLong(8, id);
                    }
            );
        }
        return getUser(id);
    }

    public void deleteUser(long id) {
        int affected = databaseManager.update("delete from users where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "账号不存在。");
        }
    }

    public Map<String, Object> resetUserPassword(long id, Map<String, Object> payload) {
        getUser(id);
        String password = requiredText(payload, "password");
        validatePassword(password);
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);
        databaseManager.update(
                "update users set password_hash = ?, password_salt = ? where id = ?",
                statement -> {
                    statement.setString(1, hash);
                    statement.setString(2, salt);
                    statement.setLong(3, id);
                }
        );
        return getUser(id);
    }

    public List<Map<String, Object>> listProjects(String keyword, String status) {
        String normalized = normalizeKeyword(keyword);
        String normalizedStatus = status == null ? "" : status.trim();
        return databaseManager.queryList(
                """
                select p.*, c.name as customer_name, e.name as manager_name,
                       (select count(*) from project_stages s where s.project_id = p.id) as stage_count
                from projects p
                join customers c on c.id = p.customer_id
                join employees e on e.id = p.manager_id
                where (? = '' or lower(p.name) like ? or lower(c.name) like ? or lower(e.name) like ? or lower(p.address) like ?)
                  and (? = '' or p.status = ?)
                order by p.start_date desc, p.id desc
                """,
                statement -> {
                    String likeKeyword = "%" + normalized + "%";
                    statement.setString(1, normalized);
                    statement.setString(2, likeKeyword);
                    statement.setString(3, likeKeyword);
                    statement.setString(4, likeKeyword);
                    statement.setString(5, likeKeyword);
                    statement.setString(6, normalizedStatus);
                    statement.setString(7, normalizedStatus);
                },
                this::projectMap
        );
    }

    public Map<String, Object> getProject(long id) {
        Map<String, Object> project = databaseManager.queryOne(
                """
                select p.*, c.name as customer_name, e.name as manager_name,
                       (select count(*) from project_stages s where s.project_id = p.id) as stage_count
                from projects p
                join customers c on c.id = p.customer_id
                join employees e on e.id = p.manager_id
                where p.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::projectMap
        );
        if (project == null) {
            throw new AppException(404, "项目不存在。");
        }
        project.put("stages", listStages(id));
        project.put("materials", listMaterials(id));
        project.put("payments", listPayments(id));
        return project;
    }

    public Map<String, Object> createProject(Map<String, Object> payload) {
        long customerId = requiredLong(payload, "customerId");
        long managerId = requiredLong(payload, "managerId");
        getCustomer(customerId);
        getEmployee(managerId);
        long id = databaseManager.insert(
                "insert into projects(name, customer_id, manager_id, status, style, address, area, contract_amount, start_date, expected_end_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setLong(2, customerId);
                    statement.setLong(3, managerId);
                    statement.setString(4, defaultText(payload, "status", "施工中"));
                    statement.setString(5, optionalText(payload, "style"));
                    statement.setString(6, requiredText(payload, "address"));
                    statement.setBigDecimal(7, requiredDecimal(payload, "area"));
                    statement.setBigDecimal(8, requiredMoney(payload, "contractAmount"));
                    statement.setDate(9, Date.valueOf(parseDate(payload.get("startDate"), LocalDate.now())));
                    statement.setDate(10, Date.valueOf(parseDate(payload.get("expectedEndDate"), LocalDate.now().plusMonths(3))));
                    statement.setString(11, optionalText(payload, "notes"));
                });
        return getProject(id);
    }

    public Map<String, Object> updateProject(long id, Map<String, Object> payload) {
        getProject(id);
        long customerId = requiredLong(payload, "customerId");
        long managerId = requiredLong(payload, "managerId");
        getCustomer(customerId);
        getEmployee(managerId);
        databaseManager.update(
                "update projects set name = ?, customer_id = ?, manager_id = ?, status = ?, style = ?, address = ?, area = ?, contract_amount = ?, start_date = ?, expected_end_date = ?, notes = ? where id = ?",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setLong(2, customerId);
                    statement.setLong(3, managerId);
                    statement.setString(4, defaultText(payload, "status", "施工中"));
                    statement.setString(5, optionalText(payload, "style"));
                    statement.setString(6, requiredText(payload, "address"));
                    statement.setBigDecimal(7, requiredDecimal(payload, "area"));
                    statement.setBigDecimal(8, requiredMoney(payload, "contractAmount"));
                    statement.setDate(9, Date.valueOf(parseDate(payload.get("startDate"), LocalDate.now())));
                    statement.setDate(10, Date.valueOf(parseDate(payload.get("expectedEndDate"), LocalDate.now().plusMonths(3))));
                    statement.setString(11, optionalText(payload, "notes"));
                    statement.setLong(12, id);
                });
        return getProject(id);
    }

    public void deleteProject(long id) {
        int affected = databaseManager.update("delete from projects where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "项目不存在。");
        }
    }

    public List<Map<String, Object>> listStages(Long projectId) {
        return databaseManager.queryList(
                """
                select s.id, s.project_id, p.name as project_name, s.stage_name, s.owner, s.status, s.planned_date, s.actual_date, s.notes
                from project_stages s
                join projects p on p.id = s.project_id
                where (? is null or s.project_id = ?)
                order by s.planned_date desc, s.id desc
                """,
                statement -> {
                    if (projectId == null) {
                        statement.setNull(1, Types.BIGINT);
                        statement.setNull(2, Types.BIGINT);
                    } else {
                        statement.setLong(1, projectId);
                        statement.setLong(2, projectId);
                    }
                },
                this::stageMap
        );
    }

    public Map<String, Object> getStage(long id) {
        Map<String, Object> stage = databaseManager.queryOne(
                """
                select s.id, s.project_id, p.name as project_name, s.stage_name, s.owner, s.status, s.planned_date, s.actual_date, s.notes
                from project_stages s
                join projects p on p.id = s.project_id
                where s.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::stageMap
        );
        if (stage == null) {
            throw new AppException(404, "施工阶段不存在。");
        }
        return stage;
    }

    public Map<String, Object> createStage(Map<String, Object> payload) {
        long projectId = requiredLong(payload, "projectId");
        getProject(projectId);
        long id = databaseManager.insert(
                "insert into project_stages(project_id, stage_name, owner, status, planned_date, actual_date, notes) values (?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, projectId);
                    statement.setString(2, requiredText(payload, "stageName"));
                    statement.setString(3, optionalText(payload, "owner"));
                    statement.setString(4, defaultText(payload, "status", "未开始"));
                    statement.setDate(5, Date.valueOf(parseDate(payload.get("plannedDate"), LocalDate.now())));
                    bindNullableDate(statement, 6, payload.get("actualDate"));
                    statement.setString(7, optionalText(payload, "notes"));
                });
        return getStage(id);
    }

    public Map<String, Object> updateStage(long id, Map<String, Object> payload) {
        getStage(id);
        long projectId = requiredLong(payload, "projectId");
        getProject(projectId);
        databaseManager.update(
                "update project_stages set project_id = ?, stage_name = ?, owner = ?, status = ?, planned_date = ?, actual_date = ?, notes = ? where id = ?",
                statement -> {
                    statement.setLong(1, projectId);
                    statement.setString(2, requiredText(payload, "stageName"));
                    statement.setString(3, optionalText(payload, "owner"));
                    statement.setString(4, defaultText(payload, "status", "未开始"));
                    statement.setDate(5, Date.valueOf(parseDate(payload.get("plannedDate"), LocalDate.now())));
                    bindNullableDate(statement, 6, payload.get("actualDate"));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setLong(8, id);
                });
        return getStage(id);
    }

    public void deleteStage(long id) {
        int affected = databaseManager.update("delete from project_stages where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "施工阶段不存在。");
        }
    }

    public List<Map<String, Object>> listSuppliers(String keyword) {
        String normalized = normalizeKeyword(keyword);
        return databaseManager.queryList(
                """
                select s.*,
                       (select count(*) from material_purchases m where m.supplier_id = s.id) as purchase_count
                from suppliers s
                where (? = '' or lower(s.name) like ? or lower(coalesce(s.contact_name, '')) like ? or lower(coalesce(s.phone, '')) like ? or lower(coalesce(s.category, '')) like ?)
                order by s.created_date desc, s.id desc
                """,
                statement -> {
                    String likeKeyword = "%" + normalized + "%";
                    statement.setString(1, normalized);
                    statement.setString(2, likeKeyword);
                    statement.setString(3, likeKeyword);
                    statement.setString(4, likeKeyword);
                    statement.setString(5, likeKeyword);
                },
                this::supplierMap
        );
    }

    public Map<String, Object> getSupplier(long id) {
        Map<String, Object> supplier = databaseManager.queryOne(
                """
                select s.*,
                       (select count(*) from material_purchases m where m.supplier_id = s.id) as purchase_count
                from suppliers s
                where s.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::supplierMap
        );
        if (supplier == null) {
            throw new AppException(404, "供应商不存在。");
        }
        return supplier;
    }

    public Map<String, Object> createSupplier(Map<String, Object> payload) {
        ensureSupplierNameAvailable(requiredText(payload, "name"), null);
        long id = databaseManager.insert(
                "insert into suppliers(name, contact_name, phone, category, address, status, notes, created_date) values (?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setString(2, optionalText(payload, "contactName"));
                    statement.setString(3, optionalText(payload, "phone"));
                    statement.setString(4, normalizeSupplierCategory(defaultText(payload, "category", "未分类")));
                    statement.setString(5, optionalText(payload, "address"));
                    statement.setString(6, normalizeSupplierStatus(defaultText(payload, "status", "合作中")));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setDate(8, Date.valueOf(parseDate(payload.get("createdDate"), LocalDate.now())));
                }
        );
        return getSupplier(id);
    }

    public Map<String, Object> updateSupplier(long id, Map<String, Object> payload) {
        getSupplier(id);
        ensureSupplierNameAvailable(requiredText(payload, "name"), id);
        databaseManager.update(
                "update suppliers set name = ?, contact_name = ?, phone = ?, category = ?, address = ?, status = ?, notes = ?, created_date = ? where id = ?",
                statement -> {
                    statement.setString(1, requiredText(payload, "name"));
                    statement.setString(2, optionalText(payload, "contactName"));
                    statement.setString(3, optionalText(payload, "phone"));
                    statement.setString(4, normalizeSupplierCategory(defaultText(payload, "category", "未分类")));
                    statement.setString(5, optionalText(payload, "address"));
                    statement.setString(6, normalizeSupplierStatus(defaultText(payload, "status", "合作中")));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setDate(8, Date.valueOf(parseDate(payload.get("createdDate"), LocalDate.now())));
                    statement.setLong(9, id);
                }
        );
        return getSupplier(id);
    }

    public void deleteSupplier(long id) {
        Long count = databaseManager.queryOne(
                "select count(*) from material_purchases where supplier_id = ?",
                statement -> statement.setLong(1, id),
                resultSet -> resultSet.getLong(1)
        );
        if (count != null && count > 0) {
            throw new AppException(400, "该供应商已关联采购记录，无法删除。");
        }
        int affected = databaseManager.update("delete from suppliers where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "供应商不存在。");
        }
    }

    public List<Map<String, Object>> listMaterials(Long projectId) {
        return databaseManager.queryList(
                """
                select m.id, m.project_id, p.name as project_name, m.material_name, m.category, m.supplier, m.supplier_id,
                       coalesce(s.name, m.supplier) as supplier_name, m.amount, m.status, m.purchase_date, m.notes
                from material_purchases m
                join projects p on p.id = m.project_id
                left join suppliers s on s.id = m.supplier_id
                where (? is null or m.project_id = ?)
                order by m.purchase_date desc, m.id desc
                """,
                statement -> {
                    if (projectId == null) {
                        statement.setNull(1, Types.BIGINT);
                        statement.setNull(2, Types.BIGINT);
                    } else {
                        statement.setLong(1, projectId);
                        statement.setLong(2, projectId);
                    }
                },
                this::materialMap
        );
    }

    public Map<String, Object> getMaterial(long id) {
        Map<String, Object> material = databaseManager.queryOne(
                """
                select m.id, m.project_id, p.name as project_name, m.material_name, m.category, m.supplier, m.supplier_id,
                       coalesce(s.name, m.supplier) as supplier_name, m.amount, m.status, m.purchase_date, m.notes
                from material_purchases m
                join projects p on p.id = m.project_id
                left join suppliers s on s.id = m.supplier_id
                where m.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::materialMap
        );
        if (material == null) {
            throw new AppException(404, "材料采购记录不存在。");
        }
        return material;
    }

    public Map<String, Object> createMaterial(Map<String, Object> payload) {
        long projectId = requiredLong(payload, "projectId");
        getProject(projectId);
        Long supplierId = optionalLongValue(payload, "supplierId");
        String supplierName = resolveSupplierName(supplierId, optionalText(payload, "supplier"));
        long id = databaseManager.insert(
                "insert into material_purchases(project_id, material_name, category, supplier, supplier_id, amount, status, purchase_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, projectId);
                    statement.setString(2, requiredText(payload, "materialName"));
                    statement.setString(3, requiredText(payload, "category"));
                    statement.setString(4, supplierName);
                    bindNullableLong(statement, 5, supplierId);
                    statement.setBigDecimal(6, requiredMoney(payload, "amount"));
                    statement.setString(7, defaultText(payload, "status", "待采购"));
                    statement.setDate(8, Date.valueOf(parseDate(payload.get("purchaseDate"), LocalDate.now())));
                    statement.setString(9, optionalText(payload, "notes"));
                });
        return getMaterial(id);
    }

    public Map<String, Object> updateMaterial(long id, Map<String, Object> payload) {
        getMaterial(id);
        long projectId = requiredLong(payload, "projectId");
        getProject(projectId);
        Long supplierId = optionalLongValue(payload, "supplierId");
        String supplierName = resolveSupplierName(supplierId, optionalText(payload, "supplier"));
        databaseManager.update(
                "update material_purchases set project_id = ?, material_name = ?, category = ?, supplier = ?, supplier_id = ?, amount = ?, status = ?, purchase_date = ?, notes = ? where id = ?",
                statement -> {
                    statement.setLong(1, projectId);
                    statement.setString(2, requiredText(payload, "materialName"));
                    statement.setString(3, requiredText(payload, "category"));
                    statement.setString(4, supplierName);
                    bindNullableLong(statement, 5, supplierId);
                    statement.setBigDecimal(6, requiredMoney(payload, "amount"));
                    statement.setString(7, defaultText(payload, "status", "待采购"));
                    statement.setDate(8, Date.valueOf(parseDate(payload.get("purchaseDate"), LocalDate.now())));
                    statement.setString(9, optionalText(payload, "notes"));
                    statement.setLong(10, id);
                });
        return getMaterial(id);
    }

    public void deleteMaterial(long id) {
        int affected = databaseManager.update("delete from material_purchases where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "材料采购记录不存在。");
        }
    }

    public List<Map<String, Object>> listPayments(Long projectId) {
        return databaseManager.queryList(
                """
                select pr.id, pr.project_id, p.name as project_name, pr.type, pr.amount, pr.status, pr.payment_date, pr.payer, pr.notes
                from payment_records pr
                join projects p on p.id = pr.project_id
                where (? is null or pr.project_id = ?)
                order by pr.payment_date desc, pr.id desc
                """,
                statement -> {
                    if (projectId == null) {
                        statement.setNull(1, Types.BIGINT);
                        statement.setNull(2, Types.BIGINT);
                    } else {
                        statement.setLong(1, projectId);
                        statement.setLong(2, projectId);
                    }
                },
                this::paymentMap
        );
    }

    public Map<String, Object> getPayment(long id) {
        Map<String, Object> payment = databaseManager.queryOne(
                """
                select pr.id, pr.project_id, p.name as project_name, pr.type, pr.amount, pr.status, pr.payment_date, pr.payer, pr.notes
                from payment_records pr
                join projects p on p.id = pr.project_id
                where pr.id = ?
                """,
                statement -> statement.setLong(1, id),
                this::paymentMap
        );
        if (payment == null) {
            throw new AppException(404, "收款记录不存在。");
        }
        return payment;
    }

    public Map<String, Object> createPayment(Map<String, Object> payload) {
        long projectId = requiredLong(payload, "projectId");
        getProject(projectId);
        long id = databaseManager.insert(
                "insert into payment_records(project_id, type, amount, status, payment_date, payer, notes) values (?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, projectId);
                    statement.setString(2, defaultText(payload, "type", "首付款"));
                    statement.setBigDecimal(3, requiredMoney(payload, "amount"));
                    statement.setString(4, defaultText(payload, "status", "待到账"));
                    statement.setDate(5, Date.valueOf(parseDate(payload.get("paymentDate"), LocalDate.now())));
                    statement.setString(6, optionalText(payload, "payer"));
                    statement.setString(7, optionalText(payload, "notes"));
                });
        return getPayment(id);
    }

    public Map<String, Object> updatePayment(long id, Map<String, Object> payload) {
        getPayment(id);
        long projectId = requiredLong(payload, "projectId");
        getProject(projectId);
        databaseManager.update(
                "update payment_records set project_id = ?, type = ?, amount = ?, status = ?, payment_date = ?, payer = ?, notes = ? where id = ?",
                statement -> {
                    statement.setLong(1, projectId);
                    statement.setString(2, defaultText(payload, "type", "首付款"));
                    statement.setBigDecimal(3, requiredMoney(payload, "amount"));
                    statement.setString(4, defaultText(payload, "status", "待到账"));
                    statement.setDate(5, Date.valueOf(parseDate(payload.get("paymentDate"), LocalDate.now())));
                    statement.setString(6, optionalText(payload, "payer"));
                    statement.setString(7, optionalText(payload, "notes"));
                    statement.setLong(8, id);
                });
        return getPayment(id);
    }

    public void deletePayment(long id) {
        int affected = databaseManager.update("delete from payment_records where id = ?", statement -> statement.setLong(1, id));
        if (affected == 0) {
            throw new AppException(404, "收款记录不存在。");
        }
    }

    public Map<String, Object> options() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerLevels", List.of("普通客户", "重点客户", "VIP客户"));
        result.put("customerIntentions", List.of("低", "中", "高"));
        result.put("employeeRoles", EMPLOYEE_ROLES);
        result.put("employeeStatuses", List.of("在职", "试用", "离职"));
        result.put("projectStatuses", List.of("待签约", "设计中", "施工中", "已完工", "售后中"));
        result.put("stageStatuses", List.of("未开始", "进行中", "已完成", "已延期"));
        result.put("materialStatuses", List.of("待采购", "已下单", "已到货", "已结算"));
        result.put("paymentTypes", List.of("首付款", "中期款", "尾款", "增项款"));
        result.put("paymentStatuses", List.of("待到账", "已到账", "已开票"));
        result.put("userRoles", USER_ROLES);
        result.put("userStatuses", USER_STATUSES);
        result.put("supplierStatuses", SUPPLIER_STATUSES);
        result.put("supplierCategories", SUPPLIER_CATEGORIES);
        result.put("customers", listCustomers(null));
        result.put("employees", listEmployees(null));
        result.put("projects", listProjects(null, null));
        result.put("suppliers", listSuppliers(null));
        return result;
    }

    private long scalarCount(String tableName) {
        Long value = databaseManager.queryOne("select count(*) from " + tableName, null, resultSet -> resultSet.getLong(1));
        return value == null ? 0L : value;
    }

    private String resolveDisplayName(Map<String, Object> payload, Long employeeId) {
        String displayName = optionalText(payload, "displayName");
        if (!displayName.isBlank()) {
            return displayName;
        }
        if (employeeId != null) {
            return String.valueOf(getEmployee(employeeId).get("name"));
        }
        throw new AppException(400, "字段 displayName 不能为空。");
    }

    private void ensureUsernameAvailable(String username, Long currentUserId) {
        Long foundId = databaseManager.queryOne(
                "select id from users where username = ?",
                statement -> statement.setString(1, username),
                resultSet -> resultSet.getLong(1)
        );
        if (foundId != null && (currentUserId == null || foundId.longValue() != currentUserId.longValue())) {
            throw new AppException(400, "该用户名已存在。");
        }
    }

    private void ensureEmployeeBindable(Long employeeId, Long currentUserId) {
        if (employeeId == null) {
            return;
        }
        getEmployee(employeeId);
        Long foundUserId = databaseManager.queryOne(
                "select id from users where employee_id = ?",
                statement -> statement.setLong(1, employeeId),
                resultSet -> resultSet.getLong(1)
        );
        if (foundUserId != null && (currentUserId == null || foundUserId.longValue() != currentUserId.longValue())) {
            throw new AppException(400, "该员工已绑定其他系统账号。");
        }
    }

    private void ensureSupplierNameAvailable(String name, Long currentSupplierId) {
        Long foundId = databaseManager.queryOne(
                "select id from suppliers where name = ?",
                statement -> statement.setString(1, name),
                resultSet -> resultSet.getLong(1)
        );
        if (foundId != null && (currentSupplierId == null || foundId.longValue() != currentSupplierId.longValue())) {
            throw new AppException(400, "该供应商名称已存在。");
        }
    }

    private String resolveSupplierName(Long supplierId, String fallbackName) {
        if (supplierId == null) {
            return fallbackName;
        }
        return String.valueOf(getSupplier(supplierId).get("name"));
    }

    private String normalizeUserRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!USER_ROLES.contains(normalized)) {
            throw new AppException(400, "账号角色不合法。");
        }
        return normalized;
    }

    private String normalizeEmployeeRole(String role) {
        String normalized = role == null ? "" : role.trim();
        if (!EMPLOYEE_ROLES.contains(normalized)) {
            throw new AppException(400, "员工岗位不在系统支持范围内。");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password.length() < 6) {
            throw new AppException(400, "密码长度不能少于 6 位。");
        }
    }

    private String normalizeUserStatus(String status) {
        String normalized = status == null ? "" : status.trim();
        if (!USER_STATUSES.contains(normalized)) {
            throw new AppException(400, "账号状态不合法。");
        }
        return normalized;
    }

    private String normalizeSupplierStatus(String status) {
        String normalized = status == null ? "" : status.trim();
        if (!SUPPLIER_STATUSES.contains(normalized)) {
            throw new AppException(400, "供应商状态不合法。");
        }
        return normalized;
    }

    private String normalizeSupplierCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        if (!SUPPLIER_CATEGORIES.contains(normalized)) {
            throw new AppException(400, "供应商分类不合法。");
        }
        return normalized;
    }

    private Map<String, Object> customerMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("name", resultSet.getString("name"));
        result.put("phone", resultSet.getString("phone"));
        result.put("source", valueOf(resultSet.getString("source")));
        result.put("level", resultSet.getString("level"));
        result.put("intention", resultSet.getString("intention"));
        result.put("address", valueOf(resultSet.getString("address")));
        result.put("notes", valueOf(resultSet.getString("notes")));
        result.put("createdDate", valueOf(resultSet.getDate("created_date")));
        result.put("projectCount", resultSet.getLong("project_count"));
        return result;
    }

    private Map<String, Object> employeeMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("name", resultSet.getString("name"));
        result.put("role", resultSet.getString("role"));
        result.put("phone", resultSet.getString("phone"));
        result.put("specialty", valueOf(resultSet.getString("specialty")));
        result.put("status", resultSet.getString("status"));
        result.put("hireDate", valueOf(resultSet.getDate("hire_date")));
        result.put("notes", valueOf(resultSet.getString("notes")));
        result.put("managedProjectCount", resultSet.getLong("managed_project_count"));
        result.put("linkedUserId", nullableLong(resultSet, "linked_user_id"));
        result.put("linkedUsername", valueOf(resultSet.getString("linked_username")));
        result.put("linkedUserStatus", valueOf(resultSet.getString("linked_user_status")));
        return result;
    }

    private Map<String, Object> userMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("username", resultSet.getString("username"));
        result.put("displayName", resultSet.getString("display_name"));
        result.put("role", resultSet.getString("role"));
        result.put("status", resultSet.getString("status"));
        result.put("createdAt", resultSet.getTimestamp("created_at").toLocalDateTime().toString());
        result.put("employeeId", nullableLong(resultSet, "employee_id"));
        result.put("employeeName", valueOf(resultSet.getString("employee_name")));
        result.put("employeeRole", valueOf(resultSet.getString("employee_role")));
        return result;
    }

    private Map<String, Object> projectMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("name", resultSet.getString("name"));
        result.put("customerId", resultSet.getLong("customer_id"));
        result.put("customerName", resultSet.getString("customer_name"));
        result.put("managerId", resultSet.getLong("manager_id"));
        result.put("managerName", resultSet.getString("manager_name"));
        result.put("status", resultSet.getString("status"));
        result.put("style", valueOf(resultSet.getString("style")));
        result.put("address", resultSet.getString("address"));
        result.put("area", resultSet.getBigDecimal("area").setScale(2, RoundingMode.HALF_UP));
        result.put("contractAmount", toMoney(resultSet.getBigDecimal("contract_amount")));
        result.put("startDate", valueOf(resultSet.getDate("start_date")));
        result.put("expectedEndDate", valueOf(resultSet.getDate("expected_end_date")));
        result.put("notes", valueOf(resultSet.getString("notes")));
        result.put("stageCount", resultSet.getLong("stage_count"));
        return result;
    }

    private Map<String, Object> stageMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("projectId", resultSet.getLong("project_id"));
        result.put("projectName", resultSet.getString("project_name"));
        result.put("stageName", resultSet.getString("stage_name"));
        result.put("owner", valueOf(resultSet.getString("owner")));
        result.put("status", resultSet.getString("status"));
        result.put("plannedDate", valueOf(resultSet.getDate("planned_date")));
        result.put("actualDate", valueOf(resultSet.getDate("actual_date")));
        result.put("notes", valueOf(resultSet.getString("notes")));
        return result;
    }

    private Map<String, Object> supplierMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("name", resultSet.getString("name"));
        result.put("contactName", valueOf(resultSet.getString("contact_name")));
        result.put("phone", valueOf(resultSet.getString("phone")));
        result.put("category", valueOf(resultSet.getString("category")));
        result.put("address", valueOf(resultSet.getString("address")));
        result.put("status", resultSet.getString("status"));
        result.put("notes", valueOf(resultSet.getString("notes")));
        result.put("createdDate", valueOf(resultSet.getDate("created_date")));
        result.put("purchaseCount", resultSet.getLong("purchase_count"));
        return result;
    }

    private Map<String, Object> materialMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("projectId", resultSet.getLong("project_id"));
        result.put("projectName", resultSet.getString("project_name"));
        result.put("materialName", resultSet.getString("material_name"));
        result.put("category", resultSet.getString("category"));
        result.put("supplierId", nullableLong(resultSet, "supplier_id"));
        result.put("supplier", valueOf(resultSet.getString("supplier_name")));
        result.put("amount", toMoney(resultSet.getBigDecimal("amount")));
        result.put("status", resultSet.getString("status"));
        result.put("purchaseDate", valueOf(resultSet.getDate("purchase_date")));
        result.put("notes", valueOf(resultSet.getString("notes")));
        return result;
    }

    private Map<String, Object> paymentMap(java.sql.ResultSet resultSet) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resultSet.getLong("id"));
        result.put("projectId", resultSet.getLong("project_id"));
        result.put("projectName", resultSet.getString("project_name"));
        result.put("type", resultSet.getString("type"));
        result.put("amount", toMoney(resultSet.getBigDecimal("amount")));
        result.put("status", resultSet.getString("status"));
        result.put("paymentDate", valueOf(resultSet.getDate("payment_date")));
        result.put("payer", valueOf(resultSet.getString("payer")));
        result.put("notes", valueOf(resultSet.getString("notes")));
        return result;
    }

    private void bindNullableDate(java.sql.PreparedStatement statement, int index, Object value) throws Exception {
        if (value == null || String.valueOf(value).isBlank()) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(parseDate(value, LocalDate.now())));
        }
    }

    private void bindNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws Exception {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String columnName) throws Exception {
        Object value = resultSet.getObject(columnName);
        return value == null ? null : resultSet.getLong(columnName);
    }

    private String requiredText(Map<String, Object> payload, String key) {
        String value = optionalText(payload, key);
        if (value.isBlank()) {
            throw new AppException(400, "字段 " + key + " 不能为空。");
        }
        return value;
    }

    private String optionalText(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultText(Map<String, Object> payload, String key, String defaultValue) {
        String value = optionalText(payload, key);
        return value.isBlank() ? defaultValue : value;
    }

    private long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new AppException(400, "字段 " + key + " 不能为空。");
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new AppException(400, "字段 " + key + " 必须为整数。");
        }
    }

    private Long optionalLongValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new AppException(400, "字段 " + key + " 必须为整数。");
        }
    }

    private BigDecimal requiredMoney(Map<String, Object> payload, String key) {
        return requiredDecimal(payload, key).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal requiredDecimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new AppException(400, "字段 " + key + " 不能为空。");
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new AppException(400, "字段 " + key + " 必须为数字。");
        }
    }

    private LocalDate parseDate(Object value, LocalDate defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ex) {
            throw new AppException(400, "日期格式必须为 yyyy-MM-dd。");
        }
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal toMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String valueOf(Date date) {
        return date == null ? "" : date.toLocalDate().toString();
    }

    private String valueOf(String value) {
        return value == null ? "" : value;
    }
}
