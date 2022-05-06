package com.kinge.scanlink;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.eurigo.wifilib.WifiUtils;
import com.kinge.scanlink.R;
import com.kinge.scanlink.wifi.WiFiTcpUtil;
import com.kinge.scanlink.wifi.WifiTcpConfig;
import com.kinge.scanlink.zxing.android.CaptureActivity;

import java.util.List;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TextView tvTcpReceive;//tcp接收数据显示
    private TextView tvTCPStatus;//tcp连接显示
    private EditText etTcpSend;//tcp发送数据
    private TextView tvWifiStatus;//wifi连接显示


    private LocalBroadcastManager localBroadcastManager;//本地广播管理器
    private MyLocalBroadcastReceiver localBroadcastReceiver;//广播接收者
    private int connectingCount = 0;//用来刷新 正在连接 与 正 在 连 接
    private WifiTcpConfig wifiTcpConfig;
    private MyWifiBroadcastReceiver myWifiBroadcastReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //获取wifi权限
        requireWifiPermissions();
        //控件初始化
        weightInit();
        //广播注册
        registerBroadcastReceiver();
    }

    /**
     * 广播注册
     */
    private void registerBroadcastReceiver() {
        IntentFilter tcpIntentFilter = new IntentFilter();
        tcpIntentFilter.addAction("WiFiTcpUtil.Connecting");//正在连接
        tcpIntentFilter.addAction("WiFiTcpUtil.Connect.Succeed");//连接成功
        tcpIntentFilter.addAction("WiFiTcpUtil.Connect.Fail");//连接失败
        tcpIntentFilter.addAction("WiFiTcpUtil.Connect.ReceiveMessage");//接收到数据
        tcpIntentFilter.addAction("WiFiTcpUtil.Disconnected");//接收到数据

        localBroadcastReceiver = new MyLocalBroadcastReceiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        WiFiTcpUtil.localBroadcastManager = localBroadcastManager;//给WiFiTcpUtil工具类中的本地广播管理器赋值
        localBroadcastManager.registerReceiver(localBroadcastReceiver, tcpIntentFilter);

        //wifi连接改变广播
        IntentFilter wifiIntentFilter = new IntentFilter();
        wifiIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        myWifiBroadcastReceiver = new MyWifiBroadcastReceiver();
        registerReceiver(myWifiBroadcastReceiver, wifiIntentFilter);
    }

    private void weightInit() {
        //初始化ui控件
        tvTcpReceive = findViewById(R.id.tv_tcpReceive);
        Button btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(this);
        Button btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(this);
        tvTCPStatus = findViewById(R.id.tv_tcpStatus);
        etTcpSend = findViewById(R.id.et_tcpSend);
        tvWifiStatus = findViewById(R.id.tv_wifiStatus);

        //初始化WiFi
        WifiUtils.getInstance().init(this.getApplicationContext());
    }


    /*
     *软件启动时权限获取
     */
    private void requireWifiPermissions() {

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 2);
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE}, 3);

        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CHANGE_WIFI_STATE}, 4);

        }


        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CHANGE_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CHANGE_NETWORK_STATE}, 5);

        }


        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 6);
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //注销广播
        localBroadcastManager.unregisterReceiver(localBroadcastReceiver);
        unregisterReceiver(myWifiBroadcastReceiver);
        //关闭Socket释放资源
        WiFiTcpUtil.closeSocketAndStream();
        //释放wifi对象
        WifiUtils.getInstance().release();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_scan) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
            } else {
                goScan();
            }
        }

        if (view.getId() == R.id.btn_send) {
            if (WiFiTcpUtil.mSocket == null) {
                Toast.makeText(this, "未连接任何设备", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(etTcpSend.getText().toString())) {
                Toast.makeText(this, "发送内容不为空", Toast.LENGTH_SHORT).show();
                return;
            }
            //发送数据
            WiFiTcpUtil.sendData(etTcpSend.getText().toString());
        }
    }

    /**
     * 跳转到扫码界面扫码
     */
    private void goScan() {
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityIfNeeded(intent, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    goScan();
                } else {
                    Toast.makeText(this, "拍照权限申请已拒绝，无法打开相机扫码", Toast.LENGTH_SHORT).show();
                }
                break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "WIFI和网络权限申请已拒绝，无法正常使用", Toast.LENGTH_SHORT).show();
                    //退出
                    onDestroy();
                }
                break;
        }
    }

    /*
     *判断wifi中是否有需要连接的wifi
     */

    private boolean isContainWifi(String ssid) {
        List<ScanResult> wifiList = WifiUtils.getInstance().getWifiList();
        for (ScanResult wifi : wifiList) {
            if (wifi.SSID.equals(ssid)) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 扫描二维码/条码回传
        if (requestCode == 0 && resultCode == RESULT_OK) {
            if (data != null) {
                //返回的文本内容
                String content = data.getStringExtra("codedContent");

                //解析wifi和tcp连接的参数
                wifiTcpConfig = WifiTcpConfig.parseString(content);

                //打开wifi
                if (!WifiUtils.getInstance().isWifiEnable()) {
                    WifiUtils.getInstance().openWifi();
                }

                //等待打开
                while (!WifiUtils.getInstance().isWifiEnable()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //如果不包含所选wifi，则退出
                if (!isContainWifi(wifiTcpConfig.getSsid())) {
                    Toast.makeText(this, "目标设备AP未开启，请检查目标设备", Toast.LENGTH_SHORT).show();
                    return;
                }

                //连接wifi
                if (!WifiUtils.getInstance().isConnectedTargetSsid(wifiTcpConfig.getSsid())) {
                    WifiUtils.getInstance().connectWifi(this, wifiTcpConfig.getSsid(), wifiTcpConfig.getPassword());
                }

                //等待连接
                while (!WifiUtils.getInstance().isConnectedTargetSsid(wifiTcpConfig.getSsid())) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //wifi连接标志开启
                WiFiTcpUtil.connectFlag = true;
                //进行tcp连接
                WiFiTcpUtil.connectByTCP(wifiTcpConfig.getIp(), Integer.parseInt(wifiTcpConfig.getPort()));
            }
        }
    }

    /**
     * 本地广播接收者
     */
    class MyLocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "WiFiTcpUtil.Connecting":
                    connectingCount++;
                    if (connectingCount % 2 == 0)
                        tvTCPStatus.setText("正在连接");
                    else
                        tvTCPStatus.setText("正 在 连 接");
                    break;
                case "WiFiTcpUtil.Connect.Succeed":
                    tvTCPStatus.setText(TextUtils.concat("TCP已连接", wifiTcpConfig.getIp()));
                    break;
                case "WiFiTcpUtil.Connect.Fail":
                    tvTCPStatus.setText("TCP连接失败");
                    break;
                case "WiFiTcpUtil.Connect.ReceiveMessage":
                    tvTcpReceive.setText(WiFiTcpUtil.DataReceive);
                    break;
                case "WiFiTcpUtil.Disconnected":
                    tvTCPStatus.setText("TCP未连接");
                    Toast.makeText(context, "连接已断开，请重新进行连接", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    /**
     * wifi连接状态改变接收者
     */
    class MyWifiBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                Parcelable parcelableExtra = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (null != parcelableExtra) {
                    NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                    if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        tvWifiStatus.setText(TextUtils.concat("WIFI已连接", WifiUtils.getInstance().getSsid()));
                    }
                }
            }

            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                switch (wifiState) {
                    case WifiManager.WIFI_STATE_DISABLED:
                        tvWifiStatus.setText("WIFI已关闭");
                        break;
                    case WifiManager.WIFI_STATE_ENABLED:
                        if ("<unknown ssid>".equals(WifiUtils.getInstance().getSsid())) {
                            tvWifiStatus.setText("WIFI已打开");
                        } else {
                            tvWifiStatus.setText(TextUtils.concat("WIFI已连接", WifiUtils.getInstance().getSsid()));
                        }

                        break;
                    case WifiManager.WIFI_STATE_ENABLING:
                        tvWifiStatus.setText("WIFI正在打开");
                        break;
                    case WifiManager.WIFI_STATE_DISABLING:
                        tvWifiStatus.setText("WIFI正在关闭");
                        break;
                    default:
                        break;
                }
            }
        }
    }
}