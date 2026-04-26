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
import java.io.FileOutputStream;
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
    private Thread outputThread;
    
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
                // 从 assets 复制引擎到私有目录
                File engineFile = new File(getContext().getFilesDir(), "pikafish");
                
                if (!engineFile.exists()) {
                    copyEngineFromAssets(engineFile);
                }
                
                // 设置可执行权限
                engineFile.setExecutable(true);
                engineFile.setReadable(true);
                
                Log.d(TAG, "Engine path: " + engineFile.getAbsolutePath());
                Log.d(TAG, "Engine exists: " + engineFile.exists());
                Log.d(TAG, "Engine can execute: " + engineFile.canExecute());
                
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
        outputThread = new Thread(() -> {
            try {
                String line;
                while (isRunning && (line = engineOutput.readLine()) != null) {
                    final String output = line;
                    Log.d(TAG, "Engine output: " + output);
                    
                    JSObject data = new JSObject();
                    data.put("message", output);
                    notifyListeners("engineMessage", data);
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Error reading engine output", e);
                }
            }
        });
        outputThread.start();
    }
    
    private void copyEngineFromAssets(File destFile) throws IOException {
        Log.d(TAG, "Copying engine from assets to: " + destFile.getAbsolutePath());
        
        // 尝试打开 assets 中的引擎文件
        String[] assetNames = {"engine/pikafish", "pikafish", "engine/libpikafish.so"};
        InputStream is = null;
        
        for (String name : assetNames) {
            try {
                is = getContext().getAssets().open(name);
                Log.d(TAG, "Found engine in assets: " + name);
                break;
            } catch (IOException e) {
                Log.d(TAG, "Asset not found: " + name);
            }
        }
        
        if (is == null) {
            throw new IOException("Engine binary not found in assets");
        }
        
        // 复制到私有目录
        FileOutputStream fos = new FileOutputStream(destFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.close();
        is.close();
        
        Log.d(TAG, "Engine copied successfully");
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
