package com.pedropathing.tuning.autotune;

import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl;

// Simulator stand-in for Pedro's app hooks: on the robot these run when the Robot Controller app starts.
// The Pedro web page and websocket servers are Pedro's own; the simulator only starts them and hands over the
// OpMode manager of the current Robot Controller.
public class Hooks {
    private static volatile OpModeManagerImpl opModeManager;
    private static WebServer server;
    private static WebSocketServer wsServer;

    static OpModeManagerImpl getOpModeManager() {
        return opModeManager;
    }

    public static void simSetOpModeManager(OpModeManagerImpl manager) {
        opModeManager = manager;
    }

    public static synchronized void simStartServers() throws java.io.IOException {
        if (server != null) return;
        server = new WebServer(new android.content.res.AssetManager());
        server.start();
        wsServer = new WebSocketServer();
        wsServer.start(Integer.MAX_VALUE);
    }

    public static void simAbortSession() {
        TuningSession.abort();
    }
}
