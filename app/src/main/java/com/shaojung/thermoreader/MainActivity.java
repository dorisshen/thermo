package com.shaojung.thermoreader;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public UUID UUID_IRT_SERV = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    public UUID UUID_IRT_DATA = UUID.fromString("00002a1e-0000-1000-8000-00805f9b34fb");
    public UUID UUID_IRT_CONF = UUID.fromString("00002906-0000-1000-8000-00805f9b34fb"); // 0: disable,1: enable

    public UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //定義手機的UUID

    public String DeviceName = "ThermocoupleSensor";
    public BluetoothAdapter BTAdapter;
    public BluetoothDevice BTDevice;
    public BluetoothGatt BTGatt;
    public BluetoothLeScanner scanner;

    public MyScanCallBack callBack;

    public boolean scanning;
    public Handler handler;
    public Console console;
    SharedPreferences sp;
    TextView tvAddr, tvDeg, tvStatus;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        console = new Console((TextView) findViewById(R.id.console), this);
        ((Button) findViewById(R.id.buttonScan)).setOnClickListener(this);
        ((Button) findViewById(R.id.buttonClear)).setOnClickListener(this);

        //初始化Bluetooth adapter，透過BluetoothManager得到一個參考Bluetooth adapter
        BluetoothManager BTManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BTAdapter = BTManager.getAdapter();
        scanner = BTAdapter.getBluetoothLeScanner();
        sp = getSharedPreferences("device", MODE_PRIVATE);
        tvAddr = (TextView) findViewById(R.id.tvAddress);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        String str = sp.getString("DEVICE1", "00:00:00:00:00:00");

        tvAddr.setText(str);
        tvDeg = (TextView) findViewById(R.id.tvDegree);
        tvDeg.setText("00.00");

        scanning = false;
        handler = new Handler();
        callBack = new MyScanCallBack();
    }

    @Override
    protected void onPause() {
        if (BTGatt != null) {
            BTGatt.disconnect();
            BTGatt.close();
        }
        super.onPause();
    }

    public BluetoothGattCallback GattCallback = new BluetoothGattCallback() {
        public void SetupSensorStep(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            BluetoothGattDescriptor descriptor;

            for (BluetoothGattCharacteristic c : gatt.getService(UUID_IRT_SERV).getCharacteristics())
            {
                output(c.getUuid().toString());

                for (BluetoothGattDescriptor desc : c.getDescriptors())
                {
                    output("desc:" + desc.getUuid().toString());
                }
            }

            // Enable local notifications
            characteristic = gatt.getService(UUID_IRT_SERV).getCharacteristic(UUID_IRT_DATA);
            gatt.setCharacteristicNotification(characteristic, true);
            // Enabled remote notifications
            descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);

        }

        // 偵測GATT client連線或斷線
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                output("Connected to GATT Server");
                gatt.discoverServices();
            } else {
                output("Disconnected from GATT Server");
                gatt.disconnect();
                gatt.close();
            }
        }

        //發現新的服務
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            output("Discover & Config GATT Services");

            SetupSensorStep(gatt);
        }


        //遠端特徵通知結果
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            if (UUID_IRT_DATA.equals(characteristic.getUuid())) {

                byte[] b = new byte[characteristic.getValue().length];
                b = characteristic.getValue();
                Byte b1 = b[2];
                Byte b2 = b[1];
                int t = b1 * 255 + b2;

                output(gatt.getDevice().getAddress() + ":DATA:" + bytesToHex(b) + ",Temp:" + t);
                String tstr = String.valueOf(t);
                tvDeg.setText(tstr.substring(0, tstr.length()-1) + "." + tstr.substring(tstr.length()-1));


            }
        }
    };

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void BTScan() {
        //檢查設備上是否支持藍牙
        if (BTAdapter == null) {
            output("No Bluetooth Adapter");
            return;
        }

        if (!BTAdapter.isEnabled()) {
            BTAdapter.enable();
        }

        //搜尋BLE藍牙裝置
        if (scanning == false) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    scanning = false;
                    // BTAdapter.stopLeScan(DeviceLeScanCallback);
                    scanner.stopScan(callBack);

                    output("Stop scanning");
                }
            }, 5000);

            scanning = true;
            BTDevice = null;
            if (BTGatt != null) {
                BTGatt.disconnect();
                BTGatt.close();
            }
            BTGatt = null;

            // BTAdapter.startLeScan(DeviceLeScanCallback);
            scanner.startScan(callBack);

            output("Start scanning");
        }
    }

    //按鍵事件
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonScan:
                BTScan();
                break;
            case R.id.buttonClear:
                clear();
                break;
        }
    }

    //訊息輸出到TextView
    public void output(String msg) {
        console.output(msg);
    }
    //清除TextView
    public void clear() {
        console.clear();
    }

    //選單(EXIT)
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_exit:
                if (BTGatt != null) {
                    BTGatt.disconnect();
                    BTGatt.close();
                }
                finish();
                System.exit(0);
                break;
            case R.id.action_select:
                if (BTGatt != null) {
                    BTGatt.disconnect();
                    BTGatt.close();
                }
                Intent it = new Intent(MainActivity.this, DevicesActivity.class);
                startActivity(it);
                break;
        }

        return super.onOptionsItemSelected(item);
    }
    class MyScanCallBack extends ScanCallback
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            output(result.toString());
            if (DeviceName.equals(result.getDevice().getName())) {
                if (BTDevice == null) {
                    BTDevice = result.getDevice();

                    String addr = sp.getString("DEVICE1", "00:00:00:00:00:00");
                    Log.d("TDATA", addr);
                    if (BTDevice.getAddress().equals(addr))
                    {
                        tvStatus.setText("Found, Receiving");
                        BTGatt = BTDevice.connectGatt(getApplicationContext(), false, GattCallback); // 連接GATT
                    }
                } else {
                    if (BTDevice.getAddress().equals(result.getDevice().getAddress())) {
                        return;
                    }
                }
                output("*<small> " + result.getDevice().getName() + ":" + result.getDevice().getAddress() + "</small>");
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            output("on Batch results");
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            output("Scan fail");
            super.onScanFailed(errorCode);
        }
    }
}

