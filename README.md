# 多币种轧差清算工作台（Clearing Netting Workbench）

单币种多边轧差清算全栈演示：录入义务 → 执行轧差 → 查看净头寸 → 确认 settle。

## How to Run

```bash
cd projects/01-clearing-netting
docker compose up --build
```

镜像默认走 `docker.m.daocloud.io` 与 Maven/npm 国内源，便于在受限网络下构建。若本机已有同名官方镜像亦可直接使用。

后台运行：

```bash
docker compose up --build -d
```

停止：

```bash
docker compose down
```

## Services

| 服务 | 宿主机地址 |
|------|------------|
| Frontend | http://localhost:3171 |
| Backend API | http://localhost:8171 |
| PostgreSQL | localhost:54371 |

容器内：backend 监听 `8080`，frontend nginx 将 `/api` 反代到 `backend:8080`。

## 测试账号

| 用户名 | 密码 | 权限 |
|--------|------|------|
| operator | op123456 | 可写（轧差、settle、新建会员/义务） |
| viewer | view123456 | 只读 |

## Verification

1. 打开 http://localhost:3171 ，使用 `operator` / `op123456` 登录
2. 首页查看 seed 灌入的待轧差义务摘要与最近批次
3. 「会员」页确认演示会员为 ACTIVE；可新建或启停
4. 「义务」页筛选 OPEN 义务，或新建一笔同币种义务
5. 「轧差执行」选择 settleDate + currency（如 USD），执行轧差
6. 确认净头寸表 ΣnetAmount = 0，批次状态 COMPLETED
7. 进入批次详情，点击「确认 Settle」，在确认框中填写必填备注后提交；批次变为 SETTLED，义务变为 SETTLED，详情与批次列表可回看备注、操作员与 settle 时间
8. 对 FAILED 或进行中的批次，Settle 按钮不可用；直接调用 `POST /api/netting-runs/{id}/settle` 也会被 4xx 拒绝
9. 使用 `viewer` 登录，确认只能浏览、无 Settle 按钮、无法执行写操作

健康检查：

```bash
curl http://localhost:8171/api/health
```

登录：

```bash
curl -X POST http://localhost:8171/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"operator\",\"password\":\"op123456\"}"
```

## 技术栈

- Backend: Java 17、Spring Boot 3、Hexagonal、JPA、PostgreSQL、JWT
- Frontend: Vue 3、Vite、Element Plus、Pinia、Vue Router、nginx
- Infra: Docker Compose（db / backend / seed / frontend）

## 项目结构

```
01-clearing-netting/
├── PRD.md
├── README.md
├── docker-compose.yml
├── backend/
├── frontend/
└── seed/
```
