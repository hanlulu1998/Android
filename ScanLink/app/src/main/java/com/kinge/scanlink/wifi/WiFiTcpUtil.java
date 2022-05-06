package com.kinge.scanlink.wifi;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * 正在连接广播Action:"WiFiTcpUtil.Connecting"
 * 连接失败广播Action:"WiFiTcpUtil.Connect.Fail"
 * 连接成功广播Action:"WiFiTcpUtil.Connect.Succeed"
 * 收到数据广播Action:"WiFiTcpUtil.Connect.ReceiveMessage"
 * 连接断开广播Action:"WiFiTcpUtil.Disconnected"
 */
public class WiFiTcpUtil {
    private static String mIp;//硬件的IP
    private static int mPort;//硬件的端口
    public static Socket mSocket = null;//连接成功可得到的Socket
    public static OutputStream outputStream = null;//定义输出流
    public static InputStream inputStream = null;//定义输入流
    public static String DataReceive = null;//数据
    public static List<String> DataList = new ArrayList<>();//数据
    public static boolean connectFlag = true;//连接成功或连接3s后变false

    /**
     * 本地广播管理器 从外面用:
     * WiFiTcpUtil.localBroadcastManager=localBroadcastManager;
     * 进行赋值
     */
    public static LocalBroadcastManager localBroadcastManager;

    /**
     * 处理消息的Handler
     */
    @SuppressLint("HandlerLeak")
    public static Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0://接收到数据
                    DataReceive = msg.obj.toString();
                    DataList.add(msg.obj.toString());//添加数据
                    Intent intent = new Intent("WiFiTcpUtil.Connect.ReceiveMessage");
                    localBroadcastManager.sendBroadcast(intent);//发送收到数据广播
                    break;
                case 1://连接成功
                    Intent intent2 = new Intent("WiFiTcpUtil.Connect.Succeed");
                    localBroadcastManager.sendBroadcast(intent2);//发送连接成功广播
                    readData();//开启接收线程
                    connectFlag = true;
                    break;
                case 2://连接断开
                    Intent intent3 = new Intent("WiFiTcpUtil.Disconnected");
                    localBroadcastManager.sendBroadcast(intent3);//发送连接失败广播
                    connectFlag = true;
                    break;
            }
        }
    };

    /***
     * 延时3s的定时器
     * 在开始连接时计时3s
     * 3s未连接上视为连接失败
     */
    private final static CountDownTimer tcpClientCountDownTimer = new CountDownTimer(3000, 300) {
        @Override
        public void onTick(long millisUntilFinished) {//每隔300ms进入
            if (connectFlag) {
                Intent intent = new Intent("WiFiTcpUtil.Connecting");
                localBroadcastManager.sendBroadcast(intent);
            }
        }

        @Override
        public void onFinish() {//3s后进入(没有取消定时器的情况下)
            if (connectFlag) {
                connectFlag = false;//连接失败
                closeSocketAndStream();
            }
            tcpClientCountDownTimer.cancel();//关掉定时器
            Intent intent = new Intent("WiFiTcpUtil.Connect.Fail");
            localBroadcastManager.sendBroadcast(intent);
        }
    };

    /**
     * 关掉Socket和输入输出流
     */
    public static void closeSocketAndStream() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = null;
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = null;
        }
    }

    /**
     * 连接服务器任务
     */
    static class ConnectSeverThread extends Thread {
        @Override
        public void run() {
            while (connectFlag) {
                try {
                    mSocket = new Socket(mIp, mPort);//进行连接
                    connectFlag = false;//已连接
                    tcpClientCountDownTimer.cancel();//关掉计时器
                    /*连接成功更新显示连接状态的UI*/
                    Message msg = new Message();
                    msg.what = 1;
                    mHandler.sendMessage(msg);
                    inputStream = mSocket.getInputStream();//获取输入流
                    outputStream = mSocket.getOutputStream();//获取输出流
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 传入硬件服务端IP建立TCP连接
     * 正在连接每200ms          发送一条‘正在连接’广播
     * 3秒后还未连接则连接失败    发送一条‘失败广播’
     * 连接成功会              发送一条‘成功广播’
     *
     * @param IPAddress
     */
    public static void connectByTCP(String IPAddress, int Port) {
        mIp = IPAddress;
        mPort = Port;
        ConnectSeverThread connectSeverThread = new ConnectSeverThread();
        connectSeverThread.start();
        tcpClientCountDownTimer.start();
    }

    /**
     * 向硬件发送数据
     */
    public static void sendData(String data) {
        if (mSocket != null) {
            byte[] sendByte = data.getBytes();
            new Thread() {
                @Override
                public void run() {
                    try {
                        DataOutputStream writer = new DataOutputStream(outputStream);
                        writer.write(sendByte, 0, sendByte.length);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    /**
     * 连接成功后开启
     * 接收硬件发送的数据
     */
    public static void readData() {
        new Thread() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(200);
                        //如果连接断开 尝试重连
                        try {
                            /*
                                sendUrgentData()方法
                                它会往输出流发送一个字节的数据，
                                只要对方Socket的SO_OOBINLINE属性没有打开，
                                就会自动舍弃这个字节，
                                就会抛出异常，
                                而SO_OOBINLINE属性默认情况下就是关闭的
                             */
                            mSocket.sendUrgentData(0xFF);//发送1个字节的紧急数据，默认情况下，服务器端没有开启紧急数据处理，不影响正常通信
                        } catch (Exception ex) {
                            Message msg = new Message();
                            msg.what = 2;
                            msg.obj = "连接已断开，请重新进行连接";
                            mHandler.sendMessage(msg);
                        }
                        DataInputStream reader = new DataInputStream(inputStream);
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = reader.read(buffer)) != -1) {
                            String data = new String(buffer, 0, len);
                            Message msg = new Message();
                            msg.what = 0;
                            msg.obj = data;
                            mHandler.sendMessage(msg);
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
