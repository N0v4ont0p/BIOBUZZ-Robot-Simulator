package org.firstinspires.ftc.robotcore.internal.network;

import com.qualcomm.robotcore.robocol.Command;
import com.qualcomm.robotcore.robocol.PeerDiscovery;
import com.qualcomm.robotcore.robocol.RobocolDatagram;
import com.qualcomm.robotcore.robocol.RobocolParsable;
import com.qualcomm.robotcore.wifi.NetworkConnection;
import com.qualcomm.robotcore.wifi.NetworkType;

import java.net.InetAddress;
import java.util.function.Consumer;

// Simulator stand-in for the SDK's Wi-Fi link to the Driver Station. Everything the Robot Controller sends to the
// Driver Station (commands, telemetry, gamepad effects) goes to the simulator's Driver Station page instead.
public class NetworkConnectionHandler {
    private static final NetworkConnectionHandler INSTANCE = new NetworkConnectionHandler();
    public static volatile Consumer<Command> toDriverStation;
    public static volatile Consumer<RobocolParsable> dataToDriverStation;

    public static NetworkConnectionHandler getInstance() { return INSTANCE; }
    public static android.net.wifi.WifiManager getWifiManager() { return null; }
    public static NetworkType getNetworkType(android.content.Context c) { return NetworkType.RCWIRELESSAP; }

    public void init(NetworkType t, String a, String b, android.content.Context c, org.firstinspires.ftc.robotcore.internal.ui.RobotCoreGamepadManager g) {}
    public void init(NetworkType t, android.content.Context c) {}
    public void setRecvLoopRunnable(RecvLoopRunnable r) {}
    public NetworkConnection getNetworkConnection() { return null; }
    public NetworkType getNetworkType() { return NetworkType.RCWIRELESSAP; }
    public boolean isNetworkConnected() { return true; }
    public boolean isPeerConnected() { return true; }
    public boolean registerPeerStatusCallback(PeerStatusCallback c) { return true; }
    public synchronized CallbackResult handleConnectionInfoAvailable() { return CallbackResult.NOT_HANDLED; }
    public synchronized CallbackResult handlePeersAvailable() { return CallbackResult.NOT_HANDLED; }
    public synchronized PeerDiscovery updateConnection(RobocolDatagram d) { return null; }

    public void sendCommand(Command c) {
        Consumer<Command> s = toDriverStation;
        if (s != null) s.accept(c);
    }

    public void sendReply(Command received, Command reply) { sendCommand(reply); }
    public void injectReceivedCommand(Command c) {}
    public CallbackResult processAcknowledgments(Command c) { return CallbackResult.NOT_HANDLED; }

    public void sendDataToPeer(RobocolParsable p) {
        Consumer<RobocolParsable> s = dataToDriverStation;
        if (s != null) s.accept(p);
    }

    public void sendDatagram(RobocolDatagram d) {}
    public synchronized void shutdown() {}
    public InetAddress getCurrentPeerAddr() { return InetAddress.getLoopbackAddress(); }
    public void pushNetworkConnectionCallback(NetworkConnection.NetworkConnectionCallback c) {}
    public void removeNetworkConnectionCallback(NetworkConnection.NetworkConnectionCallback c) {}
    public void pushReceiveLoopCallback(RecvLoopRunnable.RecvLoopCallback c) {}
    public void removeReceiveLoopCallback(RecvLoopRunnable.RecvLoopCallback c) {}
}
