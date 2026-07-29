# SonarQube Analysis Scope Configuration Guide

This document provides corporate-grade documentation on configuring the analysis scope for SonarQube. It covers how to precisely target specific files or directories during a scan, minimizing unnecessary processing and focusing on the code that matters.

---

## 1. Core Concepts & Definitions

Controlling what SonarQube analyzes revolves around three primary properties. 

> [!IMPORTANT]  
> **Emphasis on Inclusions:** It is an industry best practice to rely on **Inclusions** rather than Exclusions whenever you need to target specific files. Inclusions provide a strict, allow-list approach, ensuring you never accidentally scan untracked or unwanted directories.

### `sonar.sources`
* **Definition:** Defines the starting root directory (or directories) where the scanner begins looking for source code.
* **Default:** If not specified, it defaults to the current directory (`.`).
* **Behavior:** By default, setting `sonar.sources=.` tells the scanner to analyze *every* supported file in the current directory and all subdirectories. You can set this to a specific file (e.g., `sonar.sources=main.go`), which forces the scanner to view only that file as the entire project.

### `sonar.inclusions`
* **Definition:** A comma-separated list of file path patterns that act as an **allow-list**. 
* **Behavior:** When `sonar.inclusions` is defined, the scanner will **only** analyze files that match the inclusion patterns, completely ignoring everything else within the `sonar.sources` root. This is the safest and most reliable way to target specific files or folders.

### `sonar.exclusions`
* **Definition:** A comma-separated list of file path patterns that act as a **deny-list**.
* **Behavior:** Any file matching an exclusion pattern is explicitly skipped. Exclusions are processed *after* inclusions.

---

## 2. Pathing and Wildcards

When defining inclusions or exclusions, understanding SonarQube's wildcard syntax is critical.

| Pattern | Description | Example Usage | Matches |
| :--- | :--- | :--- | :--- |
| **Normal Path** | Exact relative path from the root. | `src/app/main.go` | Only `src/app/main.go`. Will not match `app/main.go`. |
| `*` | Matches zero or more characters in a file/directory name. | `*.go` | `main.go`, `utils.go` in the root folder only. |
| `**` | Matches zero or more directories. | `**/main.go` | `main.go` located anywhere (root, or deeply nested). |
| `**/*` | Matches any file in any directory. | `src/**/*.cs` | All `.cs` files inside `src/` and its subdirectories. |

---

## 3. Standard Scanning (Non-.NET Projects)

This section applies to languages like Go, Java, JavaScript, Python, etc., using the standard `sonar-scanner` CLI tool.

> [!NOTE]  
> All commands assume you have already authenticated and defined your project key (e.g., `-Dsonar.projectKey=my-project -Dsonar.host.url=https://sonarqube.iseccorp.in -Dsonar.token=TOKEN`). These base properties are omitted below for brevity.

### Full Project Scan
Scans everything from the current directory downwards.
```cmd
sonar-scanner -Dsonar.sources=.
```

### Single File Scan
**Method A: Using Inclusions (Recommended)**  
Maintains the project context but filters down to one file.
```cmd
sonar-scanner -Dsonar.sources=. -Dsonar.inclusions="main.go"
```
**Method B: Using Sources**  
Treats the single file as the entire project root. Useful for isolated scripts.
```cmd
sonar-scanner -Dsonar.sources="main.go"
```

### Multiple Specific Files
Separate multiple files with commas. Always use quotes to prevent shell expansion errors.
```cmd
sonar-scanner -Dsonar.sources=. -Dsonar.inclusions="main.go,utils/helper.go"
```

### Specific Folder Scan
**Using Normal Path:** Scans the folder based on its exact path from the root.
```cmd
sonar-scanner -Dsonar.sources=. -Dsonar.inclusions="src/api/**"
```
**Using Wildcards (`**`):** Scans the folder regardless of where it is nested.
```cmd
sonar-scanner -Dsonar.sources=. -Dsonar.inclusions="**/api/**"
```

---

## 4. .NET Scanning (C# / VB.NET Projects)

This section applies exclusively to .NET projects. 

> [!WARNING]  
> **Fundamental Difference:** You **cannot** use the standard `sonar-scanner` for .NET code. You must use `SonarScanner.MSBuild.exe` (or `dotnet sonarscanner`). The .NET scanner reads your `.csproj` / `.sln` files during the `build` step to find the source code.

Because the MSBuild compiler dictates what files are built, passing `sonar.inclusions` will **not** speed up the build process. The compiler will still build the entire project. However, `sonar.inclusions` will filter the final results sent to the SonarQube server during the `end` step, ensuring only the targeted files are reported and counted against your license.

### Full Project Scan
```cmd
SonarScanner.MSBuild.exe begin /k:"my-project" /d:sonar.host.url="https://sonarqube.iseccorp.in" /d:sonar.token="TOKEN"
dotnet build
SonarScanner.MSBuild.exe end /d:sonar.token="TOKEN"
```

### Single File Scan
Pass the inclusion property during the **begin** step using the `/d:` flag.
```cmd
SonarScanner.MSBuild.exe begin /k:"my-project" /d:sonar.host.url="..." /d:sonar.token="..." /d:sonar.inclusions="**/Program.cs"
dotnet build
SonarScanner.MSBuild.exe end /d:sonar.token="..."
```

### Specific Folder Scan
Target all files within a specific namespace or folder structure.
```cmd
SonarScanner.MSBuild.exe begin /k:"my-project" /d:sonar.host.url="..." /d:sonar.token="..." /d:sonar.inclusions="**/Controllers/**"
dotnet build
SonarScanner.MSBuild.exe end /d:sonar.token="..."
```

### Exclude Test Files (Common .NET Scenario)
To explicitly ignore unit tests if SonarQube fails to auto-detect them:
```cmd
SonarScanner.MSBuild.exe begin /k:"my-project" /d:sonar.host.url="..." /d:sonar.token="..." /d:sonar.exclusions="**/*Tests.cs,**/Tests/**"
dotnet build
SonarScanner.MSBuild.exe end /d:sonar.token="..."
```

---

## 5. Summary of Best Practices
1. **Always quote your patterns:** Use `"` around your inclusion/exclusion strings (e.g., `"-Dsonar.inclusions=**/*.go"`) to prevent the OS command line from misinterpreting wildcards.
2. **Prefer Inclusions over Exclusions:** It is cleaner to state exactly what you want scanned than to try and list everything you don't.
3. **Use `**` generously:** Unless you have a strict mono-repo structure, using `**/filename.ext` is much less prone to breaking if a file is moved to a new sub-folder.
