# Spring 热加载 / 类重载说明（Cursor 与 JRebel 类方案）

## 一、为什么 DevTools 改代码后还是要重启？

- **Spring Boot DevTools** 做的是「**快速重启**」：用两套 ClassLoader，只重启你的业务代码，不重启 JVM，所以比整进程重启快，但**仍然是重启**，不是「改完代码不重启」。
- **JVM 默认能力**：调试时的 **Hot Code Replace** 只支持**方法体内部**的修改；改方法签名、加新方法/新类、改字段等都会触发「类重定义失败」，必须重启。
- 所以：在 Cursor/VS Code 里用 Java 扩展 + DevTools，改完代码后**需要重新运行/重启**是正常现象，不是配置错了。

---

## 二、JRebel 类「字节码热加载」方案

想要「改完 Java 代码不重启、自动生效」，本质都是：**在运行时用新类替换旧类**，一般需要额外工具。

| 方案 | 类型 | 说明 |
|------|------|------|
| **JRebel** | 商业 | 字节码增强，支持绝大多数代码变更不重启；有 IntelliJ 插件，**无 Cursor/VS Code 官方插件**。 |
| **DCEVM + HotswapAgent** | 开源 | 使用支持「增强型 Hot Swap」的 JVM（DCEVM），配合 HotswapAgent，可支持新增/修改方法、部分结构变更，**无需 IDE 插件**，用任意 JVM 参数启动即可。 |
| **调试器 Hot Code Replace** | 内置 | Cursor/VS Code 调试 Java 时已支持，但**仅限方法体内修改**，改签名/加方法会失败并提示重启。 |

结论：  
- 在 **Cursor 里**要实现「类似 JRebel 的体验」，只能用 **JVM 层面**的方案（DCEVM+HotswapAgent），或**用 JRebel 从命令行启动进程，再用 Cursor 挂上调试器**。  
- **没有**「Cursor 插件里点一下就用上 JRebel」的官方方式，因为 JRebel 没有为 Cursor/VS Code 做插件。

---

## 三、在 Cursor 中可做的几件事

### 1. 用好现有能力（不装新 JVM）

- **编译即生效**：确保保存时自动编译（Java 扩展默认会编译）。
- **快速重启**：用 Cursor 的「运行和调试」启动 Spring Boot（如 `Application`），改完代码后按 **F5 或再次点击启动**，依赖 DevTools 的快速重启，比冷启动快。
- **Hot Code Replace**：用「调试」方式启动（F5），只改**方法体内部**时，保存后有时会自动热替换，无需重启；若控制台提示无法替换，再手动重启一次。

### 2. 使用 DCEVM + HotswapAgent（免费、类 JRebel）

这样可以在 Cursor 里**用同一套代码**，只是换一个 JVM 来跑，实现更强的热加载，**不需要 Cursor 插件**。

**步骤概要：**

1. **安装 DCEVM**  
   - 从 [DCEVM](https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases) 或 [Liberica DCEVM](https://bell-sw.com/pages/downloads/) 下载与当前 JDK 版本匹配的 DCEVM 构建，并安装到本机（例如 `JAVA_HOME` 指向该 JDK）。

2. **下载 HotswapAgent**  
   - 从 [HotswapAgent releases](https://github.com/HotswapProjects/HotswapAgent/releases) 下载 `hotswap-agent.jar`。

3. **用该 JVM + agent 启动 Spring Boot**  
   在终端（或在 Cursor 里用脚本/任务跑）执行，例如：

   ```bash
   set JAVA_HOME=C:\path\to\your\dcevm-jdk
   "%JAVA_HOME%\bin\java" -agentpath:"%JAVA_HOME%\jre\bin\hotswap.dll" -javaagent:path\to\hotswap-agent.jar -jar backend/target/stock-trading-backend-*.jar
   ```

   Linux/macOS 用 `export JAVA_HOME=...` 和对应 `hotswap` 库。若用 `mvn spring-boot:run`，可在 `pom.xml` 里为 `spring-boot-maven-plugin` 配置相同的 `jvmArguments`（`-agentpath` 与 `-javaagent`）。

4. **在 Cursor 里附加调试器（可选）**  
   - 在 `launch.json` 里添加一个 **「Attach」** 配置，使用 **Socket Attach**，端口与 Spring Boot 的远程调试端口一致（例如 `application.yml` 里 `server.port` 旁或 `jvmArguments` 里 `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`）。
   - 先按上面方式启动应用，再在 Cursor 里选择该 Attach 配置并 F5，即可在 Cursor 里断点调试，热加载仍由 DCEVM+HotswapAgent 负责。

这样做的效果：**改完 Java 代码保存后，由 HotswapAgent 重载类，多数情况不用重启**；Cursor 只负责编辑和调试，不依赖任何「热加载插件」。

### 3. 若你有 IntelliJ + JRebel

- 可以用 **IntelliJ 用 JRebel 启动** Spring Boot，实现最强热加载。
- **Cursor** 继续用来写代码，通过 Git 与 IntelliJ 同步；或只在 IntelliJ 里跑/调试带 JRebel 的进程。  
- 没有「在 Cursor 里装一个插件就启用 JRebel」的官方方式。

---

## 四、小结

- **为什么 Spring DevTools 改代码后只能重启？**  
  因为 DevTools 是「快速重启」而不是「字节码热替换」；JVM 默认也只支持方法体内的热替换。

- **有没有类似 JRebel 的方案？**  
  有：商业的 **JRebel**，或开源的 **DCEVM + HotswapAgent**。

- **如何「集成」到 Cursor？**  
  - Cursor **没有** JRebel 官方插件。  
  - 可行做法：  
    1. 用 Cursor 自带的 **Hot Code Replace**（仅方法体） + **快速重启**；  
    2. 或用 **DCEVM + HotswapAgent** 用自定义 JVM 启动应用，在 Cursor 里用 **Attach** 调试；  
    3. 或用 IntelliJ+JRebel 跑应用，Cursor 只做编辑。

按上面任选一种方式即可在「改代码」和「少重启」之间取得平衡；若你愿意用 DCEVM，我可以再根据你当前 JDK 和 OS 写一份对应的 `launch.json` 或 `pom.xml` 片段。
