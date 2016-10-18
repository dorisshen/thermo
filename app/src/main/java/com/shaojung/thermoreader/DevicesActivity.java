package com.shaojung.thermoreader;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DevicesActivity extends AppCompatActivity {
    public UUID UUID_IRT_SERV = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    public UUID UUID_IRT_DATA = UUID.fromString("00002a1e-0000-1000-8000-00805f9b34fb");
    public UUID UUID_IRT_CONF = UUID.fromString("00002906-0000-1000-8000-00805f9b34fb"); // 0: disable,1: enable

    public UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //定義手機的UUID

    public String DeviceName = "ThermocoupleSensor";
    public BluetoothAdapter BTAdapter;
    public BluetoothDevice BTDevice;
    public BluetoothGatt BTGatt;
    public BluetoothLeScanner scanner;

    public DevicesActivity.MyScanCallBack callBack;

    public boolean scanning;
    public Handler handler;
    public Console console;
    ProgressDialog pd;
    ArrayList<String> addrs;
    ArrayAdapter<String> adapter;
    SharedPreferences sp;

    ListView lv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);
        //初始化Bluetooth adapter，透過BluetoothManager得到一個參考Bluetooth adapter
        BluetoothManager BTManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BTAdapter = BTManager.getAdapter();
        scanner = BTAdapter.getBluetoothLeScanner();
        addrs = new ArrayList<>();
        handler = new Handler();
        callBack = new MyScanCallBack();
        sp = getSharedPreferences("device", MODE_PRIVATE);
        lv = (ListView) findViewById(R.id.deviceList);
        adapter = new ArrayAdapter<String>(DevicesActivity.this,
                        android.R.layout.simple_list_item_1,
                        addrs);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        SharedPreferences.Editor ed = sp.edit();
                        ed.putString("DEVICE1", addrs.get(position));
                        ed.commit();
                        finish();
                    }
                }
        );
        BTScan();
    }
    public void BTScan() {
        //檢查設備上是否支持藍牙
        if (BTAdapter == null) {
            Toast.makeText(DevicesActivity.this, "No Bluetooth Adapter", Toast.LENGTH_SHORT).show();
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
                    scanner.stopScan(callBack);
                    pd.dismiss();
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
            pd = new ProgressDialog(DevicesActivity.this);
            pd.setMessage("Scanning... Please wait...");
            pd.setTitle("BT Scanning");
            pd.show();
        }
    }

    class MyScanCallBack extends ScanCallback
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (DeviceName.equals(result.getDevice().getName())) {
                if (BTDevice == null) {
                    BTDevice = result.getDevice();
                    // BTGatt = BTDevice.connectGatt(getApplicationContext(), false, GattCallback); // 連接GATT
                    addrs.add(result.getDevice().getAddress());
                    adapter.notifyDataSetChanged();
                } else {
                    if (BTDevice.getAddress().equals(result.getDevice().getAddress())) {
                        return;
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {

            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {

            super.onScanFailed(errorCode);
        }
    }
}
