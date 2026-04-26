package com.xiaoyi.pikafish.plugins;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "PikafishEngine")
public class PikafishEnginePlugin extends Plugin {
    private static final String TAG = "PikafishEngine";
    
    private Process engineProcess;
    private OutputStream engineInput;
    private BufferedReader engineOutput;
    private ExecutorService executor;
    private boolean isRunning = false;
    
    @Override
    public void load() {
        executor = Executors.newSingleThreadExecutor();
    }
    
    @PluginMethod
    public void init(PluginCall call) {
        if (isRunning) {
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Engine already running");
            call.resolve(result);
            return;
        }
        
        executor.execute(() -> {
            try {
                // 获取原生库路径
                String nativeLibraryDir = getContext().getApplicationInfo().nativeLibraryDir;
                File engineFile = new File(nativeLibraryDir, "libpikafish.so");
                
                Log.d(TAG, "Looking for engine at: " + engineFile.getAbsolutePath());
                
                if (!engineFile.exists()) {
                    // 尝试从 jniLibs 目录
                    File jniDir = new File(getContext().getApplicationInfo().sourceDir);
                    engineFile = new File(nativeLibraryDir, "libpikafish.so");
                    
                    if (!engineFile.exists()) {
                        // 尝试从 assets 复制
                        engineFile = new File(getContext().getFilesDir(), "pikafish");
                        if (!engineFile.exists()) {
                            copyEngineFromAssets(engineFile);
                        }
                    }
                }
                
                // 设置可执行权限
                engineFile.setExecutable(true);
                engineFile.setReadable(true);
                
                Log.d(TAG, "Starting engine from: " + engineFile.getAbsolutePath());
                
                // 启动引擎进程
                ProcessBuilder pb = new ProcessBuilder(engineFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                engineProcess = pb.start();
                
                engineInput = engineProcess.getOutputStream();
                engineOutput = new BufferedReader(
                    new InputStreamReader(engineProcess.getInputStream())
                );
                
                isRunning = true;
                
                // 启动输出监听线程
                startOutputListener();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("message", "Engine initialized");
                    call.resolve(result);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize engine", e);
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
    
    @PluginMethod
    public void sendCommand(PluginCall call) {
        String command = call.getString("command");
        if (command == null || command.isEmpty()) {
            call.reject("Command is required");
            return;
        }
        
        if (!isRunning) {
            call.reject("Engine not initialized");
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Sending command: " + command);
                engineInput.write((command + "\n").getBytes("UTF-8"));
                engineInput.flush();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to send command", e);
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
    
    @PluginMethod
    public void setMessageCallback(PluginCall call) {
        // 这个方法用于初始化事件监听
        // 实际的消息通过 notifyListeners 发送
        call.resolve();
    }
    
    @PluginMethod
    public void quit(PluginCall call) {
        if (!isRunning) {
            call.resolve();
            return;
        }
        
        executor.execute(() -> {
            try {
                engineInput.write("quit\n".getBytes("UTF-8"));
                engineInput.flush();
                engineProcess.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error quitting engine", e);
            } finally {
                cleanup();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> call.resolve());
            }
        });
    }
    
    private void startOutputListener() {
        new Thread(() -> {
            try {
                String line;
                while (isRunning && (line = engineOutput.readLine()) != null) {
                    final String output = line;
                    Log.d(TAG, "Engine output: " + output);
                    
                    // 通过 Capacitor 的事件系统发送消息
                    JSObject data = new JSObject();
                    data.put("message", output);
                    notifyListeners("engineMessage", data);
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Error reading engine output", e);
                }
            }
        }).start();
    }
    
    private void copyEngineFromAssets(File destFile) throws IOException {
        // 从 assets 复制引擎文件
        try {
            InputStream is = getContext().getAssets().open("engine/pikafish");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            is.close();
            Log.d(TAG, "Copied engine to: " + destFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy engine from assets", e);
            throw e;
        }
    }
    
    private void cleanup() {
        isRunning = false;
        try {
            if (engineInput != null) engineInput.close();
            if (engineOutput != null) engineOutput.close();
            if (engineProcess != null) engineProcess.destroy();
        } catch (IOException e) {
            Log.e(TAG, "Error cleaning up", e);
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        cleanup();
        super.handleOnDestroy();
    }
}
