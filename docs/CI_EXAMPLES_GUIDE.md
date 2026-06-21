# CI 流水线示例与验证指南

> 配套：`E-02 后端质量门禁与分层规范` 第二轮最小可失败示例

---

## 1. 目录总览

```
.github/workflows/ci.yml          ← 首轮：GitHub Actions 流水线 YAML 草案
docs/ARCHITECTURE_LAYERING.md     ← 分层约束文档（Controller/Service/Mapper）
backend/src/test/.../ResultTest.java  ← 合法通过的 JUnit 5 单测
backend/src/main/java/com/club/example/FormatViolationDemo.java  ← 故意违规（触发 CI 红）
```

---

## 2. 第一轮：流水线 YAML 草案说明

### 2.1 触发条件
- `push` 到 `main` / `develop` / `feature/**` 分支
- 向 `main` / `develop` 发起 PR

### 2.2 三个 Job

| Job 名称          | 核心动作                                                         | 失败是否阻断流水线 |
|-------------------|------------------------------------------------------------------|--------------------|
| `build-and-test`  | JDK 17 编译 → Spotless 格式检查 → `mvn test` → 上传测试报告       | ✅ 阻断             |
| `docker-build`    | Buildx 双层缓存（GHA + Registry buildcache）→ 构建镜像 → PR 仅构建不 push | ⚠️ 非阻断（可配置） |
| `quality-gate`    | 汇总前序 Job 状态，统一输出 pass/fail 总结                         | ✅ 阻断             |

### 2.3 缓存策略
- **Maven 依赖缓存**：`key = $OS-maven-${hash(pom.xml)}`，命中后跳过依赖下载
- **Docker Layer 缓存**：
  - `type=gha, scope=backend`：GitHub Actions 内置 cache（默认 10GB 配额）
  - `type=registry, ref=ghcr.io/...:buildcache`：Registry 持久化缓存，跨分支复用
- 双层缓存可将后端镜像构建时间从 ~3 分钟压缩到 ~30 秒（代码改动少的场景）

---

## 3. 第二轮：最小可失败示例使用方法

### 3.1 场景 A：故意格式违规 → CI 红（Spotless check failed）

**触发文件**：[FormatViolationDemo.java](file:///D:/Asolo2/3453C/backend/src/main/java/com/club/example/FormatViolationDemo.java)

该文件故意违反以下 Spotless/Google Java Format 规则：
| 违规类型                  | 具体位置（示例）                                                |
|---------------------------|-----------------------------------------------------------------|
| Import 顺序错误           | `java.util.HashMap` 放在 `com.club.common.Result` 之前（L3-L6） |
| 类名与括号间多余空格      | `public class    FormatViolationDemo`（L9）                     |
| 方法参数列表与括号间空格  | `badStyleExample(String name, int age){`（L14）                 |
| 缩进不一致（4 空格+8 空格混用） | L17-L25 缩进混乱                                            |
| if/else 大括号换行风格错  | L20 `{` 未独立成行 / L21 `}` 位置错误                          |
| 行尾多余空格              | 多处                                                             |
| 方法签名断行位置怪异      | L29-L31 `anotherBadExample` 方法签名分行位置错误                 |

**复现步骤**：

```bash
cd backend
# 1. 本地触发格式检查（应失败）
mvn spotless:check
# 预期输出：
# [ERROR] Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:2.43.0:check (...)
# [ERROR]  The following files had format violations:
# [ERROR]      src/main/java/com/club/example/FormatViolationDemo.java

# 2. 查看具体差异
mvn spotless:check -Dspotless.check.skip=false -Dverbose=true

# 3. 一键自动修复（Google Java Format 自动重写）
mvn spotless:apply
# 执行后 FormatViolationDemo.java 将被格式化，再次 check 通过
```

**CI 中的表现**：
- PR 中 `build-and-test` Job 的 `Run Spotless check` Step 失败
- GitHub 显示 ❌，merge 按钮被禁用（若分支保护开启）
- Actions 日志中可看到 `spotless-maven-plugin` 输出的 diff

### 3.2 场景 B：故意违反分层约束（可选扩展）

在分层约束文档中已标记 `AdminStatController` 为违规（Controller 直调 Mapper）。
可通过引入 **ArchUnit** 测试让此类违规在 `mvn test` 阶段自动被拦截：

（以下代码仅为演示，未纳入源码；需要时放入 `src/test/architecture/` 并添加 ArchUnit 依赖）

```java
@ArchTest
static final ArchRule controller_should_not_depend_on_mapper =
    noClasses()
        .that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAPackage("..mapper..")
        .because("Controller 必须通过 Service 间接访问 Mapper（见 ARCHITECTURE_LAYERING.md §3.2）");
```

启用后，`AdminStatController` 注入 `ClubMapper` 将被单测捕获，CI 在 `mvn test` 阶段即变红。

### 3.3 场景 C：合法通过的单测（对照组）

**通过文件**：[ResultTest.java](file:///D:/Asolo2/3453C/backend/src/test/java/com/club/common/ResultTest.java)

```bash
cd backend
mvn test -Dtest=ResultTest
# 预期：Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

该测试不依赖 Spring 容器 / 数据库，为纯 JUnit 5 单测，CI 可稳定通过。

---

## 4. 本地完整 CI 模拟清单

在提交 PR 前，本地依次执行，确保与 CI 行为一致：

| 步骤 | 命令                                  | CI 对应 Step                  |
|------|---------------------------------------|-------------------------------|
| 1    | `mvn -B compile -DskipTests`          | `Install dependencies & compile` |
| 2    | `mvn -B spotless:check`               | `Run Spotless check`          |
| 3    | `mvn -B test`                         | `Run unit tests`              |
| 4    | `docker buildx build . -t test:local` | `Build Docker Image`          |

若 4 步全部通过，CI 大概率 ✅。

---

## 5. 清理 / 关闭演示文件

本项目中的 `FormatViolationDemo.java` 是**演示用故意违规文件**，验收完毕后应：
1. 删除整个 `com/club/example/` 包，或
2. 执行 `mvn spotless:apply` 让其格式合规后保留为示例
