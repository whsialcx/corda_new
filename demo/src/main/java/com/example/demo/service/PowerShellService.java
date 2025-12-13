package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PowerShellService {
    
    private static final Logger logger = LoggerFactory.getLogger(PowerShellService.class);
    
    @Value("${script.add-node-path:scripts/add_node.ps1}")
    private String scriptPath;
    
    @Value("${corda.project.root:E:\\springBoot\\demo}")
    private String cordaProjectRoot;

    // 在项目根执行 gradlew.bat clean deployNodes
    public ProcessResult executeGradleDeploy() 
    {
        File projectRootDir = new File(cordaProjectRoot);
        if (!projectRootDir.exists()) 
        {
            logger.error("Corda 项目根目录不存在: {}", cordaProjectRoot);
            return new ProcessResult(-1, "", "Corda 项目根目录不存在: " + cordaProjectRoot, false);
        }

        try 
        {
            logger.info("在 {} 执行: gradlew.bat clean deployNodes", projectRootDir.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder();
            // 直接调用 gradlew.bat，确保工作目录是项目根
            pb.command("cmd.exe", "/c", "gradlew.bat", "clean", "deployNodes");
            pb.directory(projectRootDir);
            pb.redirectErrorStream(true);

            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info("[gradle] {}", line);
                }
            }

            // 等待较长时间以完成构建,一般时2到5分钟，这里我们设置为10
            boolean finished = proc.waitFor(10, TimeUnit.MINUTES);
            int exitCode = finished ? proc.exitValue() : -1;

            logger.info("gradlew 执行完成，退出码: {}", exitCode);

            return new ProcessResult(exitCode, output.toString(), "", finished);

        } 
        catch (IOException | InterruptedException e) 
        {
            logger.error("执行 gradlew.bat 失败", e);
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", "执行 gradlew.bat 时发生错误: " + e.getMessage(), false);
        }
    }

    public List<String> getNodeNames() 
        {
        List<String> nodes = new ArrayList<>();
        File projectRoot = new File(cordaProjectRoot);
        File buildGradle = new File(projectRoot, "build.gradle");
        if (!buildGradle.exists()) 
        {
            logger.warn("未找到 build.gradle 文件: {}", buildGradle.getAbsolutePath());
            return nodes;
        }
        try 
        {
            String content = Files.readString(buildGradle.toPath(), StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("name\\s+\"([^\"]+)\"");
            Matcher m = p.matcher(content);
            while (m.find()) 
            {
                nodes.add(m.group(1));
            }
        } 
        catch (IOException e) 
        {
            logger.error("读取 build.gradle 时出错", e);
        }
        return nodes;
    }
    
    public ProcessResult executePowerShellScript(String arguments) 
    {
        try 
        {
            // 使用配置的 Corda 项目根目录
            File projectRootDir = new File(cordaProjectRoot);
            if (!projectRootDir.exists()) 
            {
                logger.error("Corda 项目根目录不存在: {}", cordaProjectRoot);
                return new ProcessResult(-1, "", "Corda 项目根目录不存在: " + cordaProjectRoot, false);
            }
            
            // 构建完整的脚本路径
            String fullScriptPath = Paths.get(cordaProjectRoot, scriptPath).toString();
            
            // 检查脚本文件是否存在
            File scriptFile = new File(fullScriptPath);
            if (!scriptFile.exists()) 
            {
                logger.error("PowerShell 脚本不存在: {}", fullScriptPath);
                return new ProcessResult(-1, "", "脚本文件不存在: " + fullScriptPath, false);
            }
            
            logger.info("执行 PowerShell 脚本: {}", fullScriptPath);
            logger.info("工作目录: {}", cordaProjectRoot);
            logger.info("参数: {}", arguments);
            
            // 构建进程
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // 设置命令
            if (arguments != null && !arguments.trim().isEmpty()) 
            {
                // 如果有参数，构建完整的命令数组
                String[] argsArray = arguments.split("\\s+");
                String[] command = new String[5 + argsArray.length];
                command[0] = "powershell.exe";
                command[1] = "-ExecutionPolicy";
                command[2] = "Bypass";
                command[3] = "-File";
                command[4] = fullScriptPath;
                // 添加额外参数
                System.arraycopy(argsArray, 0, command, 5, argsArray.length);
                processBuilder.command(command);
            } 
            else 
            {
                // 没有额外参数
                processBuilder.command(
                    "powershell.exe", 
                    "-ExecutionPolicy", "Bypass", 
                    "-File", fullScriptPath
                );
            }
            // 设置 Corda 项目根目录为工作目录
            processBuilder.directory(projectRootDir);
            // 重定向错误流到输出流
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            // 读取输出流
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            // 读取标准输出
            String line;
            while ((line = outputReader.readLine()) != null) 
            {
                output.append(line).append("\n");
                logger.info("PowerShell 输出: {}", line);
            }
            // 读取错误输出
            while ((line = errorReader.readLine()) != null) 
            {
                error.append(line).append("\n");
                logger.error("PowerShell 错误: {}", line);
            }
            // 等待进程完成
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;
            logger.info("PowerShell 脚本执行完成，退出码: {}", exitCode);
            return new ProcessResult(
                exitCode, 
                output.toString(), 
                error.toString(),
                finished
            );
        } 
        catch (IOException | InterruptedException e) 
        {
            logger.error("执行 PowerShell 脚本失败", e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            return new ProcessResult(-1, "", "执行脚本时发生错误: " + e.getMessage(), false);
        }
    }
    //验证配置
    public boolean validateCordaProject() 
    {
        File projectRoot = new File(cordaProjectRoot);
        if (!projectRoot.exists()) 
        {
            logger.error("Corda 项目根目录不存在: {}", cordaProjectRoot);
            return false;
        }
        
        File buildGradle = new File(projectRoot, "build.gradle");
        if (!buildGradle.exists()) 
        {
            logger.error("在 Corda 项目根目录中找不到 build.gradle 文件: {}", cordaProjectRoot);
            return false;
        }
        
        File scriptFile = new File(projectRoot, scriptPath);
        if (!scriptFile.exists()) 
        {
            logger.error("PowerShell 脚本不存在: {}", scriptFile.getAbsolutePath());
            return false;
        }    
        logger.info("Corda 项目验证成功: {}", cordaProjectRoot);
        return true;
    }
    // 获取 Corda 项目信息
    public CordaProjectInfo getCordaProjectInfo() 
    {
        File projectRoot = new File(cordaProjectRoot);
        File buildGradle = new File(projectRoot, "build.gradle");
        File scriptFile = new File(projectRoot, scriptPath);
        
        return new CordaProjectInfo(
            cordaProjectRoot,
            projectRoot.exists(),
            buildGradle.exists(),
            scriptFile.exists(),
            scriptFile.getAbsolutePath()
        );
    }
    //启动所有节点
    public ProcessResult executeRunnodesScript() 
    {
        File projectRootDir = new File(cordaProjectRoot);
        if (!projectRootDir.exists()) 
        {
            logger.error("Corda 项目根目录不存在: {}", cordaProjectRoot);
            return new ProcessResult(-1, "", "Corda 项目根目录不存在: " + cordaProjectRoot, false);
        }

        File runnodesScript = new File(projectRootDir, "build/nodes/runnodes.bat");
        if (!runnodesScript.exists()) 
        {
            logger.error("runnodes.bat 脚本不存在: {}", runnodesScript.getAbsolutePath());
            return new ProcessResult(-1, "", "runnodes.bat 脚本不存在: " + runnodesScript.getAbsolutePath(), false);
        }
        try 
        {
            logger.info("执行 runnodes.bat 脚本: {}", runnodesScript.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder();
            pb.command("cmd.exe", "/c", "runnodes.bat");
            pb.directory(new File(projectRootDir, "build/nodes"));
            pb.redirectErrorStream(true);

            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info("[runnodes] {}", line);
                }
            }
            // 等待进程完成，但注意：runnodes.bat 会启动多个节点，所以它可能会立即返回，也可能不会。
            // 这里我们等待一段时间，比如10秒，然后检查退出码。
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            int exitCode = finished ? proc.exitValue() : 0; // 如果没结束，我们假设它启动成功，因为 runnodes.bat 是启动后台进程的。
            logger.info("runnodes.bat 执行完成，退出码: {}", exitCode);
            return new ProcessResult(exitCode, output.toString(), "", finished);
        } 
        catch (IOException | InterruptedException e) 
        {
            logger.error("执行 runnodes.bat 失败", e);
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", "执行 runnodes.bat 时发生错误: " + e.getMessage(), false);
        }
    }

    //启动指定节点
    public ProcessResult startNode(String nodeName) 
    {
        File projectRootDir = new File(cordaProjectRoot);
        if (!projectRootDir.exists()) 
        {
            logger.error("Corda 项目根目录不存在: {}", cordaProjectRoot);
            return new ProcessResult(-1, "", "Corda 项目根目录不存在: " + cordaProjectRoot, false);
        }

        File nodeDir = new File(projectRootDir, "build/nodes/" + nodeName);
        if (!nodeDir.exists()) 
        {
            logger.error("节点目录不存在: {}", nodeDir.getAbsolutePath());
            return new ProcessResult(-1, "", "节点目录不存在: " + nodeDir.getAbsolutePath(), false);
        }

        File cordaJar = new File(nodeDir, "corda.jar");
        if (!cordaJar.exists()) 
        {
            logger.error("corda.jar 不存在: {}", cordaJar.getAbsolutePath());
            return new ProcessResult(-1, "", "corda.jar 不存在: " + cordaJar.getAbsolutePath(), false);
        }
        try 
        {
            logger.info("启动节点 {}: java -jar corda.jar", nodeName);
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("java", "-jar", "corda.jar");
            pb.directory(nodeDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info("[node-{}] {}", nodeName, line);
                }
            }
            // 等待一段时间，比如5秒，然后检查进程是否还在运行
            boolean finished = proc.waitFor(5, TimeUnit.SECONDS);
            int exitCode = finished ? proc.exitValue() : 0; // 如果没结束，我们假设它启动成功
            logger.info("节点 {} 启动完成，退出码: {}", nodeName, exitCode);
            return new ProcessResult(exitCode, output.toString(), "", finished);
        } 
        catch (IOException | InterruptedException e) 
        {
            logger.error("启动节点失败", e);
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", "启动节点时发生错误: " + e.getMessage(), false);
        }
    }
    //终止指定节点
    public ProcessResult stopNode(String nodeName) 
    {
        File projectRootDir = new File(cordaProjectRoot);
        if (!projectRootDir.exists()) 
        {
            logger.error("Corda 项目根目录不存在: {}", cordaProjectRoot);
            return new ProcessResult(-1, "", "Corda 项目根目录不存在: " + cordaProjectRoot, false);
        }

        File nodeDir = new File(projectRootDir, "build/nodes/" + nodeName);
        if (!nodeDir.exists()) 
        {
            logger.error("节点目录不存在: {}", nodeDir.getAbsolutePath());
            return new ProcessResult(-1, "", "节点目录不存在: " + nodeDir.getAbsolutePath(), false);
        }

        try 
        {
            logger.info("尝试终止与节点目录关联的 Java 进程: {}", nodeDir.getAbsolutePath());
            // 构造 PowerShell 命令：查找命令行中包含节点目录路径的进程并强制结束它们
            String nodePathPattern = nodeDir.getAbsolutePath().replace("\\", "\\\\");
            String psCmd = "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and $_.CommandLine -match '" +
                            nodePathPattern +
                            "' } | Select-Object -ExpandProperty ProcessId | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue; Write-Output (\"Killed process $_\") }";
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psCmd);
            pb.directory(projectRootDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) 
            {
                String line;
                while ((line = reader.readLine()) != null) 
                {
                    output.append(line).append("\n");
                    logger.info("[stop-node] {}", line);
                }
            }

            boolean finished = proc.waitFor(2, TimeUnit.MINUTES);
            int exitCode = finished ? proc.exitValue() : -1;

            if (exitCode == 0) 
            {
                logger.info("停止节点 {} 操作完成，exitCode={}", nodeName, exitCode);
                return new ProcessResult(0, output.toString(), "", true);
            } 
            else 
            {
                logger.warn("停止节点 {} 操作返回非 0 退出码: {}", nodeName, exitCode);
                return new ProcessResult(exitCode, output.toString(), "停止节点时 exitCode != 0", false);
            }
        } 
        catch (IOException | InterruptedException e) 
        {
            logger.error("停止节点失败", e);
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", "停止节点时发生错误: " + e.getMessage(), false);
        }
    }

    public static class ProcessResult {
        private final int exitCode;
        private final String output;
        private final String error;
        private final boolean success;
        
        public ProcessResult(int exitCode, String output, String error, boolean success) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
            this.success = success;
        }
        
        // Getters
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getError() { return error; }
        public boolean isSuccess() { return success; }
    }
    
    public static class CordaProjectInfo {
        private final String projectRoot;
        private final boolean rootExists;
        private final boolean buildGradleExists;
        private final boolean scriptExists;
        private final String scriptPath;
        
        public CordaProjectInfo(String projectRoot, boolean rootExists, boolean buildGradleExists, 
                               boolean scriptExists, String scriptPath) {
            this.projectRoot = projectRoot;
            this.rootExists = rootExists;
            this.buildGradleExists = buildGradleExists;
            this.scriptExists = scriptExists;
            this.scriptPath = scriptPath;
        }
        
        // Getters
        public String getProjectRoot() { return projectRoot; }
        public boolean isRootExists() { return rootExists; }
        public boolean isBuildGradleExists() { return buildGradleExists; }
        public boolean isScriptExists() { return scriptExists; }
        public String getScriptPath() { return scriptPath; }
    }
}