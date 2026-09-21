const state = {
  authToken: localStorage.getItem("manageSystemToken") || "",
  currentUser: null,
  currentTab: "dashboard",
  sidebarCollapsed: localStorage.getItem("manageSystemSidebarCollapsed") === "true",
  mobileSidebarOpen: false,
  options: null,
  customers: [],
  employees: [],
  users: [],
  projects: [],
  stages: [],
  suppliers: [],
  materials: [],
  payments: [],
  summary: null,
};

const MOBILE_SIDEBAR_BREAKPOINT = 1040;

const endpoints = {
  customers: "/api/customers",
  employees: "/api/employees",
  users: "/api/users",
  projects: "/api/projects",
  stages: "/api/stages",
  suppliers: "/api/suppliers",
  materials: "/api/materials",
  payments: "/api/payments",
};

const formResourceMap = {
  customerForm: "customers",
  employeeForm: "employees",
  userForm: "users",
  projectForm: "projects",
  stageForm: "stages",
  supplierForm: "suppliers",
  materialForm: "materials",
  paymentForm: "payments",
};

document.addEventListener("DOMContentLoaded", async () => {
  bindNavigation();
  bindSidebarToggle();
  bindForms();
  bindResets();
  bindTableActions();
  bindAuthActions();
  bindSearch();
  bindDemoAccounts();
  await bootstrapAuth();
});

async function bootstrapAuth() {
  if (!state.authToken) {
    showLoginScreen();
    return;
  }

  try {
    const response = await request("/api/auth/me");
    state.currentUser = response.data;
    showAppShell();
    activateTab("dashboard");
    await loadAll();
  } catch (error) {
    clearAuth();
    showLoginScreen();
  }
}

function bindNavigation() {
  document.querySelectorAll(".nav-link").forEach((button) => {
    button.addEventListener("click", () => {
      activateTab(button.dataset.tab);
      if (isMobileViewport()) {
        closeMobileSidebar();
      }
    });
  });
}

function bindSidebarToggle() {
  const toggleButton = document.getElementById("sidebarToggle");
  const mobileToggleButton = document.getElementById("mobileNavToggle");
  const backdrop = document.getElementById("sidebarBackdrop");
  if (toggleButton) {
    toggleButton.addEventListener("click", () => {
      if (isMobileViewport()) {
        state.mobileSidebarOpen = !state.mobileSidebarOpen;
      } else {
        state.sidebarCollapsed = !state.sidebarCollapsed;
        localStorage.setItem("manageSystemSidebarCollapsed", String(state.sidebarCollapsed));
      }
      applySidebarState();
    });
  }
  if (mobileToggleButton) {
    mobileToggleButton.addEventListener("click", () => {
      state.mobileSidebarOpen = !state.mobileSidebarOpen;
      applySidebarState();
    });
  }
  if (backdrop) {
    backdrop.addEventListener("click", closeMobileSidebar);
  }
  window.addEventListener("resize", () => {
    if (!isMobileViewport()) {
      state.mobileSidebarOpen = false;
    }
    applySidebarState();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && state.mobileSidebarOpen) {
      closeMobileSidebar();
    }
  });
  applySidebarState();
}

function bindAuthActions() {
  const loginForm = document.getElementById("loginForm");
  const loginButton = document.getElementById("loginSubmitButton");

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    loginButton.disabled = true;
    loginButton.textContent = "登录中...";
    try {
      const payload = formToJson(loginForm);
      const response = await request("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        skipAuth: true,
      });
      state.authToken = response.data.token;
      state.currentUser = response.data.user;
      localStorage.setItem("manageSystemToken", state.authToken);
      loginForm.reset();
      showAppShell();
      activateTab("dashboard");
      await loadAll();
      showMessage("登录成功，欢迎回来。");
    } catch (error) {
      showLoginScreen();
    } finally {
      loginButton.disabled = false;
      loginButton.textContent = "登录系统";
    }
  });

  document.getElementById("logoutButton").addEventListener("click", async () => {
    try {
      await request("/api/auth/logout", { method: "POST" });
    } catch (error) {
      console.error(error);
    }
    clearAuth();
    showLoginScreen();
    showMessage("已退出登录。");
  });

  document.getElementById("reloadButton").addEventListener("click", async () => {
    await loadAll();
    showMessage("数据已刷新。");
  });
}

function bindSearch() {
  ["customerSearch", "employeeSearch", "userSearch", "projectSearch", "supplierSearch"].forEach((id) => {
    const element = document.getElementById(id);
    if (!element) {
      return;
    }
    element.addEventListener("input", () => {
      if (id === "customerSearch") {
        renderCustomers();
      }
      if (id === "employeeSearch") {
        renderEmployees();
      }
      if (id === "userSearch") {
        renderUsers();
      }
      if (id === "projectSearch") {
        renderProjects();
      }
      if (id === "supplierSearch") {
        renderSuppliers();
      }
    });
  });
}

function bindDemoAccounts() {
  document.querySelectorAll("[data-demo-username]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelector('#loginForm [name="username"]').value = button.dataset.demoUsername;
      document.querySelector('#loginForm [name="password"]').value = button.dataset.demoPassword;
    });
  });
}

function bindForms() {
  Object.entries(formResourceMap).forEach(([formId, key]) => bindEntityForm(formId, key));
}

function bindResets() {
  document.querySelectorAll("[data-reset-form]").forEach((button) => {
    button.addEventListener("click", () => resetForm(button.dataset.resetForm));
  });
}

function bindTableActions() {
  document.body.addEventListener("click", async (event) => {
    const editButton = event.target.closest(".edit-btn");
    if (editButton) {
      editEntity(editButton.dataset.form, editButton.dataset.payload);
      return;
    }

    const deleteButton = event.target.closest(".delete-btn");
    if (deleteButton) {
      await deleteEntity(deleteButton.dataset.key, deleteButton.dataset.id);
      return;
    }

    const resetPasswordButton = event.target.closest(".reset-password-btn");
    if (resetPasswordButton) {
      await resetUserPassword(
        resetPasswordButton.dataset.userId,
        resetPasswordButton.dataset.username || resetPasswordButton.dataset.label || "该账号"
      );
    }
  });
}

function bindEntityForm(formId, key) {
  const form = document.getElementById(formId);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!canWrite(key)) {
      showMessage("当前账号没有该模块的编辑权限。", true);
      return;
    }

    const payload = formToJson(form);
    const id = payload.id;
    delete payload.id;

    const isUpdate = Boolean(id);
    const method = isUpdate ? "PUT" : "POST";
    const url = isUpdate ? `${endpoints[key]}/${id}` : endpoints[key];

    await request(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    showMessage(isUpdate ? "保存成功，数据已更新。" : "新增成功，数据已写入。");
    resetForm(formId);
    await loadAll();
  });
}

async function loadAll() {
  const [options, summary, customers, employees, users, projects, stages, suppliers, materials, payments] = await Promise.all([
    request("/api/options"),
    request("/api/dashboard/summary"),
    readRequest("customers", "/api/customers"),
    readRequest("employees", "/api/employees"),
    readRequest("users", "/api/users"),
    readRequest("projects", "/api/projects"),
    readRequest("stages", "/api/stages"),
    readRequest("suppliers", "/api/suppliers"),
    readRequest("materials", "/api/materials"),
    readRequest("payments", "/api/payments"),
  ]);

  state.options = options.data;
  state.currentUser = options.data.currentUser || state.currentUser;
  state.summary = summary.data;
  state.customers = customers.data;
  state.employees = employees.data;
  state.users = users.data;
  state.projects = projects.data;
  state.stages = stages.data;
  state.suppliers = suppliers.data;
  state.materials = materials.data;
  state.payments = payments.data;

  fillSelectOptions();
  applyPermissionState();
  renderDashboard();
  renderCustomers();
  renderEmployees();
  renderUsers();
  renderProjects();
  renderStages();
  renderSuppliers();
  renderMaterials();
  renderPayments();
}

function applyPermissionState() {
  document.getElementById("currentUserName").textContent = state.currentUser.displayName;
  document.getElementById("currentUserMeta").textContent = `${state.currentUser.username} / ${roleLabel(state.currentUser.role)}`;
  document.getElementById("userRoleBadge").textContent = roleLabel(state.currentUser.role);

  document.querySelectorAll(".form-card[data-resource]").forEach((card) => {
    card.classList.toggle("hidden", !canWrite(card.dataset.resource));
  });

  document.querySelectorAll(".nav-link[data-resource], .tab-panel[data-resource]").forEach((element) => {
    const resource = element.dataset.resource;
    element.classList.toggle("hidden", !canDisplayResource(resource));
  });

  if (!canDisplayResource(state.currentTab)) {
    activateTab("dashboard");
  }
}

function applySidebarState() {
  const shell = document.getElementById("appShell");
  const toggleButton = document.getElementById("sidebarToggle");
  const mobileToggleButton = document.getElementById("mobileNavToggle");
  if (!shell) {
    return;
  }
  const mobileMode = isMobileViewport();
  shell.classList.toggle("sidebar-collapsed", !mobileMode && state.sidebarCollapsed);
  shell.classList.toggle("mobile-sidebar-mode", mobileMode);
  shell.classList.toggle("mobile-sidebar-open", mobileMode && state.mobileSidebarOpen);
  document.body.classList.toggle("nav-open-lock", mobileMode && state.mobileSidebarOpen);
  if (toggleButton) {
    toggleButton.textContent = state.sidebarCollapsed ? "展开" : "收起";
    toggleButton.setAttribute("aria-label", state.sidebarCollapsed ? "展开导航" : "收起导航");
  }
  if (mobileToggleButton) {
    mobileToggleButton.setAttribute("aria-expanded", String(mobileMode && state.mobileSidebarOpen));
  }
}

function closeMobileSidebar() {
  if (!state.mobileSidebarOpen) {
    return;
  }
  state.mobileSidebarOpen = false;
  applySidebarState();
}

function isMobileViewport() {
  return window.innerWidth <= MOBILE_SIDEBAR_BREAKPOINT;
}

function readRequest(resource, url) {
  if (!canRead(resource)) {
    return Promise.resolve({ data: [] });
  }
  return request(url);
}

function fillSelectOptions() {
  if (!state.options) {
    return;
  }

  fillSelect("customerForm", "level", state.options.customerLevels);
  fillSelect("customerForm", "intention", state.options.customerIntentions);
  fillSelect("employeeForm", "role", state.options.employeeRoles);
  fillSelect("employeeForm", "status", state.options.employeeStatuses);
  fillSelect("userForm", "role", state.options.userRoles.map((role) => ({ value: role, label: roleLabel(role) })));
  fillSelect("userForm", "status", state.options.userStatuses);
  fillSelect(
    "userForm",
    "employeeId",
    state.employees.map((item) => ({
      value: item.id,
      label: `${item.name} / ${item.role}${item.linkedUsername ? ` / 已绑:${item.linkedUsername}` : ""}`,
    })),
    { includeBlank: true, blankLabel: "暂不绑定员工" }
  );
  fillSelect("projectForm", "status", state.options.projectStatuses);
  fillSelect("stageForm", "status", state.options.stageStatuses);
  fillSelect("supplierForm", "category", state.options.supplierCategories);
  fillSelect("supplierForm", "status", state.options.supplierStatuses);
  fillSelect("materialForm", "status", state.options.materialStatuses);
  fillSelect("paymentForm", "type", state.options.paymentTypes);
  fillSelect("paymentForm", "status", state.options.paymentStatuses);

  fillSelect(
    "projectForm",
    "customerId",
    state.customers.map((item) => ({ value: item.id, label: `${item.name} / ${item.phone}` }))
  );
  fillSelect(
    "projectForm",
    "managerId",
    state.employees.map((item) => ({ value: item.id, label: `${item.name} / ${item.role}` }))
  );

  ["stageForm", "materialForm", "paymentForm"].forEach((formId) => {
    fillSelect(
      formId,
      "projectId",
      state.projects.map((item) => ({ value: item.id, label: item.name }))
    );
  });

  fillSelect(
    "materialForm",
    "supplierId",
    state.suppliers.map((item) => ({ value: item.id, label: `${item.name} / ${item.category}` })),
    { includeBlank: true, blankLabel: "未指定供应商" }
  );
}

function fillSelect(formId, fieldName, values, config = {}) {
  const select = document.querySelector(`#${formId} [name="${fieldName}"]`);
  if (!select) {
    return;
  }
  const currentValue = select.value;
  const options = [];
  if (config.includeBlank) {
    options.push(`<option value="">${escapeHtml(config.blankLabel || "请选择")}</option>`);
  }
  values.forEach((item) => {
    if (typeof item === "object") {
      options.push(`<option value="${escapeHtml(String(item.value))}">${escapeHtml(item.label)}</option>`);
      return;
    }
    options.push(`<option value="${escapeHtml(String(item))}">${escapeHtml(String(item))}</option>`);
  });
  select.innerHTML = options.join("");
  if (currentValue) {
    select.value = currentValue;
  }
}

function renderDashboard() {
  const cards = [
    { label: "客户总数", value: state.summary.customerCount },
    { label: "员工总数", value: state.summary.employeeCount },
    { label: "供应商总数", value: state.summary.supplierCount },
    { label: "项目总数", value: state.summary.projectCount },
    { label: "在建项目", value: state.summary.activeProjectCount },
    { label: "待跟进节点", value: state.summary.pendingStageCount },
    { label: "待结清采购", value: state.summary.pendingMaterialCount },
    { label: "已到账金额", value: `¥${state.summary.receivedAmount}` },
  ];

  document.getElementById("summaryCards").innerHTML = cards
    .map(
      (card) => `
        <article class="summary-card">
          <p>${escapeHtml(card.label)}</p>
          <strong>${escapeHtml(String(card.value))}</strong>
        </article>
      `
    )
    .join("");

  document.getElementById("recentProjects").innerHTML = state.summary.recentProjects
    .slice(0, 5)
    .map(
      (project) => `
        <div class="list-item">
          <strong>${escapeHtml(project.name)}</strong>
          <p>客户：${escapeHtml(project.customerName)}</p>
          <p>负责人：${escapeHtml(project.managerName)}</p>
          <p>状态：${escapeHtml(project.status)} / 合同额：¥${escapeHtml(String(project.contractAmount))}</p>
        </div>
      `
    )
    .join("");

  document.getElementById("upcomingStages").innerHTML = state.summary.upcomingStages
    .map(
      (stage) => `
        <div class="list-item">
          <strong>${escapeHtml(stage.stageName)}</strong>
          <p>项目：${escapeHtml(stage.projectName)}</p>
          <p>负责人：${escapeHtml(stage.owner || "未指定")}</p>
          <p>计划日期：${escapeHtml(stage.plannedDate || "-")} / 状态：${escapeHtml(stage.status)}</p>
        </div>
      `
    )
    .join("");
}

function renderCustomers() {
  const keyword = document.getElementById("customerSearch").value.trim();
  const rows = state.customers
    .filter((item) => fuzzyMatch(keyword, [item.name, item.phone, item.source, item.address]))
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.name, `<span class="badge subtle-badge">${escapeHtml(item.level)}</span>`)}</td>
          <td>${metaCell(escapeHtml(item.phone), escapeHtml(item.address || "未填写地址"))}</td>
          <td>${metaCell(escapeHtml(item.source || "未记录来源"), `意向：${escapeHtml(item.intention)} · 录入：${escapeHtml(item.createdDate || "-")}`)}</td>
          <td><span class="metric-pill">${escapeHtml(String(item.projectCount))}<small>项目</small></span></td>
          <td>${actionButtons("customerForm", "customers", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("customersTable").innerHTML = tableTemplate(
    ["客户", "联系信息", "跟进信息", "项目", "操作"],
    rows
  );
}

function renderEmployees() {
  const keyword = document.getElementById("employeeSearch").value.trim();
  const rows = state.employees
    .filter((item) => fuzzyMatch(keyword, [item.name, item.role, item.phone, item.specialty, item.linkedUsername]))
    .map(
      (item) => `
        <tr>
          <td>
            ${entityCell(
              item.name,
              `<span class="badge subtle-badge">${escapeHtml(item.role)}</span><span class="badge">${escapeHtml(item.status)}</span>`
            )}
          </td>
          <td>${metaCell(escapeHtml(item.phone), escapeHtml(item.specialty || "未填写擅长方向"))}</td>
          <td>
            ${item.linkedUsername
              ? `
                <div class="account-cell">
                  <span class="account-chip">${escapeHtml(item.linkedUsername)}</span>
                  ${passwordResetButton(item.linkedUserId, item.linkedUsername, "account-reset-btn")}
                </div>
              `
              : '<span class="muted-text">未开通系统账号</span>'}
          </td>
          <td>${metaCell(`${escapeHtml(String(item.managedProjectCount))} 个项目`, `入职：${escapeHtml(item.hireDate || "-")}`)}</td>
          <td>${actionButtons("employeeForm", "employees", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("employeesTable").innerHTML = tableTemplate(
    ["员工", "联系与擅长", "系统账号", "项目与入职", "操作"],
    rows
  );
}

function renderUsers() {
  const rows = state.users
    .filter((item) => fuzzyMatch(document.getElementById("userSearch").value.trim(), [item.username, item.displayName, item.role, item.employeeName]))
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.username, escapeHtml(item.displayName))}</td>
          <td>${metaCell(roleLabel(item.role), `状态：${escapeHtml(item.status)}`)}</td>
          <td>${metaCell(escapeHtml(item.employeeName || "未绑定员工"), `创建：${escapeHtml(formatDateTime(item.createdAt))}`)}</td>
          <td>${userActionButtons(item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("usersTable").innerHTML = tableTemplate(
    ["账号", "角色与状态", "绑定信息", "操作"],
    rows,
    "暂无账号数据"
  );
}

function renderProjects() {
  const keyword = document.getElementById("projectSearch").value.trim();
  const rows = state.projects
    .filter((item) => fuzzyMatch(keyword, [item.name, item.customerName, item.managerName, item.address]))
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.name, escapeHtml(item.address || "未填写地址"))}</td>
          <td>${metaCell(escapeHtml(item.customerName), `负责人：${escapeHtml(item.managerName)}`)}</td>
          <td>${entityCell(item.status, `<span class="badge subtle-badge">${escapeHtml(item.style || "未设置风格")}</span>`)}</td>
          <td>${metaCell(`¥${escapeHtml(String(item.contractAmount))}`, `面积：${escapeHtml(String(item.area))}㎡ · 开工：${escapeHtml(item.startDate || "-")} · 完工：${escapeHtml(item.expectedEndDate || "-")}`)}</td>
          <td>${actionButtons("projectForm", "projects", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("projectsTable").innerHTML = tableTemplate(
    ["项目", "客户与负责人", "项目状态", "合同与进度", "操作"],
    rows
  );
}

function renderStages() {
  const rows = state.stages
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.projectName, escapeHtml(item.stageName))}</td>
          <td>${metaCell(escapeHtml(item.owner || "未指定负责人"), `状态：${escapeHtml(item.status)}`)}</td>
          <td>${metaCell(escapeHtml(item.plannedDate || "-"), `完成：${escapeHtml(item.actualDate || "-")}`)}</td>
          <td>${actionButtons("stageForm", "stages", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("stagesTable").innerHTML = tableTemplate(
    ["项目与阶段", "负责人", "计划与完成", "操作"],
    rows
  );
}

function renderSuppliers() {
  const keyword = document.getElementById("supplierSearch").value.trim();
  const rows = state.suppliers
    .filter((item) => fuzzyMatch(keyword, [item.name, item.contactName, item.phone, item.category]))
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.name, `<span class="badge subtle-badge">${escapeHtml(item.category || "未分类")}</span>`)}</td>
          <td>${metaCell(escapeHtml(item.contactName || "未填写联系人"), escapeHtml(item.phone || "未填写电话"))}</td>
          <td>${metaCell(escapeHtml(item.status), `创建：${escapeHtml(item.createdDate || "-")}`)}</td>
          <td><span class="metric-pill">${escapeHtml(String(item.purchaseCount))}<small>采购</small></span></td>
          <td>${actionButtons("supplierForm", "suppliers", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("suppliersTable").innerHTML = tableTemplate(
    ["供应商", "联系人与电话", "合作状态", "采购", "操作"],
    rows
  );
}

function renderMaterials() {
  const rows = state.materials
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.materialName, escapeHtml(item.projectName))}</td>
          <td>${metaCell(escapeHtml(item.supplier || "未指定供应商"), `分类：${escapeHtml(item.category)}`)}</td>
          <td>${entityCell(`¥${escapeHtml(String(item.amount))}`, `<span class="badge">${escapeHtml(item.status)}</span>`)}</td>
          <td>${escapeHtml(item.purchaseDate || "-")}</td>
          <td>${actionButtons("materialForm", "materials", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("materialsTable").innerHTML = tableTemplate(
    ["采购项", "供应商", "金额与状态", "日期", "操作"],
    rows
  );
}

function renderPayments() {
  const rows = state.payments
    .map(
      (item) => `
        <tr>
          <td>${entityCell(item.projectName, escapeHtml(item.type))}</td>
          <td>${entityCell(`¥${escapeHtml(String(item.amount))}`, `<span class="badge">${escapeHtml(item.status)}</span>`)}</td>
          <td>${metaCell(escapeHtml(item.payer || "未填写付款人"), escapeHtml(item.paymentDate || "-"))}</td>
          <td>${actionButtons("paymentForm", "payments", item)}</td>
        </tr>
      `
    )
    .join("");
  document.getElementById("paymentsTable").innerHTML = tableTemplate(
    ["收款项目", "金额与状态", "付款信息", "操作"],
    rows
  );
}

function actionButtons(formId, key, item) {
  if (!canWrite(key)) {
    return '<span class="muted-text">只读</span>';
  }
  return `
    <div class="table-actions">
      <button class="table-btn edit-btn" data-form="${formId}" data-payload="${encodePayload(item)}">编辑</button>
      <button class="table-btn danger delete-btn" data-key="${key}" data-id="${item.id}">删除</button>
    </div>
  `;
}

function userActionButtons(item) {
  if (!canWrite("users")) {
    return '<span class="muted-text">只读</span>';
  }
  return `
    <div class="table-actions">
      <button class="table-btn edit-btn" data-form="userForm" data-payload="${encodePayload(item)}">编辑</button>
      ${passwordResetButton(item.id, item.username)}
      <button class="table-btn danger delete-btn" data-key="users" data-id="${item.id}">删除</button>
    </div>
  `;
}

function passwordResetButton(userId, username, extraClass = "") {
  if (!userId || !canWrite("users")) {
    return "";
  }
  const className = ["table-btn", "reset-password-btn", extraClass].filter(Boolean).join(" ");
  return `<button class="${className}" data-user-id="${escapeHtml(String(userId))}" data-username="${escapeHtml(username || "")}">重置密码</button>`;
}

function tableTemplate(headers, rows, emptyText = "暂无数据") {
  return `
    <table class="data-table">
      <thead>
        <tr>${headers.map((item) => `<th>${escapeHtml(item)}</th>`).join("")}</tr>
      </thead>
      <tbody>
        ${rows || `<tr><td colspan="${headers.length}" class="empty-cell">${escapeHtml(emptyText)}</td></tr>`}
      </tbody>
    </table>
  `;
}

function entityCell(title, detail) {
  return `
    <div class="cell-stack">
      <strong class="cell-title">${escapeHtml(title)}</strong>
      <div class="cell-detail">${detail}</div>
    </div>
  `;
}

function metaCell(primary, secondary) {
  return `
    <div class="cell-stack">
      <span class="cell-primary">${primary}</span>
      <span class="cell-secondary">${secondary}</span>
    </div>
  `;
}

async function deleteEntity(key, id) {
  if (!canWrite(key)) {
    showMessage("当前账号没有删除权限。", true);
    return;
  }
  if (!window.confirm("确认删除这条记录吗？")) {
    return;
  }
  await request(`${endpoints[key]}/${id}`, { method: "DELETE" });
  showMessage("删除成功。");
  await loadAll();
}

async function resetUserPassword(userId, username) {
  if (!canWrite("users")) {
    showMessage("当前账号没有密码管理权限。", true);
    return;
  }
  const password = window.prompt(`请为账号 ${username} 设置新密码（至少 6 位）`);
  if (password === null) {
    return;
  }
  const normalizedPassword = password.trim();
  if (!normalizedPassword) {
    showMessage("密码不能为空。", true);
    return;
  }
  await request(`/api/users/${userId}/reset-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password: normalizedPassword }),
  });
  showMessage(`账号 ${username} 的密码已更新。`);
}

function editEntity(formId, payload) {
  const resource = resourceForForm(formId);
  if (!canWrite(resource)) {
    showMessage("当前账号没有编辑权限。", true);
    return;
  }

  const form = document.getElementById(formId);
  const item = JSON.parse(decodeURIComponent(payload));
  resetForm(formId);

  Object.entries(item).forEach(([key, value]) => {
    const field = form.querySelector(`[name="${key}"]`);
    if (field) {
      field.value = value ?? "";
    }
  });

  if (formId === "userForm") {
    const passwordField = form.querySelector('[name="password"]');
    if (passwordField) {
      passwordField.value = "";
    }
  }

  const title = document.getElementById(`${formId}Title`);
  if (title) {
    title.textContent = title.textContent.replace("新增", "编辑");
  }
  form.scrollIntoView({ behavior: "smooth", block: "start" });
}

function resetForm(formId) {
  const form = document.getElementById(formId);
  form.reset();
  const hiddenIdField = form.querySelector('[name="id"]');
  if (hiddenIdField) {
    hiddenIdField.value = "";
  }
  const title = document.getElementById(`${formId}Title`);
  if (title) {
    title.textContent = title.textContent.replace("编辑", "新增");
  }
  if (state.options) {
    fillSelectOptions();
  }
}

function formToJson(form) {
  const data = {};
  new FormData(form).forEach((value, key) => {
    data[key] = value;
  });
  return data;
}

async function request(url, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.authToken && !options.skipAuth) {
    headers.set("Authorization", `Bearer ${state.authToken}`);
  }

  const response = await fetch(url, { ...options, headers });
  const json = await response.json().catch(() => ({ success: false, message: "响应解析失败" }));

  if (response.status === 401 && !options.skipAuth) {
    clearAuth();
    showLoginScreen();
    showMessage("登录已失效，请重新登录。", true);
    throw new Error(json.message || "Unauthorized");
  }

  if (!response.ok || !json.success) {
    showMessage(json.message || "请求失败", true);
    throw new Error(json.message || "Request failed");
  }
  return json;
}

function showMessage(message, isError = false) {
  const element = document.getElementById(currentMessageBarId());
  element.classList.remove("hidden", "error");
  element.textContent = message;
  if (isError) {
    element.classList.add("error");
  }
  window.clearTimeout(showMessage.timer);
  showMessage.timer = window.setTimeout(() => {
    element.classList.add("hidden");
  }, 3000);
}

function currentMessageBarId() {
  return document.getElementById("loginScreen").classList.contains("hidden")
    ? "messageBar"
    : "loginMessageBar";
}

function showLoginScreen() {
  state.mobileSidebarOpen = false;
  document.body.classList.remove("nav-open-lock");
  document.getElementById("loginScreen").classList.remove("hidden");
  document.getElementById("appShell").classList.add("hidden");
}

function showAppShell() {
  document.getElementById("loginScreen").classList.add("hidden");
  document.getElementById("appShell").classList.remove("hidden");
  applySidebarState();
}

function clearAuth() {
  state.authToken = "";
  state.currentUser = null;
  localStorage.removeItem("manageSystemToken");
}

function activateTab(tab) {
  state.currentTab = tab;
  document.querySelectorAll(".nav-link").forEach((item) => item.classList.toggle("active", item.dataset.tab === tab));
  document.querySelectorAll(".tab-panel").forEach((panel) => panel.classList.toggle("active", panel.dataset.panel === tab));
}

function canRead(resource) {
  if (!state.currentUser) {
    return false;
  }
  return (state.currentUser.permissions || []).includes(`${resource}:read`);
}

function canWrite(resource) {
  if (!state.currentUser) {
    return false;
  }
  return (state.currentUser.permissions || []).includes(`${resource}:write`);
}

function canDisplayResource(resource) {
  if (resource === "users") {
    return canManageUsers();
  }
  return canRead(resource);
}

function canManageUsers() {
  return canWrite("users");
}

function resourceForForm(formId) {
  return formResourceMap[formId];
}

function roleLabel(role) {
  const mapping = {
    ADMIN: "管理员",
    MANAGER: "项目经理",
    FINANCE: "财务",
    DESIGNER: "设计师",
  };
  return mapping[role] || role;
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  return String(value).replace("T", " ").slice(0, 16);
}

function fuzzyMatch(keyword, values) {
  if (!keyword) {
    return true;
  }
  const normalized = keyword.toLowerCase();
  return values.some((value) => String(value || "").toLowerCase().includes(normalized));
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function encodePayload(value) {
  return encodeURIComponent(JSON.stringify(value));
}
