package com.xiaoyi.pikafish;

import com.getcapacitor.BridgeActivity;
import com.xiaoyi.pikafish.plugins.PikafishEnginePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        // 注册插件
        registerPlugin(PikafishEnginePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
