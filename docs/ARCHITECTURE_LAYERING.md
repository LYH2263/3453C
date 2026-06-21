# Controller / Service / Mapper 分层架构约束规范

> 版本：1.0 | 适用范围：backend 模块所有 Java 源码

---

## 1. 分层总览与依赖方向

```
┌─────────────────────────────────────────────────────┐
│  Controller 层 (@RestController)                     │
│  • 职责：HTTP 路由、参数校验、响应封装                 │
│  • 可依赖：Service 接口、DTO                          │
│  • 禁止：依赖 Mapper、依赖 Entity（作为入参/出参）     │
└──────────────┬──────────────────────────────────────┘
               │  只能向下调用
               ▼
┌─────────────────────────────────────────────────────┐
│  Service 层 (@Service / 接口 + Impl)                 │
│  • 职责：业务逻辑、事务边界、DTO ⇄ Entity 转换         │
│  • 可依赖：Mapper、其他 Service、Entity               │
│  • 禁止：出现 HttpServletRequest/Response 等 Web 对象  │
└──────────────┬──────────────────────────────────────┘
               │  只能向下调用
               ▼
┌─────────────────────────────────────────────────────┐
│  Mapper 层 (@Mapper / MyBatis-Plus)                  │
│  • 职责：单表 CRUD、复杂 SQL 映射、纯数据访问          │
│  • 可依赖：Entity、XML 映射                            │
│  • 禁止：出现业务逻辑判断、事务注解                     │
└─────────────────────────────────────────────────────┘
```

**核心原则：依赖只能向下，严禁反向或跨层。**

---

## 2. DTO 边界规范

### 2.1 DTO 类型定义

| DTO 类型        | 存放位置        | 后缀    | 用途                                  |
|-----------------|-----------------|---------|---------------------------------------|
| Request DTO     | `dto/request/`  | `*Req`  | Controller 接收 HTTP 请求体 / 查询参数 |
| Response DTO    | `dto/response/` | `*Resp` | Controller 返回给前端的结构            |
| 内部传输 DTO    | `dto/internal/` | `*Dto`  | Service 之间跨模块传递（非对外）       |

**禁止在 Controller 层使用 `Map<String, Object>` 作为入参或出参。**

### 2.2 DTO ⇄ Entity 转换规则

```
Controller 接收 DTO (Request)
       │
       ▼  Service 内部转换（可使用 MapStruct / BeanUtils / 手工 setter）
Service 操作 Entity
       │
       ▼  Service 内部转换
Controller 返回 DTO (Response)
```

- **转换必须发生在 Service 层**，不得在 Controller 中直接操作 Entity。
- 转换逻辑推荐集中到 `converter/` 包下的独立 `*Converter` 类，避免 Service 膨胀。
- List / Page 场景必须批量转换，禁止 for 循环内单条调用数据库。

### 2.3 字段裁剪

- Response DTO 严禁包含敏感字段（如 `password`、`salt`、内部状态码）。
- 分页响应统一使用 `PageResp<T>` 包装，禁止直接返回 MyBatis-Plus `IPage`。

---

## 3. Controller 层约束

### 3.1 必须遵守

1. **仅注入 Service 接口**，使用构造器注入（推荐）或 `@Autowired` setter 注入：
   ```java
   // ✅ 正确：只依赖 Service
   @RestController
   public class ActivityController {
       private final ActivityService activityService;
       public ActivityController(ActivityService activityService) {
           this.activityService = activityService;
       }
   }
   ```
2. 入参使用 `@Valid` + Request DTO 做声明式校验。
3. 所有返回使用 `Result<T>` 统一封装，禁止 `void` 写流（导出除外，需 Service 封装）。
4. 方法行数 ≤ 15 行，仅做「参数接收 → Service 调用 → 结果返回」三件事。

### 3.2 严格禁止

| 禁止项                                    | 违规示例（当前代码中存在）                                                                 |
|-------------------------------------------|-------------------------------------------------------------------------------------------|
| ❌ 注入任何 Mapper                         | `@Autowired private ClubMapper clubMapper;` (见 `AdminStatController` L28-L35)             |
| ❌ 直接调用 Mapper 方法                    | `clubMapper.selectCount(null)` (见 `AdminStatController` L48-L51)                          |
| ❌ 使用 Entity 作为 `@RequestBody` 入参    | `public Result<?> create(@Valid @RequestBody Activity activity)` (`ActivityController` L23)|
| ❌ 使用 Entity 作为返回值                  | `return Result.success(activityService.list())` (返回 `List<Activity>` Entity)              |
| ❌ 使用 `Map<String, Object>` 作为入参     | `audit(@RequestBody Map<String, String> params)` (`ActivityController` L28)                 |
| ❌ 出现业务逻辑分支（if/else 业务判断）    | Controller 中写权限判断、状态流转                                                           |

---

## 4. Service 层约束

### 4.1 必须遵守

1. **接口 + 实现分离**：`ActivityService`（接口） → `ActivityServiceImpl`（实现）。
2. 实现类上标注 `@Service`，推荐使用 `ServiceImpl<Mapper, Entity>` 基类（MyBatis-Plus）。
3. **事务边界在 Service**：需要事务的方法使用 `@Transactional`，不得上移到 Controller 或下放到 Mapper。
4. 跨表操作、批量操作、状态流转 **必须** 在 Service 内完成。
5. 调用其他 Service 时注入 **接口**，不注入 Impl 类。

### 4.2 严格禁止

| 禁止项                                          | 说明                                         |
|-------------------------------------------------|---------------------------------------------|
| ❌ 注入 `HttpServletRequest` / `HttpServletResponse` | Service 应与 Web 层解耦                     |
| ❌ 直接返回 `Result<?>` 公用响应包装             | （此项目约定可返回 `Result<?>`，但禁止写 JSON）|
| ❌ Mapper 循环依赖 A→B→A                        | 通过抽取第三方 Service 解决                  |
| ❌ 一个方法操作 ≥ 5 张主表                       | 考虑拆分子方法或领域服务                     |

---

## 5. Mapper 层约束

1. 继承 `BaseMapper<Entity>`，标注 `@Mapper`（或通过 `@MapperScan` 扫描）。
2. 自定义 SQL 使用 XML 映射（`resources/mapper/*.xml`），禁止注解写长 SQL。
3. **禁止** 在 Mapper 中写业务逻辑（如 `if(status == 'APPROVED')` 判断）。
4. 单条 SQL 不超过 3 张表 JOIN，超过则在 Service 层多次查询后组合。

---

## 6. 当前代码违规清单 & 修复指引

| 文件                                             | 违规类型                  | 修复建议                                                                 |
|--------------------------------------------------|---------------------------|--------------------------------------------------------------------------|
| [AdminStatController.java](file:///D:/Asolo2/3453C/backend/src/main/java/com/club/controller/AdminStatController.java#L28-L53) | ❌ Controller 直调 Mapper | 新增 `AdminStatService`，将 `clubMapper.selectCount()` 等调用迁移至 Service；Controller 仅注入 `AdminStatService`。 |
| [ActivityController.java](file:///D:/Asolo2/3453C/backend/src/main/java/com/club/controller/ActivityController.java#L23) | ❌ Entity 作为入参       | 新增 `ActivityCreateReq` DTO，Service 层转换为 Entity。                    |
| [ActivityController.java](file:///D:/Asolo2/3453C/backend/src/main/java/com/club/controller/ActivityController.java#L28-L30) | ❌ Map 作为入参           | 新增 `ActivityAuditReq` DTO 替代 `Map<String, String>`。                   |
| [ActivityController.java](file:///D:/Asolo2/3453C/backend/src/main/java/com/club/controller/ActivityController.java#L18-L20) | ❌ 返回 Entity List       | 新增 `ActivityResp` DTO，Service 层 `list()` 返回 `List<ActivityResp>`。   |
| [ExportController.java](file:///D:/Asolo2/3453C/backend/src/main/java/com/club/controller/ExportController.java)  | ❌ Controller 内写 EasyExcel | 抽取 `ExcelExportService`，统一处理 header、编码、异常；Controller 仅调用 Service。 |

---

## 7. 代码审查 Checklist（PR 必查）

- [ ] Controller 是否只注入 Service 接口，无任何 Mapper 引用？
- [ ] Controller 入参/出参是否使用 DTO，无 Entity 泄露？
- [ ] `@Transactional` 是否只出现在 Service 方法上？
- [ ] Service 中是否未出现 `HttpServletRequest` 等 Web 对象？
- [ ] Mapper XML 中 SQL 是否无业务逻辑分支？
- [ ] 新增的 DTO 是否放在正确的子包（request/response/internal）？
- [ ] 若使用 `Map<String, Object>`，是否有充分理由并在 Code Review 备注？

---

## 8. 架构演进方向（中期）

1. 引入 **MapStruct** 做编译期 DTO↔Entity 转换，替代 BeanUtils。
2. 引入 **ArchUnit** 单元测试，自动校验分层依赖（如 `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAPackage("..mapper..")`），把本规范固化为可执行测试并纳入 CI。
3. 按领域拆分 Service：`ActivityService` → `ActivityQueryService` + `ActivityCommandService`，向 CQRS 演进。
