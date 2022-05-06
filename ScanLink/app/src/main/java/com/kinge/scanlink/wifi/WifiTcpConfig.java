package com.kinge.scanlink.wifi;

//存储wifi的名称密码以及tcp的ip和端口
public class WifiTcpConfig {
    public String getSsid() {
        return ssid;
    }

    public String getPassword() {
        return password;
    }

    public String getIp() {
        return ip;
    }

    public String getPort() {
        return port;
    }

    private final String ssid;
    private final String password;
    private final String ip;
    private final String port;

    public WifiTcpConfig(String ssid, String password, String ip, String port) {
        this.ssid = ssid;
        this.password = password;
        this.ip = ip;
        this.port = port;
    }

    public static WifiTcpConfig parseString(String s) {

        String[] ss = s.split(",");
        if (ss.length < 4) {
            return null;
        }
        return new WifiTcpConfig(ss[0], ss[1], ss[2], ss[3]);
    }
}
