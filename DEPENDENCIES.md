# 项目依赖说明

本文档详细说明了项目的所有依赖项及其用途。

## 环境要求

### 后端环境

- **JDK**: 17 或更高版本
- **Maven**: 3.6+ 或更高版本
- **火山引擎 API Key**: 必需（用于调用视觉模型 API）

### 前端环境

- **Node.js**: 16+ 或更高版本（推荐 18+）
- **npm**: 通常随 Node.js 一起安装

## 后端依赖 (Maven)

### 核心框架

| 依赖 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.1.0 | Web 应用框架 |
| Spring Boot Starter Web | 3.1.0 | Web 开发支持（包含 Tomcat、Spring MVC） |
| Spring Boot Starter Test | 3.1.0 | 测试框架支持 |

### 第三方 SDK

| 依赖 | 版本 | 说明 |
|------|------|------|
| volcengine-java-sdk-ark-runtime | LATEST | 火山引擎 ARK 运行时 SDK |

### 文件处理

| 依赖 | 版本 | 说明 |
|------|------|------|
| Apache PDFBox | 3.0.0 | PDF 文件处理 |
| Apache PDFBox Tools | 3.0.0 | PDF 工具类 |
| Commons IO | 2.11.0 | 文件操作工具类 |

### HTTP 客户端

| 依赖 | 版本 | 说明 |
|------|------|------|
| OkHttp | 4.11.0 | HTTP 客户端库 |

### JSON 处理

| 依赖 | 版本 | 说明 |
|------|------|------|
| Jackson Databind | (由 Spring Boot 管理) | JSON 序列化/反序列化 |

### 工具类

| 依赖 | 版本 | 说明 |
|------|------|------|
| Commons Lang3 | (由 Spring Boot 管理) | Apache Commons 工具类 |
| Lombok | (由 Spring Boot 管理) | 代码生成工具（简化 getter/setter 等） |

### 测试依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| Spring Test | (由 Spring Boot 管理) | Spring 测试支持（包含 MockMultipartFile） |

## 前端依赖 (npm)

### 核心框架

| 依赖 | 版本 | 说明 |
|------|------|------|
| React | ^18.2.0 | UI 框架 |
| React DOM | ^18.2.0 | React DOM 渲染 |
| React Router DOM | ^6.20.0 | 路由管理 |

### UI 组件库

| 依赖 | 版本 | 说明 |
|------|------|------|
| Ant Design | ^5.11.0 | UI 组件库 |
| @ant-design/icons | ^5.2.6 | Ant Design 图标库 |

### HTTP 客户端

| 依赖 | 版本 | 说明 |
|------|------|------|
| Axios | ^1.6.0 | HTTP 客户端库 |

### 状态管理

| 依赖 | 版本 | 说明 |
|------|------|------|
| Zustand | ^4.4.0 | 轻量级状态管理库 |

### 开发依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| TypeScript | ^5.3.0 | 类型系统 |
| Vite | ^5.0.0 | 构建工具 |
| @vitejs/plugin-react | ^4.2.0 | Vite React 插件 |
| ESLint | ^8.45.0 | 代码检查工具 |
| @typescript-eslint/eslint-plugin | ^6.0.0 | TypeScript ESLint 插件 |
| @typescript-eslint/parser | ^6.0.0 | TypeScript ESLint 解析器 |
| @types/react | ^18.2.0 | React TypeScript 类型定义 |
| @types/react-dom | ^18.2.0 | React DOM TypeScript 类型定义 |

## 依赖安装

### 后端依赖安装

Maven 会自动下载所有依赖，无需手动安装。

```bash
# 首次编译时会自动下载依赖
mvn clean package

# 仅下载依赖（不编译）
mvn dependency:resolve

# 查看依赖树
mvn dependency:tree

# 查看依赖列表
mvn dependency:list
```

**如果下载慢，配置国内镜像**：

编辑 `~/.m2/settings.xml`（如不存在则创建）：
```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

### 前端依赖安装

npm 会自动安装所有依赖。

```bash
# 安装依赖
cd frontend
npm install

# 如果下载慢，使用国内镜像
npm config set registry https://registry.npmmirror.com
npm install
```

## 依赖版本管理

### 后端

- Spring Boot 管理的依赖版本由 `spring-boot-starter-parent` 统一管理
- 显式指定版本的依赖：PDFBox、OkHttp、Commons IO
- 使用 `LATEST` 的依赖：火山引擎 SDK（会自动获取最新版本）

### 前端

- 使用语义化版本（Semantic Versioning）
- `^` 前缀表示兼容该主版本的最新版本
- 例如：`^18.2.0` 表示兼容 18.x.x 的最新版本

## 依赖更新

### 后端依赖更新

```bash
# 更新所有依赖到最新版本（谨慎使用）
mvn versions:use-latest-versions

# 更新特定依赖
mvn versions:use-latest-versions -Dincludes=com.volcengine:volcengine-java-sdk-ark-runtime
```

### 前端依赖更新

```bash
# 检查过时的依赖
npm outdated

# 更新所有依赖到最新版本
npm update

# 更新特定依赖
npm install package-name@latest
```

## 依赖安全检查

### 后端

```bash
# 使用 OWASP Dependency Check（需要先安装）
mvn org.owasp:dependency-check-maven:check
```

### 前端

```bash
# 使用 npm audit 检查安全漏洞
npm audit

# 自动修复安全漏洞
npm audit fix
```

## 依赖说明

### 为什么需要这些依赖？

1. **Spring Boot**: 提供完整的 Web 应用框架，简化开发
2. **火山引擎 SDK**: 调用视觉模型 API 进行发票识别
3. **PDFBox**: 处理 PDF 文件，转换为图片
4. **OkHttp**: HTTP 客户端，用于 API 调用
5. **Jackson**: JSON 数据处理
6. **React**: 前端 UI 框架
7. **Ant Design**: 提供丰富的 UI 组件
8. **Axios**: 前端 HTTP 请求库
9. **TypeScript**: 提供类型安全
10. **Vite**: 快速的构建工具

## 常见问题

**Q: 为什么某些依赖没有指定版本？**

A: 这些依赖由 Spring Boot 的 parent POM 统一管理，确保版本兼容性。

**Q: 火山引擎 SDK 使用 LATEST 版本安全吗？**

A: 建议在生产环境中固定版本号，避免自动更新导致的不兼容问题。

**Q: 如何查看实际使用的依赖版本？**

A: 
- 后端：`mvn dependency:tree`
- 前端：`npm list` 或查看 `package-lock.json`

## 参考文档

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Maven 官方文档](https://maven.apache.org/)
- [npm 官方文档](https://docs.npmjs.com/)
- [火山引擎 SDK 文档](https://www.volcengine.com/docs/)



