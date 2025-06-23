package ca.cleversolutions.zebracapacitor;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.content.Context;

import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import org.json.JSONArray;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import java.util.Map;
import java.util.Set;

@CapacitorPlugin(name = "ZebraCapacitor")
public class ZebraCapacitorPlugin extends Plugin {
    private Connection printerConnection;
    private com.zebra.sdk.printer.ZebraPrinter printer;
    private String macAddress;
    static final String lock = "ZebraPluginLock";

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.resolve(ret);
    }

    @PluginMethod()
    public void print(PluginCall  call){
        String message = call.getString("cpcl");
        if(!isConnected()){
            call.reject("Printer Not Connected");
        }else {
            if (this.printCPCL(message)) {
                call.resolve();
            } else {
                call.reject("unknown error");
            }
        }
    }

    @PluginMethod()
    public void isConnected(PluginCall  call){
        JSObject ret = new JSObject();
        ret.put("connected", this.isConnected());
        call.resolve(ret);
    }

    @PluginMethod()
    public void connect(PluginCall  call){
        String address = call.getString("MACAddress");
        com.zebra.sdk.printer.ZebraPrinter printer = this.connect(address);
        JSObject ret = new JSObject();
        ret.put("success", printer != null);
        call.resolve(ret);
    }

    @PluginMethod()
    public void printerStatus(PluginCall call){
        String address = call.getString("MACAddress");
        JSObject ret = new JSObject();
        if(this.macAddress == macAddress && this.isConnected()){
            try{
                PrinterStatus status = printer.getCurrentStatus();
                ret.put("isReadyToPrint", status.isReadyToPrint);
                ret.put("isPaused", status.isPaused);
                ret.put("isReceiveBufferFull", status.isReceiveBufferFull);
                ret.put("isRibbonOut", status.isRibbonOut);
                ret.put("isPaperOut", status.isPaperOut);
                ret.put("isHeadTooHot", status.isHeadTooHot);
                ret.put("isHeadOpen", status.isHeadOpen);
                ret.put("isHeadCold", status.isHeadCold);
                ret.put("isPartialFormatInProgress", status.isPartialFormatInProgress);
            }catch(Exception ex){
                //Ignore
            }
            ret.put("connected", true);
        }else{
            ret.put("connected", false);
        }
        call.resolve(ret);
    }

    @PluginMethod()
    public void disconnect(PluginCall  call){
        this.disconnect();
        call.resolve();
    }

    @PluginMethod()
    public void discover(PluginCall  call){
        //discoverInternal(call);
        JSArray printers = this.NonZebraDiscovery();
        JSObject ret = new JSObject();
        ret.put("printers", printers);
        call.resolve(ret);
    }

    protected void finalize() throws Throwable {
        super.finalize();
        if( isConnected()) disconnect();
    }

    @PluginMethod()
    public void probeLinkOs(PluginCall call) {
        String address = call.getString("MACAddress");
        JSObject ret = new JSObject();

        com.zebra.sdk.printer.ZebraPrinter zp = connect(address);
        if (printerConnection == null || !printerConnection.isConnected()) {
            // failed to open → not a Link-OS printer (or not paired)
            ret.put("supported", false);
            call.resolve(ret);
            return;
        }

        try {
            String cmd = "! U1 getvar \"appl.link_os_version\"\r\n";
            printerConnection.write(cmd.getBytes());

            Thread.sleep(200);

            byte[] buffer = new byte[512];
            int len = printerConnection.read(buffer);
            String version = new String(buffer, 0, len).trim();

            // If it isn’t “?” then Link-OS is supported
            boolean supported = version.length() > 0 && !version.startsWith("?");
            ret.put("supported", supported);
            ret.put("version", version);
        } catch (Exception e) {
            ret.put("supported", false);
        } finally {
            disconnect();
        }

        call.resolve(ret);
    }


    private boolean printCPCL(String cpcl)
    {
        try {
            if(!isConnected()) {
                Log.v("EMO", "Printer Not Connected");
                return false;
            }

            byte[] configLabel = cpcl.getBytes();
            printerConnection.write(configLabel);

            if (printerConnection instanceof BluetoothConnection) {
                String friendlyName = ((BluetoothConnection) printerConnection).getFriendlyName();
                System.out.println(friendlyName);
            }
        } catch (ConnectionException e) {
            Log.v("EMO", "Error Printing", e);
            return  false;
        }
        return true;
    }

    private boolean isConnected(){
        return printerConnection != null && printerConnection.isConnected();
    }

    private com.zebra.sdk.printer.ZebraPrinter connect(String macAddress) {
        if( isConnected()) disconnect();
        printerConnection = null;
        this.macAddress = macAddress;
        printerConnection = new BluetoothConnection(macAddress);
        synchronized(ZebraCapacitorPlugin.lock) {
            try {
                printerConnection.open();
            }

            catch (ConnectionException e)
            {
                Log.v("EMO", "Printer - Failed to open connection", e);
                disconnect();
            }
            printer = null;
            if (printerConnection.isConnected()) {
                try {
                    printer = ZebraPrinterFactory.getInstance(printerConnection);
                    PrinterLanguage pl = printer.getPrinterControlLanguage();
                } catch (ConnectionException e) {
                    Log.v("EMO", "Printer - Error...", e);
                    printer = null;
                    disconnect();
                } catch (ZebraPrinterLanguageUnknownException e) {
                    Log.v("EMO", "Printer - Unknown Printer Language", e);
                    printer = null;
                    disconnect();
                }
            }
        }
        return printer;
    }

    private void disconnect() {
        synchronized (ZebraCapacitorPlugin.lock) {
            try {
                if (printerConnection != null) {
                    printerConnection.close();
                }
            } catch (ConnectionException e) {
                e.printStackTrace();
            }
        }
    }

    //This doesn't seem to return any printers
    private void discoverWithZebraSDK(final PluginCall  call){
        class BTDiscoveryHandler implements DiscoveryHandler {
            List<JSObject> printers = new LinkedList<JSObject>();
            PluginCall call;

            public BTDiscoveryHandler(PluginCall call) { this.call = call; }

            public void discoveryError(String message)
            {
                call.reject(message);
            }

            public void discoveryFinished()
            {
                JSObject ret = new JSObject();
                ret.put("printers", printers);
                call.resolve(ret);
            }

            @Override
            public void foundPrinter(DiscoveredPrinter printer){
                DiscoveredPrinterBluetooth pr = (DiscoveredPrinterBluetooth) printer;
                try
                {
                    Map<String,String> map = pr.getDiscoveryDataMap();

                    for (String settingsKey : map.keySet()) {
                        System.out.println("Key: " + settingsKey + " Value: " + printer.getDiscoveryDataMap().get(settingsKey));
                    }

                    String name = pr.friendlyName;
                    String mac = pr.address;
                    JSObject p = new JSObject();
                    p.put("name",name);
                    p.put("address", mac);
                    for (String settingsKey : map.keySet()) {
                        System.out.println("Key: " + settingsKey + " Value: " + map.get(settingsKey));
                        p.put(settingsKey,map.get(settingsKey));
                    }
                    printers.add(p);
                } catch (Exception e) {
                    Log.v("EMO", "Discovery Error - Error...", e);
                }
            }
        }

        final Context context = this.getContext();
        new Thread(new Runnable() {

            public void run() {
                try {
                    BluetoothDiscoverer.findPrinters(context, new BTDiscoveryHandler(call));
                } catch (Exception e) {
                    call.reject(e.getMessage());
                }
            }
        }).start();
    }

    private JSArray NonZebraDiscovery(){
        JSArray printers = new JSArray();

        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> devices = adapter.getBondedDevices();

            JSONArray array = new JSONArray();

            for (Iterator<BluetoothDevice> it = devices.iterator(); it.hasNext(); ) {
                BluetoothDevice device = it.next();
                String name = device.getName();
                String mac = device.getAddress();

                JSObject p = new JSObject();
                p.put("name",name);
                p.put("address", mac);
                printers.put(p);

            }
        }catch (Exception e){
            System.err.println(e.getMessage());
        }
        return  printers;
    }
}
