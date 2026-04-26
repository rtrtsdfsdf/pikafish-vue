package com.xiaoyi.pikafish.plugins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "PikafishEngine")
public class PikafishEnginePlugin extends Plugin {
    private static final String TAG = "PikafishEngine";
    
    private Process engineProcess;
    private BufferedWriter engineWriter;
    private BufferedReader engineReader;
    private ExecutorService executor;
    private Thread outputThread;
    private File engineWorkDir;
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private final AtomicBoolean uciOkReceived = new AtomicBoolean(false);
    
    private void debug(String msg) {
        Log.d(TAG, msg);
        JSObject data = new JSObject();
        data.put("message", "[DEBUG] " + msg);
        notifyListeners("engineMessage", data);
    }
    
    @Override
    public void load() {
        executor = Executors.newSingleThreadExecutor();
    }
    
    @PluginMethod
    public void init(PluginCall call) {
        if (isRunning.get()) {
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Engine already running");
            call.resolve(result);
            return;
        }
        
        executor.execute(() -> {
            try {
                debug("=== Starting engine initialization ===");
                
                engineWorkDir = new File(getContext().getFilesDir(), "engine");
                if (!engineWorkDir.exists()) {
                    engineWorkDir.mkdirs();
                }
                debug("Work dir: " + engineWorkDir.getAbsolutePath());
                
                // 复制 NNUE 模型到工作目录
                File nnueFile = copyNnueToFilesDir();
                if (nnueFile != null) {
                    debug("NNUE model ready: " + nnueFile.getAbsolutePath());
                }
                
                // 尝试复制 NNUE 到 nativeLibraryDir（引擎可以读取）
                File nnueInLibDir = copyNnueToNativeLibDir();
                if (nnueInLibDir != null) {
                    debug("NNUE copied to nativeLibraryDir: " + nnueInLibDir.getAbsolutePath());
                    nnueFile = nnueInLibDir;  // 使用 nativeLibraryDir 中的 NNUE
                }
                
                // 从 nativeLibraryDir 查找引擎（有正确的 SELinux 上下文）
                String enginePath = findEngine();
                
                if (enginePath == null) {
                    throw new RuntimeException("Engine not found in nativeLibraryDir");
                }
                
                debug("Using engine: " + enginePath);
                
                // 启动引擎
                debug("Starting engine process...");
                
                // 设置环境变量，让引擎知道 NNUE 文件位置
                // 设置环境变量
                String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
                String[] envp = new String[] {
                    "PIKAFISH_LIB_DIR=" + nativeLibDir
                };
                debug("Setting PIKAFISH_LIB_DIR: " + nativeLibDir);
                if (nnueFile != null) {
                    debug("NNUE file path: " + nnueFile.getAbsolutePath());
                }
                
                debug("Executing: " + enginePath);
                engineProcess = Runtime.getRuntime().exec(new String[]{enginePath}, envp, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                // 同时读取错误流
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(engineProcess.getErrorStream()));
                
                isRunning.set(true);
                debug("Process started!");
                
                // 启动错误流监听
                new Thread(() -> {
                    try {
                        String line;
                        while (engineProcess.isAlive()) {
                            line = errorReader.readLine();
                            if (line != null) {
                                debug("STDERR: " + line);
                            }
                        }
                    } catch (IOException e) {
                        debug("Error reader: " + e.getMessage());
                    }
                }).start();
                
                startOutputListener();
                
                // 等待一下，检查进程是否存活
                Thread.sleep(500);
                if (!engineProcess.isAlive()) {
                    debug("Process exited with code: " + engineProcess.exitValue());
                    throw new RuntimeException("Engine process exited immediately");
                }
                debug("Process is alive, continuing...");
                
                // 初始化 UCI
                debug("Sending uci command...");
                engineWriter.write("uci\n");
                engineWriter.flush();
                
                // 等待 uciok（最多 5 秒）
                int waitCount = 0;
                while (!uciOkReceived.get() && waitCount < 50) {
                    Thread.sleep(100);
                    waitCount++;
                }
                debug("Waited " + (waitCount * 100) + "ms for uciok, received: " + uciOkReceived.get());
                
                // 发送 EvalFile 命令，显式指定 NNUE 路径
                if (nnueFile != null) {
                    debug("NNUE file path: " + nnueFile.getAbsolutePath());
                    debug("NNUE file exists: " + nnueFile.exists());
                    debug("NNUE file canRead: " + nnueFile.canRead());
                    debug("NNUE file length: " + nnueFile.length());
                    
                    // 尝试读取文件头来验证文件是否有效
                    try {
                        java.io.FileInputStream fis = new java.io.FileInputStream(nnueFile);
                        byte[] header = new byte[4];
                        int read = fis.read(header);
                        fis.close();
                        debug("NNUE header bytes: " + (read > 0 ? java.util.Arrays.toString(header) : "empty"));
                    } catch (Exception e) {
                        debug("Failed to read NNUE header: " + e.getMessage());
                    }
                    
                    debug("Sending EvalFile command: " + nnueFile.getAbsolutePath());
                    engineWriter.write("setoption name EvalFile value " + nnueFile.getAbsolutePath() + "\n");
                    engineWriter.flush();
                }
                
                engineWriter.write("isready\n");
                engineWriter.flush();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("message", "Engine initialized");
                    call.resolve(result);
                });
                
            } catch (Exception e) {
                debug("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                isRunning.set(false);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    call.resolve(result);
                });
            }
        });
    }
    
    /**
     * 复制 NNUE 模型到 filesDir（可写目录）
     */
    private File copyNnueToFilesDir() {
        try {
            String nnueName = "pikafish.nnue";
            
            // 复制到工作目录（引擎会在工作目录查找 NNUE）
            File targetFile = new File(engineWorkDir, nnueName);
            
            debug("NNUE target path: " + targetFile.getAbsolutePath());
            debug("Files dir: " + getContext().getFilesDir().getAbsolutePath());
            
            // 缓存检查
            if (targetFile.exists() && targetFile.length() > 40000000) {
                debug("Using cached NNUE: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            // 检查 assets 中是否存在
            String[] assets = getContext().getAssets().list("engine");
            debug("Assets in engine/: " + (assets != null ? java.util.Arrays.toString(assets) : "null"));
            
            // 从 assets 复制
            InputStream is = getContext().getAssets().open("engine/" + nnueName);
            FileOutputStream fos = new FileOutputStream(targetFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long total = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            
            fos.close();
            is.close();
            
            // 设置文件可读权限
            targetFile.setReadable(true, false);
            
            debug("Copied NNUE: " + total + " bytes to " + targetFile.getAbsolutePath());
            debug("File readable after copy: " + targetFile.canRead());
            return targetFile;
            
        } catch (IOException e) {
            debug("NNUE copy FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 复制引擎到 filesDir（因为 nativeLibraryDir 中的文件无法直接执行）
     */
    private File copyEngineToFilesDir() {
        try {
            String engineName = "pikafish";
            File targetFile = new File(engineWorkDir, engineName);
            
            debug("Engine target path: " + targetFile.getAbsolutePath());
            
            // 缓存检查
            if (targetFile.exists() && targetFile.length() > 500000 && targetFile.canExecute()) {
                debug("Using cached engine: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            // 从 assets 复制
            InputStream is = getContext().getAssets().open("engine/" + engineName);
            FileOutputStream fos = new FileOutputStream(targetFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long total = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            
            fos.close();
            is.close();
            
            // 设置可执行权限
            targetFile.setExecutable(true, false);
            targetFile.setReadable(true, false);
            
            debug("Copied engine: " + total + " bytes to " + targetFile.getAbsolutePath());
            debug("File executable: " + targetFile.canExecute());
            return targetFile;
            
        } catch (IOException e) {
            debug("Engine copy FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 查找引擎文件
     */
    private String findEngine() {
        // 优先从 nativeLibraryDir 查找（有正确的 SELinux 上下文）
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        debug("nativeLibraryDir: " + nativeLibDir);
        
        // 方法1：直接检查 nativeLibraryDir
        File libDir = new File(nativeLibDir);
        if (libDir.exists() && libDir.isDirectory()) {
            File[] files = libDir.listFiles();
            debug("nativeLibraryDir files: " + (files != null ? files.length : 0));
            if (files != null) {
                for (File f : files) {
                    debug("  " + f.getName() + " exists:" + f.exists() + " canRead:" + f.canRead() + " canExecute:" + f.canExecute());
                    // 检查 libpikafish.so（不要求 canExecute，因为 .so 文件可能没有执行权限）
                    if (f.getName().equals("libpikafish.so") && f.exists()) {
                        debug("FOUND in nativeLibraryDir: " + f.getAbsolutePath());
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        
        // 方法2：检查父目录的其他 ABI
        File parentDir = libDir.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            File[] subdirs = parentDir.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    File engine = new File(subdir, "libpikafish.so");
                    if (engine.exists()) {
                        debug("FOUND in " + subdir.getName() + ": " + engine.getAbsolutePath());
                        return engine.getAbsolutePath();
                    }
                }
            }
        }
        
        debug("Engine not found in nativeLibraryDir");
        return null;
    }
    
    /**
     * 在 nativeLibraryDir 中查找 NNUE 文件
     */
    private File findNnueInNativeLibDir() {
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        File libDir = new File(nativeLibDir);
        
        if (libDir.exists() && libDir.isDirectory()) {
            // 检查是否有 pikafish.nnue
            File nnueFile = new File(libDir, "pikafish.nnue");
            if (nnueFile.exists() && nnueFile.canRead()) {
                debug("Found pikafish.nnue in nativeLibraryDir");
                return nnueFile;
            }
            
            // 也检查 libpikafish_nnue.so（如果把 NNUE 打包成 .so）
            File nnueSo = new File(libDir, "libpikafish_nnue.so");
            if (nnueSo.exists() && nnueSo.canRead()) {
                debug("Found libpikafish_nnue.so in nativeLibraryDir");
                return nnueSo;
            }
        }
        
        return null;
    }
    
    @PluginMethod
    public void sendCommand(PluginCall call) {
        String command = call.getString("command");
        if (command == null || command.isEmpty()) {
            call.reject("Command is required");
            return;
        }
        
        if (!isRunning.get() || engineWriter == null) {
            call.reject("Engine not initialized");
            return;
        }
        
        executor.execute(() -> {
            try {
                debug(">>> " + command);
                engineWriter.write(command + "\n");
                engineWriter.flush();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (IOException e) {
                debug("Send error: " + e.getMessage());
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    call.resolve(result);
                });
            }
        });
    }
    
    /**
     * 复制 NNUE 到 nativeLibraryDir（引擎子进程可以读取）
     */
    private File copyNnueToNativeLibDir() {
        try {
            String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
            File libDir = new File(nativeLibDir);
            
            if (!libDir.exists() || !libDir.isDirectory()) {
                debug("nativeLibraryDir does not exist: " + nativeLibDir);
                return null;
            }
            
            // 目标文件：pikafish.nnue（不带 .so 后缀）
            File targetFile = new File(libDir, "pikafish.nnue");
            
            debug("Attempting to copy NNUE to: " + targetFile.getAbsolutePath());
            
            // 检查是否已存在
            if (targetFile.exists() && targetFile.length() > 40000000) {
                debug("NNUE already exists in nativeLibraryDir: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            // 从 assets 复制
            InputStream is = getContext().getAssets().open("engine/pikafish.nnue");
            FileOutputStream fos = new FileOutputStream(targetFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long total = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            
            fos.close();
            is.close();
            
            // 设置可读权限
            targetFile.setReadable(true, false);
            
            debug("Copied NNUE to nativeLibraryDir: " + total + " bytes");
            debug("File canRead: " + targetFile.canRead());
            
            return targetFile;
            
        } catch (Exception e) {
            debug("Failed to copy NNUE to nativeLibraryDir: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    @PluginMethod
    public void setMessageCallback(PluginCall call) {
        call.resolve();
    }
    
    @PluginMethod
    public void quit(PluginCall call) {
        if (!isRunning.get()) {
            call.resolve();
            return;
        }
        
        executor.execute(() -> {
            try {
                if (engineWriter != null) {
                    engineWriter.write("quit\n");
                    engineWriter.flush();
                }
                if (engineProcess != null) {
                    engineProcess.waitFor();
                }
            } catch (Exception e) {
                debug("Quit error: " + e.getMessage());
            } finally {
                cleanup();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> call.resolve());
            }
        });
    }
    
    private void startOutputListener() {
        outputThread = new Thread(() -> {
            try {
                String line;
                while (isRunning.get() && engineProcess != null && engineProcess.isAlive()) {
                    line = engineReader.readLine();
                    if (line != null) {
                        debug("<<< " + line);
                        
                        if (line.equals("uciok")) {
                            uciOkReceived.set(true);
                            debug("uciok received!");
                        }
                        
                        if (line.contains("readyok")) {
                            isReady.set(true);
                        }
                        
                        JSObject data = new JSObject();
                        data.put("message", line);
                        notifyListeners("engineMessage", data);
                    }
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    debug("Read error: " + e.getMessage());
                }
            }
        });
        outputThread.start();
    }
    
    private void cleanup() {
        isRunning.set(false);
        isReady.set(false);
        
        try {
            if (engineWriter != null) {
                engineWriter.close();
                engineWriter = null;
            }
            if (engineReader != null) {
                engineReader.close();
                engineReader = null;
            }
            if (engineProcess != null) {
                engineProcess.destroy();
                engineProcess = null;
            }
        } catch (IOException e) {
            debug("Cleanup error: " + e.getMessage());
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        cleanup();
        super.handleOnDestroy();
    }
}
